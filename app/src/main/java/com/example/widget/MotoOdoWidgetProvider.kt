package com.example.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.MainActivity
import com.example.MotoOdoApplication
import com.example.R
import com.example.model.GpsSignalQuality
import com.example.model.MotionStatus
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.model.WidgetThemeStyle
import com.example.service.AutoTrackingService
import com.example.service.TelemetryStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 2x2 Square Rounded Home Screen AppWidget for MOTO ODO.
 * Features:
 * - Interactive Top-Left O/I Power Rocker Switch & tap-to-toggle Auto Tracking
 * - Automatic GPS / Location state detection (PROVIDERS_CHANGED)
 * - Prominent "GPS MATI • KETUK: HIDUPKAN GPS" warning & 1-tap GPS prompt when Auto Track is ON but Location is OFF
 * - Circular progress ring following Total Odometer across 1 - 999.999 KM
 */
class MotoOdoWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            LocationManager.PROVIDERS_CHANGED_ACTION -> {
                val pendingResult = goAsync()
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val gpsOn = isGpsHardwareEnabled(appContext)
                        val app = appContext as? MotoOdoApplication
                        val persisted = runCatching { app?.odometerPreferences?.getSnapshot() }.getOrNull()
                        val live = TelemetryStateHolder.telemetryState.value
                        val isTrackingActive = live.isAutoTrackingEnabled || (persisted?.settings?.isAutoTrackingEnabled == true)

                        TelemetryStateHolder.updateState { current ->
                            current.copy(
                                isGpsHardwareEnabled = gpsOn,
                                motionStatus = when {
                                    !isTrackingActive -> MotionStatus.TRACKING_OFF
                                    !gpsOn -> MotionStatus.SEARCHING_GPS
                                    current.motionStatus == MotionStatus.TRACKING_OFF -> MotionStatus.STOPPED
                                    else -> current.motionStatus
                                },
                                gpsSignalQuality = if (!gpsOn) GpsSignalQuality.NO_SIGNAL else current.gpsSignalQuality,
                                statusDetailMessage = if (gpsOn) {
                                    "GPS aktif • Memantau pergerakan motor otomatis"
                                } else {
                                    "GPS HP sedang mati • Ketuk widget untuk menghidupkan GPS"
                                }
                            )
                        }

                        // Automatically resume AutoTrackingService when GPS is turned back ON while Auto Track is active
                        if (isTrackingActive && gpsOn && hasLocationPermission(appContext)) {
                            runCatching {
                                AutoTrackingService.startService(
                                    appContext,
                                    AutoTrackingService.ACTION_START_AUTO_TRACKING
                                )
                            }
                        }

