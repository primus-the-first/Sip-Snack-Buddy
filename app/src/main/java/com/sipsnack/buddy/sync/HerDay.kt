package com.sipsnack.buddy.sync

import com.sipsnack.buddy.data.Prefs
import com.sipsnack.buddy.rules.LogEntry
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.rules.Med
import com.sipsnack.buddy.rules.ShareLevel
import com.sipsnack.buddy.rules.SharedDay
import com.sipsnack.buddy.rules.SharedMed
import com.sipsnack.buddy.rules.Sharing

/** What her buddy sees of today, built the same way by the open app and the background worker. */
object HerDay {
    fun build(entries: List<LogEntry>, prefs: Prefs, level: ShareLevel, steps: Int?, meds: List<Med>): SharedDay {
        val shareSteps = if (prefs.shareSteps) steps else null
        val shareMeds = if (prefs.shareMeds == true && meds.isNotEmpty()) {
            meds.map { med ->
                val taken = entries.filter { it.kind == LogKind.MED && it.label == med.name }.maxOfOrNull { it.atMillis }
                SharedMed(med.name, med.minuteOfDay, taken)
            }
        } else null
        return Sharing.summarise(entries, prefs.goal, level).copy(
            steps = shareSteps,
            stepGoal = shareSteps?.let { prefs.stepGoal },
            meds = shareMeds,
        )
    }
}
