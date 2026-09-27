package com.example.recipebox

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

class RecipeStore(private val context: Context) {
    private val file = File(context.filesDir, "recipes-v1.json")
    private val _recipes = MutableStateFlow(load())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    @Synchronized
    fun upsert(recipe: Recipe) {
        val list = _recipes.value.toMutableList()
        val idx = list.indexOfFirst { it.id == recipe.id }
        if (idx >= 0) list[idx] = recipe else list.add(0, recipe)
        _recipes.value = list.sortedByDescending { it.createdAt }
        persist()
    }

    @Synchronized
    fun delete(id: String) {
        _recipes.value = _recipes.value.filterNot { it.id == id }
        persist()
    }

    fun exportJson(): String = JSONArray().apply { _recipes.value.forEach { put(it.toJson()) } }.toString(2)

    @Synchronized
    fun importJson(text: String) {
        val arr = JSONArray(text)
        val imported = buildList {
            for (i in 0 until arr.length()) add(recipeFromJson(arr.getJSONObject(i)))
        }
        _recipes.value = imported.sortedByDescending { it.createdAt }
        persist()
    }

    private fun load(): List<Recipe> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        val arr = JSONArray(file.readText())
        buildList { for (i in 0 until arr.length()) add(recipeFromJson(arr.getJSONObject(i))) }
    }.getOrDefault(emptyList())

    private fun persist() {
        val temp = File(context.filesDir, "recipes-v1.json.tmp")
        temp.writeText(exportJson())
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }
}
