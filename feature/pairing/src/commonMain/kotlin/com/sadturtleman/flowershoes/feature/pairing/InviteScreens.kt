package com.sadturtleman.flowershoes.feature.pairing

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkToastType
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay

/** 초대 링크 하나를 보내는 데 필요한 것. 카카오 피드 템플릿 인자는 [name] · [token], 폴백(OS 공유 시트)은 [url]. */
data class InviteShare(val token: String, val url: String, val name: String)

/**
 * 플랫폼 초대 공유(셸이 넣는다): 카카오톡이 있고 템플릿이 설정돼 있으면 카카오 피드 템플릿, 아니면 OS 공유 시트.
 * 공유 결과는 알 수 없다 — 보내기를 누르면 보낸 것으로 보고 바로 홈으로 간다(#15).
 */
val LocalInviteShare = staticCompositionLocalOf<(InviteShare) -> Unit> { {} }

/** 처음 연결(초대 링크): 상태마다 Figma 1 · 연결 화면 하나. */
@Composable
internal fun ConnectScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit, onSent: () -> Unit) {
    when (val s = ui.state) {
        PairingState.Idle, is PairingState.Waiting -> SendScreen(ui, vm, close, onSent)
        is PairingState.Invited -> InvitedScreen(s.inviterName, accepting = ui.busy, onAccept = { vm.accept(s.token) }, onLater = close)
        is PairingState.Accepting -> InvitedScreen(s.inviterName, accepting = true, onAccept = {}, onLater = close)
        is PairingState.Linked -> {
            val total = ui.sendingTotal
            if (total != null) SendingProgress(total, ui.sendingLeft ?: total, ui.sendStartedMs)
            else InvitedScreen(ui.inviterName, accepting = true, onAccept = {}, onLater = close)
        }
        PairingState.Expired -> Notice(
            "이 초대 링크는 만료됐어요", close,
            primary = "내가 초대 링크 보내기" to vm::open, secondary = "처음으로" to close, busy = ui.busy,
        )
        is PairingState.OwnLink -> {
            val share = LocalInviteShare.current
            Notice("내가 보낸 초대 링크예요", close, primary = "다시 공유하기" to {
                share(InviteShare(s.token, vm.config.url(s.token), ui.myName))
                close()
            })
        }
        PairingState.AlreadyLinked -> Notice("이미 연결돼 있어요", close, primary = "닫기" to close)
        is PairingState.Failed -> Notice("연결하지 못했어요", close, primary = "다시 하기" to vm::open, secondary = "처음으로" to close, caption = s.reason)
    }
}

/** 연인 초대 (보내는 쪽) · 링크 공유 기본 / 링크 복사됨(346:529 · 346:561). 보내면 바로 홈([onSent]). */
@Composable
private fun SendScreen(ui: PairingUi, vm: PairingViewModel, close: () -> Unit, onSent: () -> Unit) {
    val c = KkTheme.colors
    val share = LocalInviteShare.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(0) }
    val waiting = ui.state as? PairingState.Waiting
    val url = waiting?.let { vm.config.url(it.token) }
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            KkTopBar("연인 초대", onLeading = close, modifier = Modifier.padding(horizontal = KkSpacing.lg))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xxl).padding(top = 20.dp),
            ) {
                InviteEnvelope(hand = "꽃신에서 만나자", caption = listOf(ui.myName, "초대장").filter { it.isNotBlank() }.joinToString(" · "), seal = true)
                Spacer(Modifier.height(28.dp))
                Text("링크 하나만 보내면 돼요", style = KkTheme.type.title, color = c.ink)
                Spacer(Modifier.height(KkSpacing.xxl))
                val shape = RoundedCornerShape(KkRadius.lg)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape)
                        .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KkIcon(KkIcons.Link, tint = c.sub, size = 20.dp)
                    Text(
                        when {
                            !ui.available -> "서버 설정 전"
                            url == null -> "링크를 만드는 중"
                            else -> url.removePrefix("https://")
                        },
                        style = KkTheme.type.body, color = if (url == null) c.sub else c.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(KkSpacing.md))
                waiting?.let {
                    Text(expiryCaption(Instant.fromEpochMilliseconds(it.expireAtMs), Clock.System.now()), style = KkTheme.type.caption, color = c.sub)
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.xxl, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KkPrimaryButton(
                    "카카오톡으로 보내기",
                    onClick = {
                        waiting?.let { w ->
                            share(InviteShare(w.token, vm.config.url(w.token), ui.myName))
                            onSent()
                        }
                    },
                    enabled = waiting != null,
                    loading = ui.busy,
                )
                KkButton(
                    "링크 복사",
                    onClick = {
                        url?.let {
                            clipboard.setText(AnnotatedString(it))
                            copied++
                        }
                    },
                    Modifier.fillMaxWidth(),
                    style = KkButtonStyle.Secondary,
                    enabled = url != null,
                )
            }
        }
        if (copied > 0) {
            // 복사도 보낸 것으로 본다: 토스트를 잠깐 보여 준 뒤 홈으로.
            LaunchedEffect(copied) {
                delay(1_500)
                copied = 0
                onSent()
            }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, bottom = 150.dp), contentAlignment = Alignment.BottomCenter) {
                KkToast("링크를 복사했어요", type = KkToastType.Success)
            }
        }
    }
}

