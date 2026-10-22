package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.database.AppDatabase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 언제 동기화를 돌릴지(설계 6-2).
 *
 * - 앱이 뜰 때 · 앞으로 나올 때 · 푸시를 받았을 때: [requestNow]
 * - 내가 쓴 직후: Op 로그가 늘면 1초 모아서
 * - 앱이 살아 있는 동안 15분마다(빠진 푸시 복구)
 *
 * 한 번에 하나만 돈다. 실패는 다음 차례에 다시 시도한다(Op 로그 · 우편함이 남아 있어 잃지 않는다).
 */
class SyncScheduler(
    private val db: AppDatabase,
    private val client: SyncClient,
    /** 상대의 기기 교체 요청을 동기화 때마다 확인해 자동 승인한다(7-3-A). */
    private val approver: RotationApprover? = null,
    /** 백업 복원 뒤 자동 기기 교체(7-3-A). 동기화 앞에서 한 걸음씩. */
    private val afterRestore: PostRestoreRotation? = null,
    private val period: Duration = 15.minutes,
) {
    private val _restoreState = kotlinx.coroutines.flow.MutableStateFlow(RestoreRotationState.NotNeeded)

    /** 화면 안내용: 상대 확인을 기다리는 중이면 "연인 폰에서 확인 중이에요". */
    val restoreState: kotlinx.coroutines.flow.StateFlow<RestoreRotationState> = _restoreState

    /** 자동 승인하지 못한 교체 요청. 화면이 "확인 코드로 직접 확인해 주세요"를 띄운다. */
    var pendingManual: List<Pair<RotationRequest, RotationDecision.NeedsManual>> = emptyList()
        private set

    private val kicks = Channel<Unit>(Channel.CONFLATED)
    private val running = Mutex()

    var lastReport: SyncReport? = null
        private set

    fun requestNow() {
        kicks.trySend(Unit)
    }

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        val writes = db.syncDao().observeLatestSeq().drop(1).debounce(1.seconds).map { }
        val ticks = flow {
            while (true) {
                delay(period)
                emit(Unit)
            }
        }
        requestNow()
        merge(kicks.receiveAsFlow(), writes, ticks).collect { runOnce() }
    }

    /** 푸시 수신처럼 결과를 기다려야 할 때. */
    suspend fun runOnce(): SyncReport? = running.withLock {
        afterRestore?.let { r -> runCatching { r.step() }.getOrNull()?.let { _restoreState.value = it } }
        val report = runCatching { client.syncOnce() }.getOrNull()?.also { lastReport = it }
        approver?.let { a ->
            runCatching { a.check() }.getOrNull()?.let { decisions ->
                pendingManual = pendingManual + decisions.mapNotNull { (r, d) -> (d as? RotationDecision.NeedsManual)?.let { r to it } }
            }
        }
        report
    }
}
