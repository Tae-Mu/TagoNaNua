package com.gatita.feature

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.gatita.core.qr.QrEncoder
import com.gatita.core.qr.QrResult
import com.gatita.core.ui.theme.GatitaTheme
import com.gatita.feature.home.HomeContent
import com.gatita.feature.home.HomeUiState
import com.gatita.feature.settlement.SettlementView
import com.gatita.feature.settlement.ShareLine
import com.gatita.feature.share.QrState
import com.gatita.feature.share.ResultContent
import com.gatita.feature.share.ResultUiState
import org.junit.Rule
import org.junit.Test

/**
 * Compose 렌더링 확인용 계측 테스트. 이 환경에서는 실행하지 못했으며 에뮬레이터/기기에서 실행해야 한다.
 */
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun home(state: HomeUiState) {
        rule.setContent {
            GatitaTheme {
                HomeContent(state, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test
    fun home_emptyState_labelsManualInputAndOffersStart() {
        home(HomeUiState(isLoading = false))
        rule.onNodeWithText("직접 입력 모드").assertExists()
        rule.onNodeWithText("기록된 여행이 없습니다").assertExists()
        rule.onNodeWithText("여행 시작").assertExists()
    }

    @Test
    fun home_starting_disablesTheStartButton() {
        home(HomeUiState(isLoading = false, isStarting = true, startOdometerInput = "12000"))
        rule.onNodeWithText("시작하는 중…").assertIsNotEnabled()
    }

    @Test
    fun home_loadError_showsMessageAndRetry() {
        home(HomeUiState(isLoading = false, loadError = "저장된 기록을 불러오지 못했습니다."))
        rule.onNodeWithText("오류: 저장된 기록을 불러오지 못했습니다.").assertExists()
        rule.onNodeWithText("다시 불러오기").assertExists()
    }

    @Test
    fun result_showsQrWithAccessibleDescriptionAndSummaryText() {
        val matrix = (QrEncoder.encode("[같이타 정산 요약]") as QrResult.Success).matrix
        val view = SettlementView(
            isEstimate = false,
            methodLabel = "구간별 입력 연비 합산",
            basisExplanation = "설명",
            fallbackReasons = emptyList(),
            totalCost = "3,685원",
            totalLiters = "10 L",
            totalDistance = "100 km",
            unitPrice = "368.5원/L (휘발유, 직접 입력)",
            headcount = 4,
            shareLines = listOf(ShareLine("1번", "922원")),
            basisLines = emptyList(),
            sourceLabel = "수동 입력",
            roundingNote = "반올림 안내",
        )
        rule.setContent {
            GatitaTheme {
                ResultContent(
                    state = ResultUiState(
                        isLoading = false,
                        view = view,
                        confirmedAt = "2026-11-09 10:00",
                        summaryText = "[같이타 정산 요약]",
                        qr = QrState.Ready(matrix),
                    ),
                    onBack = {},
                    onHome = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithContentDescription("정산 요약 QR 코드. 같은 내용이 아래 텍스트로도 표시됩니다.").assertExists()
        rule.onNodeWithText("[같이타 정산 요약]").assertExists()
    }

    @Test
    fun result_withoutSettlement_showsEmptyState() {
        rule.setContent {
            GatitaTheme {
                ResultContent(ResultUiState(isLoading = false, notFound = true), {}, {}, {})
            }
        }
        rule.onNodeWithText("확정된 정산이 없습니다").assertExists()
    }
}
