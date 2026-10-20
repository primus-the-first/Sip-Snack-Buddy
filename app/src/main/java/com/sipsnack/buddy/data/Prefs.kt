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

    /** Whose phone this is: "her", "buddy", or null before the first-run question. */
    var role: String?
        get() = sp.getString("role", null)
        set(value) = sp.edit().putString("role", value).apply()

    /** Her answer to the one-tap share card: null (not asked), "yes" or "no". */
    var shareAnswer: String?
        get() = sp.getString("shareAnswer", null)
        set(value) = sp.edit().putString("shareAnswer", value).apply()

    var shareAskedCount: Int
        get() = sp.getInt("shareAskedCount", 0)
        set(value) = sp.edit().putInt("shareAskedCount", value).apply()

    var shareAskedAt: Long
        get() = sp.getLong("shareAskedAt", 0L)
        set(value) = sp.edit().putLong("shareAskedAt", value).apply()

    /** Daily step goal; gentle by default. */
    var stepGoal: Int
        get() = sp.getInt("stepGoal", 5_000)
        set(value) = sp.edit().putInt("stepGoal", value.coerceIn(1_000, 20_000)).apply()

    /** Whether her steps go to her buddy with her shared day (her phone only). */
    var shareSteps: Boolean
        get() = sp.getBoolean("shareSteps", true)
        set(value) = sp.edit().putBoolean("shareSteps", value).apply()

    /** Keep the step counter running all day (a quiet notification shows while it counts). */
    var stepsAllDay: Boolean
        get() = sp.getBoolean("stepsAllDay", true)
        set(value) = sp.edit().putBoolean("stepsAllDay", value).apply()

    var toldStepsShared: Boolean
        get() = sp.getBoolean("toldStepsShared", false)
        set(value) = sp.edit().putBoolean("toldStepsShared", value).apply()

    var stepGoalCelebratedDay: String?
        get() = sp.getString("stepGoalCelebratedDay", null)
        set(value) = sp.edit().putString("stepGoalCelebratedDay", value).apply()

    var stepState: com.sipsnack.buddy.rules.StepState?
        get() {
            val day = sp.getString("stepDay", null) ?: return null
            return com.sipsnack.buddy.rules.StepState(day, sp.getLong("stepBase", 0), sp.getLong("stepLast", 0), sp.getLong("stepCarried", 0))
        }
        set(value) {
            if (value == null) return
            sp.edit().putString("stepDay", value.day).putLong("stepBase", value.base)
                .putLong("stepLast", value.last).putLong("stepCarried", value.carried).apply()
        }

    /** Each day's step count (yyyy-mm-dd to steps) for History. Keeps a little over a year. */
    var stepHistory: Map<String, Int>
        get() = runCatching {
            val o = org.json.JSONObject(sp.getString("stepHistory", null) ?: "{}")
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = org.json.JSONObject()
            value.entries.sortedBy { it.key }.takeLast(400).forEach { o.put(it.key, it.value) }
            sp.edit().putString("stepHistory", o.toString()).apply()
        }

    /** Her medicines (once a day each). Becoatin is set up the first time her phone opens this version. */
    var meds: List<com.sipsnack.buddy.rules.Med>
        get() = com.sipsnack.buddy.rules.Meds.decode(sp.getString("meds", null))
        set(value) = sp.edit().putString("meds", com.sipsnack.buddy.rules.Meds.encode(value)).apply()

    var medsSeeded: Boolean
        get() = sp.getBoolean("medsSeeded", false)
        set(value) = sp.edit().putBoolean("medsSeeded", value).apply()

    /** Whether her medicine check-offs go to her buddy: null until she answers the one-time question. */
    var shareMeds: Boolean?
        get() = if (sp.contains("shareMeds")) sp.getBoolean("shareMeds", false) else null
        set(value) { if (value == null) sp.edit().remove("shareMeds").apply() else sp.edit().putBoolean("shareMeds", value).apply() }

    fun medRemindersToday(id: String, today: LocalDate = LocalDate.now()): Int =
        if (sp.getString("medRemDay_$id", null) == today.toString()) sp.getInt("medRem_$id", 0) else 0

    fun countMedReminder(id: String, today: LocalDate = LocalDate.now()) {
        sp.edit().putString("medRemDay_$id", today.toString()).putInt("medRem_$id", medRemindersToday(id, today) + 1).apply()
    }

    /** Ids of logs her buddy added that this phone already took into her day (the last 200). */
    var importedIds: Set<String>
        get() = sp.getStringSet("importedIds", emptySet())!!
        set(value) = sp.edit().putStringSet("importedIds", value.toList().takeLast(200).toSet()).apply()

    /** The newest cheer or goal already notified on this phone. */
    var lastCheerSeenAt: Long
        get() = sp.getLong("lastCheerSeenAt", 0L)
        set(value) = sp.edit().putLong("lastCheerSeenAt", value).apply()

    var lastGoalNotifiedDay: String?
        get() = sp.getString("lastGoalNotifiedDay", null)
        set(value) = sp.edit().putString("lastGoalNotifiedDay", value).apply()

    /** Ask on first open; after a "not now", once more a week later, then never. */
    fun shouldAskToShare(now: Long = System.currentTimeMillis()): Boolean = when (shareAnswer) {
        null -> true
        "no" -> shareAskedCount < 2 && now - shareAskedAt >= 7L * 24 * 60 * 60 * 1000
        else -> false
    }

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
    Container("Sip", com.sipsnack.buddy.rules.Units.cups(50)),
    Container("Small cup", com.sipsnack.buddy.rules.Units.cups(100)),
    Container("Half glass", com.sipsnack.buddy.rules.Units.cups(150)),
    Container("Glass", com.sipsnack.buddy.rules.Units.cups(250)),
    Container("Bottle", com.sipsnack.buddy.rules.Units.cups(500)),
)
