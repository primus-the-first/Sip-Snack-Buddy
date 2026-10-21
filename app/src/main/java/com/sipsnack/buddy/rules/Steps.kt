package com.sipsnack.buddy.rules

/**
 * Today's steps from the phone's step counter, which only knows "steps since the phone last started".
 * We keep where today began (base) plus steps carried over a restart (carried), and when we last looked.
 */
data class StepState(val day: String, val base: Long, val last: Long, val carried: Long, val lastAtMillis: Long = 0) {
    val steps: Int get() = (carried + (last - base)).coerceAtLeast(0).toInt()
}

object Steps {
    private const val THREE_DAYS = 3 * 24 * 60 * 60 * 1000L

    /**
     * [at] is when this reading was taken, [lastStepAt] when the latest step in it happened and [dayStart] the start
     * of today, all in epoch millis. They let a reading after a long gap share its steps between yesterday and today
     * instead of putting them all on today. Without them (null) a new day continues from last night's reading.
     */
    fun update(
        state: StepState?,
        raw: Long,
        today: String,
        yesterday: String,
        at: Long? = null,
        lastStepAt: Long? = null,
        dayStart: Long? = null,
    ): StepState {
        val stamp = at ?: 0L
        return when {
            state == null -> StepState(today, base = raw, last = raw, carried = 0, lastAtMillis = stamp)
            // A new day continues from last night's reading, so early-morning steps count. After a gap, start fresh.
            state.day != today -> {
                val base = if (state.day == yesterday && raw >= state.last) {
                    rolloverBase(state, raw, at, lastStepAt, dayStart)
                } else raw
                StepState(today, base = base, last = raw, carried = 0, lastAtMillis = stamp)
            }
            // The counter went down: the phone restarted and the sensor began again from zero.
            raw < state.last -> StepState(today, base = 0, last = raw, carried = state.steps.toLong(), lastAtMillis = stamp)
            else -> state.copy(last = raw, lastAtMillis = stamp)
        }
    }

    /** Where today's count starts when the first reading of the day arrives. */
    private fun rolloverBase(state: StepState, raw: Long, at: Long?, lastStepAt: Long?, dayStart: Long?): Long {
        if (lastStepAt == null || dayStart == null) return state.last
        val gap = raw - state.last
        // The latest step was before midnight, so every step since the last reading belongs to yesterday.
        if (lastStepAt < dayStart) return raw
        // Steps happened between our last look and the latest step; assume an even pace and share by time.
        val from = state.lastAtMillis
        if (from <= 0L || lastStepAt <= from) return state.last
        val afterMidnight = ((lastStepAt - dayStart).toDouble() / (lastStepAt - from)).coerceIn(0.0, 1.0)
        return raw - Math.round(gap * afterMidnight)
    }

    /** Wall-clock time of a step from the sensor's uptime-based timestamp; null if it is in the future or days old. */
    fun stepTime(nowMillis: Long, uptimeNanos: Long, eventNanos: Long): Long? {
        val t = nowMillis - (uptimeNanos - eventNanos) / 1_000_000
        return t.takeIf { it in (nowMillis - THREE_DAYS)..nowMillis }
    }
}
