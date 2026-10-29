package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.ApproveResult
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeApprover
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangePrompt
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeRequest
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeSession
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeState
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeStatus
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.core.data.sync.TransferCounts
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkSpinner
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkToastType
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

// ── 새 폰 ─────────────────────────────────────────────────────────────────

data class DeviceChangeUi(
    val available: Boolean,
    val state: DeviceChangeState = DeviceChangeState.Idle,
    val busy: Boolean = false,
    /** 받는 중: 상대 폰이 센 기록 수 · 이 폰에 들어온 수. */
    val expected: TransferCounts? = null,
    val received: TransferCounts? = null,
    /** 다 받았거나 기다릴 만큼 기다렸다 — 완료 화면. */
    val done: Boolean = false,
)

/**
 * 새 폰(Figma 7 · 새 폰): 되찾을 기록 → 승인 기다리는 중 → 연인 폰에서 받는 중 → 완료, 또는 거절됨.
 * 앱을 껐다 켜도 이 기기의 마지막 요청으로 이어 본다. 승인을 기다리는 동안 3초마다 확인한다.
 */
class DeviceChangeViewModel(
    private val session: DeviceChangeSession?,
    private val sync: SyncClient?,
    private val settings: DeviceSettings,
    private val bizLogger: BizLogger,
) : ViewModel() {
    private val _ui = MutableStateFlow(DeviceChangeUi(available = session != null))
    val ui: StateFlow<DeviceChangeUi> = _ui.asStateFlow()
    private var polling: Job? = null

    init {
        session?.let { s -> viewModelScope.launch { show(runCatching { s.poll() }.getOrElse { s.state }) } }
    }

    /** "연인에게 요청하기" · "다시 요청". */
    fun request() = act { it.request() }

    /** "요청 취소". */
    fun cancel() = act { it.cancel() }

    private fun act(block: suspend (DeviceChangeSession) -> DeviceChangeState) {
        val s = session ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val next = runCatching { block(s) }.getOrElse { DeviceChangeState.Failed("요청하지 못했어요") }
            _ui.update { it.copy(busy = false) }
            show(next)
        }
    }

    private suspend fun show(next: DeviceChangeState) {
        _ui.update { it.copy(state = next) }
        when (next) {
            is DeviceChangeState.Waiting -> startPolling()
            is DeviceChangeState.Linked -> receive(next)
            else -> Unit
        }
    }

    private fun startPolling() {
        val s = session ?: return
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive && _ui.value.state is DeviceChangeState.Waiting) {
                delay(3_000)
                val next = runCatching { s.poll() }.getOrNull() ?: continue
                if (next != _ui.value.state) show(next)
            }
        }
    }

    /**
     * 승인됐다: 상대 폰의 전체 스냅샷이 들어올 때까지 동기화하며 받은 수를 센다(Figma 연인 폰에서 받는 중).
     * 다 받았거나 2분이 지나면 완료 — 나머지는 평소 동기화로 이어 받는다.
     */
    private suspend fun receive(linked: DeviceChangeState.Linked) {
        val s = session ?: return
        // 승인으로 처음 연결된 때 한 번(앱을 다시 켜 이어 받는 것은 세지 않는다).
        if (linked.fresh) bizLogger.record(BizView.PAIRING, BizEvent.PairingLinked(relink = true))
        val expected = linked.expected
        _ui.update { it.copy(expected = expected, received = s.localCounts()) }
        val start = Clock.System.now()
        while (Clock.System.now() - start < 2.minutes) {
            runCatching { sync?.syncOnce() }
            val got = runCatching { s.localCounts() }.getOrNull() ?: break
            _ui.update { it.copy(received = got) }
            if (got.covers(expected)) break
            delay(1_500)
        }
        _ui.update { it.copy(done = true) }
    }

    /** "시작하기": 새 폰은 온보딩 없이 들어온다 — 넘겨받은 내 역할로 정하고 온보딩을 끝낸 것으로 한다. */
    fun finish(onDone: () -> Unit) {
        val s = session ?: return onDone()
        viewModelScope.launch {
            runCatching { s.myRole()?.let { settings.setRole(com.sadturtleman.flowershoes.core.model.Role.valueOf(it)) } }
            (s.state as? DeviceChangeState.Linked)?.let { settings.setKeyEpoch(it.epoch) }
            settings.setOnboarded()
            onDone()
        }
    }
}

