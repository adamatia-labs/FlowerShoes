package com.sadturtleman.flowershoes.core.tti

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference

/**
 * [TtiRecorder] 구현. 바깥에서는 [createTtiRecorder] 로만 만든다.
 *
 * 저장은 전부 IO 디스패처 위의 스코프 하나에서 돈다. 측정 지점은 시각만 찍고 즉시 돌아간다.
 * 자식 하나가 실패해도 기록기 전체가 멈추면 안 되므로 스코프는 [SupervisorJob] 을 쓰고,
 * 각 작업은 [runCatching] 으로 감싼다 — 계측 실패가 앱을 죽이는 일은 없어야 한다.
 *
 * 작업은 [opMutex] 로 한 줄로 세운다. 순서가 어긋나면 측정이 조용히 사라지기 때문이다 —
 * 구간의 end 가 start 보다 먼저 처리되면 아직 없는 행을 갱신하는 셈이라 그 구간은 영영 닫히지 않고,
 * 쏘기와 삭제 사이에 다른 쏘기가 끼어들면 같은 기록이 두 번 나간다. (둘 다 실제로 겪었다)
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TtiRecorderImpl(
    private val store: TtiRecordStore,
    private val shooter: TtiShooter,
    private val clock: TtiClock,
    private val ioDispatcher: CoroutineDispatcher,
) : TtiRecorder {

    /** [init] 과 [destroy] 사이에만 살아 있다. 두 함수는 다른 스레드에서 올 수 있어 원자적으로 갈아 끼운다. */
    private val scope = AtomicReference<CoroutineScope?>(null)

    /**
     * 기록 작업 하나를 통째로 감싸는 잠금.
     *
     * 디스패처를 병렬성 1 로 묶는 것만으로는 부족하다 — 저장소가 자기 실행기로 넘어가는 구현이면
     * (Room 의 suspend DAO 가 그렇다) 먼저 시작한 작업이 첫 호출에서 멈춘 사이 다음 작업이 앞질러 간다.
     * 시작 순서(디스패처)와 처리 순서(이 잠금)를 함께 묶어야 순서가 선다.
     */
    private val opMutex = Mutex()

    override fun init() {
        if (scope.load() != null) return
        val opened = CoroutineScope(ioDispatcher.limitedParallelism(1) + SupervisorJob())
        // 다른 스레드가 먼저 열었으면 그것을 쓰고 방금 만든 것은 버린다(회수도 그쪽이 이미 얹었다).
        if (!scope.compareAndSet(null, opened)) {
            opened.cancel()
            return
        }
        // 지난 실행이 쏘지 못하고 죽은 것을 먼저 흘려보낸다. Android · iOS 모두 프로세스 종료를 알려주지 않으므로
        // "완성됐지만 아직 안 쏜 기록" 을 실제로 회수하는 곳은 대부분 여기다.
        record("init") {
            shootCompleted()
            store.deleteCreatedBefore(clock.wallTimeMillis() - STALE_THRESHOLD_MILLIS)
        }
    }

    override fun startRecord(timeline: TtiTimeline, tti: Tti, pageName: String) {
        // 시각은 호출한 스레드에서 지금 찍는다. 코루틴에 들어가서 찍으면 디스패치 지연이 측정값에 섞인다.
        val startedAt = clock.elapsedMillis()
        val createdAt = clock.wallTimeMillis()
        record("start $timeline of $pageName") {
            store.openSpan(
                tti = tti,
                pageName = pageName,
                createdAt = createdAt,
                timeline = timeline,
                startedAt = startedAt,
            )
        }
    }

    override fun endRecord(timeline: TtiTimeline, tti: Tti, pageName: String) {
        val endedAt = clock.elapsedMillis()
        record("end $timeline of $pageName") {
            store.closeSpan(tti = tti, timeline = timeline, endedAt = endedAt)
        }
    }

    override fun shot(tti: Tti, pageName: String) {
        record("shot $pageName") {
            // 앞선 쏘기가 이미 보내고 지웠으면 여기서는 없는 기록이 된다.
            // (화면이 다 그려져 쏘는 것과 마지막 구간이 닫혀 쏘는 것이 겹칠 수 있다)
            val record = store.find(tti) ?: return@record
            // 아직 안 끝난 구간이 있으면 그대로 둔다. destroy · 다음 init 이 완성돼 있으면 가져간다.
            if (!record.isComplete) return@record
            shootAndDelete(listOf(record))
        }
    }

    override fun destroy() {
        val closing = scope.exchange(null) ?: return
        // 마지막 쏘기가 끝난 다음에 스코프를 접는다. 먼저 접으면 그 작업까지 취소된다.
        closing.launch {
            runCatching { opMutex.withLock { shootCompleted() } }
                .onFailure { warn("destroy 중 shot 실패", it) }
        }.invokeOnCompletion { closing.cancel() }
    }

    /** 저장소에 남은 완성 기록을 한 번에 쏜다. [opMutex] 를 쥔 채로만 부른다. */
    private suspend fun shootCompleted() {
        val completed = store.findAll().filter { it.isComplete }
        if (completed.isEmpty()) return
        shootAndDelete(completed)
    }

    /**
     * 쏘고 나서 지운다.
     * 쏘기가 실패하면 예외가 그대로 올라가 삭제까지 가지 않는다 — 다음 기회에 다시 나간다.
     */
    private suspend fun shootAndDelete(records: List<TtiRecord>) {
        shooter.shoot(records)
        store.delete(records.map { it.tti })
    }

    /**
     * 기록 작업 하나를 IO 스코프에 얹는다. 얹은 순서대로 처리된다.
     * [init] 전이거나 [destroy] 뒤면 조용히 버린다 — 계측이 없다고 화면이 멈출 이유는 없다.
     */
    private fun record(what: String, block: suspend () -> Unit) {
        val scope = scope.load() ?: return
        scope.launch {
            runCatching { opMutex.withLock { block() } }
                .onFailure { warn("$what 기록 실패", it) }
        }
    }

    /**
     * 계측이 실패했다는 사실만 남긴다.
     *
     * 플랫폼 로거를 쓰지 않는 것은 이 모듈이 어느 플랫폼에서도 그대로 돌아야 하기 때문이다.
     * 안드로이드에서는 표준 출력도 Logcat 으로, iOS 에서는 Xcode 콘솔로 들어온다.
     */
    private fun warn(what: String, cause: Throwable) {
        println("$TAG: $what (${cause::class.simpleName}: ${cause.message})")
    }

    private companion object {
        const val TAG = "TTI"

        /** 이 시간이 지나도 완성되지 않은 기록은 버린다(뒤로 나가 버린 화면 등). */
        val STALE_THRESHOLD_MILLIS = 24 * 60 * 60 * 1000L
    }
}
