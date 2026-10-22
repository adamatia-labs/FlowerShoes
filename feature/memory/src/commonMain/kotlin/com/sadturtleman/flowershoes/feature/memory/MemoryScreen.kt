@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkEmptyState
import com.sadturtleman.flowershoes.core.designsystem.KkHeartIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.drawingui.ReplayCanvas
import com.sadturtleman.flowershoes.core.drawingui.rememberReplayState
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * 추억(Figma 5 · 추억 / 빈 추억 / 그림 상세).
 * [saveImage] · [shareImage] 는 플랫폼(사진첩 저장 · 공유 시트). 없으면 그 버튼이 꺼진다.
 */
@Composable
fun MemoryRoute(
    onOpenLetter: (String) -> Unit,
    onRecordMeeting: () -> Unit = {},
    saveImage: (suspend (ByteArray) -> Boolean)? = null,
    shareImage: ((ByteArray) -> Unit)? = null,
    viewModel: MemoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    // 저장 · 공유 중 무엇을 하려고 PNG 를 만들었는지.
    var exportFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.notices.collect { notice = it } }
    LaunchedEffect(viewModel) {
        viewModel.exports.collect { png ->
            when (exportFor) {
                "share" -> shareImage?.invoke(png)
                else -> notice = if (saveImage?.invoke(png) == true) "사진첩에 저장했어요" else "저장하지 못했어요"
            }
        }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }
    Box(Modifier.fillMaxSize()) {
        val detail = state.detail
        if (detail != null) {
            DrawingDetailScreen(
                detail,
                onBack = { viewModel.onIntent(MemoryIntent.CloseDrawing) },
                onShare = shareImage?.let { { exportFor = "share"; viewModel.onIntent(MemoryIntent.Export) } },
                onSave = saveImage?.let { { exportFor = "save"; viewModel.onIntent(MemoryIntent.Export) } },
                onPin = { viewModel.onIntent(MemoryIntent.PinToWidget) },
            )
        } else {
            MemoryScreen(state, viewModel::onIntent, onOpenLetter, onRecordMeeting)
        }
        notice?.let { KkToast(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg)) }
    }
}

@Composable
fun MemoryScreen(state: MemoryState, onIntent: (MemoryIntent) -> Unit, onOpenLetter: (String) -> Unit, onRecordMeeting: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("추억", style = KkTheme.type.titleL, color = c.textPrimary, modifier = Modifier.weight(1f))
            state.stats.daysTogether?.let { Text("D+$it", style = KkTheme.type.labelM, color = c.textBrand) }
        }
        KkSegmentedControl(MemoryFilter.entries.map { it.label }, state.filter.ordinal, { onIntent(MemoryIntent.Filter(MemoryFilter.entries[it])) })
        Summary(state.stats)
        if (state.empty) {
            KkEmptyState(
                title = "첫 추억을 만들어 보세요",
                description = "그림·편지를 주고받거나 면회·휴가를 기록하면 여기에 차곡차곡 쌓여요",
                icon = KkIcons.Album,
                action = "만남 기록하기",
                onAction = onRecordMeeting,
            )
            return@Column
        }
        var lastMonth: Pair<Int, Int>? = null
        state.visible.forEach { entry ->
            val month = entry.date.year to entry.date.month.ordinal + 1
            if (month != lastMonth) {
                Text("${month.first}년 ${month.second}월", style = KkTheme.type.labelM, color = c.textTertiary)
                lastMonth = month
            }
            Entry(entry, onIntent, onOpenLetter)
        }
        Spacer(Modifier.height(KkSpacing.lg))
    }
}

/** 요약(Summary): 함께 · 그림 · 편지 · 만남. */
@Composable
private fun Summary(stats: MemoryStats) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgBrandSubtle)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("${stats.daysTogether ?: 0}일", "함께")
        Stat("${stats.drawings}", "그림")
        Stat("${stats.letters}", "편지")
        Stat("${stats.meets}", "만남")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val c = KkTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = KkTheme.type.titleS, color = c.textPrimary)
        Text(label, style = KkTheme.type.caption, color = c.textBrand)
    }
}

