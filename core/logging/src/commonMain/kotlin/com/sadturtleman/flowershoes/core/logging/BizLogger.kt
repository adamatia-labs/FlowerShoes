package com.sadturtleman.flowershoes.core.logging

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 비즈니스 이벤트 기록기.
 *
 * 앱 전체에 하나만 있고, 시작은 Application 에서 [init] 으로 연다.
 *
 * 쌓아 두는 곳이 없다 — [record] 한 건이 그대로 한 번의 전송이 된다.
 * 기록하는 쪽은 [record] 하나만 보면 되고, 이벤트별 손잡이는 확장 함수로 각 feature 가 만든다:
 *
 * ```kotlin
 * fun BizLogger.recordLayoutToggle(viewName: String, layout: String) =
 *     record(viewName, BizEvent.LayoutToggle(layout = layout))
 * ```
 *
 * 이렇게 두면 파라미터 키가 그 이벤트를 쓰는 모듈 안에 모이고,
 * 부르는 쪽은 맵을 직접 짜는 대신 인자가 붙은 함수를 본다.
 *
 * [record] 는 값을 돌려주지 않고 즉시 반환한다. 전송은 IO 디스패처에서 이어 돌고,
 * 실패해도 예외가 밖으로 나오지 않는다 — 로그가 화면을 멈추게 하는 일은 없어야 한다.
 */
interface BizLogger {

    /**
     * 기록을 시작한다. Application 이 뜰 때와 다시 앞으로 나올 때 부른다.
     *
     * 사용자 UUID 가 아직 없으면 여기서 한 번 만든다 — 한 실행 동안은 같은 값을 단다.
     */
    fun init()

    /**
     * 사용자 UUID 를 새로 만들어 지금부터의 기록에 붙인다. 만든 값을 돌려준다.
     *
     * 로그인 · 로그아웃처럼 "누가 쓰는가" 가 바뀌는 지점에서 부른다.
     * 이미 기록된 건은 건드리지 않는다 — 아직 전송 중이더라도 원래 주인을 그대로 달고 나간다.
     */
    fun makeUUID(): String

    /**
     * 이벤트 한 건을 남긴다. 사용자 UUID 와 시각을 붙여 그 자리에서 전송에 얹는다.
     *
     * [init] 전이나 [destroy] 뒤에 부르면 보낼 곳이 없어 버린다.
     * 쌓아 두는 곳이 없으므로 전송이 실패한 건도 다시 나가지 않는다 —
     * 한 건도 잃으면 안 되는 이벤트가 생기면 [BizLogShooter] 구현이 재시도를 맡는다.
     *
     * @param viewName 이 이벤트가 일어난 화면. 라우팅 테이블이 쓰는 경로 상수(HomePage.PATH 등)를
     *   그대로 넘긴다 — TTI 의 pageName 과 같은 값이어야 두 지표를 같은 화면 기준으로 겹쳐 볼 수 있다.
     * @param event 무엇이 일어났는가. 그 이벤트에 딸리는 값도 함께 들고 온다([BizEvent.parameters]).
     */
    fun record(viewName: String, event: BizEvent)

    /**
     * 기록을 닫는다. 앱이 백그라운드로 내려가는, 종료 직전의 마지막 신호에서 부른다.
     *
     * 아직 나가지 못한 전송이 끝나기를 기다린 다음 스코프를 정리한다.
     * (안드로이드는 프로세스 종료를 알려주지 않으므로, 그마저 못 하고 죽으면 그 건은 사라진다)
     */
    fun destroy()
}

/**
 * 기본 구현을 만든다.
 *
 * 구현 클래스를 내보내지 않는 것은 바깥이 [BizLogger] 계약만 보게 하려는 것이다 —
 * 디스패처는 한정자(IoDispatcher)로 골라 받아야 하므로, [bizLogModule] 이 이 함수를 감싼다.
 */
fun createBizLogger(
    shooter: BizLogShooter,
    clock: BizLogClock,
    ioDispatcher: CoroutineDispatcher,
): BizLogger = BizLoggerImpl(shooter, clock, ioDispatcher)
