package com.sipsnack.buddy.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sipsnack.buddy.data.Prefs
import com.sipsnack.buddy.rules.Steps
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume

/** The phone's built-in step counter: low power, counts in the background, needs no other app. */
object StepSensor {
    fun available(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** One reading of steps since the phone started, or null if there's no sensor or permission. */
    private suspend fun read(context: Context): Long? {
        if (!available(context) || !allowed(context)) return null
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }

    /** Takes a reading and returns today's steps, or null when steps can't be counted on this phone. */
    suspend fun todaySteps(context: Context): Int? {
        val raw = read(context) ?: return null
        val prefs = Prefs(context)
        val today = LocalDate.now()
        val next = Steps.update(prefs.stepState, raw, today.toString(), today.minusDays(1).toString())
        prefs.stepState = next
        return next.steps
    }
}
