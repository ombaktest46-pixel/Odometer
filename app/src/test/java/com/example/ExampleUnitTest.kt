package com.example

import com.example.data.RouteJsonCodec
import com.example.data.TripEntity
import com.example.model.GpsAccuracyFilter
import com.example.model.MotionStatus
import com.example.model.RoutePoint
import com.example.model.SensitivityLevel
import com.example.service.GpsMotionProcessor
import com.example.service.RawGpsFix
import com.example.viewmodel.StatisticsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `gps processor blocks poor accuracy and stationary drift then detects moving and stopping`() {
        val processor = GpsMotionProcessor()
        val baseTime = 1_700_000_000_000L

        // 1. Poor accuracy (> 22m) should be rejected
        val poorFix = processor.processFix(
            fix = RawGpsFix(
                latitude = -6.20000,
                longitude = 106.81666,
                accuracyMeters = 48f,
                hasSpeed = true,
                speedMetersPerSec = 0f,
                timestampMillis = baseTime
            ),
            sensitivity = SensitivityLevel.MEDIUM,
            accuracyFilter = GpsAccuracyFilter.BALANCED_22M,
            calibrationFactor = 1.0f
        )
        assertFalse(poorFix.acceptedFix)
        assertEquals(0.0, poorFix.deltaDistanceKm, 0.0001)

        // 2. First accurate anchor fix (parked)
        val anchorFix = processor.processFix(
            fix = RawGpsFix(
                latitude = -6.20000,
                longitude = 106.81666,
                accuracyMeters = 6f,
                hasSpeed = true,
                speedMetersPerSec = 0f,
                timestampMillis = baseTime + 1000L
            ),
            sensitivity = SensitivityLevel.MEDIUM,
            accuracyFilter = GpsAccuracyFilter.BALANCED_22M,
            calibrationFactor = 1.0f
        )
        assertTrue(anchorFix.acceptedFix)
        assertEquals(MotionStatus.STOPPED, anchorFix.motionStatus)
        assertEquals(0.0, anchorFix.deltaDistanceKm, 0.0001)

        // 3. Tiny stationary jitter (1.2m drift while parked) must NOT add distance
        val driftFix = processor.processFix(
            fix = RawGpsFix(
                latitude = -6.20001,
                longitude = 106.81666,
                accuracyMeters = 6f,
                hasSpeed = true,
                speedMetersPerSec = 0.2f,
                timestampMillis = baseTime + 3000L
            ),
            sensitivity = SensitivityLevel.MEDIUM,
            accuracyFilter = GpsAccuracyFilter.BALANCED_22M,
            calibrationFactor = 1.0f
        )
        assertEquals(0.0, driftFix.deltaDistanceKm, 0.0001)
        assertEquals(MotionStatus.STOPPED, driftFix.motionStatus)

        // 4. Motorcycle starts moving (~12.5 m/s = 45 km/h): sample 1 & sample 2 trigger MOVING
        processor.processFix(
            fix = RawGpsFix(
                latitude = -6.20020,
                longitude = 106.81666,
                accuracyMeters = 5f,
                hasSpeed = true,
                speedMetersPerSec = 12.5f,
                timestampMillis = baseTime + 5000L
            ),
            sensitivity = SensitivityLevel.MEDIUM,
            accuracyFilter = GpsAccuracyFilter.BALANCED_22M,
            calibrationFactor = 1.0f
        )

        val movingResult = processor.processFix(
            fix = RawGpsFix(
                latitude = -6.20045,
                longitude = 106.81666,
                accuracyMeters = 5f,
                hasSpeed = true,
                speedMetersPerSec = 12.5f,
                timestampMillis = baseTime + 7000L
            ),
            sensitivity = SensitivityLevel.MEDIUM,
            accuracyFilter = GpsAccuracyFilter.BALANCED_22M,
            calibrationFactor = 1.0f
        )
        assertEquals(MotionStatus.MOVING, movingResult.motionStatus)
        assertTrue(movingResult.deltaDistanceKm > 0.02)
        assertTrue(movingResult.currentSpeedKmh > 35f)
    }

    @Test
    fun `route json codec and statistics calculator preserve permanent odometer`() {
        val points = listOf(
            RoutePoint(-6.2000, 106.8160, 35f, 1000L),
            RoutePoint(-6.2050, 106.8200, 52f, 2000L)
        )
        val json = RouteJsonCodec.encodeRoutePoints(points)
        val decoded = RouteJsonCodec.decodeRoutePoints(json)
        assertEquals(2, decoded.size)
        assertEquals(52f, decoded[1].speedKmh, 0.01f)

        val now = System.currentTimeMillis()
        val trips = listOf(
            TripEntity(
                id = 1L,
                startTimeMillis = now - 3600_000L,
                endTimeMillis = now - 1800_000L,
                distanceKm = 15.0,
                durationSeconds = 1800L,
                avgSpeedKmh = 30f,
                maxSpeedKmh = 62f,
                startCoordLabel = "-6.20, 106.81",
                endCoordLabel = "-6.25, 106.85",
                routePointsJson = json
            )
        )

        // Even if trip history only has 15 KM, permanentTotalOdometerKm (e.g. 40 KM) is preserved
        val stats = StatisticsCalculator.compute(
            trips = trips,
            permanentTotalOdometerKm = 40.0,
            activeTripDistanceKm = 0.0,
            activeTripDurationSeconds = 0L,
            nowMillis = now
        )
        assertEquals(40.0, stats.totalPermanentOdometerKm, 0.01)
        assertEquals(15.0, stats.todayDistanceKm, 0.01)
        assertEquals(7, stats.dailyChartPoints.size)
        assertEquals(6, stats.monthlyChartPoints.size)
    }
}
