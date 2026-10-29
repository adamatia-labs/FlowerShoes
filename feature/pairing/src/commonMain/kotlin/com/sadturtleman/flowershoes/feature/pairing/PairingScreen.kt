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
import com.sadturtleman.flowershoes.core.data.sync.InviteLinks
import com.sadturtleman.flowershoes.core.data.sync.PairingSession
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.data.sync.RelinkSession
import com.sadturtleman.flowershoes.core.data.sync.RelinkState
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
    /** 상대 폰이 넘겨주기(#40 에서 제거). */
    val relink: RelinkState = RelinkState.Idle,
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
 * 첫 연결 화면(초대 링크, #15). [session] 이 null 이면 이 빌드에는 서버 설정이 없다.
 * 상대 수락은 2초마다 확인한다(푸시 · Firestore 실시간 구독으로 바꿔도 화면은 그대로다).
 */
class PairingViewModel(
    private val session: PairingSession?,
    private val sync: SyncClient?,
    private val settings: DeviceSettings,
    /** 상대 폰이 넘겨주기(7-3-B). */
    private val relink: RelinkSession? = null,
) : ViewModel() {

    private var relinkMode = false

    /** 지수: 설정 › "연인 폰 교체 받기". */
    fun offerRelink() = runRelink { it.offer() }

    /** 새 폰: 연인 폰에서 받은 코드로 다시 연결. */
    fun acceptRelink() = runRelink { it.accept(_ui.value.codeInput) }

    private fun runRelink(block: suspend (RelinkSession) -> RelinkState) {
        val r = relink ?: return
        relinkMode = true
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val next = runCatching { block(r) }.getOrElse { RelinkState.Failed(it.message ?: "연결하지 못했어요") }
            _ui.update { it.copy(relink = next, busy = false) }
            startRelinkPolling()
        }
    }

    private val _ui = MutableStateFlow(PairingUi(available = session != null, relinkAvailable = relink != null))
    val ui: StateFlow<PairingUi> = _ui.asStateFlow()
    private var polling: Job? = null

    /** 새 초대 링크를 만든다(이전 링크는 무효). */
    fun create() = run { it.createLink() }

    /** 초대 링크로 들어왔다(딥링크). */
    fun accept(token: String) = run { it.accept(token) }

    fun editCode(text: String) = _ui.update { it.copy(codeInput = text.uppercase().take(9)) }

    /** 넘겨주기 확인 코드가 맞다. */
    fun confirm() {
        val r = relink ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val next = runCatching { r.confirm() }.getOrElse { RelinkState.Failed(it.message ?: "연결하지 못했어요") }
            _ui.update { it.copy(relink = next, busy = false) }
            if (next is RelinkState.Linked) onLinked() else startRelinkPolling()
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
            if (next is PairingState.Linked) onLinked() else startPolling(s)
        }
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
                if (next is PairingState.Linked) onLinked()
            }
        }
    }

    private fun startRelinkPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                val st = _ui.value.relink
                if (st is RelinkState.Linked || st is RelinkState.Failed || st is RelinkState.Idle) break
                delay(2_000)
                val next = runCatching { relink?.poll() }.getOrNull() ?: continue
                _ui.update { it.copy(relink = next) }
                if (next is RelinkState.Linked) onLinked()
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
    LaunchedEffect(ui.state, ui.relink, ui.receiveDone, ui.expected) {
        val linked = if (mode == PairingMode.CONNECT) ui.state is PairingState.Linked else ui.relink is RelinkState.Linked
        if (linked && (mode != PairingMode.RECEIVE || ui.receiveDone)) onDone()
    }
    // 새 폰은 확인 코드가 뜨면 바로 확인한다 — 비교 · 넘겨주기는 넘겨주는 폰(데이터를 가진 쪽)이 한다.
    LaunchedEffect(ui.relink, mode) {
        if (mode == PairingMode.RECEIVE && ui.relink is RelinkState.CompareSas && !ui.busy) viewModel.confirm()
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
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
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
private fun Title(title: String, body: String? = null) {
    Text(title, style = KkTheme.type.title, color = KkTheme.colors.ink)
    body?.let { Text(it, style = KkTheme.type.body, color = KkTheme.colors.sub) }
}

/** "● 기다리는 중…" 한 줄. */
@Composable
private fun Waiting(text: String) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
        Text(text, style = KkTheme.type.body, color = c.sub, modifier = Modifier.weight(1f))
    }
}

