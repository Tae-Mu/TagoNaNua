package com.gatita.data.vehicle

import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.vehicle.VehicleDataSource
import com.gatita.domain.vehicle.VehicleReadFailure
import com.gatita.domain.vehicle.VehicleReadResult
import com.gatita.domain.vehicle.VehicleSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Clock

/**
 * 개발·시연용 샘플 구현. 실제 차량 값이 아니며 모든 스냅샷에 [VehicleSource.SAMPLE]을 붙인다.
 * 호출할 때마다 주행거리가 [stepKm]씩 늘고 구간 평균 연비([efficiencyKmPerLiter])를 돌려준다.
 * [failNext]로 조회 실패를 흉내 낼 수 있으며, 실패 시 0 값을 돌려주지 않는다.
 */
class SampleVehicleDataSource(
    private val clock: Clock = Clock.systemUTC(),
    startOdometerKm: BigDecimal = BigDecimal("12000"),
    private val stepKm: BigDecimal = BigDecimal("37.5"),
    private val efficiencyKmPerLiter: BigDecimal = BigDecimal("12.4"),
) : VehicleDataSource {
    override val source: VehicleSource = VehicleSource.SAMPLE

    private val mutex = Mutex()
    private var odometerKm: BigDecimal = startOdometerKm
    private var pendingFailure: VehicleReadFailure? = null

    suspend fun failNext(reason: VehicleReadFailure) = mutex.withLock { pendingFailure = reason }

    override suspend fun readSnapshot(): VehicleReadResult = mutex.withLock {
        pendingFailure?.let {
            pendingFailure = null
            return@withLock VehicleReadResult.Failure(it, "Sample failure injected")
        }
        odometerKm = odometerKm.add(stepKm)
        VehicleReadResult.Success(
            VehicleSnapshot(
                odometerKm = odometerKm,
                efficiency = EfficiencyReading(
                    value = efficiencyKmPerLiter,
                    unit = EfficiencyUnit.KM_PER_LITER,
                    scope = EfficiencyScope.SEGMENT_AVERAGE,
                ),
                readAtMillis = clock.millis(),
                source = source,
            ),
        )
    }
}
