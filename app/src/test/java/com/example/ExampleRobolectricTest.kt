package com.example

import com.example.model.TelemetryFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `odometer ring progress scales cleanly across 1 to 999999 km`() {
        val zeroProgress = TelemetryFormatter.calculateOdometerRingProgress(0.0)
        assertEquals(0f, zeroProgress, 0.0001f)

        val oneKmProgress = TelemetryFormatter.calculateOdometerRingProgress(1.0)
        assertTrue(oneKmProgress > 0f)

        val tenThousandKmProgress = TelemetryFormatter.calculateOdometerRingProgress(10_000.0)
        assertTrue(tenThousandKmProgress > oneKmProgress)

        val maxProgress = TelemetryFormatter.calculateOdometerRingProgress(999_999.0)
        assertEquals(1f, maxProgress, 0.001f)
    }
}