                        updateAllWidgetsSuspend(appContext, forceActiveOverride = null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_WIDGET_ENABLE_GPS -> {
                val pendingResult = goAsync()
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = appContext as? MotoOdoApplication
                        app?.odometerPreferences?.setAutoTrackingEnabled(true)
                        val gpsOn = isGpsHardwareEnabled(appContext)
                        val hasPerm = hasLocationPermission(appContext)

                        TelemetryStateHolder.updateState { current ->
                            current.copy(
                                isAutoTrackingEnabled = true,
                                isGpsHardwareEnabled = gpsOn,
                                motionStatus = if (gpsOn) MotionStatus.STOPPED else MotionStatus.SEARCHING_GPS,
                                statusDetailMessage = if (gpsOn) {
                                    "GPS aktif • Auto Tracking berjalan"
                                } else {
                                    "Silakan aktifkan lokasi GPS pada dialog layar"
                                }
                            )
                        }

                        if (hasPerm && gpsOn) {
                            runCatching {
                                AutoTrackingService.startService(
                                    appContext,
                                    AutoTrackingService.ACTION_START_AUTO_TRACKING
                                )
                            }
                        } else {
                            if (hasPerm) {
                                runCatching {
                                    AutoTrackingService.startService(
                                        appContext,
                                        AutoTrackingService.ACTION_START_AUTO_TRACKING
                                    )
                                }
                            }
                            launchMainActivityForGpsPrompt(appContext)
                        }

                        updateAllWidgetsSuspend(appContext, forceActiveOverride = true)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_WIDGET_TOGGLE_AUTO_TRACKING -> {
                val pendingResult = goAsync()
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = appContext as? MotoOdoApplication
                        val persisted = runCatching { app?.odometerPreferences?.getSnapshot() }.getOrNull()
                        val live = TelemetryStateHolder.telemetryState.value
                        val currentlyActive = live.isAutoTrackingEnabled || (persisted?.settings?.isAutoTrackingEnabled == true)
                        val nextActive = !currentlyActive
                        val gpsOn = isGpsHardwareEnabled(appContext)
                        val hasPerm = hasLocationPermission(appContext)

                        if (nextActive) {
                            app?.odometerPreferences?.setAutoTrackingEnabled(true)
                            TelemetryStateHolder.updateState { current ->
                                current.copy(
                                    isAutoTrackingEnabled = true,
                                    isGpsHardwareEnabled = gpsOn,
                                    motionStatus = when {
                                        !gpsOn -> MotionStatus.SEARCHING_GPS
                                        current.motionStatus == MotionStatus.TRACKING_OFF -> MotionStatus.STOPPED
                                        else -> current.motionStatus
                                    },
                                    statusDetailMessage = if (gpsOn) {
                                        "Auto Tracking diaktifkan dari Widget • Memantau GPS"
                                    } else {
                                        "Auto Tracking aktif • Hidupkan lokasi GPS agar bekerja"
                                    }
                                )
                            }
                            if (hasPerm) {
                                runCatching {
                                    AutoTrackingService.startService(
                                        appContext,
                                        AutoTrackingService.ACTION_START_AUTO_TRACKING
                                    )
                                }
                            }
                            // If permission is missing OR GPS/Location is OFF, immediately prompt the user to turn on GPS/Location!
                            if (!hasPerm || !gpsOn) {
                                launchMainActivityForGpsPrompt(appContext)
                            }
                        } else {
                            app?.odometerPreferences?.setAutoTrackingEnabled(false)
                            TelemetryStateHolder.onTrackingStopped()
                            runCatching {
                                AutoTrackingService.stopService(appContext)
                            }
                        }

                        updateAllWidgetsSuspend(appContext, forceActiveOverride = nextActive)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun launchMainActivityForGpsPrompt(appContext: Context) {
        runCatching {
            val openIntent = Intent(appContext, MainActivity::class.java).apply {
                action = ACTION_WIDGET_ENABLE_GPS
                putExtra(EXTRA_PROMPT_ENABLE_GPS, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            appContext.startActivity(openIntent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAllWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE_AUTO_TRACKING = "com.example.action.WIDGET_TOGGLE_AUTO_TRACKING"
        const val ACTION_WIDGET_ENABLE_GPS = "com.example.action.WIDGET_ENABLE_GPS"
        const val EXTRA_PROMPT_ENABLE_GPS = "extra_prompt_enable_gps"

        fun isGpsHardwareEnabled(context: Context): Boolean {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            return runCatching {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
        }

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                updateAllWidgetsSuspend(appContext, forceActiveOverride = null)
            }
        }

        private suspend fun updateAllWidgetsSuspend(
            appContext: Context,
            forceActiveOverride: Boolean?
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MotoOdoWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isEmpty()) return

            val app = appContext as? MotoOdoApplication
            val persisted = runCatching { app?.odometerPreferences?.getSnapshot() }.getOrNull()
            val live = TelemetryStateHolder.telemetryState.value

            val unit = persisted?.settings?.speedUnit ?: SpeedUnit.KMH
            val bikeName = persisted?.settings?.bikeName ?: "Motor Saya"
            val widgetTheme = persisted?.settings?.widgetTheme ?: WidgetThemeStyle.LAVENDER

            val totalKm = maxOf(persisted?.totalOdometerKm ?: 0.0, live.totalOdometerKm)
            val tripKm = maxOf(persisted?.tripOdometerKm ?: 0.0, live.tripOdometerKm)
            val isTrackingActive = forceActiveOverride
                ?: (live.isAutoTrackingEnabled || (persisted?.settings?.isAutoTrackingEnabled == true))
            val isGpsEnabled = isGpsHardwareEnabled(appContext)
            val isMoving = isTrackingActive && isGpsEnabled && live.motionStatus == MotionStatus.MOVING

            val odoFormatted = TelemetryFormatter.formatOdometer(totalKm, unit.multiplier)
            val tripFormatted = "Trip ${TelemetryFormatter.formatOdometerHighPrecision(tripKm, unit.multiplier)} ${unit.distanceSymbol}"
            val odoRingProgress = TelemetryFormatter.calculateOdometerRingProgress(
                km = totalKm,
                multiplier = unit.multiplier
            )

            val bitmap = renderSquareRingWidgetBitmap(
                context = appContext,
                bikeName = bikeName,
                odometerValue = odoFormatted,
                unitLabel = unit.distanceSymbol.uppercase(),
                tripLabel = tripFormatted,
                isTrackingActive = isTrackingActive,
                isGpsEnabled = isGpsEnabled,
                isMoving = isMoving,
                odoProgressFraction = odoRingProgress,
                themeStyle = widgetTheme
            )

            val toggleIntent = Intent(appContext, MotoOdoWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TOGGLE_AUTO_TRACKING
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                appContext,
                202,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val enableGpsIntent = Intent(appContext, MotoOdoWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_ENABLE_GPS
            }
            val enableGpsPendingIntent = PendingIntent.getBroadcast(
                appContext,
                203,
                enableGpsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // When Auto Track is ON but GPS is OFF, tapping the widget body directly prompts to turn ON GPS,
            // while tapping the top-left O/I power switch still toggles Auto Track ON/OFF.
            val rootPendingIntent = if (isTrackingActive && !isGpsEnabled) {
                enableGpsPendingIntent
            } else {
                togglePendingIntent
            }

            for (widgetId in widgetIds) {
                val views = RemoteViews(appContext.packageName, R.layout.widget_moto_odo_1x1).apply {
                    setImageViewBitmap(R.id.widget_canvas_image, bitmap)
                    setOnClickPendingIntent(R.id.widget_root_container, rootPendingIntent)
                    setOnClickPendingIntent(R.id.widget_power_toggle_btn, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun renderSquareRingWidgetBitmap(
            context: Context,
            bikeName: String,
            odometerValue: String,
            unitLabel: String,
            tripLabel: String,
            isTrackingActive: Boolean,
            isGpsEnabled: Boolean,
            isMoving: Boolean,
            odoProgressFraction: Float,
            themeStyle: WidgetThemeStyle
        ): Bitmap {
            val sizePx = 560
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val customTypeface = runCatching {
                ResourcesCompat.getFont(context, R.font.space_grotesk)
            }.getOrNull() ?: Typeface.DEFAULT_BOLD

            val boldTypeface = Typeface.create(customTypeface, Typeface.BOLD)
            val mediumTypeface = Typeface.create(customTypeface, Typeface.NORMAL)

            val isGpsWarning = isTrackingActive && !isGpsEnabled

            // 1. Rounded Square Card Background with Gradient
            val cardRect = RectF(12f, 12f, sizePx - 12f, sizePx - 12f)
            val cornerRadius = 108f
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    sizePx.toFloat(),
                    sizePx.toFloat(),
                    themeStyle.startColorHex.toInt(),
                    themeStyle.endColorHex.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            // Subtle Standby Dim Veil when Auto Tracking is OFF
            if (!isTrackingActive) {
                val standbyOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(82, 10, 12, 18)
                }
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, standbyOverlayPaint)
            } else if (isGpsWarning) {
                // Subtle Red Warning Tint when Auto Track is ON but GPS is OFF
                val gpsOffOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(46, 180, 24, 34)
                }
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, gpsOffOverlayPaint)
            }

            // Outer Card Border: Red Warning if GPS OFF while active, Emerald Green when active+GPS ON, Neutral when OFF
            val activeGreen = Color.parseColor("#20B978")
            val warningRed = Color.parseColor("#E5484D")
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = if (isTrackingActive) 6f else 3f
                color = when {
                    isGpsWarning -> warningRed
                    isTrackingActive -> activeGreen
                    else -> Color.argb(48, 255, 255, 255)
                }
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

            // 2. TOP-LEFT CORNER: Saklar Daya O/I (Power Switch O | I)
            drawTopLeftPowerSwitch(
                canvas = canvas,
                left = 36f,
                top = 34f,
                isTrackingActive = isTrackingActive,
                isGpsWarning = isGpsWarning,
                boldTypeface = boldTypeface
            )

            // 3. TOP-RIGHT CORNER: Live Status Badge (● NYALA / ⚠ GPS MATI / ○ MATI)
            drawTopRightStatusBadge(
                canvas = canvas,
                right = sizePx - 36f,
                top = 34f,
                isTrackingActive = isTrackingActive,
                isGpsEnabled = isGpsEnabled,
                isMoving = isMoving,
                boldTypeface = boldTypeface
            )

            // 4. Circular Ring Track & Active Odometer Progress Ring (1 - 999.999)
            val centerX = sizePx / 2f
            val centerY = 308f
            val ringRadius = 198f
            val ringRect = RectF(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius
            )
            val ringStroke = 24f

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringStroke
                strokeCap = Paint.Cap.ROUND
                color = if (isTrackingActive) {
                    Color.argb(62, 255, 255, 255)
                } else {
                    Color.argb(34, 255, 255, 255)
                }
            }
            canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)

            val ringColor = when {
                isGpsWarning -> Color.parseColor("#F16B70")
                isTrackingActive -> themeStyle.ringColorHex.toInt()
                else -> Color.argb(125, 210, 218, 230)
            }

            if (odoProgressFraction > 0.001f) {
                val activeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke
                    strokeCap = Paint.Cap.ROUND
                    color = ringColor
                }
                val sweepAngle = 360f * odoProgressFraction.coerceIn(0f, 1f)
                canvas.drawArc(ringRect, -90f, sweepAngle, false, activeRingPaint)
            } else {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = ringColor
                    alpha = if (isTrackingActive) 220 else 120
                }
                canvas.drawCircle(centerX, centerY - ringRadius, ringStroke / 2f, dotPaint)
            }

            // 5. Top Label inside Ring: Nama Motor
            val displayBikeName = if (bikeName.length > 15) bikeName.take(14) + "…" else bikeName
            val bikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = mediumTypeface
                textSize = 31f
                color = if (isTrackingActive) {
                    Color.argb(240, 255, 255, 255)
                } else {
                    Color.argb(175, 255, 255, 255)
                }
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(displayBikeName, centerX, 206f, bikePaint)

            // 6. Center Hero Number: Total Odometer (1 - 999.999)
            val odoTextSize = when {
                odometerValue.length >= 9 -> 48f
                odometerValue.length >= 7 -> 58f
                else -> 68f
            }
            val odoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = odoTextSize
                color = if (isTrackingActive) Color.WHITE else Color.argb(205, 255, 255, 255)
                textAlign = Paint.Align.CENTER
                setShadowLayer(8f, 0f, 3f, Color.argb(45, 0, 0, 0))
            }
            canvas.drawText(odometerValue, centerX, 278f, odoPaint)

            val unitSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 19f
                color = if (isTrackingActive) {
                    Color.argb(215, 255, 255, 255)
                } else {
                    Color.argb(150, 255, 255, 255)
                }
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.06f
            }
            canvas.drawText("ODO / 999.999 $unitLabel", centerX, 308f, unitSubPaint)

            // 7. Trip KM Pill Badge
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isTrackingActive) {
                    Color.argb(58, 255, 255, 255)
                } else {
                    Color.argb(32, 255, 255, 255)
                }
            }
            val tripTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 25f
                color = if (isTrackingActive) Color.WHITE else Color.argb(190, 255, 255, 255)
                textAlign = Paint.Align.CENTER
            }
            val tripTextWidth = tripTextPaint.measureText(tripLabel)
            val pillHalfW = (tripTextWidth / 2f + 26f).coerceAtMost(155f)
            val pillRect = RectF(centerX - pillHalfW, 330f, centerX + pillHalfW, 378f)
            canvas.drawRoundRect(pillRect, 24f, 24f, pillPaint)

