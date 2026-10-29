package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth as fillWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawingui.ReplayCanvas
import com.sadturtleman.flowershoes.core.drawingui.ReplayState
import com.sadturtleman.flowershoes.core.drawingui.rememberReplayState
import com.sadturtleman.flowershoes.core.model.LetterBlock

/**
 * 그림 한 장 편지(Figma 받은 그림 / 재생 중 · 다 봄): 그리는 과정을 재생하고, 재생 막대로 멈추기 · 속도 · 다시 보기.
 * 처음 보는 그림은 바로 재생한다. 다 보면 함께 온 한 줄과 고마워 · 답장 그리기.
 */
@Composable
fun DrawingLetterScreen(open: OpenLetter, onIntent: (LetterIntent) -> Unit, onReplyDrawing: () -> Unit) {
    val c = KkTheme.colors
    val ref = open.letter.blocks.filterIsInstance<LetterBlock.DrawingRef>().first()
    val doc = open.drawings[ref.drawingId]
    val replay = doc?.let { rememberReplayState(it, autoPlay = ref.drawingId in open.firstView) }
    // 읽는 화면은 탭 바 없이 전체(Figma) — 아래 버튼이 제스처 바에 걸리지 않게.
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        KkTopBar(
            open.drawingTitle, onLeading = { onIntent(LetterIntent.Close) }, leading = KkTopBarLeading.Close,
            modifier = Modifier.padding(horizontal = KkSpacing.lg),
            trailingText = if (open.mine) "삭제" else null, onTrailing = { onIntent(LetterIntent.Delete(open.letter.id)) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.lg, vertical = KkSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            if (doc == null || replay == null) {
                Text("그림을 받는 중이에요", style = KkTheme.type.body, color = c.sub, modifier = Modifier.padding(KkSpacing.xxxl))
                return@Column
            }
            Text(
                "${open.sentAt}  · 그리는 과정 ${(replay.durationMs + 999) / 1000}초",
                style = KkTheme.type.caption, color = c.sub,
            )
            val shape = RoundedCornerShape(KkRadius.xl)
            Box(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Mid, shape).clip(shape).background(Color.White).padding(KkSpacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                ReplayCanvas(doc, replay, onFinished = { onIntent(LetterIntent.DrawingSeen(ref.drawingId)) })
                if (replay.playing) {
                    Text(
                        "탭하면 바로 완성돼요", style = KkTheme.type.caption, color = c.canvas,
                        modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(KkRadius.full))
                            .background(c.ink.copy(alpha = 0.85f)).padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
                    )
                }
            }
            ReplayBar(replay)
            if (replay.finished) {
                open.letter.title?.takeIf { it.isNotBlank() }?.let {
                    Text("“$it”", style = KkTheme.type.labelBold, color = c.ink, textAlign = TextAlign.Center)
                }
            }
        }
        // 재생 중에는 그림만 — 다 보면 답할 수 있다.
        if (!open.mine && replay?.playing != true) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = KkSpacing.lg, vertical = KkSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                KkSecondaryButton(
                    if (open.thanked) "고마워를 보냈어요" else "고마워",
                    onClick = { onIntent(LetterIntent.Thanks) }, modifier = Modifier.weight(1f), enabled = !open.thanked,
                )
                KkPrimaryButton("답장 그리기", onClick = onReplyDrawing, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 재생 막대(Replay Bar): 재생/멈춤(끝나면 다시) · 진행 · 시간 · 속도. */
@Composable
private fun ReplayBar(state: ReplayState) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.full)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            state.playing -> KkIcons.Pause
            state.finished -> KkIcons.Replay
            else -> KkIcons.Play
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.accent).clickable(role = Role.Button) { state.toggle() }
                .semantics { contentDescription = if (state.playing) "멈추기" else if (state.finished) "다시 보기" else "재생" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(icon, tint = c.surface, size = 20.dp) }
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
            Box(Modifier.fillMaxHeight().fillWidth(state.progress.coerceIn(0f, 1f)).clip(RoundedCornerShape(2.dp)).background(c.accent))
        }
        if (state.finished) {
            Text("다시 보기", style = KkTheme.type.label, color = c.accent, modifier = Modifier.clickable(role = Role.Button) { state.restart() })
        } else {
            Text("${clock(state.elapsedMs)} / ${clock(state.durationMs)}", style = KkTheme.type.caption, color = c.sub)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.line)
                .clickable(role = Role.Button) { state.speed = when (state.speed) { 1f -> 2f; 2f -> 0.5f; else -> 1f } }
                .semantics { contentDescription = "속도 ${state.speed}배" },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (state.speed == 0.5f) "0.5x" else "${state.speed.toInt()}x", style = KkTheme.type.label, color = c.sub)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms + 500) / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
