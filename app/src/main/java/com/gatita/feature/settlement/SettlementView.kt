package com.gatita.feature.settlement

import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.SettlementCalculation
import com.gatita.feature.common.Formatters
import com.gatita.feature.common.userMessage
import java.math.BigDecimal

data class ShareLine(val label: String, val amount: String)

data class BasisLine(val title: String, val detail: String)

/** 계산 결과를 화면용 문자열로 옮긴 값. 계산은 도메인이 끝냈고 여기서는 표시 변환만 한다. */
data class SettlementView(
    val isEstimate: Boolean,
    val methodLabel: String,
    val basisExplanation: String,
    val fallbackReasons: List<String>,
    val totalCost: String,
    val totalLiters: String,
    val totalDistance: String,
    val unitPrice: String,
    val headcount: Int,
    val shareLines: List<ShareLine>,
    val basisLines: List<BasisLine>,
    val sourceLabel: String,
    val roundingNote: String,
)

fun SettlementCalculation.toView(): SettlementView {
    val estimate = method == CalculationMethod.REFERENCE_ESTIMATE
    val price = fuelPrice.pricePerLiterWon
    val distance = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.distanceKm) }
    val sources = items.map { it.source }.distinct().joinToString(", ") { Formatters.source(it) }

    val basis = items.map { item ->
        val title = if (item.segmentSeq == null) "전체 여행(추정)" else "구간 ${item.segmentSeq}"
        val detail = buildString {
            append(Formatters.km(item.distanceKm))
            append(" · 연비 ")
            append(Formatters.efficiency(item.efficiency))
            append(" (")
            append(Formatters.scopeLabel(item.efficiency.scope))
            append(") → 연료 ")
            append(Formatters.liters(item.fuelLiters))
            append(" × ")
            append(Formatters.wonDecimal(item.unitPriceWonPerLiter))
            append("/L = ")
            append(Formatters.wonDecimal(item.costWon))
        }
        BasisLine(title, detail)
    }

    return SettlementView(
        isEstimate = estimate,
        methodLabel = if (estimate) "추정: 기준 연비로 전체 거리 계산" else "구간별 입력 연비 합산",
        basisExplanation = if (estimate) {
            "구간별 연비 기록이 부족해 직접 지정한 기준 연비로 전체 주행거리를 한 번에 추정했습니다. " +
                "실제 연료 사용량과 다를 수 있는 추정치입니다."
        } else {
            "구간마다 거리 ÷ 연비로 연료 사용량을 구해 합산했습니다. " +
                "연비는 사용자가 구간별로 입력한 값이며 차량이 자동으로 측정한 값이 아닙니다."
        },
        fallbackReasons = fallbackReasons.map { it.userMessage() },
        totalCost = Formatters.won(totalCostWon),
        totalLiters = Formatters.liters(totalFuelLiters),
        totalDistance = Formatters.km(distance),
        unitPrice = Formatters.decimal(price, 2) + "원/L (" + Formatters.fuelType(fuelPrice.type) + ", 직접 입력)",
        headcount = headcount,
        shareLines = shares.map { share ->
            ShareLine(
                label = share.participantName?.takeIf { it.isNotBlank() } ?: "${share.participantIndex}번",
                amount = Formatters.won(share.amountWon),
            )
        },
        basisLines = basis,
        sourceLabel = sources,
        roundingNote = "항목별 금액은 반올림하지 않고 합산한 뒤 총액만 원 단위로 반올림(HALF_UP)합니다. " +
            "나머지 원은 앞 번호부터 1원씩 더합니다. 표시 값은 소수 둘째 자리까지 반올림해 보여줍니다.",
    )
}
