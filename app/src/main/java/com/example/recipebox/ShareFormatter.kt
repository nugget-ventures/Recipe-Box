package com.example.recipebox

object ShareFormatter {
    fun format(recipe: Recipe, multiplier: Double = 1.0): String = buildString {
        appendLine("🍳 ${recipe.title}")
        recipe.servings?.let { appendLine("Serves ${FractionFormatter.format(it * multiplier)}") }
        if (recipe.prepMinutes != null || recipe.cookMinutes != null) {
            appendLine(listOfNotNull(recipe.prepMinutes?.let { "Prep ${it}m" }, recipe.cookMinutes?.let { "Cook ${it}m" }).joinToString(" • "))
        }
        appendLine()
        if (recipe.ingredients.isNotEmpty()) {
            appendLine("INGREDIENTS")
            recipe.ingredients.forEach { appendLine("• ${it.scaled(multiplier)}") }
            appendLine()
        }
        if (recipe.steps.isNotEmpty()) {
            appendLine("INSTRUCTIONS")
            recipe.steps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
            appendLine()
        }
        if (recipe.notes.isNotBlank()) {
            appendLine("MY NOTES")
            appendLine(recipe.notes)
            appendLine()
        }
        if (recipe.sourceUrl.isNotBlank()) appendLine("Original: ${recipe.sourceUrl}")
        if (recipe.videoUrl.isNotBlank()) appendLine("Video: ${recipe.videoUrl}")
    }.trim()
}
