package com.example.recipebox

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Ingredient(
    val originalText: String,
    val quantity: Double? = null,
    val unit: String = "",
    val name: String = originalText
) {
    fun scaled(multiplier: Double): String {
        val q = quantity ?: return originalText
        val scaled = q * multiplier
        val formatted = FractionFormatter.format(scaled)
        return listOf(formatted, unit, name).filter { it.isNotBlank() }.joinToString(" ")
    }
}

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val servings: Double? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val sourceName: String = "",
    val sourceUrl: String = "",
    val videoUrl: String = "",
    val imageUrl: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val categories: Set<String> = emptySet(),
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isUserRecipe: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

fun Recipe.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("description", description)
    put("servings", servings ?: JSONObject.NULL)
    put("prepMinutes", prepMinutes ?: JSONObject.NULL)
    put("cookMinutes", cookMinutes ?: JSONObject.NULL)
    put("sourceName", sourceName)
    put("sourceUrl", sourceUrl)
    put("videoUrl", videoUrl)
    put("imageUrl", imageUrl)
    put("ingredients", JSONArray().apply {
        ingredients.forEach { ingredient ->
            put(JSONObject().apply {
                put("originalText", ingredient.originalText)
                put("quantity", ingredient.quantity ?: JSONObject.NULL)
                put("unit", ingredient.unit)
                put("name", ingredient.name)
            })
        }
    })
    put("steps", JSONArray(steps))
    put("categories", JSONArray(categories.toList()))
    put("notes", notes)
    put("isFavorite", isFavorite)
    put("isUserRecipe", isUserRecipe)
    put("createdAt", createdAt)
}

fun recipeFromJson(o: JSONObject): Recipe {
    val ingredients = buildList {
        val arr = o.optJSONArray("ingredients") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            add(
                Ingredient(
                    originalText = item.optString("originalText"),
                    quantity = item.opt("quantity").let { if (it == null || it == JSONObject.NULL) null else (it as Number).toDouble() },
                    unit = item.optString("unit"),
                    name = item.optString("name")
                )
            )
        }
    }
    val steps = buildList {
        val arr = o.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until arr.length()) add(arr.optString(i))
    }
    val categories = buildSet {
        val arr = o.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until arr.length()) add(arr.optString(i))
    }
    return Recipe(
        id = o.optString("id", UUID.randomUUID().toString()),
        title = o.optString("title", "Untitled recipe"),
        description = o.optString("description"),
        servings = o.opt("servings").let { if (it == null || it == JSONObject.NULL) null else (it as Number).toDouble() },
        prepMinutes = o.opt("prepMinutes").let { if (it == null || it == JSONObject.NULL) null else (it as Number).toInt() },
        cookMinutes = o.opt("cookMinutes").let { if (it == null || it == JSONObject.NULL) null else (it as Number).toInt() },
        sourceName = o.optString("sourceName"),
        sourceUrl = o.optString("sourceUrl"),
        videoUrl = o.optString("videoUrl"),
        imageUrl = o.optString("imageUrl"),
        ingredients = ingredients,
        steps = steps,
        categories = categories,
        notes = o.optString("notes"),
        isFavorite = o.optBoolean("isFavorite"),
        isUserRecipe = o.optBoolean("isUserRecipe"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}
