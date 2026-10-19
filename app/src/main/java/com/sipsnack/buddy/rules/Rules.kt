package com.sipsnack.buddy.rules

// Pure rules for her day: no Android imports, all unit-tested. Numbers here are starting values to tune.

/** MED is a medicine ticked off as taken; its label is the medicine name. */
enum class LogKind { WATER, OTHER_DRINK, MEAL, SNACK, MED }

data class LogEntry(val atMillis: Long, val kind: LogKind, val cups: Double, val label: String = "")

/** The moments that play a meme. */
enum class Moment { CUP_LOGGED, HALFWAY, GOAL_HIT, DELAY, STILL_DELAYING, MEAL_DELAY }

/** Her awake hours; nothing nudges outside them. Minutes after midnight. */
data class DayWindow(val wakeMinute: Int = 8 * 60, val sleepMinute: Int = 22 * 60) {
    fun isAwake(minuteOfDay: Int) = minuteOfDay in wakeMinute until sleepMinute
}

object Hydration {
    /** Only water counts toward the goal for now (see the PRD open question about tea and juice). */
    fun cups(entries: List<LogEntry>): Double = entries.filter { it.kind == LogKind.WATER }.sumOf { it.cups }

    fun momentForLog(before: Double, after: Double, goal: Int): Moment {
        val half = goal / 2.0
        return when {
            before < goal && after >= goal -> Moment.GOAL_HIT
            before < half && after >= half -> Moment.HALFWAY
            else -> Moment.CUP_LOGGED
        }
    }

    /** Where she "should" be by now if she spread the goal evenly across her awake hours. */
    fun expectedCups(goal: Int, minuteOfDay: Int, window: DayWindow): Double {
        val span = (window.sleepMinute - window.wakeMinute).toDouble()
        val done = ((minuteOfDay - window.wakeMinute) / span).coerceIn(0.0, 1.0)
        return goal * done
    }
}

/** What has already been sent today, so nudges escalate once and never pile up. */
data class NudgeState(
    val lastNudgeAtMillis: Long? = null,
    val nudgesToday: Int = 0,
    val lastWasEscalation: Boolean = false,
)

object Nudges {
    const val NO_DRINK_MILLIS = 2 * 60 * 60 * 1000L
    const val ESCALATE_AFTER_MILLIS = 30 * 60 * 1000L
    const val CUPS_BEHIND = 2.0
    const val MAX_PER_DAY = 3

    /** Returns DELAY, STILL_DELAYING, or null when she should be left alone. */
    fun decide(
        nowMillis: Long,
        minuteOfDay: Int,
        dayStartMillis: Long,
        cups: Double,
        goal: Int,
        lastDrinkAtMillis: Long?,
        state: NudgeState,
        window: DayWindow,
    ): Moment? {
        if (!window.isAwake(minuteOfDay)) return null
        if (cups >= goal) return null
        if (state.nudgesToday >= MAX_PER_DAY) return null

        val wakeMillis = dayStartMillis + window.wakeMinute * 60_000L
        val sinceDrink = nowMillis - maxOf(lastDrinkAtMillis ?: wakeMillis, wakeMillis)
        val behind = Hydration.expectedCups(goal, minuteOfDay, window) - cups
        val delaying = sinceDrink >= NO_DRINK_MILLIS || behind >= CUPS_BEHIND
        if (!delaying) return null

        val lastNudge = state.lastNudgeAtMillis
        val drankSinceNudge = lastNudge == null || (lastDrinkAtMillis != null && lastDrinkAtMillis > lastNudge)
        if (drankSinceNudge) return Moment.DELAY

        val sinceNudge = nowMillis - lastNudge!!
        return when {
            !state.lastWasEscalation && sinceNudge >= ESCALATE_AFTER_MILLIS -> Moment.STILL_DELAYING
            sinceNudge >= NO_DRINK_MILLIS -> Moment.DELAY
            else -> null
        }
    }
}

enum class Meal(val dueMinute: Int, val fromMinute: Int) {
    LUNCH(dueMinute = 14 * 60, fromMinute = 11 * 60),
    DINNER(dueMinute = 20 * 60, fromMinute = 17 * 60),
}

object Meals {
    /** The meal she has skipped and not been nudged about yet, if any. */
    fun missing(minuteOfDay: Int, mealMinutes: List<Int>, alreadyNudged: Set<Meal>): Meal? =
        Meal.entries.lastOrNull { meal ->
            minuteOfDay >= meal.dueMinute &&
                meal !in alreadyNudged &&
                mealMinutes.none { it >= meal.fromMinute }
        }?.takeIf { meal ->
            // Once dinner is due, a missed lunch is old news.
            Meal.entries.none { it.dueMinute > meal.dueMinute && minuteOfDay >= it.dueMinute }
        }
}