/** 코드 카드(brand-subtle, 반경 24): 라벨 · 큰 코드 · 설명. 누르면 복사. */
@Composable
private fun CodeCard(label: String, code: String, caption: String, copyable: Boolean = true) {
    val c = KkTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.xl)).background(c.bar)
            .clickable(enabled = copyable) {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .semantics { contentDescription = "$label ${code.toList().joinToString(" ")}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = KkTheme.type.label, color = c.accent)
        Text(code, style = KkTheme.type.title, color = c.ink)
        Text(if (copied) "복사했어요" else caption, style = KkTheme.type.caption, color = c.sub)
    }
}

/** 처음 연결: 온보딩 연결과 같은 본문(내 초대 링크) → 수락되면 연결됨. 화면 자체는 #39 에서 Figma 로 다시 그린다. */
@Composable
private fun ConnectScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit, onDone: () -> Unit) {
    val share = LocalKkShare.current
    when (val s = ui.state) {
        PairingState.Idle, is PairingState.Waiting -> Frame("연인과 연결", KkTopBarLeading.Close, close) {
            Title("연인과 연결해요")
            InviteConnect(onAdvance = {}, viewModel = vm)
        }
        is PairingState.Accepting -> Frame("연인과 연결", KkTopBarLeading.Close, close) {
            Title("${s.inviterName}님과 연결하는 중")
            Waiting("연결하는 중…")
        }
        is PairingState.Linked -> Frame("연인과 연결", KkTopBarLeading.Close, onDone) {
            Title("연결됐어요")
        }
        PairingState.Expired -> Frame("연인과 연결", KkTopBarLeading.Close, close, footer = {
            KkPrimaryButton("내가 초대 링크 보내기", vm::create, enabled = !ui.busy, loading = ui.busy)
        }) {
            Title("이 초대 링크는 만료됐어요")
        }
        is PairingState.OwnLink -> Frame("연인과 연결", KkTopBarLeading.Close, close, footer = {
            KkPrimaryButton("다시 공유하기", { share(inviteMessage(s.token)) })
        }) {
            Title("내가 보낸 초대 링크예요")
        }
        PairingState.AlreadyLinked -> Frame("연인과 연결", KkTopBarLeading.Close, close) {
            Title("이미 연결돼 있어요")
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
    when (val s = ui.relink) {
        RelinkState.Idle -> Frame("상대 폰 교체 받기", KkTopBarLeading.Back, close, footer = {
            KkPrimaryButton("코드 만들기", vm::offerRelink, enabled = !ui.busy, loading = ui.busy)
        }) {
            Title("연인이 폰을 바꿨나요?")
            val shape = RoundedCornerShape(KkRadius.lg)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf("코드를 만들어 연인에게 알려 주세요", "연인이 새 폰에 코드를 입력해요", "넘겨주기를 누르면 기록이 새 폰으로 옮겨져요").forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(c.bar), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = KkTheme.type.label, color = c.accent)
                        }
                        Text(step, style = KkTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        is RelinkState.ShowCode -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = { GhostButton("코드 취소", close) }) {
            val code = CrockfordCode.format(s.code)
            Title("연인에게 코드를 알려 주세요")
            CodeCard("교체 코드", code, "24시간 동안 유효 · 눌러서 복사")
            KkSecondaryButton("메시지로 보내기", { share("꽃신 교체 코드: $code (새 폰의 '다시 연결하기'에 입력해 줘)") })
            Waiting("연인이 새 폰에 코드를 입력하길 기다리는 중…")
        }
        RelinkState.WaitingPartner -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = { GhostButton("코드 취소", close) }) {
            Title("새 폰을 확인하는 중이에요", "새 폰이 응답하면 확인 코드가 떠요.")
            Waiting("새 폰을 기다리는 중…")
        }
        is RelinkState.CompareSas -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = {
            KkPrimaryButton("넘겨줄게요", vm::confirm, enabled = !ui.busy, loading = ui.busy)
            GhostButton("취소", close)
        }) {
            Title("연인이 새 폰으로 다시 연결하려고 해요")
            val shape = RoundedCornerShape(KkRadius.lg)
            // 새 기기(Figma 지수 · 확인): 기종 · 로그인 방법. 아래 확인 코드는 바꿔치기 방지(설계 7-3-B)라 함께 둔다.
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("새 기기", style = KkTheme.type.caption, color = c.sub)
                Text(s.device ?: "새 폰", style = KkTheme.type.labelBold, color = c.ink)
                s.provider?.let { Text("${providerName(it)} 계정으로 로그인", style = KkTheme.type.body, color = c.sub) }
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(c.line))
                Text("확인 코드 ${s.sas.chunked(3).joinToString(" ")}", style = KkTheme.type.label, color = c.ink)
                Text("새 폰 화면의 숫자와 같을 때만 넘겨주세요", style = KkTheme.type.caption, color = c.sub)
            }
        }
        RelinkState.WaitingConfirm, is RelinkState.Linked -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close) {
            Title("기록을 넘겨주는 중이에요")
            Waiting("새 폰이 받는 중…")
        }
        is RelinkState.Failed -> Frame("상대 폰 교체 받기", KkTopBarLeading.Close, close, footer = { GhostButton("처음부터", vm::reset) }) {
            Title("넘겨주지 못했어요", s.reason)
        }
    }
}

