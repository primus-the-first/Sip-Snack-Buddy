package com.sipsnack.buddy.rules

import kotlin.random.Random

/** How much her buddy sees. She picks; progress only is the default. */
enum class ShareLevel { PROGRESS, WITH_MEALS }

/** One log as the buddy sees it: when and what, never labels or photos. */
data class SharedEvent(val atMillis: Long, val kind: LogKind, val cups: Double)

data class SharedDay(val cups: Double, val goal: Int, val lastSipMillis: Long?, val events: List<SharedEvent>)

object Sharing {
    // No 0/O or 1/I, so a code read out over the phone can't be misheard.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun summarise(entries: List<LogEntry>, goal: Int, level: ShareLevel): SharedDay {
        val sorted = entries.sortedBy { it.atMillis }
        return SharedDay(
            cups = Hydration.cups(sorted),
            goal = goal,
            lastSipMillis = sorted.lastOrNull { it.kind == LogKind.WATER || it.kind == LogKind.OTHER_DRINK }?.atMillis,
            events = if (level == ShareLevel.WITH_MEALS) sorted.map { SharedEvent(it.atMillis, it.kind, it.cups) } else emptyList(),
        )
    }

    fun newCode(random: Random = Random.Default): String {
        val chars = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return chars.take(4) + "-" + chars.drop(4)
    }

    /** Accepts lower case, spaces and a missing dash. Null if it can't be a code. */
    fun normalise(typed: String): String? {
        val raw = typed.uppercase().filter { it.isLetterOrDigit() }
        if (raw.length != 8 || raw.any { it !in ALPHABET }) return null
        return raw.take(4) + "-" + raw.drop(4)
    }
}
