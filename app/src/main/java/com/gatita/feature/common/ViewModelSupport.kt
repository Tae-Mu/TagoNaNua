package com.gatita.feature.common

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 재시도에서 같아야 하는 값(이벤트 ID, 요청 ID, 시각)을 보관한다.
 * 실제 구현은 SavedStateHandle이라 화면 회전과 프로세스 재시작에서도 같은 값이 유지된다.
 */
interface AttemptStore {
    fun get(key: String): String?

    /** [value]가 null이면 삭제한다. */
    fun put(key: String, value: String?)
}

class SavedStateAttemptStore(private val handle: SavedStateHandle) : AttemptStore {
    override fun get(key: String): String? = handle.get<String>(key)

    override fun put(key: String, value: String?) {
        if (value == null) handle.remove<String>(key) else handle[key] = value
    }
}

class MapAttemptStore : AttemptStore {
    private val values = HashMap<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** 저장·종료·확정 같은 한 번만 실행해야 하는 작업의 중복 탭을 막는다. 상태 갱신보다 먼저 동기적으로 잠근다. */
class SubmitGuard {
    private val busy = AtomicBoolean(false)

    fun tryAcquire(): Boolean = busy.compareAndSet(false, true)

    fun release() = busy.set(false)
}
