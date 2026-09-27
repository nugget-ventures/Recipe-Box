# RecipeBox secure extractor

This service fetches untrusted recipe URLs away from the phone. It rejects non-http(s) schemes, embedded credentials, private/local/reserved IP destinations, excessive redirects, non-HTML responses, oversized pages, and long-running requests. It never executes webpage JavaScript.

## Local run

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8080
```

Production deployment must use HTTPS. For stronger production SSRF protection, run this service in a network sandbox with egress firewall rules that deny RFC1918, link-local, metadata-service and other internal address ranges at the network layer as well as in application code.
