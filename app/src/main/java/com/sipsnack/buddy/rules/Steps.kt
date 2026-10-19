package com.sipsnack.buddy.rules

/**
 * Today's steps from the phone's step counter, which only knows "steps since the phone last started".
 * We keep where today began (base) plus steps carried over a restart (carried).
 */
data class StepState(val day: String, val base: Long, val last: Long, val carried: Long) {
    val steps: Int get() = (carried + (last - base)).coerceAtLeast(0).toInt()
}

object Steps {
    fun update(state: StepState?, raw: Long, today: String, yesterday: String): StepState = when {
        state == null -> StepState(today, base = raw, last = raw, carried = 0)
        // A new day continues from last night's reading, so early-morning steps count. After a gap, start fresh.
        state.day != today -> {
            val base = if (state.day == yesterday && raw >= state.last) state.last else raw
            StepState(today, base = base, last = raw, carried = 0)
        }
        // The counter went down: the phone restarted and the sensor began again from zero.
        raw < state.last -> StepState(today, base = 0, last = raw, carried = state.steps.toLong())
        else -> state.copy(last = raw)
    }
}
