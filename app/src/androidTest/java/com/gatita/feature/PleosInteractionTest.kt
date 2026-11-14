package com.gatita.feature

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gatita.core.ui.component.VehicleViewport
import com.gatita.core.ui.theme.TagoTheme
import com.gatita.domain.model.CostKind
import com.gatita.feature.cost.*
import com.gatita.feature.publishing.MonthPicker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Production touch targets in the car viewport, with no database writes. */
class PleosInteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun eachCostCategoryOpensItsOwnFieldsAndReturnsToSelection() {
        var state by mutableStateOf(CostFormUiState(tripName="강릉 주말 여행"))
        rule.setContent { VehicleViewport { TagoTheme {
            CostFormContent(state, {}, {}, { state=state.copy(kind=it) },
                { state=state.copy(amountInput=it) }, { state=state.copy(descriptionInput=it) },
                { state=state.copy(unitPriceInput=it) }, {}, {})
        } } }
        listOf("주유" to "1L당 가격", "주차비" to "주차 장소 · 선택",
            "톨비" to "톨게이트 / 이용 구간 · 선택", "기타 비용" to "비용 내용 · 선택").forEach { (category, field) ->
            rule.onNodeWithText(category).performScrollTo().performClick()
            rule.onAllNodesWithText(field).onFirst().assertExists()
            rule.onNodeWithText("비용 종류로 돌아가기").performScrollTo().performClick()
            rule.onNodeWithText("어떤 비용을 기록할까요?").assertExists()
        }
        assertEquals(CostKind.OTHER, state.kind)
    }

    @Test fun departureMascotKeepsSkipAndCompletionAvailable() {
        var completed=false
        rule.mainClock.autoAdvance=false
        rule.setContent { VehicleViewport { TagoTheme {
            com.gatita.feature.publishing.DepartureScreen("강릉 주말 여행",onFinished={completed=true})
        } } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("강릉 주말 여행").assertExists()
        rule.onNodeWithText("안전 운전하세요.").assertExists()
        // Allow the local animated image decoder to deliver its first frames.
        android.os.SystemClock.sleep(500)
        rule.mainClock.advanceTimeBy(200)
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        java.io.File(instrumentation.targetContext.cacheDir,"departure-mascot.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        rule.onNodeWithText("바로 기록 보기").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(true,completed) }
    }

    @Test fun dialogBackgroundDismissesWithoutApplyingMonth() {
        var visible by mutableStateOf(true)
        var applied=false
        rule.setContent { VehicleViewport { TagoTheme {
            if(visible) MonthPicker("2026년 11월", { applied=true }, { visible=false })
        } } }
        rule.onNodeWithText("여행한 달 선택").assertExists()
        rule.onNode(isDialog()).performTouchInput { click(androidx.compose.ui.geometry.Offset(5f,5f)) }
        rule.onNodeWithText("여행한 달 선택").assertDoesNotExist()
        rule.runOnIdle { assertEquals(false,applied) }
    }

    @Test fun sixWeekCalendarCanSelectAndApplyLastDay() {
        var applied="2026-11-10"
        rule.setContent { VehicleViewport { TagoTheme {
            com.gatita.feature.publishing.DayPicker(applied, { applied=it }, {})
        } } }
        rule.onNodeWithText("30").performScrollTo().performClick()
        rule.onNodeWithText("적용").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("2026-11-30", applied) }
    }

    @Test fun monthChangesAreDraftUntilAppliedAndCancelDoesNotApply() {
        var visible by mutableStateOf(true)
        var applied = "2026년 11월"
        rule.setContent { VehicleViewport { TagoTheme {
            if (visible) MonthPicker(applied, { applied=it; visible=false }, { visible=false })
        } } }
        rule.onNodeWithContentDescription("이전 연도").performClick()
        rule.onNodeWithText("3월").performClick()
        assertEquals("2026년 11월", applied)
        rule.onNodeWithText("취소").performClick()
        assertEquals("2026년 11월", applied)
        rule.runOnIdle { visible=true }
        rule.onNodeWithContentDescription("다음 연도").performClick()
        rule.onNodeWithText("2월").performClick()
        rule.onNodeWithText("적용").performClick()
        rule.runOnIdle { assertEquals("2027년 2월", applied) }
    }
}
