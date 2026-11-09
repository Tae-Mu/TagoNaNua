package com.gatita.feature

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gatita.core.qr.*
import com.gatita.core.ui.theme.TagoTheme
import com.gatita.feature.home.*
import com.gatita.feature.share.*
import org.junit.Rule
import org.junit.Test

class ScreensTest {
    @get:Rule val rule=createComposeRule()
    private fun home(state:HomeUiState) {rule.setContent {TagoTheme {HomeContent(state,{},{},{},{},{},{},{})}}}
    @Test fun homeEmptyStateOffersPrepareAndManualSource() {
        home(HomeUiState(isLoading=false))
        rule.onNodeWithText("수동 기록").assertExists()
        rule.onNodeWithText("새 여행 준비").assertExists().performClick()
        rule.onNodeWithText("여행 기록 시작").assertExists()
    }
    @Test fun homeStartingDisablesDuplicateSubmit() {
        home(HomeUiState(isLoading=false,isStarting=true,startOdometerInput="12000"))
        rule.onNodeWithText("새 여행 준비").performClick()
        rule.onNodeWithText("처리 중…").assertIsNotEnabled()
    }
    @Test fun homeLoadErrorOffersRetry() {
        home(HomeUiState(isLoading=false,loadError="저장된 기록을 불러오지 못했습니다."))
        rule.onNodeWithText("오류: 저장된 기록을 불러오지 못했습니다.").assertExists()
        rule.onNodeWithText("다시 불러오기").assertExists()
    }
    @Test fun qrHasAccessibleDescription() {
        val matrix=(QrEncoder.encode("[TAGO 정산 요약] 민수 1원") as QrResult.Success).matrix
        rule.setContent {TagoTheme {QrCodeView(matrix,"정산 요약 QR 코드. 같은 내용이 아래 텍스트로도 표시됩니다.")}}
        rule.onNodeWithContentDescription("정산 요약 QR 코드. 같은 내용이 아래 텍스트로도 표시됩니다.").assertExists()
    }
    @Test fun resultWithoutSettlementShowsEmptyState() {
        rule.setContent {TagoTheme {ResultContent(ResultUiState(isLoading=false,notFound=true),{},{},{})}}
        rule.onNodeWithText("확정된 정산이 없습니다").assertExists()
    }
}
