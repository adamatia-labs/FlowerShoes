package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase

/** 호출자는 실제 쓰기와 같은 Room immediate 트랜잭션에서 검사한다. */
internal suspend fun AppDatabase.requireNoCleanup() {
    check(cleanupDao().get() == null) { "진행 중인 연결 정리를 먼저 마쳐 주세요" }
}
