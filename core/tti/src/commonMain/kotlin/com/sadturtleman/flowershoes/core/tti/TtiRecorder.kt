package com.sadturtleman.flowershoes.core.tti

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 화면별 TTI 기록기.
 *
 * 앱 전체에 하나만 있고, 시작은 Application 에서 [init] 으로 연다(화면별 TTI 는 Android 만 잰다).
 *
 * 모든 함수는 값을 돌려주지 않고 즉시 반환한다 — 실제 기록은 IO 디스패처에서 이어 돈다.
 * 측정하려는 코드(컴포지션 · ViewModel)를 기다리게 만들면 그 대기 시간이 다시 측정값을 오염시킨다.
 * 대신 시각은 호출된 그 스레드에서 바로 찍고 넘긴다.
 *
 * [startRecord] · [endRecord] 는 여러 번 불려도 안전하다. 같은 구간의 두 번째 start 는 무시하고,
 * 이미 닫힌 구간의 end 도 무시한다 — 컴포지션은 언제든 다시 실행될 수 있기 때문이다.
 */
interface TtiRecorder {

    /**
     * 기록을 시작한다. Application 이 뜰 때와 다시 앞으로 나올 때 부른다.
     *
     * 지난 실행에서 미처 쏘지 못한 완성 기록을 먼저 흘려보내고(프로세스가 강제 종료된 경우),
     * 오래 남은 미완성 기록을 정리한다.
     */
    fun init()

    /** [timeline] 구간을 연다. 이미 열려 있으면 그대로 둔다. */
    fun startRecord(timeline: TtiTimeline, tti: Tti, pageName: String)

    /** [timeline] 구간을 닫는다. 열린 적이 없거나 이미 닫혔으면 아무 일도 하지 않는다. */
    fun endRecord(timeline: TtiTimeline, tti: Tti, pageName: String)

    /**
     * 한 건을 쏜다. 화면이 다 그려진 뒤에 부르는 것이 기본이다.
     *
     * 아직 완성되지 않았으면(예: 이미지가 아직 내려오는 중) 쏘지 않고 그대로 둔다.
     * 그 기록은 [destroy] 나 다음 [init] 때 완성돼 있으면 그때 나간다.
     */
    fun shot(tti: Tti, pageName: String)

    /**
     * 기록을 닫는다. 앱이 백그라운드로 내려가는, 종료 직전의 마지막 신호에서 부른다.
     *
     * 저장소에 남은 완성 기록을 모두 쏘고 나서 스코프를 정리한다.
     * (Android · iOS 모두 프로세스 종료를 알려주지 않으므로, 못 쏘고 죽은 것은 다음 [init] 이 주워 간다)
     */
    fun destroy()
}

/**
 * 기본 구현을 만든다.
 *
 * 구현 클래스를 내보내지 않는 것은 바깥이 [TtiRecorder] 계약만 보게 하려는 것이다 —
 * IO 디스패처는 [ttiModule] 이 Koin 한정자(IoDispatcher)로 꺼내 이 함수에 넘긴다.
 */
fun createTtiRecorder(
    store: TtiRecordStore,
    shooter: TtiShooter,
    clock: TtiClock,
    ioDispatcher: CoroutineDispatcher,
): TtiRecorder = TtiRecorderImpl(store, shooter, clock, ioDispatcher)
