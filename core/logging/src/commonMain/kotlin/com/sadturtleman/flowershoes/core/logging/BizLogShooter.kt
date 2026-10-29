package com.sadturtleman.flowershoes.core.logging

/**
 * 이벤트를 내보내는 곳.
 *
 * 이 모듈은 "무엇을 언제 남기는가" 까지만 안다. 어디로 보내는지는 구현이 정하고,
 * 릴리스는 GA4 로 보내는 [AnalyticsBizLogShooter], 디버그와 Firebase 가 없는 빌드는
 * 표준 출력으로 흘리는 [PrintBizLogShooter] 다. 고르는 것은 [bizLogModule] 이다.
 *
 * IO 디스패처에서, 한 번에 하나씩, 기록된 순서대로 불린다.
 */
fun interface BizLogShooter {
    /**
     * 실패하면 그대로 예외를 던진다. 기록기가 받아 삼키고 다음 건으로 넘어간다 —
     * 쌓아 두는 곳이 없으므로 실패한 건은 그대로 사라진다.
     */
    suspend fun shoot(log: BizLog)
}
