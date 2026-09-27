from __future__ import annotations

import asyncio
import ipaddress
import json
import re
import socket
from typing import Any
from urllib.parse import urljoin, urlparse

import httpx
from bs4 import BeautifulSoup
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, HttpUrl

app = FastAPI(title="RecipeBox Secure Extractor", version="0.1.0")

MAX_HTML_BYTES = 2_500_000
MAX_REDIRECTS = 5
TIMEOUT = httpx.Timeout(20.0, connect=7.0)
ALLOWED_CONTENT_TYPES = ("text/html", "application/xhtml+xml")
VIDEO_HOST_HINTS = ("youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "instagram.com", "bilibili.com")

class ExtractRequest(BaseModel):
    url: HttpUrl

class ExtractedRecipe(BaseModel):
    title: str
    description: str = ""
    servings: float | None = None
    prep_minutes: int | None = None
    cook_minutes: int | None = None
    source_name: str = ""
    source_url: str
    video_url: str = ""
    image_url: str = ""
    ingredients: list[str] = Field(default_factory=list)
    steps: list[str] = Field(default_factory=list)
    categories: list[str] = Field(default_factory=list)


def is_public_ip(ip_text: str) -> bool:
    try:
        ip = ipaddress.ip_address(ip_text)
        return not (
            ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast
            or ip.is_reserved or ip.is_unspecified
        )
    except ValueError:
        return False

async def validate_public_url(raw: str) -> str:
    parsed = urlparse(raw)
    if parsed.scheme not in ("http", "https"):
        raise HTTPException(400, "Only http/https URLs are accepted")
    if parsed.username or parsed.password:
        raise HTTPException(400, "URLs with embedded credentials are rejected")
    if not parsed.hostname:
        raise HTTPException(400, "Invalid URL")
    host = parsed.hostname.rstrip(".")
    loop = asyncio.get_running_loop()
    try:
        infos = await loop.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80), type=socket.SOCK_STREAM)
    except socket.gaierror:
        raise HTTPException(400, "Host could not be resolved")
    ips = {info[4][0] for info in infos}
    if not ips or any(not is_public_ip(ip) for ip in ips):
        raise HTTPException(400, "Private, local, reserved, or unsafe network destinations are blocked")
    return raw


def clean_text(value: Any, max_len: int = 12_000) -> str:
    if value is None:
        return ""
    if isinstance(value, dict):
        value = value.get("text") or value.get("name") or ""
    text = BeautifulSoup(str(value), "html.parser").get_text(" ", strip=True)
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:max_len]


def parse_duration_minutes(value: Any) -> int | None:
    if not isinstance(value, str):
        return None
    # ISO 8601 subset: PT1H20M, PT45M
    h = re.search(r"(\d+)H", value)
    m = re.search(r"(\d+)M", value)
    total = (int(h.group(1)) * 60 if h else 0) + (int(m.group(1)) if m else 0)
    return total or None


def parse_servings(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, list) and value:
        value = value[0]
    if isinstance(value, str):
        m = re.search(r"\d+(?:\.\d+)?", value)
        if m:
            return float(m.group())
    return None


def safe_http_url(value: Any, base: str = "") -> str:
    if isinstance(value, list):
        value = value[0] if value else ""
    if isinstance(value, dict):
        value = value.get("url") or value.get("contentUrl") or value.get("embedUrl") or ""
    if not isinstance(value, str) or not value.strip():
        return ""
    absolute = urljoin(base, value.strip())
    p = urlparse(absolute)
    return absolute if p.scheme in ("http", "https") and p.hostname else ""


def find_recipe_node(value: Any) -> dict[str, Any] | None:
    if isinstance(value, dict):
        type_value = value.get("@type")
        types = [type_value] if isinstance(type_value, str) else (type_value or [])
        if any(str(t).lower() == "recipe" for t in types):
            return value
        for v in value.values():
            found = find_recipe_node(v)
            if found:
                return found
    elif isinstance(value, list):
        for item in value:
            found = find_recipe_node(item)
            if found:
                return found
    return None


def extract_steps(value: Any) -> list[str]:
    out: list[str] = []
    if isinstance(value, str):
        out.extend([x.strip() for x in re.split(r"\n+", clean_text(value)) if x.strip()])
    elif isinstance(value, list):
        for item in value:
            if isinstance(item, str):
                t = clean_text(item)
                if t: out.append(t)
            elif isinstance(item, dict):
                t = clean_text(item.get("text") or item.get("name"))
                if t: out.append(t)
                elif item.get("itemListElement"):
                    out.extend(extract_steps(item.get("itemListElement")))
    elif isinstance(value, dict):
        out.extend(extract_steps(value.get("itemListElement") or value.get("text")))
    return out[:100]


def categories(node: dict[str, Any]) -> list[str]:
    values: list[str] = []
    for key in ("recipeCategory", "recipeCuisine", "keywords"):
        v = node.get(key)
        if isinstance(v, str):
            values.extend([x.strip() for x in re.split(r"[,;]", v) if x.strip()])
        elif isinstance(v, list):
            values.extend(clean_text(x, 80) for x in v)
    return list(dict.fromkeys(x for x in values if x))[:20]


