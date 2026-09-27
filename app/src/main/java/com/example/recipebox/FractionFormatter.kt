package com.example.recipebox

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

object FractionFormatter {
    private val fractions = listOf(
        0.125 to "⅛", 0.25 to "¼", 1.0 / 3.0 to "⅓", 0.375 to "⅜",
        0.5 to "½", 0.625 to "⅝", 2.0 / 3.0 to "⅔", 0.75 to "¾", 0.875 to "⅞"
    )

    fun format(value: Double): String {
        if (value <= 0.0) return "0"
        val whole = floor(value).toInt()
        val rem = value - whole
        val best = fractions.minByOrNull { abs(it.first - rem) }
        if (best != null && abs(best.first - rem) < 0.035) {
            return when {
                whole == 0 -> best.second
                best.first > 0.84 && rem > 0.96 -> (whole + 1).toString()
                else -> "$whole${best.second}"
            }
        }
        if (abs(value - value.roundToInt()) < 0.01) return value.roundToInt().toString()
        return "%.2f".format(value).trimEnd('0').trimEnd('.')
    }
}
