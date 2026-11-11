package com.sadturtleman.flowershoes.core.auth

import kotlinx.coroutines.CancellationException

/** 등록 성공과 자동 해제 준비를 구분한다. 실패/불확정은 안내하며 취소는 상위 작업에 전달한다. */
class AppleTokenRegistration(
    private val saveCode: suspend (String) -> Boolean,
    private val readReady: suspend () -> Boolean,
) {
    suspend fun register(code: String?): Boolean = attempt {
        !code.isNullOrBlank() && saveCode(code) && readReady()
    }

    suspend fun ready(): Boolean = attempt { readReady() }

    private suspend fun attempt(action: suspend () -> Boolean): Boolean = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }
}