private fun TransferCounts.covers(expected: TransferCounts) =
    letters >= expected.letters && events >= expected.events && photos >= expected.photos && drawings >= expected.drawings

/** 연인 이름이 없을 때(새 폰은 승인 전 상대 이름을 모른다). */
private const val PARTNER = "연인"

@Composable
fun DeviceChangeRoute(onDone: () -> Unit, onBack: () -> Unit, viewModel: DeviceChangeViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    DeviceChangeScreen(ui, onRequest = viewModel::request, onCancel = viewModel::cancel, onStart = { viewModel.finish(onDone) }, onBack = onBack)
}

@Composable
internal fun DeviceChangeScreen(
    ui: DeviceChangeUi,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    partnerName: String = PARTNER,
) {
    val c = KkTheme.colors
    val partner = partnerName.ifBlank { PARTNER }
    when (val s = ui.state) {
        DeviceChangeState.Idle, is DeviceChangeState.Failed -> Screen(KkTopBarLeading.Back, onBack, footer = {
            KkPrimaryButton("${partner}에게 요청하기", onRequest, enabled = ui.available && !ui.busy, loading = ui.busy)
        }) {
            Text("${partner}의 폰에서 되찾아요", style = KkTheme.type.title, color = c.ink)
            Spacer(Modifier.height(KkSpacing.xxxl))
            Card {
                Text("돌아와요", style = KkTheme.type.labelBold, color = c.ink)
                listOf("편지 · 답장 · 그림", "일정 · 휴가 · 만남", "사진 · 추억 책").forEach { CheckLine(it, KkIcons.Check, c.success, c.ink) }
            }
            Spacer(Modifier.height(KkSpacing.lg))
            Column(
                Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(KkRadius.sm)).padding(horizontal = 15.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("돌아오지 않아요", style = KkTheme.type.labelBold, color = c.sub)
                listOf("이 폰의 설정 (알림 · 테마 · 글꼴)", "보내지 않은 임시저장 편지").forEach { CheckLine(it, KkIcons.Close, c.sub, c.sub) }
            }
            (s as? DeviceChangeState.Failed)?.let { Text(it.reason, style = KkTheme.type.caption, color = c.danger) }
        }
        is DeviceChangeState.Waiting -> Screen(KkTopBarLeading.Close, onBack, footer = { TextButton("요청 취소", onCancel, enabled = !ui.busy) }) {
            Phones(partner, Link.Waiting)
            Spacer(Modifier.height(KkSpacing.xxxl))
            CenterTitle("${partner.withSubject()} 확인하면 이어져요")
        }
        is DeviceChangeState.Linked -> if (!ui.done) Screen(KkTopBarLeading.Close, null) {
            Phones(partner, Link.Done, top = 24.dp)
            Spacer(Modifier.height(KkSpacing.xxxl))
            Text("${partner.withSubject()} 승인했어요", style = KkTheme.type.title, color = c.ink)
            Spacer(Modifier.height(KkSpacing.xxxl))
            ReceivingRows(ui.expected ?: s.expected, ui.received ?: TransferCounts())
            Spacer(Modifier.height(KkSpacing.xl))
            Text("앱을 닫지 마세요", style = KkTheme.type.caption, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else Screen(KkTopBarLeading.Close, onStart, footer = { KkPrimaryButton("시작하기", onStart) }) {
            val got = ui.received ?: TransferCounts()
            Spacer(Modifier.height(KkSpacing.xxxl))
            KkIcon(KkIcons.Check, tint = c.success, size = 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(KkSpacing.xxl))
            CenterTitle("모두 옮겨 왔어요")
            Spacer(Modifier.height(KkSpacing.sm))
            Text(
                "편지 ${got.letters}통 · 일정 ${got.events}개 · 사진 ${got.photos}장 · 그림 ${got.drawings}개",
                style = KkTheme.type.body, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        DeviceChangeState.Rejected, DeviceChangeState.Blocked, DeviceChangeState.Expired -> Screen(KkTopBarLeading.Close, onBack, footer = {
            KkPrimaryButton("다시 요청하기", onRequest, enabled = !ui.busy, loading = ui.busy)
            TextButton("옛 폰으로 돌아가기", onBack)
        }) {
            Phones(partner, Link.Failed)
            Spacer(Modifier.height(KkSpacing.xxxl))
            CenterTitle(
                when (s) {
                    DeviceChangeState.Rejected -> "${partner.withSubject()} 확인해 주지 않았어요"
                    DeviceChangeState.Blocked -> "옛 폰에서 요청을 막았어요"
                    else -> "요청이 만료됐어요"
                },
            )
        }
    }
}

private enum class Link { Waiting, Done, Failed }

/** 새 폰 — 선 — 연인의 폰. 가운데는 기다리는 중 스피너 · 거절 경고, 선 색은 상태. */
@Composable
private fun Phones(partner: String, link: Link, top: androidx.compose.ui.unit.Dp = 44.dp) {
    val c = KkTheme.colors
    Box(Modifier.fillMaxWidth().padding(top = top), contentAlignment = Alignment.TopCenter) {
        Row(verticalAlignment = Alignment.Top) {
            PhoneLabel("새 폰")
            Box(Modifier.width(96.dp).height(56.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(when (link) {
                    Link.Waiting -> c.line
                    Link.Done -> c.success
                    Link.Failed -> c.danger
                }))
                when (link) {
                    Link.Waiting -> Box(Modifier.size(32.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.canvas), contentAlignment = Alignment.Center) { KkSpinner(c.accent, 32.dp) }
                    Link.Failed -> Box(Modifier.size(28.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.canvas)) { KkIcon(KkIcons.Alert, tint = c.danger, size = 28.dp) }
                    Link.Done -> Unit
                }
            }
            PhoneLabel("${partner}의 폰")
        }
    }
}

@Composable
private fun PhoneLabel(label: String) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KkIcon(KkIcons.Phone, tint = KkTheme.colors.ink, size = 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = KkTheme.type.caption, color = KkTheme.colors.sub, maxLines = 1, softWrap = false)
    }
}

/** 받는 중 줄(편지 · 일정 · 사진 · 그림): 이름 · 상태 · 막대. 앞 줄이 끝나야 다음 줄이 받는 중으로 보인다. */
@Composable
private fun ReceivingRows(expected: TransferCounts, got: TransferCounts) {
    val c = KkTheme.colors
    val rows = listOf(
        Triple("편지", got.letters, expected.letters) to "통",
        Triple("일정", got.events, expected.events) to "개",
        Triple("사진", got.photos, expected.photos) to "장",
        Triple("그림", got.drawings, expected.drawings) to "개",
    )
    var before = true
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.xxl)) {
        rows.forEach { (row, unit) ->
            val (label, n, of) = row
            val complete = n >= of
            val active = !complete && before
            if (!complete) before = false
            Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            complete -> "$of$unit · 다 받았어요"
                            active -> "${minOf(n, of)} / $of$unit"
                            else -> "기다리는 중"
                        },
                        style = KkTheme.type.caption, color = if (complete) c.success else c.sub,
                    )
                }
                val fraction = if (of == 0) 1f else minOf(n, of).toFloat() / of
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.line)) {
                    if (complete || active) Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(KkRadius.full)).background(if (complete) c.success else c.accent))
                }
            }
        }
    }
}