/** 다시 연결하기(새 폰, Figma 재연결 / 민준 · 코드 입력 · 확인 대기 · 넘겨받는 중). */
@Composable
private fun ReceiveScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit) {
    // 확인 코드는 바로 확인(넘겨주는 폰이 비교한다)해 상태가 넘어가도, 넘겨줄 때까지 계속 보여 준다.
    var lastSas by remember { mutableStateOf<String?>(null) }
    (ui.relink as? RelinkState.CompareSas)?.let { lastSas = it.sas }
    when (val s = ui.relink) {
        RelinkState.Idle, is RelinkState.Failed -> Frame("다시 연결하기", KkTopBarLeading.Back, close, footer = {
            KkPrimaryButton("다음", vm::acceptRelink, enabled = CrockfordCode.normalize(ui.codeInput)?.length == 8 && !ui.busy, loading = ui.busy)
        }) {
            Title("연인 폰에서 받은 코드를 입력해 주세요")
            KkInput(
                value = ui.codeInput,
                onValueChange = vm::editCode,
                label = "교체 코드",
                placeholder = "XXXX-XXXX",
                helper = "대문자와 숫자 8자리",
                error = (s as? RelinkState.Failed)?.reason,
            )
        }
        RelinkState.WaitingPartner, is RelinkState.CompareSas, RelinkState.WaitingConfirm, is RelinkState.ShowCode ->
            Frame("다시 연결하기", KkTopBarLeading.Close, close, footer = { GhostButton("취소", close) }) {
                Title("코드가 맞았어요")
                lastSas?.let { sas -> CodeCard("확인 코드", sas.chunked(3).joinToString(" "), "연인 폰에도 같은 숫자가 떠 있어요", copyable = false) }
                Waiting("연인이 확인하길 기다리는 중…")
            }
        is RelinkState.Linked -> ReceivingProgress(ui)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) =
    KkButton(text, onClick, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost)

/**
 * 온보딩 3 · 연결(Figma 53:923)의 본문: 내 초대 링크 · 카카오톡으로 초대 보내기.
 * 들어오면 바로 링크를 만든다. 상대가 링크를 누르면 수락돼 [onAdvance]. 카카오 피드 템플릿 공유 · Figma 화면은 #39.
 */
@Composable
fun ColumnScope.InviteConnect(onAdvance: () -> Unit, viewModel: PairingViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = KkTheme.colors
    val share = LocalKkShare.current
    LaunchedEffect(ui.available) { if (ui.available && ui.state is PairingState.Idle) viewModel.create() }
    LaunchedEffect(ui.state) { if (ui.state is PairingState.Linked) onAdvance() }
    val token = (ui.state as? PairingState.Waiting)?.token
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        Modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Low, shape)
            .clip(shape)
            .background(c.bar)
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Text("내 초대 링크", style = KkTheme.type.label, color = c.accent)
        Text(
            when {
                !ui.available -> "서버 설정 전"
                token == null -> "링크를 만드는 중"
                else -> "7일 동안 유효 · 1회용"
            },
            style = KkTheme.type.caption, color = c.sub,
        )
    }
    KkPrimaryButton(
        "카카오톡으로 초대 보내기",
        onClick = { token?.let { share(inviteMessage(it)) } },
        enabled = token != null,
    )
    (ui.state as? PairingState.Failed)?.let { Text(it.reason, style = KkTheme.type.caption, color = c.sub) }
}

/** OS 공유 시트로 보낼 문구. 카카오 피드 템플릿 공유는 #39. */
private fun inviteMessage(token: String) = "꽃신에서 나랑 연결해 줘! ${InviteLinks.url(token)}"

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
        Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Text("기록을 넘겨받는 중이에요", style = KkTheme.type.title, color = c.ink)
        val shape = RoundedCornerShape(KkRadius.lg)
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$percent%", style = KkTheme.type.title, color = c.accent, modifier = Modifier.weight(1f))
                Text(
                    when {
                        percent >= 100 -> "다 받았어요"
                        remainingSec == null -> "준비 중"
                        else -> "약 ${maxOf(1, (remainingSec + 59) / 60)}분 남음"
                    },
                    style = KkTheme.type.caption, color = c.sub,
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.line)) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(6.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.accent))
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
                        Text(label, style = KkTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        Text(state, style = KkTheme.type.caption, color = if (state == "받는 중") c.accent else c.sub)
                    }
                }
            }
        }
    }
}
