package com.sadturtleman.flowershoes.core.tti

/**
 * 완성된 측정을 내보내는 곳. 수집 서버가 없어 지금은 [ttiModule] 이 [PrintTtiShooter] 를 꽂는다.
 *
 * IO 디스패처에서 불린다.
 */
fun interface TtiShooter {
    /** 실패하면 그대로 예외를 던진다 — 기록은 지워지지 않고 다음 기회에 다시 나간다. */
    suspend fun shoot(records: List<TtiRecord>)
}

/**
 * 기본 구현. 아직 보낼 서버가 없으므로 표준 출력으로만 흘린다.
 *
 * 플랫폼 로거(android.util.Log 등)를 쓰지 않는 것은 이 모듈이 어느 플랫폼에서도 그대로 돌아야 하기 때문이다.
 * 안드로이드에서는 표준 출력도 Logcat 으로, iOS 에서는 Xcode 콘솔로 들어오므로 보는 데는 차이가 없다.
 */
class PrintTtiShooter : TtiShooter {

    override suspend fun shoot(records: List<TtiRecord>) {
        records.forEach { record ->
            val breakdown = TtiTimeline.entries.joinToString(" | ") { timeline ->
                "$timeline=${record.spans[timeline]?.durationMillis}ms"
            }
            println("$TAG: ${record.pageName} total=${record.totalTimeMillis}ms ($breakdown)")
        }
    }

    private companion object {
        const val TAG = "TTI"
    }
}
