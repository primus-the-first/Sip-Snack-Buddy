package com.sipsnack.buddy.data

import android.content.Context
import com.sipsnack.buddy.rules.DayWindow
import com.sipsnack.buddy.rules.Meal
import com.sipsnack.buddy.rules.NudgeState
import java.time.LocalDate

/** Her settings, plus what reminders already went out today. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sip-snack", Context.MODE_PRIVATE)

    var goal: Int
        get() = sp.getInt("goal", 8)
        set(value) = sp.edit().putInt("goal", value.coerceIn(4, 16)).apply()

    /** Master switch for meme videos and sounds. */
    var memesOn: Boolean
        get() = sp.getBoolean("memesOn", true)
        set(value) = sp.edit().putBoolean("memesOn", value).apply()

    /** Delay and meal nudges, separate from the celebrations. */
    var nudgesOn: Boolean
        get() = sp.getBoolean("nudgesOn", true)
        set(value) = sp.edit().putBoolean("nudgesOn", value).apply()

    val window: DayWindow
        get() = DayWindow(sp.getInt("wakeMinute", 8 * 60), sp.getInt("sleepMinute", 22 * 60))

    fun nudgeState(today: LocalDate = LocalDate.now()): NudgeState {
        if (sp.getString("nudgeDay", null) != today.toString()) return NudgeState()
        return NudgeState(
            lastNudgeAtMillis = sp.getLong("lastNudgeAt", 0L).takeIf { it > 0 },
            nudgesToday = sp.getInt("nudgesToday", 0),
            lastWasEscalation = sp.getBoolean("lastWasEscalation", false),
        )
    }

    fun saveNudge(state: NudgeState, today: LocalDate = LocalDate.now()) {
        sp.edit()
            .putString("nudgeDay", today.toString())
            .putLong("lastNudgeAt", state.lastNudgeAtMillis ?: 0L)
            .putInt("nudgesToday", state.nudgesToday)
            .putBoolean("lastWasEscalation", state.lastWasEscalation)
            .apply()
    }

    fun mealsNudged(today: LocalDate = LocalDate.now()): Set<Meal> {
        if (sp.getString("mealDay", null) != today.toString()) return emptySet()
        return sp.getStringSet("mealsNudged", emptySet())!!.map { Meal.valueOf(it) }.toSet()
    }

    fun markMealNudged(meal: Meal, today: LocalDate = LocalDate.now()) {
        val updated = mealsNudged(today) + meal
        sp.edit().putString("mealDay", today.toString()).putStringSet("mealsNudged", updated.map { it.name }.toSet()).apply()
    }
}

/** Her saved containers. Starting values; a settings screen to edit them comes later. */
data class Container(val name: String, val cups: Double)

val defaultContainers = listOf(
    Container("My bottle", 2.0),
    Container("Glass", 1.0),
    Container("Mug", 1.5),
)
