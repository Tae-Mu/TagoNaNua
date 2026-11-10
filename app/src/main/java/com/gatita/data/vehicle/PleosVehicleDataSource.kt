package com.gatita.data.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.vehicle.VehicleDataSource
import com.gatita.domain.vehicle.VehicleReadFailure
import com.gatita.domain.vehicle.VehicleReadResult
import com.gatita.domain.vehicle.VehicleSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK(2.3.2)로 실제 차량 값을 읽는 구현. 모든 스냅샷에 [VehicleSource.PLEOS]를 붙인다.
 *
 * 1차 범위는 계기판 주행거리(`Odometer.getCurrentDistanceDriven`, km)뿐이다.
 * 연비는 SDK가 단위를 제공하지 않고(CapabilityConfig에 단위 없음) 집계 시작점도 문서에 없어
 * 확인되기 전까지 `efficiency = null`(측정 실패)로 돌려준다. 0이나 추측한 단위로 채우지 않는다.
 *
 * 필요 권한: `pleos.car.permission.CAR_DRIVE`(Manifest에 선언). 쓰기 권한은 쓰지 않는다.
 *
 * 아직 Pleos Connect AVD에서 실행해 검증하지 않았다. 검증 전에는 AppContainer에 연결하지 않는다.
 */
class PleosVehicleDataSource(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : VehicleDataSource {
    override val source: VehicleSource = VehicleSource.PLEOS

    private val appContext: Context = context.applicationContext
    private val initLock = Mutex()
    private var vehicle: Vehicle? = null

    override suspend fun readSnapshot(): VehicleReadResult {
        val sdk = obtainVehicle()
            ?: return VehicleReadResult.Failure(VehicleReadFailure.UNAVAILABLE, "Vehicle SDK initialization failed")

        val outcome = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<OdometerOutcome> { cont ->
                try {
                    sdk.getOdometer().getCurrentDistanceDriven(
                        { value -> if (cont.isActive) cont.resume(OdometerOutcome.Value(value)) },
                        { error -> if (cont.isActive) cont.resume(OdometerOutcome.Error(error)) },
                    )
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(OdometerOutcome.Error(e))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(OdometerOutcome.Error(e))
                }
            }
        } ?: return VehicleReadResult.Failure(VehicleReadFailure.TIMEOUT, "Odometer read timed out")

        return when (outcome) {
            is OdometerOutcome.Error -> VehicleReadResult.Failure(
                if (outcome.error is SecurityException) VehicleReadFailure.PERMISSION_DENIED else VehicleReadFailure.UNAVAILABLE,
                outcome.error.message,
            )

            is OdometerOutcome.Value -> {
                val km = PleosValueMapper.odometerKm(outcome.value)
                    ?: return VehicleReadResult.Failure(VehicleReadFailure.UNAVAILABLE, "Odometer value unavailable")
                VehicleReadResult.Success(
                    VehicleSnapshot(
                        odometerKm = km,
                        efficiency = null,
                        readAtMillis = clock.millis(),
                        source = source,
                    ),
                )
            }
        }
    }

    /** SDK 사용이 끝났을 때 호출한다(공식 가이드: 콜백 처리가 끝난 뒤 release). */
    suspend fun release() = initLock.withLock {
        vehicle?.release()
        vehicle = null
    }

    private suspend fun obtainVehicle(): Vehicle? = initLock.withLock {
        vehicle ?: try {
            Vehicle(appContext).also { it.initialize() }.also { vehicle = it }
        } catch (e: Exception) {
            null
        }
    }

    private sealed interface OdometerOutcome {
        data class Value(val value: Float?) : OdometerOutcome
        data class Error(val error: Exception) : OdometerOutcome
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
