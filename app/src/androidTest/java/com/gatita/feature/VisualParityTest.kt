package com.gatita.feature

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gatita.core.ui.theme.TagoTheme
import com.gatita.feature.home.*
import com.gatita.feature.share.*
import com.gatita.feature.settlement.ShareLine
import com.gatita.feature.trip.*
import com.gatita.domain.model.*
import com.gatita.navigation.ConnectedShell
import java.math.BigDecimal
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Renders production components with isolated values; never opens or changes the user's database. */
class VisualParityTest {
    @get:Rule val rule=createComposeRule()
    private fun shot(name:String) {
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"parity").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun homeAndPrepareKeepSdkFieldsOut() {
        rule.setContent { TagoTheme { ConnectedShell("home",{}) { HomeContent(HomeUiState(isLoading=false),{},{},{},{},{},{}) } } }
        shot("home")
        rule.onNodeWithText("새 여행 준비").performClick()
        rule.onNodeWithText("여행 기록 시작").assertExists()
        rule.onNodeWithText("출발 계기판 주행거리 (km)").assertDoesNotExist()
        shot("prepare")
    }
    @Test fun confirmedResultKeepsStoredAmountsVisible() {
        val view=PaymentResultView("함께한 주말 여행","2026-11-02","60,000원","60,000원","0원","3명",
            listOf(ShareLine("나","20,000원"),ShareLine("참여자 2","20,000원"),ShareLine("참여자 3","20,000원")),
            listOf(CostLine("주유 · 샘플 주유소","60,000원",listOf("2026. 11. 2 · 1,890원/L"),true)),
            distance="82 km",quality="측정됨",source="샘플",calculationVersion="payments-v1",perPerson="20,000원",splitPeople=3)
        rule.setContent { TagoTheme { ConnectedShell("settlements",{}) { ResultContent(ResultUiState(isLoading=false,content=ResultContentState.Payments(view,"테스트 요약",QrState.None)),{},{},{}) } } }
        rule.onNodeWithText("함께 나눌 총금액").assertExists()
        rule.onNodeWithText("20,000  원 / 인").assertExists()
        shot("confirmed-result")
    }
    @Test fun recordingAndFinishDialogKeepSameJourney() {
        val trip=Trip(1,TripStatus.IN_PROGRESS,VehicleSource.SAMPLE,BigDecimal.ZERO,0,null,null,name="강릉 주말 여행",people=4)
        val ledger=CostLedger(1,TripStatus.IN_PROGRESS,0,true,emptyList(),0,0,0,0,0,emptyMap())
        var state by mutableStateOf(TripUiState(isLoading=false,trip=trip,ledger=ledger,recordedDistance="30.0 km",recordedMinutes=42,
            segments=listOf(SegmentRow(1,"출발 → 경유지","10 km","","샘플"),SegmentRow(2,"경유지 → 주유 지점","4 km","","샘플"),SegmentRow(3,"주유 지점 → 도착","16 km","","샘플"))))
        rule.setContent { TagoTheme { ConnectedShell("home",{}) { TripContent(state,
            onBack={},onRetryLoad={},onAddCost={},onEditCost={},onIncludedChange={_,_->},onRequestDelete={},onConfirmDelete={},onDismissDelete={},
            onRequestComplete={state=state.copy(showCompleteConfirm=true)},
            onConfirmComplete={},onDismissComplete={state=state.copy(showCompleteConfirm=false)},onRequestDiscard={},onConfirmDiscard={},onDismissDiscard={},onOpenSettlement={},onOpenResult={}) } } }
        rule.onNodeWithText("강릉 주말 여행").assertExists()
        shot("recording")
        rule.onNodeWithText("여행 마치기").performScrollTo().performClick()
        rule.onNodeWithText("이번 여행을 마칠까요?").assertExists()
        // Dialogs are separate roots; the production dialog remains independently inspectable.
    }
    @Test fun completedJourneyHasPrimarySettlementAction() {
        val trip=Trip(1,TripStatus.FINISHED,VehicleSource.SAMPLE,BigDecimal.ZERO,0,BigDecimal("30"),2520000,name="강릉 주말 여행",people=4)
        rule.setContent { TagoTheme { ConnectedShell("home",{}) { TripCompletionContent(TripUiState(isLoading=false,trip=trip,recordedDistance="30 km",recordedMinutes=42),{},{}) } } }
        rule.onNodeWithText("정산하기").assertExists()
        shot("completed-journey")
    }

}