// ── 상대 폰 · 옛 폰 ────────────────────────────────────────────────────────

/**
 * 연결된 폰에 뜨는 기기 교체 화면. 상대 폰(Figma 7 · 연인 폰): 승인 요청 · 24시간 안 두 번째 요청 → 승인함.
 * 옛 폰(Figma 7 · 옛 폰): 새 폰에서 로그인했어요 → 막음 완료. [onHandled] 를 부르면 셸이 닫는다.
 * 답을 보내면 서버 상태가 바뀌어 요청이 목록에서 빠진다 — [onActed] 로 셸이 결과 화면을 붙잡아 둔다.
 */
@Composable
fun DeviceChangePromptRoute(prompt: DeviceChangePrompt, partnerName: String, provider: String?, onActed: () -> Unit = {}, onHandled: () -> Unit) {
    val approver = org.koin.compose.getKoin().getOrNull<DeviceChangeApprover>()
    val scope = rememberCoroutineScope()
    var busy by remember(prompt.request.id) { mutableStateOf(false) }
    var result by remember(prompt.request.id) { mutableStateOf<PromptResult?>(null) }
    var failed by remember(prompt.request.id) { mutableStateOf(false) }
    val act: (suspend (DeviceChangeApprover) -> PromptResult) -> Unit = { block ->
        if (approver != null && !busy) scope.launch {
            onActed()
            busy = true
            failed = false
            runCatching { block(approver) }.onSuccess { result = it }.onFailure { failed = true }
            busy = false
        }
    }
    DeviceChangePromptScreen(
        prompt, partnerName, provider, result, busy, failed,
        onApprove = {
            act {
                when (val r = it.approve(prompt.request)) {
                    is ApproveResult.Approved -> PromptResult.Approved(r.skipped)
                    ApproveResult.Sent -> PromptResult.Approved(0)
                    // 그사이 막혔거나 취소됐다: 바뀐 것이 없으니 화면만 닫는다.
                    ApproveResult.Stopped -> PromptResult.Rejected
                }
            }
        },
        onReject = { act { it.reject(prompt.request); PromptResult.Rejected } },
        onBlock = { act { it.block(prompt.request); PromptResult.Blocked } },
        onClose = onHandled,
    )
}

