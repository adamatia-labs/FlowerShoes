package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawingui.ReplayCanvas
import com.sadturtleman.flowershoes.core.drawingui.ReplayState
import com.sadturtleman.flowershoes.core.drawingui.rememberReplayState
import com.sadturtleman.flowershoes.core.model.LetterBlock

/**
 * 그림 한 장 편지(Figma 받은 그림 / 재생 중 · 재생 끝): 그린 순서대로 다시 그리고, 아래 재생 줄로 멈추기 · 다시 보기.
 * 처음 보는 그림은 바로 재생한다. 다 보면 함께 온 한 줄과 다시 보기 · 고마워 · 답장 그리기.
 */
@Composable
fun DrawingLetterScreen(open: OpenLetter, onIntent: (LetterIntent) -> Unit, onReplyDrawing: () -> Unit) {
    val c = KkTheme.colors
    val ref = open.letter.blocks.filterIsInstance<LetterBlock.DrawingRef>().first()
    val doc = open.drawings[ref.drawingId]
    val replay = doc?.let { rememberReplayState(it, autoPlay = ref.drawingId in open.firstView) }
    // 읽는 화면은 탭 바 없이 전체(Figma) — 아래 버튼이 제스처 바에 걸리지 않게.
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(
            open.drawingTitle, onLeading = { onIntent(LetterIntent.Close) }, subtitle = open.sentAt.ifBlank { null },
            modifier = Modifier.padding(horizontal = KkSpacing.lg),
            trailingText = if (open.mine) "삭제" else null, onTrailing = { onIntent(LetterIntent.Delete(open.letter.id)) },
        )
        Box(Modifier.weight(1f).fillMaxWidth().background(c.surface).padding(KkSpacing.lg), contentAlignment = Alignment.Center) {
            if (doc == null || replay == null) {
                Text("그림을 받는 중이에요", style = KkTheme.type.body, color = c.sub)
            } else {
                val shape = RoundedCornerShape(KkRadius.md)
                Box(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(Color.White)) {
                    ReplayCanvas(doc, replay, onFinished = { onIntent(LetterIntent.DrawingSeen(ref.drawingId)) })
                }
            }
        }
        if (replay != null) {
            ReplayBar(replay, Modifier.padding(start = 20.dp, end = 18.dp, top = KkSpacing.lg))
            if (replay.finished) {
                open.letter.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "“$it”", style = KkTheme.type.labelBold, color = c.ink, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xs),
                    )
                }
            }
        }
        when {
            // 재생 중에는 그림만 — 다 보면 답할 수 있다.
            replay?.playing == true -> Text(
                "그린 순서대로 다시 그려지는 중", style = KkTheme.type.caption, color = c.sub, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = KkSpacing.xxxl),
            )
            !open.mine && replay != null -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = KkSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            ) {
                KkButton("답장 그리기", onReplyDrawing, style = KkButtonStyle.Secondary, size = KkButtonSize.M)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                    KkSecondaryButton("다시 보기", onClick = { replay.restart() }, modifier = Modifier.weight(1f))
                    KkPrimaryButton(
                        if (open.thanked) "고마워를 보냈어요" else "고마워",
                        onClick = { onIntent(LetterIntent.Thanks) }, modifier = Modifier.weight(1f), enabled = !open.thanked,
                    )
                }
            }
            else -> Box(Modifier.height(KkSpacing.xxxl))
        }
    }
}

/** 재생 줄(Figma replay): 멈추기 · 이어 보기 · 다시(24) + 4pt 진행 줄과 14pt 손잡이 + 시간. */
@Composable
private fun ReplayBar(state: ReplayState, modifier: Modifier) {
    val c = KkTheme.colors
    Row(
        modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            state.playing -> KkIcons.Pause
            state.finished -> KkIcons.Replay
            else -> KkIcons.Play
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { state.toggle() }
                .semantics { contentDescription = if (state.playing) "멈추기" else if (state.finished) "다시 보기" else "재생" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(icon, tint = c.ink) }
        BoxWithConstraints(Modifier.weight(1f).height(14.dp), contentAlignment = Alignment.CenterStart) {
            val p = state.progress.coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(c.accent))
            }
            Box(
                Modifier.offset(x = (maxWidth - 14.dp) * p).size(14.dp).clip(CircleShape).background(c.surface).border(2.dp, c.accent, CircleShape),
            )
        }
        Text("${clock(state.elapsedMs)} / ${clock(state.durationMs)}", style = KkTheme.type.caption, color = c.sub)
    }
}

private fun clock(ms: Long): String {
    val s = (ms + 500) / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
