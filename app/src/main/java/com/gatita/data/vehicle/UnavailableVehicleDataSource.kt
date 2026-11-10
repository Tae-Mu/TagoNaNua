package com.gatita.data.vehicle

import com.gatita.domain.model.VehicleSource
import com.gatita.domain.vehicle.*

/** SDK 어댑터 연결 전의 명시적 실패. 샘플이나 0km를 실제 차량 값으로 반환하지 않는다. */
class UnavailableVehicleDataSource : VehicleDataSource {
    override val source = VehicleSource.PLEOS
    override suspend fun readSnapshot(): VehicleReadResult =
        VehicleReadResult.Failure(VehicleReadFailure.UNAVAILABLE, "차량 SDK 연결 대기")
}