internal sealed interface PromptResult {
    /** [skipped]: 크기 한도를 넘어 보내지 못한 항목 수(#20 결정 5). */
    data class Approved(val skipped: Int) : PromptResult

    data object Rejected : PromptResult

    data object Blocked : PromptResult
}

@Composable
internal fun DeviceChangePromptScreen(
    prompt: DeviceChangePrompt,
    partnerName: String,
    provider: String?,
    result: PromptResult?,
    busy: Boolean,
    failed: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBlock: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(result) { if (result == PromptResult.Rejected) onClose() }
    when (prompt) {
        is DeviceChangePrompt.Approve -> ApproveScreen(prompt, partnerName.ifBlank { PARTNER }, result as? PromptResult.Approved, busy, failed, onApprove, onReject, onClose)
        is DeviceChangePrompt.SignedInElsewhere ->
            if (result == PromptResult.Blocked) BlockedScreen(prompt.request, partnerName.ifBlank { PARTNER }, provider, onClose)
            else SignedInScreen(prompt, partnerName.ifBlank { PARTNER }, busy, failed, onBlock, onClose)
    }
}

@Composable
private fun ApproveScreen(
    prompt: DeviceChangePrompt.Approve,
    partner: String,
    approved: PromptResult.Approved?,
    busy: Boolean,
    failed: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClose: () -> Unit,
) {
    val c = KkTheme.colors
    val r = prompt.request
    var confirming by remember(r.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Screen(
            KkTopBarLeading.Close, onClose, title = "새 폰 확인",
            footer = {
                if (approved != null) {
                    KkToast(
                        if (approved.skipped > 0) "승인했어요 · ${approved.skipped}개는 너무 커서 보내지 못했어요" else "승인했어요 · ${partner}의 새 폰으로 보내는 중",
                        Modifier.align(Alignment.CenterHorizontally), type = KkToastType.Success,
                    )
                    Spacer(Modifier.height(KkSpacing.lg))
                    KkPrimaryButton("닫기", onClose)
                } else {
                    // 24시간 안 두 번째 요청은 한 번 더 확인한다(#20 결정 4).
                    KkPrimaryButton("승인하기", { if (r.second) confirming = true else onApprove() }, enabled = !busy, loading = busy)
                    TextButton("거절하기", onReject, enabled = !busy)
                }
            },
        ) {
            if (r.second && approved == null) {
                Banner("24시간 안에 두 번째 교체 요청이에요")
                Spacer(Modifier.height(KkSpacing.xxl))
            }
            RequestCard(r, partner, approved != null)
            if (approved == null) {
                Spacer(Modifier.height(KkSpacing.xxxl))
                if (r.second) {
                    Text("최근 바뀐 기록", style = KkTheme.type.labelBold, color = c.ink)
                    Spacer(Modifier.height(KkSpacing.sm))
                    prompt.recent.forEach { o ->
                        Text(
                            listOf(whenText(o.createdAtMs), o.deviceName.ifBlank { "새 폰" }, statusText(o.status)).joinToString(" · "),
                            style = KkTheme.type.caption, color = if (o.id == r.id) c.ink else c.sub,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                } else {
                    Banner("${partner}에게 직접 바꿨는지 물어보세요")
                    Spacer(Modifier.height(KkSpacing.xxxl))
                    Text("승인하면 우리 둘의 기록이 새 폰으로 가고, ${partner}의 옛 폰은 로그아웃돼요.", style = KkTheme.type.caption, color = c.sub)
                }
                if (failed) Text("보내지 못했어요. 다시 눌러 주세요", style = KkTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = KkSpacing.lg))
            }
        }
        if (confirming) KkDialogHost({ confirming = false }) {
            KkDialog(
                "한 번 더 확인할게요", "${partner}에게 전화로 확인했을 때만 승인하세요", "승인하기",
                onConfirm = { confirming = false; onApprove() }, onCancel = { confirming = false },
            )
        }
    }
}

@Composable
private fun SignedInScreen(prompt: DeviceChangePrompt.SignedInElsewhere, partner: String, busy: Boolean, failed: Boolean, onBlock: () -> Unit, onClose: () -> Unit) {
    val c = KkTheme.colors
    val r = prompt.request
    Screen(null, null, footer = {
        if (prompt.canBlock) {
            KkButton("새 폰 막기", onBlock, Modifier.fillMaxWidth(), enabled = !busy, loading = busy, danger = true)
            TextButton("닫기", onClose, enabled = !busy)
        } else {
            KkPrimaryButton("닫기", onClose)
        }
    }) {
        Spacer(Modifier.height(KkSpacing.xxxl))
        KkIcon(KkIcons.Phone, tint = c.ink, size = 56.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(KkSpacing.xl))
        CenterTitle("새 폰에서 로그인했어요")
        Spacer(Modifier.height(72.dp))
        Card {
            Text(r.deviceName.ifBlank { "새 폰" }, style = KkTheme.type.labelBold, color = c.ink)
            Text(whenText(r.createdAtMs), style = KkTheme.type.caption, color = c.sub)
            Text(
                if (r.status == DeviceChangeStatus.APPROVED) "${partner.withSubject()} ${clockText(r.answeredAtMs ?: r.createdAtMs)}에 승인" else "${partner}의 확인을 기다리는 중",
                style = KkTheme.type.caption, color = c.sub,
            )
        }
        if (failed) Text("막지 못했어요. 다시 눌러 주세요", style = KkTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = KkSpacing.lg))
    }
}

