package com.sipsnack.buddy.steps

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.sipsnack.buddy.MainActivity
import com.sipsnack.buddy.R
import com.sipsnack.buddy.reminders.Nudger
import java.util.Locale

/** Stays registered to the step counter so it keeps counting all day, with a quiet notification showing today's steps. */
class StepService : Service(), SensorEventListener {
    private var lastShown = -1
    private var lastShownAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val started = sensor != null && StepSensor.allowed(this) && runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification(null))
            }
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        manager.unregisterListener(this)
        // Batched: the sensor hub counts while the phone sleeps and hands over the total every few seconds when awake.
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 10_000_000)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val steps = StepSensor.record(this, event.values[0].toLong(), StepSensor.stepTime(event))
        val now = System.currentTimeMillis()
        if (steps != lastShown && now - lastShownAt > 30_000) {
            lastShown = steps
            lastShownAt = now
            runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(steps)) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        getSystemService(SensorManager::class.java)?.unregisterListener(this)
        super.onDestroy()
    }

    private fun notification(steps: Int?): Notification {
        val open = PendingIntent.getActivity(
            this, 900, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Nudger.CHANNEL_STEPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3D8BFF.toInt())
            .setContentTitle(if (steps == null) "Counting your steps" else "${"%,d".format(Locale.US, steps)} steps today")
            .setContentText("Turn this off in Settings any time.")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 77
    }
}

/** Restarts the step counter after the phone restarts or the app updates. */
class StepRestarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            StepSensor.keepCounting(context)
        }
    }
}
