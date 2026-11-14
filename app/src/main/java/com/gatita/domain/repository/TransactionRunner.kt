package com.gatita.domain.repository

/**
 * 읽기-검증-쓰기를 하나의 원자적 단위로 묶는다. 동시에 실행되는 블록은 직렬화되어야 한다.
 * 블록에서 예외가 나면 블록 안의 모든 쓰기가 취소되어야 한다.
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
