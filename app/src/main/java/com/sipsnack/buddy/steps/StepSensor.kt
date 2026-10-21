package com.sipsnack.buddy.steps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.sipsnack.buddy.data.Prefs
import com.sipsnack.buddy.rules.Steps
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

/**
 * The phone's built-in step counter: low power, needs no other app. It only counts while someone is
 * listening, so [StepService] stays registered all day; the one-off reads here just fetch the total.
 */
object StepSensor {
    fun available(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** A reading of the counter and when its latest step happened (null if the sensor's clock looks wrong). */
    private class Reading(val raw: Long, val lastStepAt: Long?)

    /** One reading of steps since the phone started, or null if there's no sensor or permission. */
    private suspend fun read(context: Context): Reading? {
        if (!available(context) || !allowed(context)) return null
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(Reading(event.values[0].toLong(), stepTime(event)))
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }

    /** Takes a reading and returns today's steps, or null when steps can't be counted on this phone. */
    suspend fun todaySteps(context: Context): Int? = read(context)?.let { record(context, it.raw, it.lastStepAt) }

    /** When the sensor says its latest step happened, on the wall clock. */
    fun stepTime(event: SensorEvent): Long? =
        Steps.stepTime(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos(), event.timestamp)

    /** Turns a raw counter value into today's steps and saves where today stands. */
    @Synchronized
    fun record(context: Context, raw: Long, lastStepAt: Long?): Int {
        val prefs = Prefs(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val next = Steps.update(
            prefs.stepState, raw, today.toString(), today.minusDays(1).toString(),
            at = System.currentTimeMillis(), lastStepAt = lastStepAt, dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        prefs.stepState = next
        prefs.stepHistory = prefs.stepHistory + (next.day to next.steps)
        return next.steps
    }

    /** Starts the all-day counter if steps are allowed and wanted. Safe to call often. */
    fun keepCounting(context: Context) {
        if (!available(context) || !allowed(context)) return
        if (!Prefs(context).stepsAllDay) return stopCounting(context)
        // Android only lets us start it while the app is open or right after a restart or update.
        runCatching { ContextCompat.startForegroundService(context, Intent(context, StepService::class.java)) }
    }

    fun stopCounting(context: Context) {
        context.stopService(Intent(context, StepService::class.java))
    }
}