@Composable
private fun BlockedScreen(r: DeviceChangeRequest, partner: String, provider: String?, onClose: () -> Unit) {
    val c = KkTheme.colors
    Screen(null, null, footer = { KkPrimaryButton("닫기", onClose) }) {
        Spacer(Modifier.height(KkSpacing.xxxl))
        KkIcon(KkIcons.Lock, tint = c.ink, size = 48.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(KkSpacing.xxl))
        CenterTitle("새 폰을 막았어요")
        Spacer(Modifier.height(KkSpacing.sm))
        Text("${r.deviceName.ifBlank { "새 폰" }}의 요청을 취소했어요", style = KkTheme.type.body, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(80.dp))
        Card {
            Text("지금 해 두면 좋아요", style = KkTheme.type.labelBold, color = c.ink)
            listOfNotNull(
                provider?.let { "$it 계정 비밀번호 바꾸기" },
                "다른 기기 로그인 기록 확인하기",
                "${partner}에게 직접 연락하기",
            ).forEach { CheckLine(it, KkIcons.Check, c.success, c.ink) }
        }
    }
}

/** 요청 카드(Figma 요청 카드): 폰 아이콘 · "지수가 새 폰으로 바꿨대요. 맞나요?" · 기기 · 시각(· 승인함 ✓). */
@Composable
private fun RequestCard(r: DeviceChangeRequest, partner: String, approved: Boolean) {
    val c = KkTheme.colors
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KkIcon(KkIcons.Phone, tint = c.danger, size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${partner.withSubject()} 새 폰으로 바꿨대요. 맞나요?", style = KkTheme.type.labelBold, color = c.ink)
                Spacer(Modifier.height(KkSpacing.lg))
                Text(r.deviceName.ifBlank { "새 폰" }, style = KkTheme.type.caption, color = c.sub)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${whenText(r.createdAtMs)} ${if (approved) "· 승인함" else "요청"}", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.weight(1f))
                    if (approved) KkIcon(KkIcons.Check, tint = c.success, size = 20.dp)
                }
            }
        }
    }
}

