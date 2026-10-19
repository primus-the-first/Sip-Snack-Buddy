package com.sipsnack.buddy.reminders

import com.sipsnack.buddy.BUDDY_NAME
import com.sipsnack.buddy.HER_NAME
import com.sipsnack.buddy.sync.Pairing
import com.sipsnack.buddy.sync.Importer
import com.sipsnack.buddy.steps.StepSensor
import com.sipsnack.buddy.sync.HerDay
import com.sipsnack.buddy.rules.Meds
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sipsnack.buddy.MainActivity
import com.sipsnack.buddy.R
import com.sipsnack.buddy.data.AppDb
import com.sipsnack.buddy.data.Prefs
import com.sipsnack.buddy.rules.Hydration
import com.sipsnack.buddy.rules.LogKind
import com.sipsnack.buddy.rules.Meal
import com.sipsnack.buddy.rules.Meals
import com.sipsnack.buddy.rules.Moment
import com.sipsnack.buddy.rules.Nudges
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object Nudger {
    const val EXTRA_MOMENT = "moment"
    private const val CHANNEL_DELAY = "delay"
    private const val CHANNEL_STILL = "still_delaying"
    private const val CHANNEL_MEALS = "meals"
    private const val CHANNEL_BUDDY = "buddy"
    private const val CHANNEL_MEDS = "meds"
    private const val WORK = "nudge-check"

    /** Each channel carries its meme sound; Android fixes a channel's sound once it exists. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        fun raw(res: Int) = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$res")
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DELAY, "Water nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "The side-eye when you're behind on water"
                    setSound(raw(R.raw.delay_sound), attrs)
                },
                NotificationChannel(CHANNEL_STILL, "Still delaying", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "The follow-up when a nudge is ignored"
                    setSound(raw(R.raw.still_delaying_sound), attrs)
                },
                NotificationChannel(CHANNEL_MEALS, "Meal nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When lunch or dinner hasn't been logged"
                },
                NotificationChannel(CHANNEL_MEDS, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Your medicine at your chosen time, and one follow-up"
                },
                NotificationChannel(CHANNEL_BUDDY, "Cheers and wins", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Cheers from your buddy, and her goal wins on a buddy phone"
                },
            ),
        )
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NudgeWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun notify(context: Context, moment: Moment, cupsBehind: Int, meal: Meal? = null, name: String = HER_NAME) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val (channel, title, text) = when (moment) {
            Moment.STILL_DELAYING -> Triple(CHANNEL_STILL, "Akrobeto says go and drink your water.", behindText(cupsBehind, "Come on, go back to your bottle."))
            Moment.MEAL_DELAY -> Triple(
                CHANNEL_MEALS,
                if (meal == Meal.DINNER) "Dinner time, $name." else "Lunch o!",
                "No ${if (meal == Meal.DINNER) "dinner" else "lunch"} logged yet. Eat something good.",
            )
            else -> Triple(CHANNEL_DELAY, "The sea lion has noticed.", behindText(cupsBehind, "Tap for the full side-eye."))
        }
        val open = PendingIntent.getActivity(
            context,
            moment.ordinal,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_MOMENT, moment.name).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3D8BFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(if (moment == Moment.MEAL_DELAY) 2 else 1, notification)
    }

    /** Her medicine reminder; the follow-up is worded more gently insistent. Tapping opens the app to tick it off. */
    fun notifyMed(context: Context, med: com.sipsnack.buddy.rules.Med, followUp: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 300 + med.id.hashCode() % 1000, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3D8BFF.toInt())
            .setContentTitle(if (followUp) "Still time for your ${med.name}" else "Time for your ${med.name}")
            .setContentText(if (followUp) "Tap Taken once you've had it. Your streak is waiting." else "Tap Taken in the app once you've had it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1000 + (med.id.hashCode() and 0xFFF), notification)
    }

    /** A plain notification that opens the app: cheers on her phone, goal wins on his. */
    fun notifyBuddy(context: Context, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 100 + id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDDY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3D8BFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(10 + id, notification)
    }

    private fun behindText(cups: Int, tail: String) =
        if (cups >= 1) "${com.sipsnack.buddy.rules.Units.ml(cups.toDouble())} behind. $tail" else "No water in a while. $tail"
}

