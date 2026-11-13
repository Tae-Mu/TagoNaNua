package com.gatita.feature

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gatita.core.ui.theme.TagoTheme
import com.gatita.domain.model.Cost
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.CostLedger
import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.FuelExclusion
import com.gatita.domain.model.FuelIntervalResult
import com.gatita.domain.model.FuelSettlementResult
import com.gatita.domain.model.MeasurementQuality
import com.gatita.domain.model.PaymentPreview
import com.gatita.domain.model.PaymentShare
import com.gatita.domain.model.RecordingMetadata
import com.gatita.domain.model.RefuelEvent
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import com.gatita.feature.settlement.SettlementContent
import com.gatita.feature.settlement.SettlementUiState
import com.gatita.feature.settlement.ShareRow
import com.gatita.feature.share.CostLine
import com.gatita.feature.share.PaymentResultView
import com.gatita.feature.share.QrState
import com.gatita.feature.share.ResultContent
import com.gatita.feature.share.ResultContentState
import com.gatita.feature.share.ResultUiState
import com.gatita.feature.settlement.ShareLine
import com.gatita.feature.trip.CostLedgerSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** 구간별 유류비(fuel-segments-v2) 화면: 주유 이벤트 목록, 유류비 미포함, 구간별 근거, 긴 문구·빈/로딩/오류 상태. */
class FuelSegmentsScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun d(v: String) = BigDecimal(v)

    private fun fuelCost(id: Long, undetected: Boolean = false) = Cost(
        id = id, tripId = 1, requestId = "r$id", kind = CostKind.FUEL, amountWon = 50_000, description = "",
        unitPriceWonPerLiter = d("1700"), paidOn = LocalDate.of(2026, 11, 12), included = true,
        createdAtMillis = id, updatedAtMillis = id, undetected = undetected,
    )

    private fun ledger(costs: List<Cost>, editable: Boolean = true) = CostLedger(
        tripId = 1, tripStatus = TripStatus.IN_PROGRESS, tripRevision = 1, editable = editable, costs = costs,
        count = costs.size, includedCount = costs.size, registeredTotalWon = costs.sumOf { it.amountWon },
        includedTotalWon = costs.sumOf { it.amountWon }, excludedTotalWon = 0, totalsByKind = emptyMap(),
    )

    private fun showLedger(ledger: CostLedger, refuels: List<RefuelEvent>, onDismiss: (RefuelEvent, Boolean) -> Unit = { _, _ -> }) =
        rule.setContent {
            TagoTheme {
                CostLedgerSection(
                    ledger, busy = false, onIncludedChange = { _, _ -> }, onEdit = {}, onDelete = {},
                    fuelSegments = true, refuels = refuels, onRefuelDismissedChange = onDismiss,
                )
            }
        }

    // ---------------------------------------------------------------- 주유 이벤트 목록

    @Test fun ledgerShowsRefuelEventsAndMatchingCounts() {
        showLedger(ledger(listOf(fuelCost(1))), listOf(RefuelEvent(1, 1, 3_600_000, 25, 90)))
        rule.onNodeWithText("여행을 마치면 계산해요").assertExists()
        rule.onNodeWithText("감지한 주유 1회 · 주유 비용 1건").assertExists()
        rule.onNodeWithText("주유 횟수와 비용 건수가 맞아요.").assertExists()
        rule.onNodeWithText("연료 25% → 90%", substring = true).assertExists()
    }

    @Test fun ledgerWarnsWhenRefuelAndCostCountsDiffer() {
        showLedger(ledger(emptyList()), listOf(RefuelEvent(1, 1, 3_600_000, 25, 90)))
        rule.onNodeWithText("감지한 주유 1회 · 주유 비용 0건").assertExists()
        rule.onNodeWithText("두 수가 같아야 여행을 마칠 수 있어요.", substring = true).assertExists()
    }

    @Test fun ledgerEmptyStateWhenNoRefuelDetected() {
        showLedger(ledger(emptyList()), emptyList())
        rule.onNodeWithText("감지한 주유 0회 · 주유 비용 0건").assertExists()
        rule.onNodeWithText("아직 감지한 주유가 없어요.", substring = true).assertExists()
        rule.onNodeWithText("아직 등록한 비용이 없어요").assertExists()
    }

    @Test fun dismissingRefuelReportsTheEvent() {
        val event = RefuelEvent(7, 1, 3_600_000, 25, 90)
        var reported: Pair<RefuelEvent, Boolean>? = null
        showLedger(ledger(emptyList()), listOf(event)) { e, v -> reported = e to v }
        rule.onNode(isToggleable()).performClick()
        assertEquals(event to true, reported)
    }

    @Test fun dismissedRefuelDoesNotCountAndUndetectedCostDoesNotPair() {
        showLedger(
            ledger(listOf(fuelCost(1, undetected = true))),
            listOf(RefuelEvent(1, 1, 3_600_000, 25, 90, dismissed = true)),
        )
        rule.onNodeWithText("감지한 주유 0회 · 주유 비용 0건").assertExists()
        rule.onNodeWithText("감지 안 됨").assertExists()
    }

    // ---------------------------------------------------------------- 정산 미리보기

    private fun interval(ordinal: Int, exclusion: FuelExclusion? = null) = FuelIntervalResult(
        ordinal = ordinal, distanceKm = d("100"),
        usage = if (exclusion == null) d("8.00") else null,
        unitPriceWonPerLiter = if (exclusion == null) d("1700") else null,
        costWonExact = if (exclusion == null) d("13600") else null,
        exclusion = exclusion,
    )

    private fun preview(fuel: FuelSettlementResult, fuelTotal: Long, other: Long = 3_000, canConfirm: Boolean = true) = PaymentPreview(
        tripId = 1, tripRevision = 1, tripStatus = TripStatus.FINISHED, totalWon = fuelTotal + other,
        fuelTotalWon = fuelTotal, otherTotalWon = other, includedCount = 1, registeredCount = 1, people = 2,
        splitPeople = 2, includeDriver = true,
        shares = listOf(PaymentShare(0, "나", true, (fuelTotal + other + 1) / 2), PaymentShare(1, "참여자 2", false, (fuelTotal + other) / 2)),
        canConfirm = canConfirm, blockingReason = null,
        recording = RecordingMetadata(VehicleSource.PLEOS, d("100"), 3_600, MeasurementQuality.PARTIAL),
        fuel = fuel,
    )

    private fun result(intervals: List<FuelIntervalResult>, fuelCost: Long, notIncluded: Boolean) = FuelSettlementResult(
        intervals = intervals, fuelCostWon = fuelCost, otherTotalWon = 3_000, totalWon = fuelCost + 3_000,
        excludedIntervalCount = intervals.count { it.exclusion != null }, fuelNotIncluded = notIncluded,
        canConfirm = true, blockingReason = null,
    )

    private fun showSettlement(state: SettlementUiState) = rule.setContent {
        TagoTheme {
            SettlementContent(
                state, onBack = {}, onRetry = {}, onDriverNameChange = {}, onPeopleChange = {}, onIncludeDriverChange = {},
                onRequestConfirm = {}, onConfirm = {}, onDismissConfirm = {}, onEditCosts = {},
            )
        }
    }

    @Test fun settlementShowsFuelNotIncludedAndExclusionReason() {
        val fuel = result(listOf(interval(0, FuelExclusion.MEASUREMENT_MISSING)), 0, notIncluded = true)
        showSettlement(SettlementUiState(isLoading = false, preview = preview(fuel, 0), shares = listOf(ShareRow("나", 1_500, true))))
        rule.onNodeWithText("유류비 미포함").assertExists()
        rule.onNodeWithText("차량 측정이 없어 유류비에서 제외", substring = true).assertExists()
        rule.onNodeWithText("구간별 유류비").assertExists()
        // 유류비가 없어도 정산은 막지 않는다.
        rule.onNodeWithText("이 금액으로 확정").assertIsEnabled()
        // 구간이 하나뿐이면 직접 단가를 넣을 수 있다.
        rule.onNodeWithText("단가 적용").assertExists()
    }

    @Test fun settlementShowsPerIntervalBasisAndExcludedNotice() {
        val fuel = result(listOf(interval(0), interval(1, FuelExclusion.UNIT_PRICE_MISSING)), 13_600, notIncluded = false)
        showSettlement(SettlementUiState(isLoading = false, preview = preview(fuel, 13_600)))
        rule.onNodeWithText("구간 1").assertExists()
        rule.onNodeWithText("구간 2").assertExists()
        rule.onNodeWithText("제외").assertExists()
        rule.onNodeWithText("적용할 단가가 없어 유류비에서 제외", substring = true).assertExists()
        rule.onNodeWithText("제외된 구간은 평균이나 0으로 채우지 않았어요.").assertExists()
        rule.onNodeWithText("구간별 사용량 × 주유 단가로 계산했어요.").assertExists()
    }

    @Test fun settlementLongDriverNameAndErrorKeepConfirmVisible() {
        val fuel = result(listOf(interval(0)), 13_600, notIncluded = false)
        showSettlement(
            SettlementUiState(
                isLoading = false, preview = preview(fuel, 13_600),
                driverNameInput = "아주아주아주아주아주아주아주아주 긴 운전자 표시 이름입니다",
                confirmError = "정산을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요. 입력한 내용은 그대로 남아 있습니다.",
            ),
        )
        rule.onNodeWithText("정산을 저장하지 못했습니다.", substring = true).assertExists()
        rule.onNodeWithText("이 금액으로 확정").assertExists()
    }

    @Test fun settlementLoadingNotFoundAndErrorStates() {
        showSettlement(SettlementUiState(isLoading = true))
        rule.onNodeWithText("정산을 계산하는 중").assertExists()
    }

    @Test fun settlementNotFoundState() {
        showSettlement(SettlementUiState(isLoading = false, notFound = true))
        rule.onNodeWithText("여행을 찾을 수 없어요").assertExists()
    }

    @Test fun settlementErrorOffersRetry() {
        showSettlement(SettlementUiState(isLoading = false, loadError = "정산을 불러오지 못했습니다."))
        rule.onNodeWithText("정산을 불러오지 못했습니다.", substring = true).assertExists()
    }

    // ---------------------------------------------------------------- 확정 결과

    @Test fun confirmedResultShowsIntervalLinesAndFuelCostNote() {
        val longLine = "구간 3 · 128.4 km · 사용량 9.87 L × 1,689원/L = 16,670.43원 · 마지막 주유 이후 누적 연비 차이로 계산"
        val view = PaymentResultView(
            tripName = "함께한 주말 여행", tripDate = "2026-11-12", total = "16,670원", fuelTotal = "13,670원",
            otherTotal = "3,000원", peopleLine = "2명",
            shares = listOf(ShareLine("나", "8,335원"), ShareLine("참여자 2", "8,335원")),
            costs = listOf(CostLine("주유 · 샘플 주유소", "50,000원", listOf("1,700원/L"), true)),
            fuelIntervalLines = listOf("구간 1 · 100 km · 사용량 8.00 L × 1,700원/L = 13,600원", longLine),
            fuelSegments = true, distance = "100 km", quality = "일부 측정", source = "샘플",
            calculationVersion = FUEL_SEGMENTS_CALCULATION_VERSION, perPerson = "8,335원", splitPeople = 2,
        )
        rule.setContent {
            TagoTheme {
                ResultContent(
                    ResultUiState(isLoading = false, content = ResultContentState.Payments(view, "요약", QrState.None)),
                    {}, {}, {},
                )
            }
        }
        rule.onNodeWithText("구간별 유류비 계산").assertExists()
        rule.onNodeWithText("구간 1 · 100 km", substring = true).assertExists()
        rule.onNodeWithText(longLine).assertExists()
        rule.onNodeWithText("단가 산정용 · 금액은 합계에 직접 더하지 않아요").assertExists()
    }
}
