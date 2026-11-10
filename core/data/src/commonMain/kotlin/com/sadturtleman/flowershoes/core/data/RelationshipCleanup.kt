package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import kotlinx.coroutines.CancellationException

/** 정리로 무효가 된 화면/동기화 작업만 취소한다. 앱의 복구 안내가 재시도를 맡는다. */
class CleanupInProgressException : CancellationException("진행 중인 연결 정리를 먼저 마쳐 주세요")

/** 호출자는 실제 쓰기와 같은 Room immediate 트랜잭션에서 검사한다. */
internal suspend fun AppDatabase.requireNoCleanup() {
    if (cleanupDao().get() != null) throw CleanupInProgressException()
}
