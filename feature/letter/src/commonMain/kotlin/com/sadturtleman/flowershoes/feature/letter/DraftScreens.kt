package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkMenu
import com.sadturtleman.flowershoes.core.designsystem.KkMenuItem
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import org.koin.compose.viewmodel.koinViewModel

/** 편지함 맨 위 임시저장 줄(Figma Draft Row): "쓰던 편지 N통" · 마지막으로 고친 때. 누르면 쓰던 편지 목록. */
@Composable
internal fun DraftRow(drafts: List<DraftSummary>, onClick: () -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Note, tint = c.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("쓰던 편지 ${drafts.size}통", style = KkTheme.type.labelBold, color = c.ink)
            Text(drafts.first().whenLabel, style = KkTheme.type.caption, color = c.sub)
        }
        KkIcon(KkIcons.ChevronRight, tint = c.sub, size = 20.dp)
    }
}

/** 쓰던 편지 목록(Figma 4 · 쓰던 편지 목록): 줄마다 첫 줄 · 고친 때 · 편지지, ⋯ 로 이어 쓰기 · 지우기. */
@Composable
fun LetterDraftsRoute(onBack: () -> Unit, onResume: (String) -> Unit, viewModel: LetterViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LetterDraftsScreen(state.drafts, onBack, onResume, onDelete = { viewModel.onIntent(LetterIntent.DeleteDraft(it)) })
}

@Composable
fun LetterDraftsScreen(drafts: List<DraftSummary>, onBack: () -> Unit, onResume: (String) -> Unit, onDelete: (String) -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar("쓰던 편지", onLeading = onBack, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            items(drafts, key = { it.id }) { d -> DraftListItem(d, onResume, onDelete) }
        }
    }
}

@Composable
private fun DraftListItem(d: DraftSummary, onResume: (String) -> Unit, onDelete: (String) -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape)
            .clickable(role = Role.Button) { onResume(d.id) }
            .padding(start = KkSpacing.lg, end = KkSpacing.xs, top = KkSpacing.md, bottom = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DraftText(d, "${d.whenLabel} · ${d.paper.label}", Modifier.weight(1f))
        Box {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }
                    .semantics { contentDescription = "더보기" },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.More, tint = c.ink) }
            KkMenu(
                menu, { menu = false },
                listOf(KkMenuItem("이어 쓰기", KkIcons.Note) { onResume(d.id) }, KkMenuItem("지우기", KkIcons.Brush) { onDelete(d.id) }),
            )
        }
    }
}

/** 쓰던 편지 첫 줄(손글씨) + 아래 한 줄. */
@Composable
private fun DraftText(d: DraftSummary, meta: String, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
        Text(d.firstLine, style = KkTheme.type.handLetter, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, style = KkTheme.type.caption, color = c.sub, maxLines = 1)
    }
}

/**
 * 편지 쓰기를 열 때 쓰던 편지가 있으면(Figma 편지 쓰기 · 이어 쓰기 시트): 한 통이면 첫 줄 + 이어 쓰기 / 새로 쓰기,
 * 여러 통이면 눌러서 고르는 목록 + 새로 쓰기.
 */
@Composable
internal fun ResumeDraftSheet(drafts: List<DraftSummary>, onResume: (String) -> Unit, onNew: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    KkModalSheet(onNew) {
        KkSheetHeader("쓰던 편지를 이어 쓸까요?")
        val single = drafts.size == 1
        // 여러 통이어도 시트가 스크롤한다.
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            drafts.forEach { d ->
                DraftText(
                    d, d.whenLabel,
                    Modifier.fillMaxWidth().clip(shape).background(c.canvas).border(1.dp, c.line, shape)
                        .then(if (single) Modifier else Modifier.clickable(role = Role.Button) { onResume(d.id) })
                        .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
                )
            }
        }
        if (single) KkPrimaryButton("이어 쓰기", onClick = { onResume(drafts.single().id) }, modifier = Modifier.fillMaxWidth())
        KkSecondaryButton("새로 쓰기", onClick = onNew, modifier = Modifier.fillMaxWidth())
    }
}
