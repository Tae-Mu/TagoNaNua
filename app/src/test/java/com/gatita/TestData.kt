package com.gatita

import com.gatita.domain.model.DomainError
import com.gatita.domain.model.DomainResult
import com.gatita.domain.model.EfficiencyReading
import com.gatita.domain.model.EfficiencyScope
import com.gatita.domain.model.EfficiencyUnit
import com.gatita.domain.model.ErrorCode
import com.gatita.domain.model.FuelPrice
import com.gatita.domain.model.FuelType
import com.gatita.domain.model.Segment
import com.gatita.domain.model.SettlementInput
import com.gatita.domain.model.Trip
import com.gatita.domain.model.TripStatus
import com.gatita.domain.model.VehicleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.math.BigDecimal

fun d(text: String): BigDecimal = BigDecimal(text)

fun kmPerLiter(
    value: String,
    scope: EfficiencyScope = EfficiencyScope.SEGMENT_AVERAGE,
    key: String? = null,
    cumulative: String? = null,
) = EfficiencyReading(d(value), EfficiencyUnit.KM_PER_LITER, scope, key, cumulative?.let(::d))

fun price(won: String) = FuelPrice(FuelType.GASOLINE, d(won), "2026-11-09")

fun input(won: String = "1500", headcount: Int = 1, fallback: EfficiencyReading? = null) =
    SettlementInput(price(won), headcount, fallbackEfficiency = fallback)

fun finishedTrip(start: String = "0", end: String = "100") = Trip(
    id = 1,
    status = TripStatus.FINISHED,
    source = VehicleSource.MANUAL,
    startOdometerKm = d(start),
    startedAtMillis = 1_000,
    endOdometerKm = d(end),
    endedAtMillis = 9_000,
)

fun segment(
    seq: Int,
    start: String,
    end: String,
    efficiency: EfficiencyReading? = kmPerLiter("10"),
    baseline: EfficiencyReading? = null,
) = Segment(
    id = seq.toLong(),
    tripId = 1,
    seq = seq,
    eventId = "e$seq",
    startOdometerKm = d(start),
    endOdometerKm = d(end),
    startedAtMillis = 1_000L + seq,
    endedAtMillis = 2_000L + seq,
    efficiency = efficiency,
    baseline = baseline,
    source = VehicleSource.MANUAL,
)

fun <T> DomainResult<T>.success(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> fail("expected success but was ${error.code}: ${error.message}").let { throw AssertionError() }
}

fun DomainResult<*>.failure(): DomainError = when (this) {
    is DomainResult.Success -> fail("expected failure but was success: $value").let { throw AssertionError() }
    is DomainResult.Failure -> error
}

fun DomainResult<*>.assertFailure(code: ErrorCode) = assertEquals(code, failure().code)

/** 값 비교용(스케일 무시). */
fun assertDecimal(expected: String, actual: BigDecimal) =
    assertEquals("expected $expected but was $actual", 0, d(expected).compareTo(actual))