class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val steps = StepSensor.todaySteps(applicationContext)
        remindMeds(prefs)
        checkBuddyNews(prefs)
        takeHisLogs(prefs, steps)
        if (!prefs.nudgesOn) return Result.success()

        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val minuteOfDay = now.hour * 60 + now.minute
        val entries = AppDb.get(applicationContext).logs()
            .betweenNow(dayStart, dayStart + 24 * 60 * 60 * 1000L)
            .map { it.toEntry() }

        val cups = Hydration.cups(entries)
        val goal = prefs.goal
        val window = prefs.window
        val lastDrink = entries.filter { it.kind == LogKind.WATER || it.kind == LogKind.OTHER_DRINK }.maxOfOrNull { it.atMillis }
        val state = prefs.nudgeState(today)
        val moment = Nudges.decide(now.toInstant().toEpochMilli(), minuteOfDay, dayStart, cups, goal, lastDrink, state, window)
        if (moment != null) {
            val behind = ceil(Hydration.expectedCups(goal, minuteOfDay, window) - cups).toInt().coerceAtLeast(0)
            Nudger.notify(applicationContext, moment, behind)
            prefs.saveNudge(
                state.copy(
                    lastNudgeAtMillis = now.toInstant().toEpochMilli(),
                    nudgesToday = state.nudgesToday + 1,
                    lastWasEscalation = moment == Moment.STILL_DELAYING,
                ),
                today,
            )
        }

        if (window.isAwake(minuteOfDay)) {
            val mealMinutes = entries.filter { it.kind == LogKind.MEAL }.map {
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.atMillis), zone).let { t -> t.hour * 60 + t.minute }
            }
            Meals.missing(minuteOfDay, mealMinutes, prefs.mealsNudged(today))?.let { meal ->
                Nudger.notify(applicationContext, Moment.MEAL_DELAY, 0, meal, if (prefs.role == "buddy") BUDDY_NAME else HER_NAME)
                prefs.markMealNudged(meal, today)
            }
        }
        return Result.success()
    }
}

/** Cheers for her, goal wins for him. Read every 15 minutes; free, no push server needed. */
private suspend fun NudgeWorker.checkBuddyNews(prefs: Prefs) {
    if (!Pairing.available || prefs.role == null) return
    runCatching {
        val me = Pairing.uid()
        val pair = Pairing.readPair() ?: return
        if (prefs.role == "her" && pair.ownerUid == me && pair.cheer != null && pair.cheerAt > prefs.lastCheerSeenAt) {
            prefs.lastCheerSeenAt = pair.cheerAt
            Nudger.notifyBuddy(applicationContext, 1, BUDDY_NAME, pair.cheer)
        }
        if (prefs.role == "buddy" && pair.buddyUid == me && pair.sharing && !pair.paused) {
            val today = LocalDate.now().toString()
            val day = Pairing.readDay(today)?.day ?: return
            if (day.cups >= day.goal && prefs.lastGoalNotifiedDay != today) {
                prefs.lastGoalNotifiedDay = today
                Nudger.notifyBuddy(applicationContext, 2, "$HER_NAME hit her ${com.sipsnack.buddy.rules.Units.ml(day.goal.toDouble())} goal!", "Goal done for today. Send her a cheer.")
            }
        }
    }
}

/** Medicine reminders: one at her time, one follow-up two hours later, none once it's ticked off. */
private suspend fun NudgeWorker.remindMeds(prefs: Prefs) {
    val meds = prefs.meds
    if (meds.isEmpty()) return
    val now = ZonedDateTime.now()
    val today = now.toLocalDate()
    val minute = now.hour * 60 + now.minute
    val todays = AppDb.get(applicationContext).logs().betweenNow(today.startMillis(), today.plusDays(1).startMillis())
    for (med in meds) {
        val taken = todays.any { it.kind == LogKind.MED.name && it.label == med.name }
        val count = prefs.medRemindersToday(med.id, today)
        if (Meds.shouldRemind(med, minute, taken, count)) {
            Nudger.notifyMed(applicationContext, med, followUp = count >= 1)
            prefs.countMedReminder(med.id, today)
        }
    }
}

/** On her phone, logs her buddy added while her app was closed land in her day and the shared summary. */
private suspend fun NudgeWorker.takeHisLogs(prefs: Prefs, steps: Int?) {
    if (!Pairing.available || prefs.role != "her") return
    runCatching {
        val me = Pairing.uid()
        val pair = Pairing.readPair() ?: return
        if (pair.ownerUid != me || !pair.sharing) return
        Importer.take(applicationContext, Pairing.readAdded())
        if (pair.paused) return
        // Refresh her shared day every run, so his view keeps up with her steps and any logs he added.
        val today = LocalDate.now()
        val entries = AppDb.get(applicationContext).logs()
            .betweenNow(today.startMillis(), today.plusDays(1).startMillis()).map { it.toEntry() }
        Pairing.pushDay(today.toString(), HerDay.build(entries, prefs, pair.level, steps, prefs.meds))
    }
}

fun LocalDate.startMillis(zone: ZoneId = ZoneId.systemDefault()) = atStartOfDay(zone).toInstant().toEpochMilli()
