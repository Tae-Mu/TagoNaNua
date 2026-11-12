@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gatita.feature.publishing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatita.core.ui.component.*
import com.gatita.core.ui.theme.*

internal fun expenseKindLabel(kind: String) = when (kind) {
    "톨비" -> "통행료"
    "기타" -> "기타 비용"
    else -> kind
}

@Composable internal fun ExpenseScreen(
    state: SampleState, go: (Page) -> Unit, kind: String,
    onKind: (String) -> Unit, tripName: String, editor:CostUi?=null,returnTo:Page=Page.Trip
) {
    var amount by rememberSaveable { mutableStateOf(editor?.amount?.replace(",","")?:"") }
    var note by rememberSaveable { mutableStateOf(editor?.title?:"") }
    val field = when (kind) {
        "주차비" -> Triple("주차 장소", "예: 강릉 중앙시장 공영주차장", "어디에 주차했는지 남겨두면 나중에 확인하기 쉬워요.")
        "톨비" -> Triple("톨게이트 / 이용 구간", "예: 서울 → 강릉, 대관령 톨게이트", "이용한 구간이나 톨게이트 이름을 남겨보세요.")
        else -> Triple("비용 내용", "예: 세차비, 여행 중 함께 구매한 물품", "어떤 비용인지 자유롭게 적어 주세요.")
    }
    Panel(Modifier.fillMaxWidth()) {
        Surface(color = Tint, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(tripName, Modifier.padding(14.dp), color = Blue, fontSize = 15.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("비용 종류", color = Muted, fontSize = 16.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("주차비", "톨비", "기타").forEach { value ->
                    FilterChip(kind == value, { onKind(value) }, label = { Text(expenseKindLabel(value)) })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyInput("결제 금액", amount, { amount = it }, "3,000", "원")
            Note("실제로 결제한 금액을 입력해 주세요.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Input("${field.first} · 선택", note, { note = it.take(100) }, field.second)
            Note(field.third)
        }
        if (state == SampleState.Error) Text("결제 금액을 확인해 주세요.", color = MaterialTheme.colorScheme.error)
        Action(if(editor==null)"추가하기" else "수정 완료", enabled=amount.toLongOrNull()?.let{it in 1..10000000}==true,modifier = Modifier.fillMaxWidth()) { go(returnTo) }
        Note("금액만 입력해도 등록할 수 있어요. 정산 포함 여부는 내역에서 변경해요.")
    }
}
