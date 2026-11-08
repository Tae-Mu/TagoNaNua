package com.gatita.domain.calculator

import com.gatita.domain.model.CostInput
import com.gatita.domain.model.CostKind
import com.gatita.domain.model.CostLimits
import com.gatita.domain.model.DEFAULT_TRIP_NAME
import com.gatita.domain.model.Decimals
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.domainFailure

/** 사용자 입력 검증. 통과한 값은 trim·정규화되어 저장 가능한 형태로 돌아온다. */
object CostValidator {

    fun validate(input: CostInput): DomainResult<CostInput> {
        if (input.amountWon !in CostLimits.MIN_AMOUNT_WON..CostLimits.MAX_AMOUNT_WON) {
            return domainFailure(
                ErrorCode.INVALID_COST_AMOUNT,
                "Amount must be ${CostLimits.MIN_AMOUNT_WON}..${CostLimits.MAX_AMOUNT_WON} won.",
                "amountWon",
            )
        }
        val maxDescription =
            if (input.kind == CostKind.FUEL) CostLimits.FUEL_STATION_MAX_LENGTH else CostLimits.DESCRIPTION_MAX_LENGTH
        val description = normalizeText(input.description, maxDescription)
            ?: return domainFailure(
                ErrorCode.INVALID_COST_DESCRIPTION,
                "Description must be at most $maxDescription characters without control characters.",
                "description",
            )

        if (input.kind != CostKind.FUEL) {
            return DomainResult.Success(
                CostInput(input.kind, input.amountWon, description.value, unitPriceWonPerLiter = null, paidOn = null),
            )
        }

        val price = input.unitPriceWonPerLiter
            ?: return domainFailure(ErrorCode.INVALID_FUEL_PRICE, "Fuel unit price is required.", "unitPrice")
        val canonical = Decimals.canonical(price)
        if (canonical < CostLimits.MIN_UNIT_PRICE ||
            canonical > CostLimits.MAX_UNIT_PRICE ||
            canonical.scale() > CostLimits.UNIT_PRICE_MAX_SCALE
        ) {
            return domainFailure(
                ErrorCode.INVALID_FUEL_PRICE,
                "Fuel unit price must be 1..9999 won/L with at most 2 decimals.",
                "unitPrice",
            )
        }
        return DomainResult.Success(
            CostInput(CostKind.FUEL, input.amountWon, description.value, canonical, input.paidOn),
        )
    }

    /** 이름 정규화. 빈 값은 [DEFAULT_TRIP_NAME]. */
    fun normalizeTripName(raw: String?): DomainResult<String> {
        val text = normalizeText(raw, CostLimits.TRIP_NAME_MAX_LENGTH)
            ?: return domainFailure(
                ErrorCode.INVALID_TRIP_NAME,
                "Trip name must be at most ${CostLimits.TRIP_NAME_MAX_LENGTH} characters without control characters.",
                "name",
            )
        return DomainResult.Success(text.value ?: DEFAULT_TRIP_NAME)
    }

    /** 인원 1..8. 1명이면 운전자 포함으로 강제한다. 반환은 (people, includeDriver). */
    fun normalizeSplitPolicy(people: Int, includeDriver: Boolean): DomainResult<Pair<Int, Boolean>> {
        if (people !in CostLimits.MIN_PEOPLE..CostLimits.MAX_PEOPLE) {
            return domainFailure(
                ErrorCode.INVALID_HEADCOUNT,
                "People must be ${CostLimits.MIN_PEOPLE}..${CostLimits.MAX_PEOPLE}.",
                "people",
            )
        }
        return DomainResult.Success(people to (includeDriver || people == 1))
    }

    /** 표시 이름. 빈 값은 [ShareLabels.DEFAULT_DRIVER_NAME]. */
    fun normalizeDisplayName(raw: String?): DomainResult<String> {
        val text = normalizeText(raw, CostLimits.DISPLAY_NAME_MAX_LENGTH)
            ?: return domainFailure(
                ErrorCode.INVALID_PARTICIPANTS,
                "Display name must be at most ${CostLimits.DISPLAY_NAME_MAX_LENGTH} characters without control characters.",
                "displayName",
            )
        return DomainResult.Success(text.value ?: ShareLabels.DEFAULT_DRIVER_NAME)
    }

    /** trim 후 빈 값은 null. 길이 초과나 제어 문자가 있으면 null 반환 대신 실패를 알리려고 Normalized로 감싼다. */
    private class Normalized(val value: String?)

    private fun normalizeText(raw: String?, maxLength: Int): Normalized? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.any(Char::isISOControl)) return null
        if (trimmed.codePointCount(0, trimmed.length) > maxLength) return null
        return Normalized(trimmed.ifEmpty { null })
    }
}
