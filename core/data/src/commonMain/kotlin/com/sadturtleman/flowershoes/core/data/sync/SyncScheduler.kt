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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/**
 * 언제 동기화를 돌릴지(설계 6-2).
 *
 * - 앱이 뜰 때 · 앞으로 나올 때 · 푸시를 받았을 때: [requestNow]
 * - 내가 쓴 직후: Op 로그가 늘면 1초 모아서
 * - 앱이 살아 있는 동안 1분마다(조용히 끊긴 수신 리스너 복구)
 *
 * 한 번에 하나만 돈다. 실패는 다음 차례에 다시 시도한다(Op 로그 · 우편함이 남아 있어 잃지 않는다).
 */
class SyncScheduler(
    private val db: AppDatabase,
    private val client: SyncClient,
    /** 상대의 기기 교체 요청을 동기화 때마다 확인해 자동 승인한다(7-3-A). */
    private val approver: RotationApprover? = null,
    /** 앱이 살아 있는 동안 우편함 초인종(Firestore 리스너). */
    private val doorbell: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    private val period: Duration = 1.minutes,
    /** 서버가 이 커플을 지웠는지(상대가 연결 해제). 확인하지 못하면 false. */
    private val partnerGone: suspend () -> Boolean = { false },
    private val retryBase: Duration = 5.seconds,
    private val syncTimeout: Duration = 30.seconds,
) {
    private val _partnerUnlinked = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 상대가 연결을 해제했다. 화면이 "연결을 해제했어요"(보관 · 지우기 고르기)를 띄우고 [partnerUnlinkHandled] 를 부른다. */
    val partnerUnlinked: kotlinx.coroutines.flow.StateFlow<Boolean> = _partnerUnlinked

    fun partnerUnlinkHandled() {
        _partnerUnlinked.value = false
    }

    private val _approved = kotlinx.coroutines.flow.MutableStateFlow<List<RotationRequest>>(emptyList())

    /** 자동 승인한 상대 기기 교체. 화면이 "새 폰으로 바꿨어요"를 한 번 보여 주고 [rotationSeen] 을 부른다. */
    val approvedRotations: kotlinx.coroutines.flow.StateFlow<List<RotationRequest>> = _approved

    fun rotationSeen(id: String) {
        _approved.value = _approved.value.filterNot { it.id == id }
    }

    /** 자동 승인하지 못한 교체 요청. 화면이 "확인 코드로 직접 확인해 주세요"를 띄운다. */
    var pendingManual: List<Pair<RotationRequest, RotationDecision.NeedsManual>> = emptyList()
        private set

    private val kicks = Channel<Unit>(Channel.CONFLATED)
    private val running = Mutex()
    private val retryNeeded = MutableStateFlow(false)

    var lastReport: SyncReport? = null
        private set

    fun requestNow() {
        kicks.trySend(Unit)
    }

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        val writes = db.syncDao().observeLatestSeq().drop(1).debounce(1.seconds).map { }
        val ticks = flow {
            while (true) {
                delay(period)
                emit(Unit)
            }
        }
        requestNow()
        // Firestore ends a listener after errors such as the pending-device permission window.
        val rings = doorbell.retryWhen { _, attempt ->
            delay(minOf(60.seconds, retryBase * (attempt + 1).coerceAtMost(60).toInt()))
            true
        }.debounce(300.milliseconds)
        val retries = retryNeeded.flatMapLatest { needed ->
            if (!needed) emptyFlow() else flow {
                var attempt = 1L
                while (true) {
                    delay(minOf(60.seconds, retryBase * (attempt++).coerceAtMost(60).toInt()))
                    emit(Unit)
                }
            }
        }
        merge(kicks.receiveAsFlow(), writes, ticks, rings, retries).collect { runOnce() }
    }

    /** 푸시 수신처럼 결과를 기다려야 할 때. */
    suspend fun runOnce(): SyncReport? = running.withLock {
        var finished = false
        val report = withTimeoutOrNull(syncTimeout) { syncOnce().also { finished = true } }
        if (!finished) withTimeoutOrNull(5.seconds) { attempt { client.reconnect() } }
        val awaitingAcknowledgement = db.coupleDao().get()?.partnerUserId?.isNotBlank() == true && db.syncDao().streamsWithLog().isNotEmpty()
        retryNeeded.value = report == null || awaitingAcknowledgement
        report
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }

    private suspend fun syncOnce(): SyncReport? {
        val report = attempt { client.syncOnce() }?.also { lastReport = it }
        approver?.let { a ->
            attempt { a.check() }?.let { decisions ->
                pendingManual = pendingManual + decisions.mapNotNull { (r, d) -> (d as? RotationDecision.NeedsManual)?.let { r to it } }
                _approved.value = _approved.value + decisions.filter { it.second == RotationDecision.Approved }.map { it.first }
            }
        }
        if (!_partnerUnlinked.value && attempt { partnerGone() } == true) _partnerUnlinked.value = true
        return report
    }
}
