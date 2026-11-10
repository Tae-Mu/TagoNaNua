package com.gatita.data.vehicle

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.vehicle.VehicleReadResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pleos Connect 에뮬레이터(Vehicle Service 포함)에서만 의미가 있는 기기 테스트.
 * 일반 에뮬레이터에서는 SDK 초기화나 조회가 실패하므로 이 테스트도 실패한다.
 */
@RunWith(AndroidJUnit4::class)
class PleosVehicleDataSourceTest {
    @Test
    fun readsOdometerFromPleosEmulator() = runBlocking {
        val source = PleosVehicleDataSource(ApplicationProvider.getApplicationContext())
        try {
            val result = source.readSnapshot()
            Log.i(TAG, "readSnapshot result = $result")
            val snapshot = (result as? VehicleReadResult.Success)?.snapshot
                ?: fail("차량 값을 읽지 못했다: $result").let { return@runBlocking }

            assertEquals(VehicleSource.PLEOS, snapshot.source)
            assertTrue("주행거리는 0 이상이어야 한다: ${snapshot.odometerKm}", snapshot.odometerKm.signum() >= 0)
            // 연비 단위가 확인되기 전까지 연비는 기록하지 않는다(0이 아니라 null).
            assertNull(snapshot.efficiency)
        } finally {
            source.release()
        }
    }

    private companion object {
        const val TAG = "PleosTest"
    }
}
