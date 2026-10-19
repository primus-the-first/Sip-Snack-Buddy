package com.sipsnack.buddy.sync

import android.content.Context
import com.sipsnack.buddy.BUDDY_NAME
import com.sipsnack.buddy.data.AppDb
import com.sipsnack.buddy.data.LogRow
import com.sipsnack.buddy.data.Prefs
import com.sipsnack.buddy.rules.LogKind

/**
 * Takes the logs her buddy added into her own log on her phone, marked as his, then removes them from
 * the shared pair. Each one is remembered by id, so a failed delete can't add it twice.
 */
object Importer {
    suspend fun take(context: Context, waiting: List<AddedLog>): Int {
        if (waiting.isEmpty()) return 0
        val prefs = Prefs(context)
        val dao = AppDb.get(context).logs()
        var taken = 0
        for (log in waiting) {
            if (log.id !in prefs.importedIds) {
                val what = when (log.kind) {
                    LogKind.WATER -> "Water"
                    LogKind.OTHER_DRINK -> "Drink"
                    LogKind.MEAL -> "Meal"
                    LogKind.SNACK -> "Snack"
                    LogKind.MED -> "Medicine"
                }
                dao.insert(LogRow(atMillis = log.atMillis, kind = log.kind.name, cups = log.cups, label = "$what · by $BUDDY_NAME"))
                prefs.importedIds = prefs.importedIds + log.id
                taken++
            }
            runCatching { Pairing.deleteAdded(log.id) }
        }
        return taken
    }
}