/** 타임라인 한 칸: 왼쪽 레일(날짜 · 점) + 카드. */
@Composable
private fun Entry(entry: MemoryEntry, onIntent: (MemoryIntent) -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    val rose = entry is MemoryEntry.Drawing || entry is MemoryEntry.Meet
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
            Text("${entry.date.month.ordinal + 1}.${entry.date.day}", style = KkTheme.type.caption, color = c.textTertiary)
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (rose) c.heart else c.bgMilitary))
        }
        val shape = RoundedCornerShape(KkRadius.lg)
        val click: (() -> Unit)? = when (entry) {
            is MemoryEntry.Drawing -> { { onIntent(MemoryIntent.OpenDrawing(entry.letterId)) } }
            is MemoryEntry.Letter -> { { onOpenLetter(entry.letterId) } }
            else -> null
        }
        Column(
            Modifier.weight(1f).kkElevation(KkElevation.Mid, shape).clip(shape).background(c.bgSurface)
                .then(if (click != null) Modifier.clickable(role = Role.Button, onClick = click) else Modifier)
                .padding(KkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            when (entry) {
                is MemoryEntry.Drawing -> Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgCanvas), contentAlignment = Alignment.Center) {
                        entry.thumb?.let { Box(Modifier.size(52.dp)) { DrawingView(it) } } ?: KkIcon(KkIcons.Heart, tint = c.textBrand)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Pill("그림", c.bgBrandSubtle, c.textBrand)
                        Text(entry.title, style = KkTheme.type.labelM, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.meta, style = KkTheme.type.caption, color = c.textTertiary)
                    }
                }
                is MemoryEntry.Letter -> {
                    Pill("편지", c.bgMilitarySubtle, c.textMilitary)
                    Text(entry.title, style = KkTheme.type.labelM, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.preview.isNotBlank()) Text(entry.preview, style = KkTheme.type.bodyS, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                is MemoryEntry.Meet -> {
                    Pill(entry.label, c.bgBrandSubtle, c.textBrand)
                    Text(entry.title, style = KkTheme.type.labelM, color = c.textPrimary)
                }
                is MemoryEntry.Anniversary -> Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Pill("기념일", c.bgMilitary, c.textOnBrand)
                    Text(entry.title, style = KkTheme.type.labelM, color = c.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, style = KkTheme.type.labelS, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(bg).padding(horizontal = KkSpacing.xs, vertical = KkSpacing.xxs),
    )
}

// ── 그림 상세 ─────────────────────────────────────────────────────────────

/** 추억 / 그림 상세: 완성본 + "그리는 과정 보기", 제목 · 누가 언제, 답장, 저장 · 위젯에 다시 띄우기. */
@Composable
private fun DrawingDetailScreen(
    detail: DrawingDetail,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onPin: () -> Unit,
) {
    val c = KkTheme.colors
    val replay = rememberReplayState(detail.doc, autoPlay = false)
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        KkTopBar(
            detail.dateTitle, onLeading = onBack, modifier = Modifier.padding(horizontal = KkSpacing.xl),
            trailingText = if (onShare != null) "공유" else null, onTrailing = { onShare?.invoke() },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            val shape = RoundedCornerShape(KkRadius.xl)
            Box(Modifier.fillMaxWidth().kkElevation(KkElevation.Mid, shape).clip(shape).background(Color.White).padding(KkSpacing.xxl)) {
                ReplayCanvas(detail.doc, replay)
                if (!replay.playing) {
                    Row(
                        Modifier.align(Alignment.BottomStart).clip(RoundedCornerShape(KkRadius.full)).background(c.bgInverse.copy(alpha = 0.88f))
                            .clickable(role = Role.Button) { replay.restart() }
                            .padding(horizontal = KkSpacing.md, vertical = KkSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KkIcon(KkIcons.Play, tint = c.textInverse, size = 16.dp)
                        Text("그리는 과정 보기 · ${(replay.durationMs + 999) / 1000}초", style = KkTheme.type.labelS, color = c.textInverse)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(detail.title, style = KkTheme.type.titleM, color = c.textPrimary)
                Text(detail.meta, style = KkTheme.type.bodyS, color = c.textSecondary)
            }
            detail.reply?.let { r ->
                val shape2 = RoundedCornerShape(KkRadius.lg)
                Row(
                    Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape2).clip(shape2).background(c.bgMilitarySubtle).padding(KkSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.bgSurface), contentAlignment = Alignment.Center) {
                        Text(r.initial, style = KkTheme.type.labelM, color = c.textMilitary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.who, style = KkTheme.type.caption, color = c.textMilitary)
                            KkHeartIcon(color = c.textMilitary, size = 13.dp)
                        }
                        Text(r.text, style = KkTheme.type.bodyM, color = c.textPrimary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkButton("저장", { onSave?.invoke() }, Modifier.weight(1f), style = KkButtonStyle.Ghost, enabled = onSave != null)
                if (!detail.mine) KkSecondaryButton("위젯에 다시 띄우기", onClick = onPin, modifier = Modifier.weight(1f))
            }
        }
    }
}
