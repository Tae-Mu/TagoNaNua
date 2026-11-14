package com.gatita.data.vehicle

import com.gatita.domain.model.VehicleSource
import com.gatita.domain.vehicle.VehicleReadFailure
import com.gatita.domain.vehicle.VehicleReadResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SampleVehicleDataSourceTest {
    @Test
    fun `snapshots are labeled as sample and the odometer advances`() = runTest {
        val source = SampleVehicleDataSource(startOdometerKm = BigDecimal("100"), stepKm = BigDecimal("10"))
        val first = (source.readSnapshot() as VehicleReadResult.Success).snapshot
        val second = (source.readSnapshot() as VehicleReadResult.Success).snapshot

        assertEquals(VehicleSource.SAMPLE, source.source)
        assertEquals(VehicleSource.SAMPLE, first.source)
        assertEquals(0, BigDecimal("110").compareTo(first.odometerKm))
        assertEquals(0, BigDecimal("120").compareTo(second.odometerKm))
    }

    @Test
    fun `injected failure is reported as failure once and does not advance the odometer`() = runTest {
        val source = SampleVehicleDataSource(startOdometerKm = BigDecimal("100"), stepKm = BigDecimal("10"))
        source.failNext(VehicleReadFailure.TIMEOUT)

        val failed = source.readSnapshot()
        assertTrue(failed is VehicleReadResult.Failure)
        assertEquals(VehicleReadFailure.TIMEOUT, (failed as VehicleReadResult.Failure).reason)

        val next = (source.readSnapshot() as VehicleReadResult.Success).snapshot
        assertEquals(0, BigDecimal("110").compareTo(next.odometerKm))
    }
}
