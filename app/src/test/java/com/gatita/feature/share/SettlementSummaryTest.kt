package com.gatita.feature.share

import com.gatita.core.qr.QrEncoder
import com.gatita.core.qr.QrResult
import com.gatita.domain.calculator.SettlementCalculator
import com.gatita.domain.model.Settlement
import com.gatita.domain.model.SettlementInput
import com.gatita.domain.model.FuelPrice
import com.gatita.domain.model.FuelType
import com.gatita.d
import com.gatita.finishedTrip
import com.gatita.kmPerLiter
import com.gatita.segment
import com.gatita.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class SettlementSummaryTest {
    private fun settlement(headcount: Int = 4, names: List<String>? = null, fallback: Boolean = false): Settlement {
        val trip = finishedTrip(start = "0", end = "100")
        val segments = if (fallback) listOf(segment(1, "0", "100", efficiency = null)) else listOf(segment(1, "0", "100"))
        val input = SettlementInput(
            fuelPrice = FuelPrice(FuelType.GASOLINE, d("368.5"), "2026-11-09"),
            headcount = headcount,
            participantNames = names,
            fallbackEfficiency = if (fallback) kmPerLiter("10", scope = com.gatita.domain.model.EfficiencyScope.VEHICLE_REFERENCE) else null,
        )
        val calc = SettlementCalculator().calculate(trip, segments, input).success()
        return Settlement(id = 1, tripId = 1, requestId = "r", confirmedAtMillis = 1_762_650_000_000, calculation = calc)
    }

    @Test
    fun `summary holds the totals and groups equal shares`() {
        val text = SettlementSummary.build(settlement(), zone = ZoneOffset.UTC)
        assertTrue(text, text.contains("총 유류비: 3,685원"))
        assertTrue(text, text.contains("인원: 4명"))
        assertTrue(text, text.contains("분담: 1번 922원 / 2~4번 921원"))
        assertTrue(text, text.contains("구간별 입력 연비 합산"))
        assertTrue(text, text.endsWith(SettlementSummary.DISCLAIMER))
    }

    @Test
    fun `summary says estimate when a reference efficiency was used`() {
        val text = SettlementSummary.build(settlement(fallback = true), zone = ZoneOffset.UTC)
        assertTrue(text, text.contains("총 유류비: 3,685원 (추정)"))
        assertTrue(text, text.contains("추정"))
    }

    @Test
    fun `summary never claims payment status and omits odometer and vehicle identifiers`() {
        val text = SettlementSummary.build(settlement(), zone = ZoneOffset.UTC)
        assertFalse(text.contains("입금 완료"))
        assertFalse(text.contains("송금 완료"))
        assertFalse(text.contains("주행거리계"))
        assertTrue(text.contains("송금·입금 확인이 아닙니다"))
    }

    @Test
    fun `named participants are listed individually and can be dropped`() {
        val s = settlement(headcount = 2, names = listOf("민수", "지은"))
        assertTrue(SettlementSummary.build(s, includeNames = true, zone = ZoneOffset.UTC).contains("민수"))
        assertFalse(SettlementSummary.build(s, includeNames = false, zone = ZoneOffset.UTC).contains("민수"))
    }

    @Test
    fun `summary for the maximum headcount fits in a QR code`() {
        val text = SettlementSummary.build(settlement(headcount = 100), zone = ZoneOffset.UTC)
        assertTrue(QrEncoder.encode(text) is QrResult.Success)
        assertEquals(1, text.lines().count { it.startsWith("분담:") })
    }
}
