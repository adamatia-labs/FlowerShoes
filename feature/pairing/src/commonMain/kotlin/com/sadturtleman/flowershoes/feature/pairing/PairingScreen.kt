package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.crypto.CrockfordCode
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.PairingSession
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.data.sync.RelinkSession
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkScreenHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTextField
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class PairingUi(
    /** 서버(Firebase)가 연결되지 않은 빌드면 false. 연결 화면 대신 안내를 보여 준다. */
    val available: Boolean,
    val state: PairingState = PairingState.Idle,
    val relinkAvailable: Boolean = false,
    val codeInput: String = "",
    val busy: Boolean = false,
)

/**
 * 첫 연결 화면(설계 7-1). [session] 이 null 이면 이 빌드에는 서버 설정이 없다.
 * 상대 단계는 2초마다 확인한다(Firestore 실시간 구독으로 바꿔도 화면은 그대로다).
 */
class PairingViewModel(
    private val session: PairingSession?,
    private val sync: SyncClient?,
    private val settings: DeviceSettings,
    /** 상대 폰이 넘겨주기(7-3-B). 같은 화면 상태(코드 · SAS)를 쓴다. */
    private val relink: RelinkSession? = null,
) : ViewModel() {

    private var relinkMode = false

    /** 지수: 설정 › "연인 폰 교체 받기". */
    fun offerRelink() = runRelink { it.offer() }

    /** 새 폰: 연인 폰에서 받은 코드로 다시 연결. */
    fun acceptRelink() = runRelink { it.accept(_ui.value.codeInput) }

    private fun runRelink(block: suspend (RelinkSession) -> PairingState) {
        val r = relink ?: return
        relinkMode = true
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val next = runCatching { block(r) }.getOrElse { PairingState.Failed(it.message ?: "연결하지 못했어요") }
            _ui.update { it.copy(state = next, busy = false) }
            startPolling(null)
        }
    }

    private val _ui = MutableStateFlow(PairingUi(available = session != null, relinkAvailable = relink != null))
    val ui: StateFlow<PairingUi> = _ui.asStateFlow()
    private var polling: Job? = null

    fun create() = run { it.start() }

    fun editCode(text: String) = _ui.update { it.copy(codeInput = text.uppercase().take(9)) }

    fun join() = run { it.join(_ui.value.codeInput) }

    fun confirm() {
        val r = relink
        if (relinkMode && r != null) {
            viewModelScope.launch {
                _ui.update { it.copy(busy = true) }
                val next = runCatching { r.confirm() }.getOrElse { PairingState.Failed(it.message ?: "연결하지 못했어요") }
                _ui.update { it.copy(state = next, busy = false) }
                if (next is PairingState.Linked) onLinked() else startPolling(null)
            }
        } else {
            run { it.confirm() }
        }
    }

    fun reset() {
        relinkMode = false
        polling?.cancel()
        _ui.update { PairingUi(available = session != null, relinkAvailable = relink != null) }
    }

    private fun run(block: suspend (PairingSession) -> PairingState) {
        val s = session ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val next = runCatching { block(s) }.getOrElse { PairingState.Failed(it.message ?: "연결하지 못했어요") }
            _ui.update { it.copy(state = next, busy = false) }
            startPolling(s)
        }
    }

    private fun startPolling(s: PairingSession?) {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                val st = _ui.value.state
                if (st is PairingState.Linked || st is PairingState.Failed || st is PairingState.Idle) break
                delay(2_000)
                val next = runCatching { if (relinkMode) relink?.poll() else s?.poll() }.getOrNull() ?: continue
                _ui.update { it.copy(state = next) }
                if (next is PairingState.Linked) onLinked()
            }
        }
    }

    private suspend fun onLinked() {
        settings.setKeyEpoch(1)
        // 혼자 모드에서 쌓인 내 데이터가 첫 동기화로 넘어간다.
        runCatching { sync?.syncOnce() }
    }
}

