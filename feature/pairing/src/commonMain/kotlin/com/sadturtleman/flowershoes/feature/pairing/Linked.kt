package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.sync.Outbox
import com.sadturtleman.flowershoes.core.data.sync.PairingSession
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 연결된 직후(보낸 쪽 · 받는 쪽 공통): 키 epoch 1 을 적고, 혼자 쓴 기록(편지 · 그림)을 첫 동기화로 보낸다.
 * 화면 이동을 막지 않고 기존 동기화 경로로 전달한다.
 */
class LinkHandoff(private val settings: DeviceSettings, private val sync: SyncClient?) {
    suspend fun begin(keyEpoch: Int = 1) {
        settings.setKeyEpoch(keyEpoch)
        // 기록은 별도 안내 없이 평소 동기화 경로로 전달한다.
        settings.setSoloArrivalWatch(false)
    }

    /** 다 보낼 때까지 동기화하며 남은 것을 [onLeft] 로 알린다. 2분이 지나면 멈춘다 — 나머지는 평소 동기화로. */
    suspend fun send(onLeft: (Outbox) -> Unit) = coroutineScope {
        val client = sync ?: return@coroutineScope onLeft(Outbox(0, 0, 0))
        val start = Clock.System.now()
        while (Clock.System.now() - start < SEND_LIMIT) {
            val round = launch { runCatching { client.syncOnce() } }
            while (round.isActive) {
                delay(400)
                runCatching { client.outbox() }.getOrNull()?.let(onLeft)
            }
            val left = runCatching { client.outbox() }.getOrNull() ?: break
            onLeft(left)
            if (left.isEmpty) break
            delay(1_500)
        }
    }

    private companion object {
        val SEND_LIMIT = 2.minutes
    }
}

/** 초대를 보낸 사람에게 보여 주는 연결 완료 안내. */
data class AfterLinkUi(val partnerName: String)

/**
 * 보낸 쪽: 초대를 보내고 홈으로 간 뒤, 앱이 앞에 있는 동안 상대 수락을 확인한다(푸시를 누르고 들어와도 여기서 이어진다).
 * 수락되면 연결 안내를 보여 주고 기록은 백그라운드에서 동기화한다.
 */
class LinkWatchViewModel(
    private val session: PairingSession?,
    private val settings: DeviceSettings,
    private val sync: SyncClient?,
    private val profile: ProfileRepository,
    private val bizLogger: BizLogger,
) : ViewModel() {
    private val handoff = LinkHandoff(settings, sync)
    private val _ui = MutableStateFlow<AfterLinkUi?>(null)
    val ui: StateFlow<AfterLinkUi?> = _ui.asStateFlow()
    private var handoffJob: Job? = null
    private var pendingDialog: String? = null

    /** 앱이 앞에 있는 동안 부른다. 기다리는 링크가 없거나 결과가 나면 끝난다. */
    suspend fun watch() {
        val s = session ?: return
        while (true) {
            val next = try { s.poll() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            when {
                next is PairingState.Linked -> {
                    if (handled != next.coupleId) linked(next.coupleId, showDialog = s.isInviter)
                    return
                }
                next is PairingState.Waiting || next is PairingState.Accepting -> delay(POLL_MS)
                next == null && (s.state is PairingState.Waiting || s.state is PairingState.Accepting) -> delay(POLL_MS)
                else -> return
            }
        }
    }

    private fun linked(coupleId: String, showDialog: Boolean) {
        if (handoffJob?.isActive == true || pendingDialog == coupleId) return
        handoffJob = viewModelScope.launch {
            val current = profile.observeCouple().first()
            if (current?.id != coupleId) throw com.sadturtleman.flowershoes.core.data.ScreenSessionChangedException()
            handoff.begin(current.keyEpoch)
            val name = if (showDialog) partnerName() else null
            currentCoroutineContext().ensureActive()
            if (logged != coupleId) {
                bizLogger.record(BizView.PAIRING, BizEvent.PairingLinked(relink = false))
                logged = coupleId
            }
            if (name != null) {
                pendingDialog = coupleId
                _ui.value = AfterLinkUi(name)
            } else handled = coupleId
            handoff.send {}
        }
    }

    /** 연결 안내를 닫는다. 기록 동기화는 계속된다. */
    fun start() {
        pendingDialog?.let { handled = it }
        pendingDialog = null
        _ui.value = null
    }

    private suspend fun partnerName(): String {
        val partner = runCatching { profile.observePartnerId().first() }.getOrNull() ?: return "연인"
        return runCatching { profile.observeUsers().first().firstOrNull { it.id == partner }?.nickname }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "연인"
    }

    private companion object {
        const val POLL_MS = 10_000L

        /**
         * 후처리와 모달 확인을 마친 커플. 첫 연결로 화면 범위가 교체되더라도 취소된 후처리는 재개하고,
         * 아직 확인하지 않은 안내는 새 ViewModel에서 다시 보인다. 분석 이벤트는 별도로 한 번만 기록한다.
         */
        var handled: String? = null
        var logged: String? = null
    }
}

/**
 * 셸(홈 위)에 놓는다: 앱이 앞에 있는 동안 상대 수락을 확인하고 연결 안내를 보여 준다.
 */
@Composable
fun LinkWatcher(viewModel: LinkWatchViewModel = org.koin.compose.viewmodel.koinViewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.watch() } }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val shown = ui ?: return
    LinkedDialog(shown.partnerName, viewModel::start)
}

/** 연결 완료 안내. */
@Composable
internal fun LinkedDialog(partnerName: String, onStart: () -> Unit) {
    val c = KkTheme.colors
    KkDialogHost(onDismiss = onStart) {
        val shape = RoundedCornerShape(KkRadius.xl)
        Column(
            Modifier.width(312.dp).kkElevation(KkElevation.High, shape).clip(shape).background(c.surface).padding(KkSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            Text("${partnerName.withRang()} 연결됐어요", style = KkTheme.type.title, color = c.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                KkButton("시작하기", onStart, size = KkButtonSize.M)
            }
        }
    }
}

