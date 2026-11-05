package com.example.widget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.MotoOdoApplication
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.service.AutoTrackingService
import com.example.service.TelemetryStateHolder
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 100% Transparent Home-Screen Overlay Activity (`Theme.Translucent.NoTitleBar`).
 * Launched directly from the Home Screen Widget when GPS/Location is OFF so the user
 * gets the 1-tap system "Turn on Location" popup right on their Home Screen without opening the app.
 */
class WidgetGpsPromptActivity : Activity() {

    companion object {
        private const val REQ_LOCATION_PERMISSION = 401
        private const val REQ_RESOLUTION_GPS = 402
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        enableAutoTrackingState()

        if (!hasLocationPermission()) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_LOCATION_PERMISSION)
        } else {
            checkAndPromptGpsOnHomeScreen()
        }
    }

    private fun enableAutoTrackingState() {
        val appContext = applicationContext
        val app = appContext as? MotoOdoApplication
        val gpsOn = isGpsEnabled()

        CoroutineScope(Dispatchers.IO).launch {
            app?.odometerPreferences?.setAutoTrackingEnabled(true)
            TelemetryStateHolder.updateState { current ->
                current.copy(
                    isAutoTrackingEnabled = true,
                    isGpsHardwareEnabled = gpsOn,
                    motionStatus = if (gpsOn) MotionStatus.STOPPED else MotionStatus.SEARCHING_GPS,
                    gpsSignalQuality = if (gpsOn) current.gpsSignalQuality else GpsSignalQuality.NO_SIGNAL,
                    statusDetailMessage = if (gpsOn) {
                        "Auto Tracking aktif • Memantau GPS"
                    } else {
                        "Menunggu lokasi GPS diaktifkan dari layar utama"
                    }
                )
            }
            if (hasLocationPermission()) {
                runCatching {
                    AutoTrackingService.startService(
                        appContext,
                        AutoTrackingService.ACTION_START_AUTO_TRACKING
                    )
                }
            }
            MotoOdoWidgetProvider.updateAllWidgets(appContext)
        }
    }

    private fun checkAndPromptGpsOnHomeScreen() {
        if (isGpsEnabled()) {
            startTrackingAndFinish()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setWaitForAccurateLocation(true)
            .build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(this)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                startTrackingAndFinish()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    runCatching {
                        exception.startResolutionForResult(this, REQ_RESOLUTION_GPS)
                    }.onFailure {
                        showFloatingGpsDialog()
                    }
                } else {
                    showFloatingGpsDialog()
                }
            }
    }

    private fun showFloatingGpsDialog() {
        if (isFinishing || isDestroyed) return
        runCatching {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hidupkan Lokasi / GPS")
                .setMessage("Nyalakan Lokasi/GPS agar Auto Track di widget dapat menghitung kilometer otomatis saat motor jalan.")
                .setPositiveButton("Hidupkan GPS") { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                    finishSilently()
                }
                .setNegativeButton("Batal") { _, _ ->
                    finishSilently()
                }
                .setOnCancelListener {
                    finishSilently()
                }
                .show()
        }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
            finishSilently()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_RESOLUTION_GPS) {
            if (resultCode == RESULT_OK || isGpsEnabled()) {
                Toast.makeText(
                    applicationContext,
                    "GPS Aktif • Auto Track siap mencatat kilometer",
                    Toast.LENGTH_SHORT
                ).show()
                startTrackingAndFinish()
            } else {
                MotoOdoWidgetProvider.updateAllWidgets(applicationContext)
                finishSilently()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION_PERMISSION) {
            if (hasLocationPermission()) {
                enableAutoTrackingState()
                checkAndPromptGpsOnHomeScreen()
            } else {
                Toast.makeText(
                    applicationContext,
                    "Izin lokasi diperlukan agar widget Auto Track berfungsi",
                    Toast.LENGTH_SHORT
                ).show()
                finishSilently()
            }
        }
    }

    private fun startTrackingAndFinish() {
        val appContext = applicationContext
        if (hasLocationPermission()) {
            runCatching {
                AutoTrackingService.startService(
                    appContext,
                    AutoTrackingService.ACTION_START_AUTO_TRACKING
                )
            }
        }
        TelemetryStateHolder.updateState { current ->
            current.copy(
                isAutoTrackingEnabled = true,
                isGpsHardwareEnabled = true,
                motionStatus = if (current.motionStatus == MotionStatus.TRACKING_OFF) {
                    MotionStatus.STOPPED
                } else {
                    current.motionStatus
                },
                statusDetailMessage = "GPS aktif • Memantau pergerakan motor otomatis"
            )
        }
        MotoOdoWidgetProvider.updateAllWidgets(appContext)
        finishSilently()
    }

    private fun finishSilently() {
        finish()
        overridePendingTransition(0, 0)
    }

    private fun isGpsEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
