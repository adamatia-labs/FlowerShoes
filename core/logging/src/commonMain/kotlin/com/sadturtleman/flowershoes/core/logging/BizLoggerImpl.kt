package com.sadturtleman.flowershoes.core.logging

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.uuid.Uuid

/**
 * [BizLogger] 구현. 바깥에서는 [createBizLogger] 로만 만든다.
 *
 * 기록하는 일과 쏘는 일을 나눠 둔다 — [record] 는 사용자 UUID 와 시각만 그 자리에서 찍어 넘기고
 * 곧바로 돌아간다(그래야 기록하는 화면이 전송을 기다리지 않는다). 전송만 IO 스코프에서 돈다.
 *
 * 자식 하나가 실패해도 기록기 전체가 멈추면 안 되므로 스코프는 [SupervisorJob] 을 쓰고,
 * 각 전송은 [runCatching] 으로 감싼다 — 로그 실패가 앱을 죽이는 일은 없어야 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BizLoggerImpl(
    private val shooter: BizLogShooter,
    private val clock: BizLogClock,
    private val ioDispatcher: CoroutineDispatcher,
) : BizLogger {

    /** [init] 과 [destroy] 사이에만 살아 있다. 두 함수는 다른 스레드에서 올 수 있어 원자적으로 갈아 끼운다. */
    private val scope = AtomicReference<CoroutineScope?>(null)

    /** 지금 기록에 붙는 사용자 식별자. [init] 이 한 번 만들고 [makeUUID] 가 갈아 끼운다. */
    private val userUuid = AtomicReference<String?>(null)

    /**
     * 전송 하나를 통째로 감싸는 잠금.
     *
     * 디스패처를 병렬성 1 로 묶는 것만으로는 부족하다 — [BizLogShooter] 가 네트워크로 나가면
     * 먼저 얹힌 건이 첫 중단 지점에서 멈춘 사이 다음 건이 앞질러 나간다.
     * 시작 순서(디스패처)와 나가는 순서(이 잠금)를 함께 묶어야 일어난 순서대로 도착한다.
     *
     * [destroy] 가 기다릴 자리이기도 하다.
     */
    private val shotMutex = Mutex()

    override fun init() {
        // 한 실행 동안은 같은 사용자다. 앱이 앞뒤로 오갈 때마다 init 이 다시 오므로 처음 것만 만든다.
        userUuid.compareAndSet(null, Uuid.random().toString())
        if (scope.load() != null) return
        val opened = CoroutineScope(ioDispatcher.limitedParallelism(1) + SupervisorJob())
        // 다른 스레드가 먼저 열었으면 그것을 쓰고 방금 만든 것은 버린다.
        if (!scope.compareAndSet(null, opened)) opened.cancel()
    }

    override fun makeUUID(): String {
        val uuid = Uuid.random().toString()
        userUuid.store(uuid)
        return uuid
    }

    override fun record(viewName: String, event: BizEvent) {
        // init 전이면 붙일 사용자가 없다. 로그가 없다고 화면이 멈출 이유는 없으므로 조용히 버린다.
        val userUuid = userUuid.load() ?: return warn("${event.name} 기록 버림", "init 전이다")
        shot(
            BizLog(
                viewName = viewName,
                event = event,
                userUuid = userUuid,
                recordedAt = clock.nowMillis(),
            )
        )
    }

    override fun destroy() {
        val closing = scope.exchange(null) ?: return
        // 아직 나가지 못한 전송이 끝난 다음에 스코프를 접는다. 먼저 접으면 그 건들이 취소된다.
        // 잠금은 먼저 기다린 쪽부터 풀리므로, 맨 뒤에 서는 이 빈 작업이 곧 "앞의 전송이 다 끝났다" 는 신호다.
        closing.launch { shotMutex.withLock { } }
            .invokeOnCompletion { closing.cancel() }
    }

    /**
     * 한 건을 IO 스코프에 얹는다. 얹은 순서대로 나간다.
     *
     * 밖으로 열지 않는 것은 기록과 전송이 언제나 한 짝이기 때문이다 —
     * 쌓아 두는 곳이 없으니 따로 쏠 것도 없다. 바깥이 보는 손잡이는 [record] 하나다.
     */
    private fun shot(log: BizLog) {
        // destroy 뒤면 보낼 곳이 없다. 스코프를 다시 여는 것은 init 의 몫이다.
        val scope = scope.load() ?: return warn("${log.event.name} 기록 버림", "destroy 뒤다")
        scope.launch {
            runCatching { shotMutex.withLock { shooter.shoot(log) } }
                .onFailure { warn("${log.event.name} 전송 실패", it.describe()) }
        }
    }

    /**
     * 로그가 실패했다는 사실만 남긴다.
     *
     * 플랫폼 로거를 쓰지 않는 것은 이 모듈이 어느 플랫폼에서도 그대로 돌아야 하기 때문이다.
     * 안드로이드에서는 표준 출력도 Logcat 으로, iOS 에서는 Xcode 콘솔로 들어온다.
     */
    private fun warn(what: String, cause: String) {
        println("$TAG: $what ($cause)")
    }

    private fun Throwable.describe(): String = "${this::class.simpleName}: $message"

    private companion object {
        const val TAG = "BIZLOG"
    }
}
