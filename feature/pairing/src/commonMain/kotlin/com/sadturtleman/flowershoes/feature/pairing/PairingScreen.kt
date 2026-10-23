package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.foundation.background
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.designsystem.LocalKkShare
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
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
    /** 새 폰: 넘겨받는 중 진행(넘겨줄 기록 수 · 받은 수). 다 받으면 [receiveDone]. */
    val expected: com.sadturtleman.flowershoes.core.data.sync.RelinkCounts? = null,
    val received: com.sadturtleman.flowershoes.core.data.sync.RelinkCounts? = null,
    val receiveStartedMs: Long = 0,
    val receiveDone: Boolean = false,
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
        val r = relink
        val expected = r?.expected
        // 받는 폰만 expected(넘겨줄 기록 수)를 안다 — 넘겨주는 폰 · 처음 연결은 바로 끝.
        if (relinkMode && r != null && expected != null) receive(r, expected) else _ui.update { it.copy(receiveDone = true) }
    }

    /**
     * 새 폰: 넘겨주는 폰의 전체 스냅샷이 들어올 때까지 동기화하며 받은 수를 센다(Figma 넘겨받는 중).
     * 다 받았거나 2분이 지나면 끝 — 나머지는 평소 동기화로 이어 받는다.
     */
    private suspend fun receive(r: RelinkSession, expected: com.sadturtleman.flowershoes.core.data.sync.RelinkCounts) {
        val start = kotlin.time.Clock.System.now().toEpochMilliseconds()
        _ui.update { it.copy(expected = expected, received = r.localCounts(), receiveStartedMs = start) }
        while (kotlin.time.Clock.System.now().toEpochMilliseconds() - start < 120_000) {
            runCatching { sync?.syncOnce() }
            val got = runCatching { r.localCounts() }.getOrNull() ?: break
            _ui.update { it.copy(received = got) }
            if (got.letters >= expected.letters && got.drawings >= expected.drawings && got.events >= expected.events) break
            delay(1_500)
        }
        finishReceiving(r)
    }

    /** 받는 폰은 온보딩 없이 들어온다: 넘겨받은 내 역할로 정하고 온보딩을 끝낸 것으로 한다(셸이 홈으로 바뀐다). */
    private suspend fun finishReceiving(r: RelinkSession) {
        runCatching { r.myRole()?.let { settings.setRole(com.sadturtleman.flowershoes.core.model.Role.valueOf(it)) } }
        // 다 받은 화면(100%)을 잠깐 보여 준 뒤 홈으로 — 기록이 적으면 첫 동기화로 바로 끝나 진행 화면이 스쳐 지나간다.
        delay(1_500)
        settings.setOnboarded()
        _ui.update { it.copy(receiveDone = true) }
    }
}

/** 연결 화면 모드. CONNECT 처음 연결 · HAND_OVER 연인 폰 교체 받기(넘겨주는 쪽) · RECEIVE 다시 연결하기(새 폰). */
enum class PairingMode { CONNECT, HAND_OVER, RECEIVE }

@Composable
fun PairingRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** 설정 › "연인 폰 교체 받기"로 들어오면 넘겨주는 쪽. */
    handOver: Boolean = false,
    mode: PairingMode = if (handOver) PairingMode.HAND_OVER else PairingMode.CONNECT,
    viewModel: PairingViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // 새 폰은 넘겨받기가 끝날 때까지 진행 화면을 보여 준 뒤 닫는다.
    LaunchedEffect(ui.state, ui.receiveDone, ui.expected) {
        if (ui.state is PairingState.Linked && (mode != PairingMode.RECEIVE || ui.receiveDone)) onDone()
    }
    // 새 폰은 확인 코드가 뜨면 바로 확인한다 — 비교 · 넘겨주기는 넘겨주는 폰(데이터를 가진 쪽)이 한다.
    LaunchedEffect(ui.state, mode) {
        if (mode == PairingMode.RECEIVE && ui.state is PairingState.CompareSas && !ui.busy) viewModel.confirm()
    }
    val close = {
        viewModel.reset()
        onBack()
    }
    when (mode) {
        PairingMode.CONNECT -> ConnectScreen(ui, viewModel, close, onDone)
        PairingMode.HAND_OVER -> HandOverScreen(ui, viewModel, close)
        PairingMode.RECEIVE -> ReceiveScreen(ui, viewModel, close)
    }
}

/** 화면 틀: 상단 바 + 본문(좌우 20 · 사이 16) + 하단 버튼. */
@Composable
private fun Frame(
    title: String,
    leading: KkTopBarLeading,
    onClose: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(title, onLeading = onClose, leading = leading)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            content = footer,
        )
    }
}

@Composable
private fun Title(title: String, body: String) {
    Text(title, style = KkTheme.type.titleL, color = KkTheme.colors.textPrimary)
    Text(body, style = KkTheme.type.bodyL, color = KkTheme.colors.textSecondary)
}