// ── 공용 조각 ─────────────────────────────────────────────────────────────

/** 화면 틀: 상단 바(선택) + 본문(좌우 24) + 하단 버튼. */
@Composable
private fun Screen(
    leading: KkTopBarLeading?,
    onLeading: (() -> Unit)?,
    title: String = "새 폰으로 옮기기",
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        if (leading != null) KkTopBar(title, onLeading = onLeading ?: {}, leading = leading)
        else Spacer(Modifier.height(48.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xxl, vertical = KkSpacing.sm),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.lg, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = footer,
        )
    }
}

@Composable
private fun CenterTitle(text: String) =
    Text(text, style = KkTheme.type.title, color = KkTheme.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

/**
 * 종이 카드. Figma 는 테이프를 붙이고 살짝 기울였지만, 읽고 행동하는 카드라 반듯하게 둔다
 * (design.md: 기울기 · 테이프는 보여 주기만 하는 물건에만).
 */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(KkRadius.sm)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(KkTheme.colors.surface).padding(KkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        content = content,
    )
}

@Composable
private fun CheckLine(text: String, icon: KkIcons, tint: Color, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        KkIcon(icon, tint = tint, size = 16.dp)
        Text(text, style = KkTheme.type.body, color = color)
    }
}

/** 배너(Figma Banner): tag 바탕 · 경고 아이콘 · 굵은 한 줄. */
@Composable
private fun Banner(title: String) {
    val c = KkTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(c.tag).padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Alert, tint = c.ink, size = 22.dp)
        Text(title, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
    }
}

/** Figma Button · Text: 바탕 없이 강조색 글자. */
@Composable
private fun TextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(KkRadius.full))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KkTheme.type.labelBold, color = KkTheme.colors.accent.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

private val SEOUL = TimeZone.of("Asia/Seoul")

/** "오후 9:30". */
internal fun clockText(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(SEOUL)
    val h = t.hour % 12
    return "${if (t.hour < 12) "오전" else "오후"} ${if (h == 0) 12 else h}:${t.minute.toString().padStart(2, '0')}"
}

/** "오늘 오후 9:30" · "어제 오후 3:10" · "10.21 오후 3:10". */
internal fun whenText(ms: Long, now: Instant = Clock.System.now()): String {
    val at = Instant.fromEpochMilliseconds(ms).toLocalDateTime(SEOUL)
    val day = when (at.date.daysUntil(now.toLocalDateTime(SEOUL).date)) {
        0 -> "오늘"
        1 -> "어제"
        else -> "${at.month.number}.${at.day}"
    }
    return "$day ${clockText(ms)}"
}

private fun statusText(s: DeviceChangeStatus) = when (s) {
    DeviceChangeStatus.PENDING -> "확인 기다림"
    DeviceChangeStatus.APPROVED -> "승인함"
    DeviceChangeStatus.REJECTED -> "거절함"
    DeviceChangeStatus.CANCELLED -> "취소됨"
    DeviceChangeStatus.BLOCKED -> "막음"
}
