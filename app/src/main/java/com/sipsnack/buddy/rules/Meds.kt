package com.sipsnack.buddy.rules

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A medicine she takes once a day at her chosen time. Reminders only: never doses or advice. */
data class Med(val id: String, val name: String, val minuteOfDay: Int)

object Meds {
    const val FOLLOW_UP_MINUTES = 120

    /** First reminder when it's due, one follow-up two hours later, then nothing more that day. */
    fun shouldRemind(med: Med, minuteOfDay: Int, takenToday: Boolean, remindersToday: Int): Boolean = when {
        takenToday -> false
        remindersToday == 0 -> minuteOfDay >= med.minuteOfDay
        remindersToday == 1 -> minuteOfDay >= med.minuteOfDay + FOLLOW_UP_MINUTES
        else -> false
    }

    /** Days in a row taken, ending today (or yesterday, if today's isn't ticked yet). */
    fun streak(daysTaken: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in daysTaken) today else today.minusDays(1)
        var count = 0
        while (day in daysTaken) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun encode(meds: List<Med>): String = JSONArray(meds.map { JSONObject().put("id", it.id).put("name", it.name).put("minute", it.minuteOfDay) }).toString()

    fun decode(text: String?): List<Med> = runCatching {
        val array = JSONArray(text ?: return emptyList())
        (0 until array.length()).map { array.getJSONObject(it) }.map { Med(it.getString("id"), it.getString("name"), it.getInt("minute")) }
    }.getOrDefault(emptyList())
}