def extract_video(node: dict[str, Any], soup: BeautifulSoup, base: str) -> str:
    v = node.get("video")
    if v:
        url = safe_http_url(v, base)
        if url: return url
        if isinstance(v, dict):
            for key in ("contentUrl", "embedUrl", "url"):
                url = safe_http_url(v.get(key), base)
                if url: return url
    for iframe in soup.find_all("iframe", src=True, limit=20):
        url = safe_http_url(iframe.get("src"), base)
        host = urlparse(url).hostname or ""
        if any(h in host for h in VIDEO_HOST_HINTS):
            return url
    return ""

async def fetch_html(start_url: str) -> tuple[str, str]:
    current = await validate_public_url(start_url)
    headers = {
        "User-Agent": "RecipeBox/0.1 (+recipe metadata importer)",
        "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1",
        "Accept-Encoding": "gzip, deflate",
    }
    async with httpx.AsyncClient(timeout=TIMEOUT, follow_redirects=False, headers=headers, trust_env=False) as client:
        for _ in range(MAX_REDIRECTS + 1):
            current = await validate_public_url(current)
            async with client.stream("GET", current) as response:
                if response.status_code in (301, 302, 303, 307, 308):
                    location = response.headers.get("location")
                    if not location:
                        raise HTTPException(400, "Redirect without destination")
                    current = urljoin(current, location)
                    continue
                if response.status_code >= 400:
                    raise HTTPException(422, f"Source returned HTTP {response.status_code}")
                ctype = response.headers.get("content-type", "").lower()
                if not any(x in ctype for x in ALLOWED_CONTENT_TYPES):
                    raise HTTPException(415, "URL did not return an HTML page")
                data = bytearray()
                async for chunk in response.aiter_bytes():
                    data.extend(chunk)
                    if len(data) > MAX_HTML_BYTES:
                        raise HTTPException(413, "Page is too large to import safely")
                encoding = response.encoding or "utf-8"
                return data.decode(encoding, errors="replace"), str(response.url)
        raise HTTPException(400, "Too many redirects")

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}

@app.post("/extract", response_model=ExtractedRecipe)
async def extract(req: ExtractRequest) -> ExtractedRecipe:
    html, final_url = await fetch_html(str(req.url))
    soup = BeautifulSoup(html, "html.parser")

    recipe_node = None
    for script in soup.find_all("script", attrs={"type": re.compile(r"application/ld\+json", re.I)}):
        raw = script.string or script.get_text()
        if not raw or len(raw) > 1_000_000:
            continue
        try:
            parsed = json.loads(raw)
        except Exception:
            continue
        recipe_node = find_recipe_node(parsed)
        if recipe_node:
            break

    host = urlparse(final_url).hostname or ""
    source_name = host.removeprefix("www.")

    if recipe_node:
        ingredients_raw = recipe_node.get("recipeIngredient") or recipe_node.get("ingredients") or []
        if isinstance(ingredients_raw, str):
            ingredients_raw = [ingredients_raw]
        ingredients = [clean_text(x, 500) for x in ingredients_raw if clean_text(x, 500)][:200]
        image = safe_http_url(recipe_node.get("image"), final_url)
        if not image:
            og = soup.find("meta", attrs={"property": "og:image"})
            image = safe_http_url(og.get("content") if og else "", final_url)
        return ExtractedRecipe(
            title=clean_text(recipe_node.get("name"), 300) or clean_text(soup.title.string if soup.title else "Untitled recipe", 300),
            description=clean_text(recipe_node.get("description"), 2000),
            servings=parse_servings(recipe_node.get("recipeYield")),
            prep_minutes=parse_duration_minutes(recipe_node.get("prepTime")),
            cook_minutes=parse_duration_minutes(recipe_node.get("cookTime")),
            source_name=source_name,
            source_url=final_url,
            video_url=extract_video(recipe_node, soup, final_url),
            image_url=image,
            ingredients=ingredients,
            steps=extract_steps(recipe_node.get("recipeInstructions")),
            categories=categories(recipe_node),
        )

    # Safe fallback: preserve source + basic metadata. No JavaScript execution.
    title = ""
    og_title = soup.find("meta", attrs={"property": "og:title"})
    if og_title: title = clean_text(og_title.get("content"), 300)
    if not title and soup.title: title = clean_text(soup.title.string, 300)
    og_desc = soup.find("meta", attrs={"property": "og:description"}) or soup.find("meta", attrs={"name": "description"})
    og_img = soup.find("meta", attrs={"property": "og:image"})
    source_is_video = any(h in host for h in VIDEO_HOST_HINTS)
    return ExtractedRecipe(
        title=title or "Imported link",
        description=clean_text(og_desc.get("content") if og_desc else "", 2000),
        source_name=source_name,
        source_url=final_url,
        video_url=final_url if source_is_video else extract_video({}, soup, final_url),
        image_url=safe_http_url(og_img.get("content") if og_img else "", final_url),
    )
