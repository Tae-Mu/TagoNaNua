package com.gatita.domain.calculator

object ShareAllocator {
    /**
     * [totalWon]을 [headcount]명에게 정수 원 단위로 나눈다. 나머지는 참여자 순서대로 1원씩 더한다.
     * 예: 3,685원 / 4명 → 922, 921, 921, 921. 합계는 항상 [totalWon]과 같다.
     */
    fun allocate(totalWon: Long, headcount: Int): List<Long> {
        require(totalWon >= 0) { "totalWon must not be negative" }
        require(headcount > 0) { "headcount must be positive" }
        val base = totalWon / headcount
        val remainder = (totalWon % headcount).toInt()
        return List(headcount) { index -> if (index < remainder) base + 1 else base }
    }
}
