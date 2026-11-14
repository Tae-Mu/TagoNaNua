package com.gatita.feature

import com.gatita.FakeSettlementRepository
import com.gatita.FakeStore
import com.gatita.FakeTripRepository
import com.gatita.SerialTransactionRunner
import com.gatita.d
import com.gatita.domain.calculator.SettlementCalculator
import com.gatita.domain.model.Segment
import com.gatita.domain.model.Settlement
import com.gatita.domain.model.VehicleSource
import com.gatita.domain.repository.SettlementRepository
import com.gatita.domain.repository.TripRepository
import com.gatita.domain.usecase.ConfirmSettlementUseCase
import com.gatita.domain.usecase.FinishTripCommand
import com.gatita.domain.usecase.FinishTripUseCase
import com.gatita.domain.usecase.PreviewSettlementUseCase
import com.gatita.domain.usecase.RestoreActiveTripUseCase
import com.gatita.domain.usecase.SaveSegmentCommand
import com.gatita.domain.usecase.SaveSegmentUseCase
import com.gatita.domain.usecase.StartTripCommand
import com.gatita.domain.usecase.StartTripUseCase
import com.gatita.kmPerLiter
import com.gatita.success
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** viewModelScope가 쓰는 Main 디스패처를 테스트용으로 바꾼다. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Room 없이 유스케이스와 인메모리 저장소로 ViewModel을 조립한다. */
class Fixture(
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-09T01:00:00Z"), ZoneOffset.UTC),
    tripsWrapper: (TripRepository) -> TripRepository = { it },
    settlementsWrapper: (SettlementRepository) -> SettlementRepository = { it },
) {
    val store = FakeStore()
    val trips: TripRepository = tripsWrapper(FakeTripRepository(store))
    val settlements: SettlementRepository = settlementsWrapper(FakeSettlementRepository(store))
    val tx = SerialTransactionRunner()
    val payments = com.gatita.PaymentFakes(store)
    val observeList = com.gatita.domain.usecase.ObserveSettlementListUseCase(payments)
    val complete = com.gatita.domain.usecase.CompleteTripUseCase(trips,payments,tx)
    val discard = com.gatita.domain.usecase.DiscardEmptyTripUseCase(trips,payments,payments,tx)
    val observeLedger = com.gatita.domain.usecase.ObserveCostLedgerUseCase(payments)
    val setIncluded = com.gatita.domain.usecase.SetCostIncludedUseCase(trips,payments,tx,clock)
    val deleteCost = com.gatita.domain.usecase.DeleteCostUseCase(trips,payments,tx,clock)
    val addCost = com.gatita.domain.usecase.AddCostUseCase(trips,payments,tx,clock)
    val previewPayment = com.gatita.domain.usecase.PreviewPaymentSettlementUseCase(trips,payments,tx)
    val updatePolicy = com.gatita.domain.usecase.UpdateSplitPolicyUseCase(trips,payments,payments,tx,clock)
    val confirmPayment = com.gatita.domain.usecase.ConfirmPaymentSettlementUseCase(trips,payments,payments.settlements,tx,clock)
    private val calculator = SettlementCalculator()

    val startTrip = StartTripUseCase(trips, tx)
    val restoreActiveTrip = RestoreActiveTripUseCase(trips)
    val saveSegment = SaveSegmentUseCase(trips, tx)
    val finishTrip = FinishTripUseCase(trips, tx)
    val previewSettlement = PreviewSettlementUseCase(trips, calculator)
    val confirmSettlement = ConfirmSettlementUseCase(trips, settlements, tx, calculator, clock)

    suspend fun seedActiveTrip(startKm: String = "0"): Long =
        startTrip(StartTripCommand(d(startKm), clock.millis(), VehicleSource.MANUAL)).success().id

    /** 0 → 100 km를 한 구간으로 기록하고 종료한 여행. [measured]가 false면 연비 없는 구간이다. */
    suspend fun seedFinishedTrip(measured: Boolean = true): Long {
        val id = seedActiveTrip()
        saveSegment(
            SaveSegmentCommand(
                tripId = id,
                eventId = "seed-1",
                endOdometerKm = d("100"),
                endedAtMillis = clock.millis(),
                efficiency = if (measured) kmPerLiter("10") else null,
                source = VehicleSource.MANUAL,
            ),
        ).success()
        finishTrip(FinishTripCommand(id, d("100"), clock.millis())).success()
        return id
    }
}

/** appendSegment가 [gate]가 열릴 때까지 멈춘다. 중복 탭 검증용이며 구간 중복 확인 조회 횟수를 센다. */
class GatedTripRepository(
    private val delegate: TripRepository,
    private val gate: CompletableDeferred<Unit>,
) : TripRepository by delegate {
    var eventLookups = 0

    override suspend fun findSegmentByEventId(tripId: Long, eventId: String): Segment? {
        eventLookups++
        return delegate.findSegmentByEventId(tripId, eventId)
    }

    override suspend fun appendSegment(segment: Segment): Segment {
        gate.await()
        return delegate.appendSegment(segment)
    }
}

/** 첫 appendSegment는 저장한 뒤 응답을 잃은 것처럼 예외를 던진다(저장 여부가 불확실한 실패). */
class LostAckTripRepository(private val delegate: TripRepository) : TripRepository by delegate {
    private var failed = false

    override suspend fun appendSegment(segment: Segment): Segment {
        val saved = delegate.appendSegment(segment)
        if (!failed) {
            failed = true
            throw IOException("lost ack")
        }
        return saved
    }
}

/** 첫 정산 저장은 저장한 뒤 응답을 잃은 것처럼 예외를 던진다. */
class LostAckSettlementRepository(private val delegate: SettlementRepository) : SettlementRepository by delegate {
    private var failed = false

    override suspend fun saveAndMarkTripSettled(settlement: Settlement): Settlement {
        val saved = delegate.saveAndMarkTripSettled(settlement)
        if (!failed) {
            failed = true
            throw IOException("lost ack")
        }
        return saved
    }
}