/** "● 기다리는 중…" 한 줄. */
@Composable
private fun Waiting(text: String) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.bgBrand))
        Text(text, style = KkTheme.type.bodyS, color = c.textSecondary, modifier = Modifier.weight(1f))
    }
}

/** 코드 카드(brand-subtle, 반경 24): 라벨 · 큰 코드 · 설명. 누르면 복사. */
@Composable
private fun CodeCard(label: String, code: String, caption: String, copyable: Boolean = true) {
    val c = KkTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.xl)).background(c.bgBrandSubtle)
            .clickable(enabled = copyable) {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { contentDescription = "$label ${code.toList().joinToString(" ")}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = KkTheme.type.labelM, color = c.textBrand)
        Text(code, style = KkTheme.type.display, color = c.textPrimary)
        Text(if (copied) "복사했어요" else caption, style = KkTheme.type.caption, color = c.textSecondary)
    }
}

/** 처음 연결: 온보딩 연결과 같은 본문 → 확인 코드 비교. */
@Composable
private fun ConnectScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit, onDone: () -> Unit) {
    when (val s = ui.state) {
        PairingState.Idle, is PairingState.ShowCode -> Frame("연인과 연결", KkTopBarLeading.Close, close) {
            Title("연인과 연결해요", "초대 코드를 보내면 둘의 D-day와 상태가 함께 보여요.")
            InviteConnect(onAdvance = {}, viewModel = vm)
        }
        PairingState.WaitingPartner -> Frame("연인과 연결", KkTopBarLeading.Close, close, footer = { GhostButton("취소", close) }) {
            Title("코드가 맞았어요", "연인 폰이 응답하면 확인 코드가 떠요.")
            Waiting("연인 폰을 기다리는 중…")
        }
        is PairingState.CompareSas -> Frame("연인과 연결", KkTopBarLeading.Close, close, footer = {
            KkPrimaryButton("맞아요", vm::confirm, enabled = !ui.busy, loading = ui.busy)
            GhostButton("달라요 — 그만할게요", close)
        }) {
            Title("두 화면의 숫자가 같나요?", "전화 · 면회 · 메시지로 서로 읽어 주세요. 다르면 누군가 가운데서 바꿔치기한 거예요.")
            CodeCard("확인 코드", s.sas.chunked(3).joinToString(" "), "연인 폰에도 같은 숫자가 떠 있어요", copyable = false)
        }
        PairingState.WaitingConfirm -> Frame("연인과 연결", KkTopBarLeading.Close, close, footer = { GhostButton("취소", close) }) {
            Title("거의 다 됐어요", "연인도 [맞아요]를 누르면 바로 연결돼요.")
            Waiting("연인이 확인하길 기다리는 중…")
        }
        is PairingState.Linked -> Frame("연인과 연결", KkTopBarLeading.Close, onDone) {
            Title("연결됐어요", "이제 보고싶어 · 편지 · 일정을 함께 써요.")
        }
        is PairingState.Failed -> Frame("연인과 연결", KkTopBarLeading.Close, close, footer = { GhostButton("처음부터", vm::reset) }) {
            Title("연결하지 못했어요", s.reason)
        }
    }
}

