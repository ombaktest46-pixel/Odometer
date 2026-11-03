package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.SpeedAlertMode
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter

/**
 * Handles physical vibration, audible warning tones, and high-priority speed limit notifications
 * when the motorcycle speed exceeds the user's configured limit.
 */
class SpeedLimitAlertController(private val context: Context) {

    companion object {
        const val SPEED_ALERT_CHANNEL_ID = "moto_odo_speed_limit_alert_channel"
        const val SPEED_ALERT_NOTIFICATION_ID = 1002
        private const val ALERT_COOLDOWN_MS = 8_000L
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastAlertTriggerMs: Long = 0L
    private var wasOverSpeedLimit: Boolean = false

    init {
        ensureAlertNotificationChannel()
    }

    private fun ensureAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SPEED_ALERT_CHANNEL_ID,
                "Peringatan Batas Kecepatan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi peringatan saat kecepatan motor melebihi batas kecepatan yang diatur"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Evaluates current speed against the user's speed limit and triggers vibration/sound/notification
     * if exceeded, respecting a cooldown so it doesn't spam every millisecond.
     */
    fun checkAndTriggerIfExceeding(
        currentSpeedKmh: Float,
        isSpeedLimitEnabled: Boolean,
        speedLimitKmh: Float,
        alertMode: SpeedAlertMode,
        speedUnit: SpeedUnit,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isSpeedLimitEnabled || speedLimitKmh <= 0f) {
            if (wasOverSpeedLimit) {
                wasOverSpeedLimit = false
                dismissSpeedAlertNotification()
            }
            return false
        }

        val isOver = currentSpeedKmh > speedLimitKmh
        if (!isOver) {
            if (wasOverSpeedLimit) {
                wasOverSpeedLimit = false
                dismissSpeedAlertNotification()
            }
            return false
        }

        // Trigger immediately when crossing the threshold OR every ALERT_COOLDOWN_MS while staying above limit
        val shouldFire = !wasOverSpeedLimit || (nowMillis - lastAlertTriggerMs) >= ALERT_COOLDOWN_MS
        wasOverSpeedLimit = true

        if (shouldFire) {
            lastAlertTriggerMs = nowMillis
            triggerPhysicalAlert(alertMode)
            postSpeedLimitWarningNotification(
                currentSpeedKmh = currentSpeedKmh,
                speedLimitKmh = speedLimitKmh,
                speedUnit = speedUnit
            )
        }

        return true
    }

    /**
     * Directly fires the configured vibration and/or sound alert (also used by the "Uji Peringatan" button in Settings).
     */
    fun triggerPhysicalAlert(alertMode: SpeedAlertMode) {
        if (alertMode.enableVibration) {
            triggerVibrationPulse()
        }
        if (alertMode.enableSound) {
            triggerWarningTone()
        }
    }

    private fun triggerVibrationPulse() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0L, 350L, 140L, 420L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        }
    }

    private fun triggerWarningTone() {
        runCatching {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 95)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650)
        }
    }

    private fun postSpeedLimitWarningNotification(
        currentSpeedKmh: Float,
        speedLimitKmh: Float,
        speedUnit: SpeedUnit
    ) {
        runCatching {
            val curStr = TelemetryFormatter.formatSpeedInt(currentSpeedKmh, speedUnit.multiplier)
            val limStr = TelemetryFormatter.formatSpeedInt(speedLimitKmh, speedUnit.multiplier)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                201,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, SPEED_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_moto_notification)
                .setContentTitle("⚠️ PERINGATAN BATAS KECEPATAN!")
                .setContentText("Kecepatan $curStr ${speedUnit.symbol} melebihi batas $limStr ${speedUnit.symbol}!")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Kecepatan motor Anda saat ini ($curStr ${speedUnit.symbol}) telah melampaui batas kecepatan aman yang diatur ($limStr ${speedUnit.symbol}). Kurangi kecepatan Anda."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(SPEED_ALERT_NOTIFICATION_ID, notification)
        }
    }

    fun dismissSpeedAlertNotification() {
        runCatching {
            notificationManager.cancel(SPEED_ALERT_NOTIFICATION_ID)
        }
    }
}
