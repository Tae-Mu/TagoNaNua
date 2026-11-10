package com.gatita.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PleosValueMapperTest {
    @Test
    fun `정상 값은 이진 오차 없이 십진수로 바꾼다`() {
        assertEquals(BigDecimal("100.1"), PleosValueMapper.odometerKm(100.1f))
        assertEquals(BigDecimal("12345.6"), PleosValueMapper.odometerKm(12345.6f))
    }

    @Test
    fun `정수 값은 소수부 없이 표현한다`() {
        assertEquals(0, BigDecimal("120").compareTo(PleosValueMapper.odometerKm(120f)))
        assertEquals(0, PleosValueMapper.odometerKm(120f)!!.scale())
    }

    @Test
    fun `큰 값도 지수 표기 없이 정규화한다`() {
        assertEquals(0, BigDecimal("12345678").compareTo(PleosValueMapper.odometerKm(12345678f)))
        assertEquals(0, PleosValueMapper.odometerKm(12345678f)!!.scale())
    }

    @Test
    fun `0은 유효한 계기판 값이다`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(PleosValueMapper.odometerKm(0f)))
    }

    @Test
    fun `null NaN 무한대 음수는 값 없음으로 처리하고 0으로 바꾸지 않는다`() {
        assertNull(PleosValueMapper.odometerKm(null))
        assertNull(PleosValueMapper.odometerKm(Float.NaN))
        assertNull(PleosValueMapper.odometerKm(Float.POSITIVE_INFINITY))
        assertNull(PleosValueMapper.odometerKm(Float.NEGATIVE_INFINITY))
        assertNull(PleosValueMapper.odometerKm(-1f))
    }
}
