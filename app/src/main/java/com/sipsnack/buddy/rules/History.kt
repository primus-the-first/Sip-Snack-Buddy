package com.sipsnack.buddy.rules

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** One past day, from her own logs or from the shared copy on his phone. Null fields weren't recorded that day. */
data class DayStats(
    val date: String,
    val cups: Double,
    val goal: Int,
    val steps: Int? = null,
    val stepGoal: Int? = null,
    val meals: Int? = null,
    val medsTaken: Boolean? = null,
)

/** Averages over a stretch of days. Only days with something recorded count, so days before the app don't drag them down. */
data class PeriodStats(
    val recordedDays: Int,
    val avgCups: Double?,
    val waterGoalDays: Int,
    val stepDays: Int,
    val avgSteps: Int?,
    val stepGoalDays: Int,
    val medDays: Int,
    val medTakenDays: Int,
)

data class Growth(val current: PeriodStats, val previous: PeriodStats) {
    val waterChange: Int? get() = History.percent(current.avgCups, previous.avgCups)
    val stepsChange: Int? get() = History.percent(current.avgSteps?.toDouble(), previous.avgSteps?.toDouble())
}

object History {
    /** The [days] dates ending on [lastDay], oldest first, each with its stats or null when nothing was recorded. */
    fun range(byDate: Map<String, DayStats>, lastDay: LocalDate, days: Int): List<Pair<String, DayStats?>> =
        (days - 1 downTo 0).map { back -> lastDay.minusDays(back.toLong()).toString().let { it to byDate[it] } }

    fun period(days: List<DayStats?>): PeriodStats {
        val recorded = days.filterNotNull()
        val withSteps = recorded.filter { it.steps != null }
        val withMeds = recorded.filter { it.medsTaken != null }
        return PeriodStats(
            recordedDays = recorded.size,
            avgCups = recorded.takeIf { it.isNotEmpty() }?.map { it.cups }?.average(),
            waterGoalDays = recorded.count { it.cups >= it.goal },
            stepDays = withSteps.size,
            avgSteps = withSteps.takeIf { it.isNotEmpty() }?.map { it.steps!! }?.average()?.roundToInt(),
            stepGoalDays = withSteps.count { it.steps!! >= (it.stepGoal ?: 5_000) },
            medDays = withMeds.size,
            medTakenDays = withMeds.count { it.medsTaken == true },
        )
    }

    /** The [days] up to yesterday against the [days] before them. Today is left out because it isn't over yet. */
    fun growth(byDate: Map<String, DayStats>, today: LocalDate, days: Int): Growth {
        val yesterday = today.minusDays(1)
        return Growth(
            current = period(range(byDate, yesterday, days).map { it.second }),
            previous = period(range(byDate, yesterday.minusDays(days.toLong()), days).map { it.second }),
        )
    }

    /** Whole-number percent change; null when there's nothing earlier to compare with. */
    fun percent(now: Double?, before: Double?): Int? =
        if (now == null || before == null || before <= 0.0) null else ((now - before) / before * 100).roundToInt()

    /**
     * Her own phone's logs and saved step counts, grouped into local days. Medicine counts from the first
     * day she ticked one off, so the days before medicines were set up don't read as missed.
     */
    fun fromLogs(
        entries: List<LogEntry>,
        zone: ZoneId,
        goal: Int,
        stepGoal: Int,
        stepsByDate: Map<String, Int>,
        tracksMeds: Boolean,
    ): Map<String, DayStats> {
        val byDate = entries.groupBy { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate().toString() }
        val medsFrom = if (tracksMeds) byDate.filterValues { day -> day.any { it.kind == LogKind.MED } }.keys.minOrNull() else null
        return (byDate.keys + stepsByDate.keys).associateWith { date ->
            val logs = byDate[date].orEmpty()
            DayStats(
                date = date,
                cups = Hydration.cups(logs),
                goal = goal,
                steps = stepsByDate[date],
                stepGoal = stepGoal,
                meals = logs.count { it.kind == LogKind.MEAL },
                medsTaken = if (medsFrom != null && date >= medsFrom) logs.any { it.kind == LogKind.MED } else null,
            )
        }
    }
}