@Composable
fun PairingRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** 설정 › "연인 폰 교체 받기"로 들어오면 바로 넘겨주기 코드를 만든다. */
    handOver: Boolean = false,
    viewModel: PairingViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(handOver) { if (handOver && ui.state is PairingState.Idle) viewModel.offerRelink() }
    LaunchedEffect(ui.state) { if (ui.state is PairingState.Linked) onDone() }
    PairingScreen(ui, viewModel, onBack)
}

@Composable
private fun PairingScreen(ui: PairingUi, vm: PairingViewModel, onBack: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().padding(KkSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Text("← 돌아가기", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable {
            vm.reset()
            onBack()
        })
        if (!ui.available) {
            KkScreenHeader("연인과 연결하기", "이 빌드에는 아직 서버(Firebase) 설정이 없어요. 설정이 들어가면 여기서 초대 코드로 연결할 수 있어요.")
            return@Column
        }
        when (val s = ui.state) {
            PairingState.Idle -> {
                KkScreenHeader("연인과 연결하기", "한 사람이 코드를 만들고, 다른 사람이 그 코드를 입력해요.")
                Spacer(Modifier.height(KkSpacing.md))
                KkPrimaryButton("초대 코드 만들기", onClick = vm::create, enabled = !ui.busy)
                Spacer(Modifier.height(KkSpacing.lg))
                Text("받은 코드가 있나요?", style = KkTheme.type.labelM, color = c.textSecondary)
                KkTextField(ui.codeInput, vm::editCode, placeholder = "8자리 코드")
                KkSecondaryButton("코드로 연결하기", onClick = vm::join)
                if (ui.relinkAvailable) {
                    Spacer(Modifier.height(KkSpacing.lg))
                    Text("폰을 바꿨나요? 연인 폰의 설정 › '연인 폰 교체 받기'에서 받은 코드를 위에 넣고 눌러 주세요.", style = KkTheme.type.bodyS, color = c.textSecondary)
                    KkSecondaryButton("연인 폰에서 받은 코드로 다시 연결하기", onClick = vm::acceptRelink)
                }
            }
            is PairingState.ShowCode -> {
                KkScreenHeader("이 코드를 연인에게 알려 주세요", "24시간 동안 한 번만 쓸 수 있어요. 카메라가 막힌 부대 폰에서도 직접 입력하면 돼요.")
                BigText(CrockfordCode.format(s.code))
                Text("상대가 입력하기를 기다리는 중…", style = KkTheme.type.bodyS, color = c.textTertiary)
            }
            PairingState.WaitingPartner -> KkScreenHeader("상대 폰을 기다리는 중", "코드를 만든 폰이 응답하면 확인 코드가 떠요.")
            is PairingState.CompareSas -> {
                KkScreenHeader("두 화면의 숫자가 같나요?", "전화 · 면회 · 메시지로 서로 읽어 주세요. 다르면 누군가 가운데서 바꿔치기한 거예요.")
                BigText(s.sas.chunked(3).joinToString(" "))
                KkPrimaryButton("맞아요", onClick = vm::confirm, enabled = !ui.busy)
                KkSecondaryButton("달라요 — 그만할게요", onClick = vm::reset)
            }
            PairingState.WaitingConfirm -> KkScreenHeader("상대 확인을 기다리는 중", "상대도 [맞아요]를 누르면 연결돼요.")
            is PairingState.Linked -> KkScreenHeader("연결됐어요", "이제 보고싶어 · 편지 · 일정을 함께 써요.")
            is PairingState.Failed -> {
                KkScreenHeader("연결하지 못했어요", s.reason)
                KkSecondaryButton("처음부터", onClick = vm::reset)
            }
        }
    }
}

@Composable
private fun BigText(text: String) {
    Text(
        text,
        style = KkTheme.type.display.copy(fontSize = 40.sp, letterSpacing = 4.sp),
        color = KkTheme.colors.textBrand,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { contentDescription = text.toList().joinToString(" ") },
    )
}
