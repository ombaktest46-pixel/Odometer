package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.example.MainActivity
import com.example.MotoOdoApplication
import com.example.R
import com.example.model.MotionStatus
import com.example.model.SpeedUnit
import com.example.model.TelemetryFormatter
import com.example.model.WidgetThemeStyle
import com.example.service.TelemetryStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 2x2 Square Rounded Home Screen AppWidget for MOTO ODO.
 * Renders a flagship circular ring card where the circular progress ring
 * follows the Total Odometer value across 1 - 999.999 KM.
 */
class MotoOdoWidgetProvider : AppWidgetProvider() {

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

    companion object {
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, MotoOdoWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                val app = appContext as? MotoOdoApplication
                val persisted = runCatching { app?.odometerPreferences?.getSnapshot() }.getOrNull()
                val live = TelemetryStateHolder.telemetryState.value

                val unit = persisted?.settings?.speedUnit ?: SpeedUnit.KMH
                val bikeName = persisted?.settings?.bikeName ?: "Motor Saya"
                val widgetTheme = persisted?.settings?.widgetTheme ?: WidgetThemeStyle.LAVENDER

                val totalKm = maxOf(persisted?.totalOdometerKm ?: 0.0, live.totalOdometerKm)
                val tripKm = maxOf(persisted?.tripOdometerKm ?: 0.0, live.tripOdometerKm)
                val isMoving = live.isAutoTrackingEnabled && live.motionStatus == MotionStatus.MOVING

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
                    isMoving = isMoving,
                    odoProgressFraction = odoRingProgress,
                    themeStyle = widgetTheme
                )

                val openIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    appContext,
                    201,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                for (widgetId in widgetIds) {
                    val views = RemoteViews(appContext.packageName, R.layout.widget_moto_odo_1x1).apply {
                        setImageViewBitmap(R.id.widget_canvas_image, bitmap)
                        setOnClickPendingIntent(R.id.widget_root_container, pendingIntent)
                    }
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun renderSquareRingWidgetBitmap(
            context: Context,
            bikeName: String,
            odometerValue: String,
            unitLabel: String,
            tripLabel: String,
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

            // 1. Rounded Square Card Background with Gradient
            val cardRect = RectF(12f, 12f, sizePx - 12f, sizePx - 12f)
            val cornerRadius = 112f
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

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.argb(45, 255, 255, 255)
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

            // 2. Circular Ring Track & Active Odometer Progress Ring (1 - 999.999)
            val ringMargin = 62f
            val ringRect = RectF(
                ringMargin,
                ringMargin,
                sizePx - ringMargin,
                sizePx - ringMargin
            )
            val ringStroke = 26f

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = ringStroke
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(55, 255, 255, 255)
            }
            canvas.drawArc(ringRect, 0f, 360f, false, trackPaint)

            val centerX = sizePx / 2f

            if (odoProgressFraction > 0.001f) {
                val activeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke
                    strokeCap = Paint.Cap.ROUND
                    color = themeStyle.ringColorHex.toInt()
                }
                val sweepAngle = 360f * odoProgressFraction.coerceIn(0f, 1f)
                canvas.drawArc(ringRect, -90f, sweepAngle, false, activeRingPaint)
            } else {
                // At 0 KM, draw a starting dot at 12 o'clock (-90 deg)
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = themeStyle.ringColorHex.toInt()
                    alpha = 180
                }
                canvas.drawCircle(centerX, ringMargin, ringStroke / 2f, dotPaint)
            }

            // 3. Top Label inside Ring: Nama Motor
            val displayBikeName = if (bikeName.length > 16) bikeName.take(15) + "…" else bikeName
            val bikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = mediumTypeface
                textSize = 34f
                color = Color.argb(235, 255, 255, 255)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(displayBikeName, centerX, 178f, bikePaint)

            // 4. Center Hero Number: Total Odometer (1 - 999.999)
            val odoTextSize = when {
                odometerValue.length >= 9 -> 52f
                odometerValue.length >= 7 -> 62f
                else -> 74f
            }
            val odoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = odoTextSize
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                setShadowLayer(8f, 0f, 3f, Color.argb(45, 0, 0, 0))
            }
            canvas.drawText(odometerValue, centerX, 256f, odoPaint)

            val unitSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 21f
                color = Color.argb(215, 255, 255, 255)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.06f
            }
            canvas.drawText("ODO / 999.999 $unitLabel", centerX, 290f, unitSubPaint)

            // 5. Trip KM Pill Badge
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(48, 255, 255, 255)
            }
            val tripTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldTypeface
                textSize = 28f
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }
            val tripTextWidth = tripTextPaint.measureText(tripLabel)
            val pillHalfW = (tripTextWidth / 2f + 30f).coerceAtMost(165f)
            val pillRect = RectF(centerX - pillHalfW, 316f, centerX + pillHalfW, 368f)
            canvas.drawRoundRect(pillRect, 26f, 26f, pillPaint)
            canvas.drawText(tripLabel, centerX, 352f, tripTextPaint)

            // 6. Bottom Vector Motorcycle Emblem inside Ring
            drawMotoEmblem(canvas, centerX, 418f, isMoving)

            return bmp
        }

        private fun drawMotoEmblem(canvas: Canvas, cx: Float, cy: Float, isMoving: Boolean) {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6.5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.argb(235, 255, 255, 255)
            }
            canvas.drawCircle(cx - 34f, cy + 12f, 15f, iconPaint)
            canvas.drawCircle(cx + 34f, cy + 12f, 15f, iconPaint)

            val path = Path().apply {
                moveTo(cx - 34f, cy + 12f)
                lineTo(cx - 10f, cy - 8f)
                lineTo(cx + 16f, cy - 8f)
                lineTo(cx + 34f, cy + 12f)
                moveTo(cx + 12f, cy - 8f)
                lineTo(cx + 22f, cy - 22f)
                lineTo(cx + 8f, cy - 22f)
                moveTo(cx - 18f, cy - 8f)
                lineTo(cx + 2f, cy - 8f)
            }
            canvas.drawPath(path, iconPaint)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isMoving) Color.parseColor("#4ADE80") else Color.argb(180, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy - 26f, 5.5f, dotPaint)
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
