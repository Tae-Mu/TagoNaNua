package com.gatita.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareAllocatorTest {
    @Test
    fun `remainder is distributed one won each in participant order`() {
        assertEquals(listOf(922L, 921L, 921L, 921L), ShareAllocator.allocate(3685, 4))
        assertEquals(listOf(4L, 3L, 3L), ShareAllocator.allocate(10, 3))
        assertEquals(listOf(2L, 2L, 1L, 1L, 1L), ShareAllocator.allocate(7, 5))
    }

    @Test
    fun `total smaller than headcount gives first participants one won`() {
        assertEquals(listOf(1L, 1L, 0L, 0L), ShareAllocator.allocate(2, 4))
    }

    @Test
    fun `sum always equals total and shares differ by at most one won`() {
        for (total in 0L..500L) {
            for (people in 1..13) {
                val shares = ShareAllocator.allocate(total, people)
                assertEquals(people, shares.size)
                assertEquals(total, shares.sum())
                assertEquals(true, shares.max() - shares.min() <= 1)
                assertEquals(shares, shares.sortedDescending()) // earlier participants never pay less
            }
        }
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ShareAllocator.allocate(100, 0) }
        assertThrows(IllegalArgumentException::class.java) { ShareAllocator.allocate(-1, 2) }
    }
}
