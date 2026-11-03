package com.example.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.MotoOdoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Automatically resumes MOTO ODO Auto Tracking after a phone reboot or app update
 * if Auto Tracking was enabled by the user and location permissions are granted.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in validActions) return

        val pendingResult = goAsync()
        val app = context.applicationContext as? MotoOdoApplication
        if (app == null) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = app.odometerPreferences.getSnapshot()
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (snapshot.settings.isAutoTrackingEnabled && (hasFineLocation || hasCoarseLocation)) {
                    AutoTrackingService.startService(context)
                }
            } catch (_: Exception) {
                // Android 12+ background start restrictions may block start if battery optimization is strict;
                // MainActivity also checks and recovers service automatically when opened.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
