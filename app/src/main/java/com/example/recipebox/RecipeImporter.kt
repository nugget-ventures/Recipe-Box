package com.example.recipebox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RecipeImporter {
    suspend fun import(url: String): Result<Recipe> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUrl = SecureUrl.validate(url).getOrThrow()
            val base = BuildConfig.EXTRACTOR_BASE_URL.trimEnd('/')
            require(base.startsWith("https://")) {
                "Secure extractor is not configured yet. Set EXTRACTOR_BASE_URL to your deployed HTTPS server."
            }
            val endpoint = URL("$base/extract")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().put("url", safeUrl).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(JSONObject(text).optString("detail", "Import failed ($code)"))
            fromApi(JSONObject(text), safeUrl)
        }
    }

    private fun fromApi(o: JSONObject, originalUrl: String): Recipe {
        val ingredientTexts = o.optJSONArray("ingredients")
        val ingredients = buildList {
            if (ingredientTexts != null) for (i in 0 until ingredientTexts.length()) {
                add(IngredientParser.parse(ingredientTexts.optString(i)))
            }
        }
        val stepsJson = o.optJSONArray("steps")
        val steps = buildList {
            if (stepsJson != null) for (i in 0 until stepsJson.length()) add(stepsJson.optString(i))
        }
        val categoriesJson = o.optJSONArray("categories")
        val categories = buildSet {
            if (categoriesJson != null) for (i in 0 until categoriesJson.length()) {
                categoriesJson.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return Recipe(
            title = o.optString("title", "Imported recipe"),
            description = o.optString("description"),
            servings = o.optDouble("servings").takeIf { !it.isNaN() && it > 0 },
            prepMinutes = o.optInt("prep_minutes").takeIf { it > 0 },
            cookMinutes = o.optInt("cook_minutes").takeIf { it > 0 },
            sourceName = o.optString("source_name"),
            sourceUrl = o.optString("source_url", originalUrl),
            videoUrl = o.optString("video_url"),
            imageUrl = o.optString("image_url"),
            ingredients = ingredients,
            steps = steps,
            categories = categories
        )
    }
}
