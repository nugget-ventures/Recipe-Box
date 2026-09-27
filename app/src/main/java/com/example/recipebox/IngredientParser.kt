package com.example.recipebox

object IngredientParser {
    private val unicodeFractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0/3, '⅔' to 2.0/3, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)
    private val units = setOf("tsp", "teaspoon", "teaspoons", "tbsp", "tablespoon", "tablespoons", "cup", "cups", "g", "kg", "mg", "oz", "lb", "lbs", "ml", "l", "clove", "cloves", "can", "cans", "pinch", "片", "杯", "克", "公斤", "毫升", "茶匙", "湯匙", "汤匙")

    fun parse(line: String): Ingredient {
        val clean = line.trim().replace(Regex("^[•●▪☐-]+\\s*"), "")
        if (clean.isBlank()) return Ingredient("")
        val tokens = clean.split(Regex("\\s+"))
        var quantity: Double? = null
        var consumed = 0

        if (tokens.isNotEmpty()) {
            quantity = parseQuantity(tokens[0])
            if (quantity != null) consumed = 1
            if (quantity != null && tokens.size > 1) {
                val second = parseQuantity(tokens[1])
                if (second != null && tokens[1].contains('/')) {
                    quantity += second
                    consumed = 2
                }
            }
        }

        val possibleUnit = tokens.getOrNull(consumed)?.trimEnd(',', '.')?.lowercase().orEmpty()
        val hasUnit = possibleUnit in units
        val unit = if (hasUnit) tokens[consumed] else ""
        val nameStart = consumed + if (hasUnit) 1 else 0
        val name = tokens.drop(nameStart).joinToString(" ").ifBlank { clean }
        return Ingredient(clean, quantity, unit, name)
    }

    private fun parseQuantity(token: String): Double? {
        val t = token.trim().trimEnd(',', '.')
        unicodeFractions[t.singleOrNull()]?.let { return it }
        val mixed = Regex("(\\d+)([½¼¾⅓⅔⅛⅜⅝⅞])").matchEntire(t)
        if (mixed != null) return mixed.groupValues[1].toDouble() + (unicodeFractions[mixed.groupValues[2][0]] ?: 0.0)
        if ('/' in t) {
            val parts = t.split('/')
            if (parts.size == 2) {
                val n = parts[0].toDoubleOrNull()
                val d = parts[1].toDoubleOrNull()
                if (n != null && d != null && d != 0.0) return n / d
            }
        }
        return t.toDoubleOrNull()
    }
}
