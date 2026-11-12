package com.gatita.domain.calculator

import com.gatita.domain.model.FUEL_SEGMENTS_CALCULATION_VERSION
import com.gatita.domain.model.PaymentSettlement
import java.util.Locale

/**
 * 확정된 스냅샷만으로 만드는 공유용 요약 텍스트(QR/복사). 저장된 표시 이름과 금액을 쓰므로 이후 설정이 바뀌어도 같다.
 * 정산에 필요한 값만 담으며 차량 식별자·주행거리계·구간 기록·비용별 메모(주유소·장소)는 포함하지 않는다.
 * 송금이나 입금 확인이 아니며, 텍스트 QR은 암호화나 접근 제어가 아니다.
 */
object PaymentSummaryFormatter {
    const val DISCLAIMER = "※ 계산 결과 공유용 요약입니다. 송금·입금 확인이 아닙니다."

    fun format(settlement: PaymentSettlement): String = buildString {
        appendLine("[TAGO 정산 요약]")
        appendLine("여행: ${settlement.tripName} (${settlement.tripDate})")
        val label = if (settlement.calculationVersion == FUEL_SEGMENTS_CALCULATION_VERSION) "총 정산액" else "총 결제액"
        appendLine("$label: ${won(settlement.totalWon)} (주유 ${won(settlement.fuelTotalWon)} · 기타 ${won(settlement.otherTotalWon)})")
        appendLine(
            "인원: ${settlement.people}명" + if (settlement.includeDriver) "" else " (운전자 제외, ${settlement.splitPeople}명이 분담)",
        )
        appendLine("분담: " + settlement.shares.joinToString(" / ") { "${it.displayName}: ${won(it.amountWon)}" })
        append(DISCLAIMER)
    }

    private fun won(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)
}
