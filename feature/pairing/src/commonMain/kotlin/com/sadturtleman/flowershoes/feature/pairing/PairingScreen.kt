package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.sync.InviteConfig
import com.sadturtleman.flowershoes.core.data.sync.Outbox
import com.sadturtleman.flowershoes.core.data.sync.PairingSession
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class PairingUi(
    /** 서버(Firebase)가 연결되지 않은 빌드면 false. 연결 화면 대신 안내를 보여 준다. */
    val available: Boolean,
    val state: PairingState = PairingState.Idle,
    val busy: Boolean = false,
    /** 연결 뒤 정리(받는 쪽의 혼자 쓴 기록 보내기)가 끝났다. */
    val receiveDone: Boolean = false,
    /** 받는 쪽: 연결 뒤 혼자 쓴 편지 · 그림이 많아 보내는 중(처음 센 것 · 남은 것). */
    val sendingTotal: Outbox? = null,
    val sendingLeft: Outbox? = null,
    val sendStartedMs: Long = 0,
    /** 받는 쪽: 링크를 보낸 사람(연결 중 화면에 계속 보인다). */
    val inviterName: String = "",
    /** 보내는 쪽: 내 표시 이름(초대장 · 카카오 템플릿 인자). 비어 있을 수 있다. */
    val myName: String = "",
)

/**
 * 첫 연결 화면(초대 링크, #15). [session] 이 null 이면 이 빌드에는 서버 설정이 없다.
 * 화면이 열려 있는 동안 상대 수락은 2초마다 확인한다. 보낸 쪽이 홈으로 간 뒤에는 [LinkWatchViewModel] 이 이어 본다.
 * 같은 계정의 새 폰은 여기가 아니라 기기 교체([DeviceChangeViewModel])로 연결된다.
 */
class PairingViewModel(
    private val session: PairingSession?,
    private val sync: SyncClient?,
    private val settings: DeviceSettings,
    /** 링크 호스트 · 카카오 템플릿(빌드 설정). */
    val config: InviteConfig = InviteConfig(),
    private val profile: ProfileRepository? = null,
    private val bizLogger: BizLogger,
) : ViewModel() {
    private val handoff = LinkHandoff(settings, sync)

    private val _ui = MutableStateFlow(PairingUi(available = session != null))
    val ui: StateFlow<PairingUi> = _ui.asStateFlow()
    private var polling: Job? = null

    /** 연인 초대 화면을 열었다: 아직 유효한 내 링크가 있으면 그대로(다시 보내기), 없으면 새로 만든다(이전 링크는 무효). */
    fun open() = run { s ->
        val now = s.poll()
        if (now is PairingState.Waiting || now is PairingState.Linked) now
        else s.createLink().also { st -> if (st is PairingState.Waiting) bizLogger.record(BizView.PAIRING, BizEvent.PairingLinkCreated) }
    }

    /** 초대 링크로 들어왔다(딥링크): 보낸 사람을 보여 주고 수락을 기다린다. 초대는 아직 쓰지 않는다. */
    fun preview(token: String) = run { it.preview(token) }

    /** "수락하고 연결하기": 여기서 초대를 쓴다. */
    fun accept(token: String) = run { it.accept(token).also { st -> if (st is PairingState.Accepting || st is PairingState.Linked) bizLogger.record(BizView.PAIRING, BizEvent.PairingLinkAccepted) } }

    fun reset() {
        polling?.cancel()
        _ui.update { PairingUi(available = session != null) }
    }

    private fun run(block: suspend (PairingSession) -> PairingState) {
        val s = session ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val next = runCatching { block(s) }.getOrElse { PairingState.Failed(it.message ?: "연결하지 못했어요") }
            val myName = runCatching { profile?.let { p -> p.observeUsers().first().firstOrNull { it.id == p.myUserId() }?.nickname } }.getOrNull().orEmpty()
            _ui.update { it.copy(state = next, busy = false, inviterName = next.inviterName() ?: it.inviterName, myName = myName) }
            if (next is PairingState.Linked) linked(s) else startPolling(s)
        }
    }

    /** 보낸 쪽은 홈의 연결 모달([LinkWatcher])이 이어 받고 화면만 닫는다. 받는 쪽은 여기서 혼자 쓴 기록을 보낸다. */
    private suspend fun linked(s: PairingSession) {
        if (!s.isInviter) return onLinked()
        // 모달이 뜨기 전에 앱이 꺼져도 커플 키 epoch 는 남아 있어야 한다(보내기는 다음 동기화가 한다).
        settings.setKeyEpoch(1)
        _ui.update { it.copy(receiveDone = true) }
    }

    private fun startPolling(s: PairingSession) {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                val st = _ui.value.state
                if (st !is PairingState.Waiting && st !is PairingState.Accepting) break
                delay(2_000)
                val next = runCatching { s.poll() }.getOrNull() ?: continue
                _ui.update { it.copy(state = next) }
                if (next is PairingState.Linked) linked(s)
            }
        }
    }

    private suspend fun onLinked() {
        // 보낸 쪽 처음 연결은 LinkWatchViewModel 이 기록한다(모달과 함께 한 번).
        bizLogger.record(BizView.PAIRING, BizEvent.PairingLinked(relink = false))
        handOff()
    }

    /** 처음 연결(받는 쪽): 혼자 모드에서 쌓인 내 기록을 첫 동기화로 보낸다. 많으면 보내는 중 화면. */
    private suspend fun handOff() {
        val total = handoff.begin()
        if (total.showsProgress()) {
            _ui.update { it.copy(sendingTotal = total, sendingLeft = total, sendStartedMs = kotlin.time.Clock.System.now().toEpochMilliseconds()) }
            handoff.send { left -> _ui.update { it.copy(sendingLeft = left) } }
        } else {
            runCatching { sync?.syncOnce() }
        }
        _ui.update { it.copy(receiveDone = true) }
    }
}

private fun PairingState.inviterName(): String? = when (this) {
    is PairingState.Invited -> inviterName
    is PairingState.Accepting -> inviterName
    else -> null
}

@Composable
fun PairingRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** 초대 링크로 들어왔다(받는 쪽). null 이면 연인 초대(보내는 쪽). */
    invite: String? = null,
    viewModel: PairingViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // ViewModel 은 셸과 같은 범위라 닫을 때마다 비운다 — 다음에 열 때 지난 결과(연결됨 등)로 바로 닫히지 않게.
    val done = {
        viewModel.reset()
        onDone()
    }
    val close = {
        viewModel.reset()
        onBack()
    }
    LaunchedEffect(invite) {
        if (invite != null) viewModel.preview(invite) else viewModel.open()
    }
    // 받는 쪽은 혼자 쓴 기록 보내기가 끝날 때까지 기다린 뒤 닫는다.
    LaunchedEffect(ui.state, ui.receiveDone) {
        if (ui.state is PairingState.Linked && ui.receiveDone) done()
    }
    // 보내기를 누르면 바로 홈(#15) — 수락은 홈의 연결 모달이 이어 받는다.
    ConnectScreen(ui, viewModel, close, onSent = done)
}
