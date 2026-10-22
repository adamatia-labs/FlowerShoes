package com.sadturtleman.flowershoes.logging.domain

/**
 * 이벤트 한 건. [BizLogShooter] 로 나가는 것이 이 모양이다.
 *
 * 쌓아 두지 않고 [BizLogger.record] 때마다 하나씩 나간다.
 * 무엇이 일어났고 어떤 값이 딸렸는지는 [event] 가 통째로 들고 있다.
 *
 * 사용자 식별자를 기록기가 아니라 건마다 붙이는 이유는, 전송이 IO 로 넘어가 나중에 도는 사이
 * [BizLogger.makeUUID] 로 사용자가 바뀔 수 있기 때문이다. 먼저 기록된 것은 원래 주인을 달고 나간다.
 *
 * @param viewName 이 이벤트가 일어난 화면. 같은 행동이라도 어느 화면에서 일어났는지로 갈린다
 *   (같은 보기 전환이 여러 화면에서 일어날 수 있다). TTI 의 pageName 과 같은 값을 쓴다.
 * @param recordedAt 기록된 벽시계 시각(ms). 쏘는 시각과 일어난 시각이 다르므로 따로 남긴다.
 */
data class BizLog(
    val viewName: String,
    val event: BizEvent,
    val userUuid: String,
    val recordedAt: Long,
)
