package com.sipsnack.buddy.rules

data class Guess(val kind: LogKind, val confidence: Float)

/** Maps on-device image labels to the four things she logs. Below the threshold she picks herself. */
object Recognition {
    const val MIN_CONFIDENCE = 0.7f

    // Checked in this order, so "coffee in a cup" is a drink before it is water.
    private val keywords = listOf(
        LogKind.OTHER_DRINK to listOf("coffee", "tea", "juice", "soda", "soft drink", "beer", "wine", "cocktail", "milk"),
        LogKind.SNACK to listOf("snack", "cookie", "biscuit", "chips", "candy", "chocolate", "fruit", "nut", "cake", "dessert", "doughnut"),
        LogKind.MEAL to listOf("food", "dish", "meal", "cuisine", "rice", "noodle", "pasta", "soup", "salad", "sandwich", "meat", "pizza", "bread", "vegetable", "lunch", "dinner", "breakfast"),
        LogKind.WATER to listOf("water", "bottle", "glass", "cup", "drink", "tumbler", "mug"),
    )

    fun guess(labels: List<Pair<String, Float>>): Guess? {
        val confident = labels.filter { it.second >= MIN_CONFIDENCE }
        for ((kind, words) in keywords) {
            val hit = confident.filter { (label, _) -> words.any { label.lowercase().contains(it) } }.maxByOrNull { it.second }
            if (hit != null) return Guess(kind, hit.second)
        }
        return null
    }
}