/** 초대 받음 (받는 쪽) · 수락 / 연결 중(346:636 · 346:664). 초대 문서에는 이름만 있어 이름만 보여 준다(#15 결정 5). */
@Composable
private fun InvitedScreen(inviterName: String, accepting: Boolean, onAccept: () -> Unit, onLater: () -> Unit) {
    val c = KkTheme.colors
    val name = inviterName.ifBlank { "연인" }
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xxl).padding(top = 76.dp)) {
            InviteEnvelope(hand = "꽃신에서 만나자", caption = "$name · 초대장", seal = true, modifier = Modifier.padding(top = 32.dp))
            Spacer(Modifier.height(50.dp))
            Text("${name.withSubject()} 초대했어요", style = KkTheme.type.handL, color = c.ink)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.xxl, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KkPrimaryButton(if (accepting) "연결하는 중…" else "수락하고 연결하기", onAccept, enabled = !accepting, loading = accepting)
            KkButton("나중에 할게요", onLater, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary)
        }
    }
}

/** 초대 받음 · 링크 만료(346:695)와 같은 모양의 안내: 닫기 · 가운데 편지 아이콘 + 한 줄 · 아래 버튼. */
@Composable
private fun Notice(
    title: String,
    close: () -> Unit,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    caption: String? = null,
    busy: Boolean = false,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar("초대", onLeading = close, leading = KkTopBarLeading.Close, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = KkSpacing.xxl).padding(top = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            KkIcon(KkIcons.Mail, tint = c.sub, size = 56.dp)
            Text(title, style = KkTheme.type.handL, color = c.ink, textAlign = TextAlign.Center)
            caption?.let { Text(it, style = KkTheme.type.caption, color = c.sub, textAlign = TextAlign.Center) }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.xxl, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                KkPrimaryButton(primary.first, primary.second, enabled = !busy, loading = busy)
                secondary?.let { (label, action) -> KkButton(label, action, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary) }
            },
        )
    }
}

/**
 * 초대장 봉투(Envelope Row, 편지함 한 줄 모양): 반듯한 봉투 + 덮개 선 · 손글씨 한 줄 · 보낸 사람 · 기울인 우표 · "새" 봉인.
 * 행은 반듯하게, 기울기는 우표에만(design.md).
 */
@Composable
private fun InviteEnvelope(hand: String, caption: String, seal: Boolean, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Box(modifier.fillMaxWidth().height(100.dp)) {
        Box(
            Modifier.fillMaxSize().background(c.surface).border(1.dp, c.line)
                .drawBehind {
                    // 덮개: 양 끝에서 가운데 아래(40)로 모이는 두 선.
                    val stroke = 1.dp.toPx()
                    val tip = Offset(size.width / 2, 40.dp.toPx())
                    drawLine(c.line, Offset(0f, 0f), tip, strokeWidth = stroke)
                    drawLine(c.line, Offset(size.width, 0f), tip, strokeWidth = stroke)
                },
        ) {
            Column(Modifier.padding(start = 17.dp, top = 35.dp, end = 80.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hand, style = KkTheme.type.handLetter, color = c.ink, maxLines = 1)
                Text(caption, style = KkTheme.type.caption, color = c.sub, maxLines = 1)
            }
            // 우표: 종이 태그색 + 점선 테두리, -3° 기울임.
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 14.dp).rotate(-3f).size(44.dp, 54.dp).background(c.tag)
                    .drawBehind {
                        drawRect(c.line, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                    },
            )
        }
        if (seal) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-11).dp).size(28.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("새", style = KkTheme.type.labelBold, color = c.canvas)
            }
        }
    }
}
