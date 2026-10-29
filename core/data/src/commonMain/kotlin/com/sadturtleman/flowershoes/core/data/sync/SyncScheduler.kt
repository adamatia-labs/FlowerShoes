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
 * 동기화가 실패하고 있다.
 *
 * @param code 전송 계층이 아는 오류 코드(예: Firestore UNAVAILABLE). 모르거나 시간 초과면 null.
 * @param since 이번 실패가 처음 일어난 때. 실패가 이어지는 동안 바뀌지 않는다 — 화면이 "닫은 실패" 를 이것으로 가린다.
 */
data class SyncFailure(val code: String?, val since: kotlin.time.Instant)

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
    /** 기기 교체 요청(상대의 새 폰 · 내 계정의 다른 폰)을 동기화 때마다 읽어 [deviceChanges] 로 내보낸다(#20). */
    private val approver: DeviceChangeApprover? = null,
    /** 앱이 살아 있는 동안 우편함 초인종(Firestore 리스너). */
    private val doorbell: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    private val period: Duration = 1.minutes,
    /** 서버가 이 커플을 지웠는지(상대가 연결 해제). 확인하지 못하면 false. */
    private val partnerGone: suspend () -> Boolean = { false },
    private val retryBase: Duration = 5.seconds,
    private val syncTimeout: Duration = 30.seconds,
    private val clock: kotlin.time.Clock = kotlin.time.Clock.System,
) {
    private val _failure = kotlinx.coroutines.flow.MutableStateFlow<SyncFailure?>(null)

    /**
     * 지금 동기화가 실패 중인가(공통 상태 · 서버 오류, #72). 다음 차례가 성공하면 null 로 돌아간다.
     * 다시 시도는 따로 두지 않는다 — 실패는 원래처럼 다음 차례(재시도 · 1분 · 푸시)가 다시 돈다.
     * 오프라인이어도 실패하므로, 화면은 네트워크가 있을 때만 이것을 서버 오류로 보인다.
     */
    val failure: kotlinx.coroutines.flow.StateFlow<SyncFailure?> = _failure

    private val _partnerUnlinked = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 상대가 연결을 해제했다. 화면이 "연결을 해제했어요"(보관 · 지우기 고르기)를 띄우고 [partnerUnlinkHandled] 를 부른다. */
    val partnerUnlinked: kotlinx.coroutines.flow.StateFlow<Boolean> = _partnerUnlinked

    fun partnerUnlinkHandled() {
        _partnerUnlinked.value = false
    }

    private val _deviceChanges = kotlinx.coroutines.flow.MutableStateFlow<List<DeviceChangePrompt>>(emptyList())
    private val dismissed = mutableSetOf<String>()

    /** 띄울 기기 교체 화면: 상대 폰의 승인 요청 · 옛 폰의 "새 폰에서 로그인했어요". 답하거나 닫으면 [deviceChangeHandled]. */
    val deviceChanges: kotlinx.coroutines.flow.StateFlow<List<DeviceChangePrompt>> = _deviceChanges

    /** 이 실행 동안은 다시 띄우지 않는다(답은 서버 상태로 사라지고, 옛 폰 알림은 "괜찮아요"로 닫는다). */
    fun deviceChangeHandled(id: String) {
        dismissed += id
        _deviceChanges.value = _deviceChanges.value.filterNot { it.request.id == id }
    }

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
        if (!finished) {
            // 서버가 시간 안에 답하지 않았다 — 이것도 서버 오류 화면에 보인다(코드 없음).
            failed(null)
            withTimeoutOrNull(5.seconds) { attempt { client.reconnect() } }
        }
        val awaitingAcknowledgement = db.coupleDao().get()?.partnerUserId?.isNotBlank() == true && db.syncDao().streamsWithLog().isNotEmpty()
        retryNeeded.value = report == null || awaitingAcknowledgement
        report
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }

    /** 이번 실패를 적는다. 이미 실패 중이면 시작 시각은 그대로 두고 코드만 바꾼다. */
    private fun failed(code: String?) {
        _failure.value = SyncFailure(code, _failure.value?.since ?: clock.now())
    }

    private suspend fun syncOnce(): SyncReport? {
        val report = try {
            client.syncOnce()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            failed(client.errorCode(e))
            null
        }
        if (report != null) {
            lastReport = report
            _failure.value = null
        }
        approver?.let { a -> attempt { a.prompts() }?.let { p -> _deviceChanges.value = p.filterNot { it.request.id in dismissed } } }
        if (!_partnerUnlinked.value && attempt { partnerGone() } == true) _partnerUnlinked.value = true
        return report
    }
}
