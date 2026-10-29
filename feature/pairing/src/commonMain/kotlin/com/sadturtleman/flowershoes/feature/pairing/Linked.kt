package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
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
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkToastType
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 연결된 직후(보낸 쪽 · 받는 쪽 공통): 키 epoch 1 을 적고, 혼자 쓴 기록(편지 · 그림)을 첫 동기화로 보낸다.
 * 보낼 것은 [begin] 이 센다 — 많으면([showsProgress]) 화면이 "편지 N통 보내는 중"을 보여 준다.
 */
class LinkHandoff(private val settings: DeviceSettings, private val sync: SyncClient?) {
    suspend fun begin(): Outbox {
        settings.setKeyEpoch(1)
        // 상대가 혼자 쓴 편지가 들어오면 "훈련소 동안 쓴 편지 N통 도착"([SoloArrivals]).
        settings.setSoloArrivalWatch(true)
        return runCatching { sync?.outbox() }.getOrNull() ?: Outbox(0, 0, 0)
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

/** 연결 뒤 화면: 모달 → (많으면) 보내는 중 → 다 보냄 토스트. */
data class AfterLinkUi(
    val partnerName: String,
    val total: Outbox,
    val left: Outbox,
    val startedMs: Long,
    val phase: Phase = Phase.MODAL,
) {
    enum class Phase { MODAL, SENDING, SENT }
}

/**
 * 보낸 쪽: 초대를 보내고 홈으로 간 뒤, 앱이 앞에 있는 동안 상대 수락을 확인한다(푸시를 누르고 들어와도 여기서 이어진다).
 * 수락되면 연결 모달(Figma 연결 완료 · 연결 모달) → 혼자 쓴 편지가 많으면 보내는 중 → 다 보냄 토스트.
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
    private var sending = false

    /** 앱이 앞에 있는 동안 부른다. 기다리는 링크가 없거나 결과가 나면 끝난다. */
    suspend fun watch() {
        val s = session ?: return
        while (true) {
            val next = runCatching { s.poll() }.getOrNull()
            when {
                next is PairingState.Linked -> {
                    if (s.isInviter && handled != next.coupleId) linked(next.coupleId)
                    return
                }
                next is PairingState.Waiting -> delay(POLL_MS)
                next == null && settings.pendingInvites.load() != null -> delay(POLL_MS)
                else -> return
            }
        }
    }

    private fun linked(coupleId: String) {
        handled = coupleId
        bizLogger.record(BizView.PAIRING, BizEvent.PairingLinked(relink = false))
        viewModelScope.launch {
            val total = handoff.begin()
            _ui.value = AfterLinkUi(partnerName(), total, total, Clock.System.now().toEpochMilliseconds())
            sending = true
            handoff.send { left -> _ui.update { it?.copy(left = left) } }
            sending = false
            val name = partnerName()
            _ui.update { ui ->
                ui?.copy(partnerName = name, phase = if (ui.phase == AfterLinkUi.Phase.SENDING) AfterLinkUi.Phase.SENT else ui.phase)
            }
        }
    }

    /** 모달의 "시작하기". 편지 · 그림이 많으면 보내는 중 화면, 아니면 닫는다(동기화는 계속). */
    fun start() = _ui.update { ui ->
        when {
            ui == null -> null
            !ui.total.showsProgress() -> null
            !sending || ui.left.isEmpty -> ui.copy(phase = AfterLinkUi.Phase.SENT)
            else -> ui.copy(phase = AfterLinkUi.Phase.SENDING, startedMs = Clock.System.now().toEpochMilliseconds())
        }
    }

    fun toastShown() = _ui.update { if (it?.phase == AfterLinkUi.Phase.SENT) null else it }

    private suspend fun partnerName(): String {
        val partner = runCatching { profile.observePartnerId().first() }.getOrNull() ?: return "연인"
        return runCatching { profile.observeUsers().first().firstOrNull { it.id == partner }?.nickname }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "연인"
    }

    private companion object {
        const val POLL_MS = 10_000L

        /**
         * 모달을 띄운 커플. 세션(PairingSession)은 앱 전체에 하나라 화면이 새로 만들어져도 연결됨을 계속 돌려준다 —
         * 프로세스 안에서 한 번만 띄우고 한 번만 기록한다.
         */
        var handled: String? = null
    }
}

/**
 * 셸(홈 위)에 놓는다: 앱이 앞에 있는 동안 상대 수락을 확인하고, 연결되면 모달 · 보내는 중 화면 · 토스트를 덮어 그린다.
 */
@Composable
fun LinkWatcher(viewModel: LinkWatchViewModel = org.koin.compose.viewmodel.koinViewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.watch() } }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val shown = ui ?: return
    when (shown.phase) {
        AfterLinkUi.Phase.MODAL -> LinkedDialog(shown.partnerName, shown.total, viewModel::start)
        AfterLinkUi.Phase.SENDING -> SendingProgress(shown.total, shown.left, shown.startedMs)
        AfterLinkUi.Phase.SENT -> {
            LaunchedEffect(shown) {
                delay(2_500)
                viewModel.toastShown()
            }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = KkSpacing.xl, end = KkSpacing.xl, bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
                KkToast("${shown.total.summary()}을 보냈어요", type = KkToastType.Success)
            }
        }
    }
}

