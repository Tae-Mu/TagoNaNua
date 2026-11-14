package com.gatita.feature.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InputParsersTest {
    private fun ParsedNumber.value(): BigDecimal = (this as ParsedNumber.Ok).value

    @Test
    fun `accepts plain decimals with thousands separators and spaces`() {
        assertEquals(0, BigDecimal("12000.5").compareTo(InputParsers.odometerKm(" 12,000.5 ", "주행거리").value()))
        assertEquals(0, BigDecimal("0").compareTo(InputParsers.odometerKm("0", "주행거리").value()))
        assertEquals(0, BigDecimal("1650").compareTo(InputParsers.pricePerLiter("1650").value()))
    }

    @Test
    fun `blank, signed, exponent and text inputs are errors and never zero`() {
        listOf("", "  ", "-1", "+1", "1e3", "abc", "1.2.3", ".5", "5.", "１２").forEach {
            assertTrue("'$it' should be invalid", InputParsers.odometerKm(it, "주행거리") is ParsedNumber.Invalid)
        }
    }

    @Test
    fun `too many fraction or integer digits are rejected`() {
        assertTrue(InputParsers.odometerKm("1.25", "주행거리") is ParsedNumber.Invalid)
        assertTrue(InputParsers.efficiency("12.345", "연비") is ParsedNumber.Invalid)
        assertTrue(InputParsers.odometerKm("1234567890", "주행거리") is ParsedNumber.Invalid)
    }

    @Test
    fun `efficiency and price must be greater than zero`() {
        assertTrue(InputParsers.efficiency("0", "연비") is ParsedNumber.Invalid)
        assertTrue(InputParsers.efficiency("0.00", "연비") is ParsedNumber.Invalid)
        assertTrue(InputParsers.pricePerLiter("0") is ParsedNumber.Invalid)
    }

    @Test
    fun `headcount must be a whole number within range`() {
        assertEquals(4, (InputParsers.headcount(" 4 ", 100) as HeadcountResult.Ok).value)
        listOf("", "0", "101", "3.5", "-2", "abc").forEach {
            assertTrue("'$it' should be invalid", InputParsers.headcount(it, 100) is HeadcountResult.Invalid)
        }
    }
}
