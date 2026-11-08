package com.gatita.feature.common

import com.gatita.domain.model.CostLimits
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface ParsedNumber {
    data class Ok(val value: BigDecimal) : ParsedNumber

    data class Invalid(val message: String) : ParsedNumber
}

/**
 * 화면 입력 문자열을 BigDecimal로 바꾼다. 빈 값·지수 표기·부호·범위 초과는 0으로 바꾸지 않고 오류로 돌려준다.
 * 천 단위 쉼표와 앞뒤 공백만 허용한다.
 */
object InputParsers {
    private const val MAX_INTEGER_DIGITS = 9

    fun decimal(
        raw: String,
        label: String,
        maxFractionDigits: Int,
        allowZero: Boolean = false,
    ): ParsedNumber {
        val text = raw.trim().replace(",", "")
        if (text.isEmpty()) return ParsedNumber.Invalid("$label 값을 입력하세요.")
        val pattern = Regex("""\d{1,$MAX_INTEGER_DIGITS}(\.\d{1,$maxFractionDigits})?""")
        if (!pattern.matches(text)) {
            return ParsedNumber.Invalid(
                "$label 은(는) 숫자만 입력하세요(소수점 ${maxFractionDigits}자리까지, 정수부 ${MAX_INTEGER_DIGITS}자리까지).",
            )
        }
        val value = BigDecimal(text)
        if (!allowZero && value.signum() == 0) {
            return ParsedNumber.Invalid("$label 은(는) 0보다 커야 합니다.")
        }
        return ParsedNumber.Ok(value)
    }

    fun odometerKm(raw: String, label: String): ParsedNumber =
        decimal(raw, label, maxFractionDigits = 1, allowZero = true)

    fun efficiency(raw: String, label: String): ParsedNumber =
        decimal(raw, label, maxFractionDigits = 2)

    fun pricePerLiter(raw: String): ParsedNumber =
        decimal(raw, "리터당 유가", maxFractionDigits = 2)

    /** 원 단위 정수 금액. 소수·부호·지수 표기는 거부하며 쉼표와 앞뒤 공백만 허용한다. */
    fun wonAmount(raw: String): ParsedWon {
        val text = raw.trim().replace(",", "")
        if (text.isEmpty()) return ParsedWon.Invalid("금액을 입력하세요.")
        if (!Regex("""\d{1,9}""").matches(text)) return ParsedWon.Invalid("금액은 원 단위 정수로 입력하세요.")
        val value = text.toLong()
        if (value !in CostLimits.MIN_AMOUNT_WON..CostLimits.MAX_AMOUNT_WON) {
            return ParsedWon.Invalid("금액은 1원 이상 10,000,000원 이하여야 합니다.")
        }
        return ParsedWon.Ok(value)
    }

    /** 빈 값은 "입력 없음"(null)이다. 실제로 존재하는 날짜만 허용하며 미래 날짜를 임의로 막지 않는다. */
    fun isoDate(raw: String): ParsedDate {
        val text = raw.trim()
        if (text.isEmpty()) return ParsedDate.Ok(null)
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(text)) {
            return ParsedDate.Invalid("날짜는 2026-11-09 형식으로 입력하세요.")
        }
        return try {
            ParsedDate.Ok(LocalDate.parse(text))
        } catch (e: DateTimeParseException) {
            ParsedDate.Invalid("존재하지 않는 날짜입니다.")
        }
    }

    fun headcount(raw: String, max: Int): HeadcountResult {
        val text = raw.trim()
        if (text.isEmpty()) return HeadcountResult.Invalid("인원을 입력하세요.")
        // 정규식의 \d는 ASCII 숫자만 허용한다(전각 숫자 등 거부).
        val value = if (Regex("""\d{1,4}""").matches(text)) text.toInt() else null
        if (value == null || value !in 1..max) {
            return HeadcountResult.Invalid("인원은 1~${max}명의 정수로 입력하세요.")
        }
        return HeadcountResult.Ok(value)
    }
}

sealed interface HeadcountResult {
    data class Ok(val value: Int) : HeadcountResult

    data class Invalid(val message: String) : HeadcountResult
}

sealed interface ParsedWon {
    data class Ok(val value: Long) : ParsedWon

    data class Invalid(val message: String) : ParsedWon
}

sealed interface ParsedDate {
    /** [value]가 null이면 입력하지 않은 것이다. */
    data class Ok(val value: LocalDate?) : ParsedDate

    data class Invalid(val message: String) : ParsedDate
}