/** 연결 완료 · 연결 모달(411:8607): 제목 · 혼자 쓴 기록 한 줄 · "시작하기". */
@Composable
internal fun LinkedDialog(partnerName: String, outbox: Outbox, onStart: () -> Unit) {
    val c = KkTheme.colors
    KkDialogHost(onDismiss = onStart) {
        val shape = RoundedCornerShape(KkRadius.xl)
        Column(
            Modifier.width(312.dp).kkElevation(KkElevation.High, shape).clip(shape).background(c.surface).padding(KkSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            Text("${partnerName.withRang()} 연결됐어요", style = KkTheme.type.title, color = c.ink)
            outbox.linkedBody()?.let { Text(it, style = KkTheme.type.body, color = c.sub) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                KkButton("시작하기", onStart, size = KkButtonSize.M)
            }
        }
    }
}

/**
 * 연결 완료 · 편지 보내는 중(411:8617): 구름 배지 · "편지 23통 · 그림 4장 보내는 중" · 남은 시간 · 진행 길 · 단계 줄.
 * [total] 은 처음 센 것, [left] 는 아직 못 보낸 것.
 */
@Composable
internal fun SendingProgress(total: Outbox, left: Outbox, startedMs: Long) {
    val c = KkTheme.colors
    val all = (total.letters + total.drawings).coerceAtLeast(1)
    val leftCount = (left.letters + left.drawings).coerceAtMost(all)
    val progress = 1f - leftCount.toFloat() / all
    val elapsed = (Clock.System.now().toEpochMilliseconds() - startedMs).coerceAtLeast(1)
    val remaining = when {
        leftCount == 0 -> "다 보냈어요"
        progress <= 0f -> "준비 중"
        else -> "약 ${maxOf(1L, (elapsed * (1 - progress) / progress / 60_000).toLong() + 1)}분"
    }
    Column(
        Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = KkSpacing.xxl).padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) {
            KkIcon(KkIcons.Cloud, tint = c.accent, size = 36.dp)
        }
        Spacer(Modifier.height(KkSpacing.xl))
        Text("${total.summary()} 보내는 중", style = KkTheme.type.title, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(KkSpacing.md))
        Text(remaining, style = KkTheme.type.body, color = c.sub, textAlign = TextAlign.Center)
        Spacer(Modifier.height(42.dp))
        ProgressPath(progress)
        Spacer(Modifier.height(KkSpacing.sm))
        Row(Modifier.fillMaxWidth()) {
            Text(
                listOfNotNull(
                    "편지 ${total.letters - left.letters.coerceAtMost(total.letters)}/${total.letters}".takeIf { total.letters > 0 },
                    "그림 ${total.drawings - left.drawings.coerceAtMost(total.drawings)}/${total.drawings}".takeIf { total.drawings > 0 },
                ).joinToString(" · "),
                style = KkTheme.type.caption, color = c.sub, modifier = Modifier.weight(1f),
            )
            Text("${(progress * 100).toInt()}%", style = KkTheme.type.caption, color = c.accent)
        }
        Spacer(Modifier.height(38.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (total.letters > 0) Step(left.letters == 0, "편지 ${total.letters}통")
            if (total.drawings > 0) Step(left.drawings == 0, "그림 ${total.drawings}장")
        }
    }
}

@Composable
private fun Step(done: Boolean, label: String) {
    val c = KkTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        KkIcon(if (done) KkIcons.Check else KkIcons.Cloud, tint = if (done) c.success else c.sub, size = 20.dp)
        Text(if (done) "$label 보냄" else "$label 보내는 중", style = KkTheme.type.body, color = if (done) c.ink else c.sub)
    }
}

/** 진행 길(Service Progress · Path): 지난 길은 accent 실선, 남은 길은 sub 점선, 지금 위치에 점. */
@Composable
private fun ProgressPath(progress: Float) {
    val c = KkTheme.colors
    val dash = with(LocalDensity.current) { 3.dp.toPx() }
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val at = size.width * progress.coerceIn(0f, 1f)
        drawLine(c.sub, Offset(at, y), Offset(size.width, y), strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
        drawLine(c.accent, Offset(0f, y), Offset(at, y), strokeWidth = 3.dp.toPx())
        drawCircle(c.accent, radius = 6.dp.toPx(), center = Offset(at, y))
    }
}