/** 연인 폰 교체 받기(넘겨주는 쪽, Figma 재연결 / 지수 · 교체 받기 · 코드 · 확인). */
@Composable
private fun HandOverScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit) {
    val share = LocalKkShare.current
    val c = KkTheme.colors
    when (val s = ui.state) {
        PairingState.Idle -> Frame("상대 폰 교체 받기", KkTopBarLeading.Back, close, footer = {
            KkPrimaryButton("코드 만들기", vm::offerRelink, enabled = !ui.busy, loading = ui.busy)
        }) {
            Title("연인이 폰을 바꿨나요?", "새 폰에서 코드를 입력하면, 확인을 거쳐 지금까지의 기록을 이 폰에서 넘겨줘요.")
            val shape = RoundedCornerShape(KkRadius.lg)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf("코드를 만들어 연인에게 알려 주세요", "연인이 새 폰에 코드를 입력해요", "넘겨주기를 누르면 기록이 새 폰으로 옮겨져요").forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = KkTheme.type.labelS, color = c.textBrand)
                        }
                        Text(step, style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        is PairingState.ShowCode -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = { GhostButton("코드 취소", close) }) {
            val code = CrockfordCode.format(s.code)
            Title("연인에게 코드를 알려 주세요", "전화나 문자로 불러 줘도 돼요. 코드는 한 번만 쓸 수 있어요.")
            CodeCard("교체 코드", code, "24시간 동안 유효 · 눌러서 복사")
            KkSecondaryButton("메시지로 보내기", { share("꽃신 교체 코드: $code (새 폰의 '다시 연결하기'에 입력해 줘)") })
            Waiting("연인이 새 폰에 코드를 입력하길 기다리는 중…")
        }
        PairingState.WaitingPartner -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = { GhostButton("코드 취소", close) }) {
            Title("새 폰을 확인하는 중이에요", "새 폰이 응답하면 확인 코드가 떠요.")
            Waiting("새 폰을 기다리는 중…")
        }
        is PairingState.CompareSas -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = {
            KkPrimaryButton("넘겨줄게요", vm::confirm, enabled = !ui.busy, loading = ui.busy)
            GhostButton("취소", close)
        }) {
            Title("연인이 새 폰으로 다시 연결하려고 해요", "교체 코드가 맞는 새 폰이에요. 넘겨주면 지금까지의 기록이 새 폰으로 옮겨져요.")
            val shape = RoundedCornerShape(KkRadius.lg)
            // 새 기기(Figma 지수 · 확인): 기종 · 로그인 방법. 아래 확인 코드는 바꿔치기 방지(설계 7-3-B)라 함께 둔다.
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("새 기기", style = KkTheme.type.caption, color = c.textTertiary)
                Text(s.device ?: "새 폰", style = KkTheme.type.titleS, color = c.textPrimary)
                s.provider?.let { Text("${providerName(it)} 계정으로 로그인", style = KkTheme.type.bodyS, color = c.textSecondary) }
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(c.borderDefault))
                Text("확인 코드 ${s.sas.chunked(3).joinToString(" ")}", style = KkTheme.type.labelM, color = c.textPrimary)
                Text("새 폰 화면의 숫자와 같을 때만 넘겨주세요", style = KkTheme.type.caption, color = c.textSecondary)
            }
        }
        PairingState.WaitingConfirm, is PairingState.Linked -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close) {
            Title("기록을 넘겨주는 중이에요", "앱을 닫지 말고 와이파이에 연결해 두세요.")
            Waiting("새 폰이 받는 중…")
        }
        is PairingState.Failed -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = { GhostButton("처음부터", vm::reset) }) {
            Title("넘겨주지 못했어요", s.reason)
        }
    }
}

/** 다시 연결하기(새 폰, Figma 재연결 / 민준 · 코드 입력 · 확인 대기 · 넘겨받는 중). */
@Composable
private fun ReceiveScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit) {
    // 확인 코드는 바로 확인(넘겨주는 폰이 비교한다)해 상태가 넘어가도, 넘겨줄 때까지 계속 보여 준다.
    var lastSas by remember { mutableStateOf<String?>(null) }
    (ui.state as? PairingState.CompareSas)?.let { lastSas = it.sas }
    when (val s = ui.state) {
        PairingState.Idle, is PairingState.Failed -> Frame("다시 연결하기", KkTopBarLeading.Back, close, footer = {
            KkPrimaryButton("다음", vm::acceptRelink, enabled = CrockfordCode.normalize(ui.codeInput)?.length == 8 && !ui.busy, loading = ui.busy)
        }) {
            Title("연인 폰에서 받은 코드를 입력해 주세요", "연인이 [설정 › 상대 폰 교체 받기]에서 만든 8자리 코드예요.")
            KkInput(
                value = ui.codeInput,
                onValueChange = vm::editCode,
                label = "교체 코드",
                placeholder = "XXXX-XXXX",
                helper = "대문자와 숫자 8자리",
                error = (s as? PairingState.Failed)?.reason,
            )
        }
        PairingState.WaitingPartner, is PairingState.CompareSas, PairingState.WaitingConfirm, is PairingState.ShowCode ->
            Frame("다시 연결하기", KkTopBarLeading.Close, close, footer = { GhostButton("취소", close) }) {
                Title("코드가 맞았어요", "연인 폰에서 넘겨주기를 누르면 바로 넘겨받기 시작해요.")
                lastSas?.let { sas -> CodeCard("확인 코드", sas.chunked(3).joinToString(" "), "연인 폰에도 같은 숫자가 떠 있어요", copyable = false) }
                Waiting("연인이 확인하길 기다리는 중…")
            }
        is PairingState.Linked -> ReceivingProgress(ui)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) =
    KkButton(text, onClick, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)

/**
 * 온보딩 3 · 연결(Figma 53:923)의 본문: 내 초대 코드(눌러서 복사) · 카카오톡으로 초대 보내기 · 상대 코드 입력.
 * 들어오면 바로 내 코드를 만든다. 상대가 내 코드를 넣었거나 내가 상대 코드를 넣으면 [onAdvance] — 확인 코드(SAS) 비교로 넘어간다.
 */
@Composable
fun ColumnScope.InviteConnect(onAdvance: () -> Unit, viewModel: PairingViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    val clipboard = LocalClipboardManager.current
    val share = LocalKkShare.current
    LaunchedEffect(ui.available) { if (ui.available && ui.state is PairingState.Idle) viewModel.create() }
    LaunchedEffect(ui.state) {
        when (ui.state) {
            PairingState.WaitingPartner, is PairingState.CompareSas, PairingState.WaitingConfirm -> onAdvance()
            else -> Unit
        }
    }
    val code = (ui.state as? PairingState.ShowCode)?.code?.let(CrockfordCode::format)
    var copied by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        Modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Low, shape)
            .clip(shape)
            .background(c.bgBrandSubtle)
            .clickable(enabled = code != null) {
                clipboard.setText(AnnotatedString(code.orEmpty()))
                copied = true
            }
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Text("내 초대 코드", style = KkTheme.type.labelM, color = c.textBrand)
        Text(code ?: "····-····", style = KkTheme.type.display, color = c.textPrimary)
        Text(
            when {
                !ui.available -> "서버 설정이 들어가면 코드를 만들 수 있어요"
                copied -> "복사했어요"
                else -> "24시간 동안 유효 · 눌러서 복사"
            },
            style = KkTheme.type.caption, color = c.textSecondary,
        )
    }
    KkPrimaryButton(
        "카카오톡으로 초대 보내기",
        onClick = { code?.let { share("꽃신에서 나랑 연결해 줘! 초대 코드: $it (24시간 동안 유효)") } },
        enabled = code != null,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(c.borderDefault))
        Text("코드를 받았나요?", style = KkTheme.type.caption, color = c.textTertiary)
        Box(Modifier.weight(1f).height(1.dp).background(c.borderDefault))
    }
    KkInput(
        value = ui.codeInput,
        onValueChange = {
            viewModel.editCode(it)
            // 8자리를 다 넣으면 바로 연결을 시작한다(디자인에 별도 버튼이 없다).
            if (CrockfordCode.normalize(it)?.length == 8) viewModel.join()
        },
        label = "상대 초대 코드",
        placeholder = "XXXX-XXXX",
        helper = "연인이 보낸 코드를 입력하세요",
        error = (ui.state as? PairingState.Failed)?.reason,
        enabled = ui.available,
    )
}


