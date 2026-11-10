package com.gatita.di

import android.content.Context
import com.gatita.data.local.AppDatabase
import com.gatita.data.repository.RoomCostRepository
import com.gatita.data.repository.RoomPaymentSettlementRepository
import com.gatita.data.repository.RoomSettlementRepository
import com.gatita.data.repository.RoomTransactionRunner
import com.gatita.data.repository.RoomTripLedgerRepository
import com.gatita.data.repository.RoomTripRepository
import com.gatita.data.vehicle.UnavailableVehicleDataSource
import com.gatita.domain.calculator.SettlementCalculator
import com.gatita.domain.repository.CostRepository
import com.gatita.domain.repository.PaymentSettlementRepository
import com.gatita.domain.repository.SettlementRepository
import com.gatita.domain.repository.TransactionRunner
import com.gatita.domain.repository.TripLedgerRepository
import com.gatita.domain.repository.TripRepository
import com.gatita.domain.usecase.AddCostUseCase
import com.gatita.domain.usecase.BuildPaymentSummaryUseCase
import com.gatita.domain.usecase.CompleteTripUseCase
import com.gatita.domain.usecase.ConfirmPaymentSettlementUseCase
import com.gatita.domain.usecase.ConfirmSettlementUseCase
import com.gatita.domain.usecase.DeleteCostUseCase
import com.gatita.domain.usecase.DiscardEmptyTripUseCase
import com.gatita.domain.usecase.FinishTripUseCase
import com.gatita.domain.usecase.GetSettlementRecordUseCase
import com.gatita.domain.usecase.ObserveCostLedgerUseCase
import com.gatita.domain.usecase.ObserveSettlementListUseCase
import com.gatita.domain.usecase.ObserveTripHistoryUseCase
import com.gatita.domain.usecase.PreviewPaymentSettlementUseCase
import com.gatita.domain.usecase.PreviewSettlementUseCase
import com.gatita.domain.usecase.RestoreActiveTripUseCase
import com.gatita.domain.usecase.SaveSegmentUseCase
import com.gatita.domain.usecase.SetCostIncludedUseCase
import com.gatita.domain.usecase.StartTripUseCase
import com.gatita.domain.usecase.UpdateCostUseCase
import com.gatita.domain.usecase.UpdateSplitPolicyUseCase
import com.gatita.domain.vehicle.VehicleDataSource
import java.time.Clock
import java.time.ZoneId

/**
 * 수동 의존성 연결. UI 작업은 이 컨테이너의 유스케이스를 ViewModel에 주입한다.
 *
 * [vehicleDataSource]는 SDK 연결 전 명시적 실패를 반환한다. 검증된 Pleos 어댑터로 교체해야 한다.
 * 앱은 차량 데이터 조회 성공 전 여행을 만들지 않는다. 샘플 구현을 제품 시작 경로에 연결하지 않는다.
 *
 * 결제액 방식(payments-v1)이 정산의 기준이다. "legacy" 표시가 있는 항목은 v1 추정 정산용이며
 * 기존 화면이 계속 컴파일되도록 남겨 둔 것이다. 새 여행의 정산 확정에는 [confirmPaymentSettlement]를 쓴다.
 */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemUTC(),
    /** 여행 날짜와 주유일 기본값을 정하는 지역 시간대. */
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val database: AppDatabase = AppDatabase.create(context)

    val settingsRepository: com.gatita.domain.repository.SettingsRepository = com.gatita.data.repository.RoomSettingsRepository(database.settingsDao())
    val saveSettings = com.gatita.domain.usecase.SaveSettingsUseCase(settingsRepository)

    private val roomTripRepository = RoomTripRepository(database)
    val tripRepository: TripRepository = roomTripRepository
    val costRepository: CostRepository = RoomCostRepository(database)
    val tripLedgerRepository: TripLedgerRepository = RoomTripLedgerRepository(database)
    val paymentSettlementRepository: PaymentSettlementRepository = RoomPaymentSettlementRepository(database)

    /** legacy: v1 추정 정산 저장소. 옛 정산 이력을 읽는 데 쓴다. */
    val settlementRepository: SettlementRepository = RoomSettlementRepository(database)
    private val transactions: TransactionRunner = RoomTransactionRunner(database)
    private val calculator = SettlementCalculator()
    val tripSessionRepository = com.gatita.data.repository.RoomTripSessionRepository(database.tripSessionDao())
    val hideTrip = com.gatita.domain.usecase.HideTripUseCase(tripRepository,tripSessionRepository,transactions)
    val setTripPaused = com.gatita.domain.usecase.SetTripPausedUseCase(tripRepository,tripSessionRepository,transactions,clock)

    val vehicleDataSource: VehicleDataSource = UnavailableVehicleDataSource()

    // ---- 여행·구간(기록). 기록 메타데이터이며 정산 금액과 무관하다. ----
    /** 이름·인원·운전자 포함 여부를 함께 받는다(StartTripCommand의 선택 필드). */
    val startTrip = StartTripUseCase(tripRepository, transactions, zone)
    val restoreActiveTrip = RestoreActiveTripUseCase(tripRepository)
    val saveSegment = SaveSegmentUseCase(tripRepository, transactions)

    /** 거리 기록 없이 비용만 있는 여행도 완료할 수 있다. 새 화면은 이것을 쓴다. */
    val completeTrip = CompleteTripUseCase(tripRepository, costRepository, transactions)

    /** 구간·비용·거리가 모두 없는 진행 중 여행만 닫는다. 완료·확정 여행의 삭제는 지원하지 않는다. */
    val discardEmptyTrip = DiscardEmptyTripUseCase(tripRepository, costRepository, roomTripRepository, transactions)

    // ---- 비용 ----
    val addCost = AddCostUseCase(tripRepository, costRepository, transactions, clock, zone)
    val updateCost = UpdateCostUseCase(tripRepository, costRepository, transactions, clock, zone)
    val setCostIncluded = SetCostIncludedUseCase(tripRepository, costRepository, transactions, clock)
    val deleteCost = DeleteCostUseCase(tripRepository, costRepository, transactions, clock)
    val observeCostLedger = ObserveCostLedgerUseCase(tripLedgerRepository)

    // ---- 분담·정산 ----
    val updateSplitPolicy = UpdateSplitPolicyUseCase(tripRepository, costRepository, tripLedgerRepository, transactions, clock)
    val previewPaymentSettlement = PreviewPaymentSettlementUseCase(tripRepository, costRepository, transactions)
    val confirmPaymentSettlement =
        ConfirmPaymentSettlementUseCase(tripRepository, costRepository, paymentSettlementRepository, transactions, clock)
    val getSettlementRecord = GetSettlementRecordUseCase(paymentSettlementRepository, settlementRepository)
    val buildPaymentSummary = BuildPaymentSummaryUseCase(paymentSettlementRepository)

    // ---- 목록 ----
    val observeSettlementList = ObserveSettlementListUseCase(tripLedgerRepository)
    val observeTripHistory = ObserveTripHistoryUseCase(tripLedgerRepository)

    // ---- legacy: 거리·연비·유가 기반 추정 정산. 새 여행에는 쓰지 않는다. ----
    val finishTrip = FinishTripUseCase(tripRepository, transactions)
    val previewSettlement = PreviewSettlementUseCase(tripRepository, calculator)
    val confirmSettlement = ConfirmSettlementUseCase(tripRepository, settlementRepository, transactions, calculator, clock)
}
