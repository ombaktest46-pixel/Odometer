package com.example.viewmodel

import com.example.data.TripEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ChartBarPoint(
    val label: String,
    val fullLabel: String,
    val distanceKm: Double,
    val tripCount: Int
)

data class RideStatistics(
    val todayDistanceKm: Double = 0.0,
    val thisWeekDistanceKm: Double = 0.0,
    val thisMonthDistanceKm: Double = 0.0,
    val totalPermanentOdometerKm: Double = 0.0,
    val totalRecordedTripsCount: Int = 0,
    val totalDrivingDurationSeconds: Long = 0L,
    val allTimeMaxSpeedKmh: Float = 0f,
    val overallAvgSpeedKmh: Float = 0f,
    val dailyChartPoints: List<ChartBarPoint> = emptyList(),
    val monthlyChartPoints: List<ChartBarPoint> = emptyList()
)

object StatisticsCalculator {

    private val indoLocale = Locale("id", "ID")

    fun compute(
        trips: List<TripEntity>,
        permanentTotalOdometerKm: Double,
        activeTripDistanceKm: Double,
        activeTripDurationSeconds: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): RideStatistics {
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }

        // Start of Today (00:00:00)
        val startOfTodayMillis = (calNow.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Start of This Week (Monday 00:00:00)
        val startOfWeekMillis = (calNow.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val dow = get(Calendar.DAY_OF_WEEK)
            val diff = if (dow == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dow)
            add(Calendar.DAY_OF_YEAR, diff)
        }.timeInMillis

        // Start of This Month (1st 00:00:00)
        val startOfMonthMillis = (calNow.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var todayKm = activeTripDistanceKm
        var weekKm = activeTripDistanceKm
        var monthKm = activeTripDistanceKm
        var totalDurationSec = activeTripDurationSeconds
        var maxSpd = 0f
        var weightedSpeedSum = 0.0

        for (trip in trips) {
            if (trip.startTimeMillis >= startOfTodayMillis) {
                todayKm += trip.distanceKm
            }
            if (trip.startTimeMillis >= startOfWeekMillis) {
                weekKm += trip.distanceKm
            }
            if (trip.startTimeMillis >= startOfMonthMillis) {
                monthKm += trip.distanceKm
            }
            totalDurationSec += trip.durationSeconds
            if (trip.maxSpeedKmh > maxSpd) {
                maxSpd = trip.maxSpeedKmh
            }
            weightedSpeedSum += trip.avgSpeedKmh * trip.durationSeconds
        }

        val overallAvgSpd = if (totalDurationSec > 0L) {
            (weightedSpeedSum / totalDurationSec).toFloat()
        } else {
            0f
        }

        // Build Last 7 Days Chart
        val dayShortFmt = SimpleDateFormat("EEE", indoLocale)
        val dayFullFmt = SimpleDateFormat("dd MMM", indoLocale)
        val dailyBars = ArrayList<ChartBarPoint>(7)

        for (offset in 6 downTo 0) {
            val dayStartCal = (calNow.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val dStart = dayStartCal.timeInMillis
            val dEnd = dStart + 86_400_000L

            val dayTrips = trips.filter { it.startTimeMillis in dStart until dEnd }
            val sumKm = dayTrips.sumOf { it.distanceKm } + if (offset == 0) activeTripDistanceKm else 0.0
            val count = dayTrips.size + if (offset == 0 && activeTripDistanceKm > 0.01) 1 else 0

            dailyBars.add(
                ChartBarPoint(
                    label = dayShortFmt.format(Date(dStart)).take(3).uppercase(indoLocale),
                    fullLabel = dayFullFmt.format(Date(dStart)),
                    distanceKm = sumKm,
                    tripCount = count
                )
            )
        }

        // Build Last 6 Months Chart
        val monthShortFmt = SimpleDateFormat("MMM", indoLocale)
        val monthFullFmt = SimpleDateFormat("MMMM yyyy", indoLocale)
        val monthlyBars = ArrayList<ChartBarPoint>(6)

        for (mOffset in 5 downTo 0) {
            val mStartCal = (calNow.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -mOffset)
            }
            val mEndCal = (mStartCal.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
            }
            val mStart = mStartCal.timeInMillis
            val mEnd = mEndCal.timeInMillis

            val mTrips = trips.filter { it.startTimeMillis in mStart until mEnd }
            val sumKm = mTrips.sumOf { it.distanceKm } + if (mOffset == 0) activeTripDistanceKm else 0.0
            val count = mTrips.size + if (mOffset == 0 && activeTripDistanceKm > 0.01) 1 else 0

            monthlyBars.add(
                ChartBarPoint(
                    label = monthShortFmt.format(Date(mStart)).take(3).uppercase(indoLocale),
                    fullLabel = monthFullFmt.format(Date(mStart)),
                    distanceKm = sumKm,
                    tripCount = count
                )
            )
        }

        return RideStatistics(
            todayDistanceKm = todayKm,
            thisWeekDistanceKm = weekKm,
            thisMonthDistanceKm = monthKm,
            totalPermanentOdometerKm = permanentTotalOdometerKm,
            totalRecordedTripsCount = trips.size,
            totalDrivingDurationSeconds = totalDurationSec,
            allTimeMaxSpeedKmh = maxSpd,
            overallAvgSpeedKmh = overallAvgSpd,
            dailyChartPoints = dailyBars,
            monthlyChartPoints = monthlyBars
        )
    }
}
