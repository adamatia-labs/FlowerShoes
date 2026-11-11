package com.sadturtleman.flowershoes.core.data.sync

import kotlinx.coroutines.CancellationException

/** 서버 요청 전에 저장한 작업 토큰. 다른 계정을 삭제하는 권한으로 사용하지 않는다. */
data class AccountDeletionAttempt(val ownerUid: String, val token: String)

/** 인증이 사라져도 같은 작업의 완료 결과를 확인하고 로컬 정리를 이어간다. */
class AccountDeletionRecovery(
    private val currentUid: () -> String?,
    private val delete: suspend (AccountDeletionAttempt) -> AppleRevocation,
    private val completed: suspend (AccountDeletionAttempt) -> AppleRevocation?,
) {
    suspend fun execute(attempt: AccountDeletionAttempt): AppleRevocation {
        completed(attempt)?.let { return it }
        check(currentUid() == attempt.ownerUid) { "이 계정의 탈퇴 결과를 아직 확인하지 못했어요" }
        return try {
            delete(attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val recovered = try {
                completed(attempt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            recovered ?: throw failure
        }
    }
}
