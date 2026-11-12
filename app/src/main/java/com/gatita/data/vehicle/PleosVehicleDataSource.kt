package com.gatita.data.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.vehicle.VehicleDataSource
import com.gatita.domain.vehicle.VehicleReadFailure
import com.gatita.domain.vehicle.VehicleReadResult
import com.gatita.domain.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK(2.3.2)로 실제 차량 값을 읽는 구현. 모든 스냅샷에 [VehicleSource.PLEOS]를 붙인다.
 *
 * 주행거리(`Odometer.getCurrentDistanceDriven`, km)는 필수이며 못 읽으면 실패다.
 * 연료 %·"주유 이후" 연비·주유 이후 거리·P단 여부는 선택이며 못 읽으면 그 값만 null이다(0으로 채우지 않는다).
 * 연비 단위는 SDK 2.3.2 소스의 KDoc 기준이다: 내연 L/100km, 전기 km/kWh, 주유 이후 거리는 m(km로 변환).
 * 에뮬레이터는 연비 API를 지원하지 않아 null이었고, 실제 차량 값과 시동 때 초기화 여부는 검증하지 않았다.
 *
 * 필요 권한: `pleos.car.permission.CAR_DRIVE`(주행거리·기어), `CAR_ENERGY`(연료·연비)이며 Manifest에 선언했다. 쓰기 권한은 쓰지 않는다.
 *
 * AppContainer에 연결되어 있다. Android Studio에서 동작을 확인했다는 사용자 보고가 있으나 확인 범위는 기록되지 않았고, 실차는 검증하지 않았다.
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

        val odometer = sdk.getOdometer()
        val outcome = read<Float> { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) }
        val km = when (outcome) {
            is Outcome.Timeout -> return VehicleReadResult.Failure(VehicleReadFailure.TIMEOUT, "Odometer read timed out")
            is Outcome.Error -> return VehicleReadResult.Failure(
                if (outcome.error is SecurityException) VehicleReadFailure.PERMISSION_DENIED else VehicleReadFailure.UNAVAILABLE,
                outcome.error.message,
            )

            is Outcome.Value -> PleosValueMapper.odometerKm(outcome.value)
                ?: return VehicleReadResult.Failure(VehicleReadFailure.UNAVAILABLE, "Odometer value unavailable")
        }

        // 연료·연비·기어는 주행거리와 달리 선택 값이다. 하나라도 못 읽으면 그 값만 null이고 0으로 채우지 않는다.
        val fuel = sdk.getFuel()
        val ev = sdk.getEvBattery()
        var percent = PleosValueMapper.percent(read<Int> { ok, fail -> fuel.getFuelPercent(ok, fail) }.valueOrNull())
        var unit = EfficiencyUnit.LITERS_PER_100_KM
        var efficiencyRaw: Float? = null
        if (percent != null) {
            efficiencyRaw = read<Float> { ok, fail -> fuel.getSinceChargeFuelEconomy(ok, fail) }.valueOrNull()
        } else {
            percent = PleosValueMapper.percent(read<Int> { ok, fail -> ev.getEvBatteryStateOfCharge(ok, fail) }.valueOrNull())
            if (percent != null) {
                unit = EfficiencyUnit.KM_PER_KWH
                efficiencyRaw = read<Float> { ok, fail -> ev.getSinceChargeEvEfficiency(ok, fail) }.valueOrNull()
            }
        }
        val sinceRefuelKm = PleosValueMapper.distanceKmFromMeters(
            read<Float> { ok, fail -> odometer.getTripSinceRefillDrivingDistance(ok, fail) }.valueOrNull(),
        )

        return VehicleReadResult.Success(
            VehicleSnapshot(
                odometerKm = km,
                efficiency = PleosValueMapper.sinceRefuelEfficiency(efficiencyRaw, sinceRefuelKm, unit),
                readAtMillis = clock.millis(),
                source = source,
                fuelPercent = percent,
                parked = readParked(sdk),
            ),
        )
    }

    /**
     * 기어 API는 SDK에서 숨김(@hide)·사용 중단(Deprecated) 상태라 동작이 보장되지 않는다. 실패하면 null(알 수 없음)이며
     * 정차 판정은 주행거리 변화 없음만으로 한다.
     */
    @Suppress("DEPRECATION")
    private suspend fun readParked(sdk: Vehicle): Boolean? = try {
        val gear = read<VehicleGear> { ok, fail -> sdk.getGear().getCurrentGearSelectionState(ok, fail) }.valueOrNull()
        gear?.let { it == VehicleGear.GEAR_PARK }
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }

    /** 콜백 API를 제한 시간이 있는 호출로 바꾼다. 콜백은 한 번만 반영한다. */
    private suspend fun <T : Any> read(
        call: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): Outcome<T> = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine<Outcome<T>> { cont ->
            try {
                call(
                    { value -> if (cont.isActive) cont.resume(Outcome.Value(value)) },
                    { error -> if (cont.isActive) cont.resume(Outcome.Error(error)) },
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Outcome.Error(e))
            }
        }
    } ?: Outcome.Timeout

    /**
     * 기어 선택 리스너로 P단 진입을 알린다(숨김·deprecated API). 등록에 실패하면 흐름이 오류로 끝나며,
     * 호출자는 주기 기록만으로 계속한다. 수집이 끝나면 리스너를 해제한다.
     */
    @Suppress("DEPRECATION")
    override fun parkedEvents(): Flow<Boolean> = callbackFlow {
        val sdk = obtainVehicle() ?: run { close(); return@callbackFlow }
        val gear = sdk.getGear()
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                if (gear == VehicleGear.GEAR_PARK) trySend(true)
            }
        }
        gear.registerGearSelection(listener)
        awaitClose { try { gear.unregisterGearSelection(listener) } catch (e: Exception) { } }
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

    private sealed interface Outcome<out T> {
        data class Value<T>(val value: T?) : Outcome<T>
        data class Error(val error: Exception) : Outcome<Nothing>
        data object Timeout : Outcome<Nothing>
    }

    private fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Value<T>)?.value

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
