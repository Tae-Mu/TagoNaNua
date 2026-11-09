package com.gatita.feature.share

import com.gatita.domain.model.CalculationMethod
import com.gatita.domain.model.Settlement
import com.gatita.domain.model.SettlementShare
import com.gatita.feature.common.Formatters
import java.time.ZoneId

/**
 * QR에 담는 정산 요약 텍스트. 정산에 필요한 값만 담으며 주행거리계 값, 차량 식별자, 구간별 기록은 포함하지 않는다.
 * 송금·입금 여부를 나타내지 않는 계산 결과이며, 텍스트 QR은 암호화나 접근 제어가 아니다.
 */
object SettlementSummary {
    const val DISCLAIMER = "※ 계산 결과 공유용 요약입니다. 송금·입금 확인이 아닙니다."

    fun build(
        settlement: Settlement,
        includeNames: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val calc = settlement.calculation
        val estimate = calc.method == CalculationMethod.REFERENCE_ESTIMATE
        val distance = calc.items.fold(java.math.BigDecimal.ZERO) { acc, item -> acc.add(item.distanceKm) }
        return buildString {
            appendLine("[TAGO 정산 요약]")
            appendLine("확정일: ${Formatters.dateTime(settlement.confirmedAtMillis, zone)}")
            appendLine("총 유류비: ${Formatters.won(calc.totalCostWon)}" + if (estimate) " (추정)" else "")
            appendLine("인원: ${calc.headcount}명")
            appendLine("분담: " + shareParts(calc.shares, includeNames).joinToString(" / "))
            appendLine(
                "거리 ${Formatters.km(distance)} · 연료 ${Formatters.liters(calc.totalFuelLiters)} · " +
                    "${Formatters.decimal(calc.fuelPrice.pricePerLiterWon, 2)}원/L(${Formatters.fuelType(calc.fuelPrice.type)})",
            )
            appendLine(
                if (estimate) "계산: 기준 연비로 전체 거리 추정(실제와 다를 수 있음)" else "계산: 구간별 입력 연비 합산",
            )
            append(DISCLAIMER)
        }
    }

    /** 같은 금액이 이어지는 이름 없는 참여자는 "2~4번 921원"처럼 묶어 길이를 제한한다. */
    internal fun shareParts(shares: List<SettlementShare>, includeNames: Boolean): List<String> {
        fun nameOf(share: SettlementShare): String? =
            if (includeNames) share.participantName?.takeIf { it.isNotBlank() } else null

        val parts = mutableListOf<String>()
        var i = 0
        while (i < shares.size) {
            val share = shares[i]
            val name = nameOf(share)
            if (name != null) {
                parts += "$name ${Formatters.won(share.amountWon)}"
                i++
                continue
            }
            var j = i
            while (j + 1 < shares.size && shares[j + 1].amountWon == share.amountWon && nameOf(shares[j + 1]) == null) {
                j++
            }
            val won = Formatters.won(share.amountWon)
            parts += if (j == i) "${share.participantIndex}번 $won" else "${share.participantIndex}~${shares[j].participantIndex}번 $won"
            i = j + 1
        }
        return parts
    }
}