/** "KAKAO" → "카카오". */
private fun providerName(id: String) = when (id) {
    "KAKAO" -> "카카오"
    "APPLE" -> "Apple"
    "GOOGLE" -> "Google"
    else -> id
}

/** 민준 · 넘겨받는 중(Figma 118:1459): 상단 바 없이 제목 · 진행률 카드(퍼센트 · 남은 시간 · 막대 · 편지 · 그림 · 일정 줄). */
@Composable
internal fun ReceivingProgress(ui: PairingUi) {
    val c = KkTheme.colors
    val expected = ui.expected
    val got = ui.received
    val total = expected?.total ?: 0
    val done = if (expected == null || got == null) 0 else
        minOf(got.letters, expected.letters) + minOf(got.drawings, expected.drawings) + minOf(got.events, expected.events)
    val percent = if (total == 0) 100 else (done * 100 / total)
    val elapsed = (kotlin.time.Clock.System.now().toEpochMilliseconds() - ui.receiveStartedMs).coerceAtLeast(1)
    val remainingSec = if (percent in 1..99) (elapsed * (100 - percent) / percent / 1000) else null
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Text("기록을 넘겨받는 중이에요", style = KkTheme.type.titleL, color = c.textPrimary)
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$percent%", style = KkTheme.type.titleM, color = c.textBrand, modifier = Modifier.weight(1f))
                Text(
                    when {
                        percent >= 100 -> "다 받았어요"
                        remainingSec == null -> "준비 중"
                        else -> "약 ${maxOf(1, (remainingSec + 59) / 60)}분 남음"
                    },
                    style = KkTheme.type.caption, color = c.textTertiary,
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgSubtle)) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(6.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgBrand))
            }
            if (expected != null) {
                val rows = listOf(
                    Triple("편지 ${expected.letters}개", got?.letters ?: 0, expected.letters),
                    Triple("그림 ${expected.drawings}개", got?.drawings ?: 0, expected.drawings),
                    Triple("일정 · 추억", got?.events ?: 0, expected.events),
                )
                // 앞 줄이 끝나야 다음 줄이 "받는 중" — 스냅샷이 한 번에 오더라도 Figma 처럼 차례로 보인다.
                var before = true
                rows.forEach { (label, n, of) ->
                    val state = when {
                        n >= of -> "완료"
                        before -> "받는 중"
                        else -> "대기"
                    }
                    if (n < of) before = false
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = KkTheme.type.bodyS, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(state, style = KkTheme.type.caption, color = if (state == "받는 중") c.textBrand else c.textTertiary)
                    }
                }
            }
        }
    }
}