            if (isTrackingActive) {
                val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = if (isGpsWarning) {
                        Color.argb(175, 241, 107, 112)
                    } else {
                        Color.argb(140, 32, 185, 120)
                    }
                }
                canvas.drawRoundRect(pillRect, 24f, 24f, pillStrokePaint)
            }
            canvas.drawText(tripLabel, centerX, 362f, tripTextPaint)

            // 8. Bottom Vector Motorcycle Emblem & GPS / Tracking State inside Ring
            drawMotoEmblem(
                canvas = canvas,
                cx = centerX,
                cy = 428f,
                isTrackingActive = isTrackingActive,
                isGpsEnabled = isGpsEnabled,
                isMoving = isMoving,
                boldTypeface = boldTypeface
            )

            return bmp
        }

        /**
         * Draws a tactile O/I Power Rocker Switch with Power Symbol in the top-left corner.
         */
        private fun drawTopLeftPowerSwitch(
            canvas: Canvas,
            left: Float,
            top: Float,
            isTrackingActive: Boolean,
            isGpsWarning: Boolean,
            boldTypeface: Typeface
        ) {
            val width = 142f
            val height = 52f
            val switchRect = RectF(left, top, left + width, top + height)
            val radius = height / 2f

            val housingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(135, 15, 19, 26)
            }
            canvas.drawRoundRect(switchRect, radius, radius, housingPaint)

            val housingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = when {
                    isGpsWarning -> Color.parseColor("#F16B70")
                    isTrackingActive -> Color.parseColor("#20B978")
                    else -> Color.argb(95, 255, 255, 255)
                }
            }
            canvas.drawRoundRect(switchRect, radius, radius, housingBorderPaint)

            val pad = 5f
            val thumbW = (width - pad * 2f) / 2f
            val thumbRect = if (isTrackingActive) {
                RectF(left + pad + thumbW, top + pad, left + width - pad, top + height - pad)
            } else {
                RectF(left + pad, top + pad, left + pad + thumbW, top + height - pad)
            }
            val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = when {
                    isGpsWarning -> Color.parseColor("#E5484D")
                    isTrackingActive -> Color.parseColor("#16A36A")
                    else -> Color.argb(115, 115, 123, 136)
                }
            }
            canvas.drawRoundRect(thumbRect, (height - pad * 2f) / 2f, (height - pad * 2f) / 2f, thumbPaint)

            val leftCenterX = left + pad + thumbW / 2f
            val rightCenterX = left + pad + thumbW + thumbW / 2f
            val textBaselineY = top + height / 2f + 8f

            val labelOPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 23f
                textAlign = Paint.Align.CENTER
                color = if (!isTrackingActive) Color.WHITE else Color.argb(120, 255, 255, 255)
            }
            canvas.drawText("O", leftCenterX, textBaselineY, labelOPaint)

            val rightColor = if (isTrackingActive) Color.WHITE else Color.argb(130, 255, 255, 255)
            val powerIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.2f
                strokeCap = Paint.Cap.ROUND
                color = rightColor
            }
            val pCx = rightCenterX - 10f
            val pCy = top + height / 2f + 1f
            val pRadius = 8.5f
            val arcRect = RectF(pCx - pRadius, pCy - pRadius, pCx + pRadius, pCy + pRadius)
            canvas.drawArc(arcRect, -55f, 290f, false, powerIconPaint)
            canvas.drawLine(pCx, pCy - pRadius - 2.5f, pCx, pCy - 1f, powerIconPaint)

            val labelIPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 23f
                textAlign = Paint.Align.CENTER
                color = rightColor
            }
            canvas.drawText("I", rightCenterX + 12f, textBaselineY, labelIPaint)
        }

        /**
         * Draws a clean active/off/GPS-off status pill in the top-right corner of the widget.
         */
        private fun drawTopRightStatusBadge(
            canvas: Canvas,
            right: Float,
            top: Float,
            isTrackingActive: Boolean,
            isGpsEnabled: Boolean,
            isMoving: Boolean,
            boldTypeface: Typeface
        ) {
            val isGpsWarning = isTrackingActive && !isGpsEnabled
            val badgeW = if (isGpsWarning) 152f else 128f
            val badgeH = 44f
            val rect = RectF(right - badgeW, top + 4f, right, top + 4f + badgeH)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = when {
                    isGpsWarning -> Color.argb(185, 68, 16, 22)
                    isTrackingActive -> Color.argb(145, 16, 42, 32)
                    else -> Color.argb(120, 15, 19, 26)
                }
            }
            canvas.drawRoundRect(rect, badgeH / 2f, badgeH / 2f, bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = when {
                    isGpsWarning -> Color.parseColor("#F16B70")
                    isTrackingActive -> Color.parseColor("#20B978")
                    else -> Color.argb(75, 255, 255, 255)
                }
            }
            canvas.drawRoundRect(rect, badgeH / 2f, badgeH / 2f, borderPaint)

            val dotColor = when {
                isGpsWarning -> Color.parseColor("#F16B70")
                isTrackingActive -> Color.parseColor("#20B978")
                else -> Color.argb(140, 160, 168, 181)
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = dotColor
            }
            val dotX = rect.left + 20f
            val dotY = rect.centerY()
            canvas.drawCircle(dotX, dotY, 6.5f, dotPaint)

            val statusLabel = when {
                isGpsWarning -> "GPS MATI"
                isMoving -> "JALAN"
                isTrackingActive -> "NYALA"
                else -> "MATI"
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = if (isGpsWarning) 17.5f else 19f
                textAlign = Paint.Align.LEFT
                color = when {
                    isGpsWarning -> Color.parseColor("#FCA5A5")
                    isTrackingActive -> Color.parseColor("#4ADE80")
                    else -> Color.argb(175, 255, 255, 255)
                }
            }
            canvas.drawText(statusLabel, dotX + 12f, dotY + 6.5f, textPaint)
        }

        private fun drawMotoEmblem(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            isTrackingActive: Boolean,
            isGpsEnabled: Boolean,
            isMoving: Boolean,
            boldTypeface: Typeface
        ) {
            val isGpsWarning = isTrackingActive && !isGpsEnabled
            val iconAlpha = if (isTrackingActive) 240 else 135
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5.5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = if (isGpsWarning) {
                    Color.parseColor("#FCA5A5")
                } else {
                    Color.argb(iconAlpha, 255, 255, 255)
                }
            }
            canvas.drawCircle(cx - 28f, cy + 4f, 12f, iconPaint)
            canvas.drawCircle(cx + 28f, cy + 4f, 12f, iconPaint)

            val path = Path().apply {
                moveTo(cx - 28f, cy + 4f)
                lineTo(cx - 8f, cy - 12f)
                lineTo(cx + 14f, cy - 12f)
                lineTo(cx + 28f, cy + 4f)
                moveTo(cx + 10f, cy - 12f)
                lineTo(cx + 18f, cy - 23f)
                lineTo(cx + 6f, cy - 23f)
                moveTo(cx - 15f, cy - 12f)
                lineTo(cx + 2f, cy - 12f)
            }
            canvas.drawPath(path, iconPaint)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = when {
                    isGpsWarning -> Color.parseColor("#E5484D")
                    isMoving -> Color.parseColor("#4ADE80")
                    isTrackingActive -> Color.parseColor("#20B978")
                    else -> Color.argb(120, 255, 255, 255)
                }
            }
            canvas.drawCircle(cx, cy - 26f, 5f, dotPaint)

            if (isGpsWarning) {
                // Prominent clickable action pill inside the widget telling the rider to turn on GPS
                val warnPillRect = RectF(cx - 124f, cy + 16f, cx + 124f, cy + 48f)
                val warnPillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.parseColor("#E5484D")
                }
                canvas.drawRoundRect(warnPillRect, 16f, 16f, warnPillBg)

                val warnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = boldTypeface
                    textSize = 15.5f
                    textAlign = Paint.Align.CENTER
                    color = Color.WHITE
                }
                canvas.drawText("KETUK: HIDUPKAN GPS", cx, cy + 37.5f, warnTextPaint)
            } else {
                val subStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = boldTypeface
                    textSize = 16f
                    textAlign = Paint.Align.CENTER
                    color = if (isTrackingActive) {
                        Color.parseColor("#4ADE80")
                    } else {
                        Color.argb(150, 255, 255, 255)
                    }
                }
                val subText = if (isTrackingActive) "AUTO TRACK ON" else "AUTO TRACK OFF"
                canvas.drawText(subText, cx, cy + 36f, subStatusPaint)
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val myProvider = ComponentName(context, MotoOdoWidgetProvider::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    return appWidgetManager.requestPinAppWidget(myProvider, null, null)
                }
            }
            return false
        }
    }
}
