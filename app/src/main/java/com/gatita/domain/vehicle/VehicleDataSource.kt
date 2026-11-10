package com.gatita.domain.vehicle

import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.VehicleSource
import java.math.BigDecimal

/** 한 시점의 차량 측정값. [efficiency]가 null이면 연비를 얻지 못한 것이며 0이 아니다. */
data class VehicleSnapshot(
    val odometerKm: BigDecimal,
    val efficiency: EfficiencyReading?,
    val readAtMillis: Long,
    val source: VehicleSource,
)

enum class VehicleReadFailure { UNAVAILABLE, TIMEOUT, PERMISSION_DENIED, UNSUPPORTED }

sealed interface VehicleReadResult {
    data class Success(val snapshot: VehicleSnapshot) : VehicleReadResult

    /** 조회 실패. 호출자는 이 결과를 0 값 기록으로 바꾸지 말고 연결 대기/실패 상태로 처리한다. 사용자 거리 입력으로 대체하지 않는다. */
    data class Failure(val reason: VehicleReadFailure, val message: String? = null) : VehicleReadResult
}

/** 차량 데이터 조회 규약. 실제(Pleos)와 샘플 구현은 data/vehicle에 둔다. */
interface VehicleDataSource {
    val source: VehicleSource

    suspend fun readSnapshot(): VehicleReadResult
}
