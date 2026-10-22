package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.designsystem.KkCard
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.drawingui.ReplayPlayer
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/** 편지함. 편지를 열면 같은 화면에서 읽기로 바뀐다. */
@Composable
fun LetterInboxRoute(
    onCompose: () -> Unit,
    onDrawOnly: () -> Unit,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = state.open
    if (open != null) {
        LetterReadScreen(open, viewModel::onIntent)
    } else {
        LetterInboxScreen(state, viewModel::onIntent, onCompose, onDrawOnly)
    }
}

@Composable
fun LetterInboxScreen(state: LetterState, onIntent: (LetterIntent) -> Unit, onCompose: () -> Unit, onDrawOnly: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Text("편지함", style = KkTheme.type.titleL, color = c.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Chip("전체", !state.filterDrawings) { onIntent(LetterIntent.Filter(false)) }
            Chip("그림 있는 편지", state.filterDrawings) { onIntent(LetterIntent.Filter(true)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            KkPrimaryButton("편지 쓰기", onClick = onCompose, modifier = Modifier.weight(1f))
            KkSecondaryButton("그림만 보내기", onClick = onDrawOnly, modifier = Modifier.weight(1f))
        }
        if (state.visibleInbox.isEmpty()) {
            Text("아직 주고받은 편지가 없어요", style = KkTheme.type.bodyM, color = c.textTertiary, modifier = Modifier.padding(top = KkSpacing.xl))
        }
        state.visibleInbox.forEach { card ->
            KkCard(onClick = { onIntent(LetterIntent.Open(card.letter.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (card.mine) "보낸 편지" else "받은 편지", style = KkTheme.type.labelS, color = if (card.mine) c.textTertiary else c.textBrand)
                    Spacer(Modifier.weight(1f))
                    Text(card.letter.visibleAt.dateText(), style = KkTheme.type.caption, color = c.textTertiary)
                }
                Spacer(Modifier.padding(top = KkSpacing.xs))
                if (card.firstLine.isBlank()) {
                    Text("그림 ${card.drawingCount}장", style = KkTheme.type.titleS, color = c.textPrimary)
                } else {
                    Text(card.firstLine, style = KkTheme.type.titleS, color = c.textPrimary, maxLines = 1)
                    if (card.drawingCount > 0) Text("그림 ${card.drawingCount}장 포함", style = KkTheme.type.bodyS, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
fun LetterReadScreen(open: OpenLetter, onIntent: (LetterIntent) -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row {
            Text("← 편지함", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable { onIntent(LetterIntent.Close) })
            Spacer(Modifier.weight(1f))
            Text("삭제", style = KkTheme.type.labelM, color = c.statusDanger, modifier = Modifier.clickable { onIntent(LetterIntent.Delete(open.letter.id)) })
        }
        Text(open.letter.visibleAt.dateText(), style = KkTheme.type.caption, color = c.textTertiary)
        open.letter.blocks.forEach { block ->
            when (block) {
                is LetterBlock.Text -> Text(block.text, style = KkTheme.type.bodyL, color = c.textPrimary)
                is LetterBlock.DrawingRef -> {
                    val doc = open.drawings[block.drawingId]
                    if (doc == null) {
                        Text("그림을 받는 중이에요", style = KkTheme.type.bodyS, color = c.textTertiary)
                    } else {
                        ReplayPlayer(
                            doc,
                            autoPlay = block.drawingId in open.firstView,
                            onFinished = { onIntent(LetterIntent.DrawingSeen(block.drawingId)) },
                        )
                    }
                }
                is LetterBlock.Photo -> Text("사진", style = KkTheme.type.bodyS, color = c.textTertiary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterComposeRoute(
    onAddDrawing: () -> Unit,
    onClose: () -> Unit,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.draft
    val c = KkTheme.colors
    var picking by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("닫기", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable(onClick = onClose).padding(KkSpacing.sm))
            Spacer(Modifier.weight(1f))
            Text("편지 쓰기", style = KkTheme.type.titleS, color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.padding(KkSpacing.xl))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            BasicTextField(
                value = d.text,
                onValueChange = { viewModel.onIntent(LetterIntent.EditText(it)) },
                textStyle = KkTheme.type.bodyL.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.bgBrand),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(KkRadius.lg))
                    .background(c.bgSurface).border(1.dp, c.borderDefault, RoundedCornerShape(KkRadius.lg)).padding(KkSpacing.lg),
                decorationBox = { inner ->
                    if (d.text.isEmpty()) Text("마음을 적어 보세요", style = KkTheme.type.bodyL, color = c.textTertiary)
                    inner()
                },
            )
            d.drawings.forEach { meta ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("그림 ${meta.orderInLetter + 1} · ${(meta.replayDurationMs + 999) / 1000}초", style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text("빼기", style = KkTheme.type.labelM, color = c.statusDanger, modifier = Modifier.clickable { viewModel.onIntent(LetterIntent.RemoveDrawing(meta.id)) })
                }
            }
            if (d.canAddDrawing) KkSecondaryButton("그림 넣기 (${d.drawings.size}/3)", onClick = onAddDrawing)
            KkSecondaryButton(d.revealOn?.let { "예약 공개 ${it.year}.${it.month.ordinal + 1}.${it.day} 오전 9시" } ?: "예약 메시지로 보내기", onClick = { picking = true })
            if (d.revealOn != null) {
                Text("예약 취소", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable { viewModel.onIntent(LetterIntent.Schedule(null)) })
                Text("예약한 날 전에는 상대 화면 어디에도 보이지 않아요.", style = KkTheme.type.caption, color = c.textTertiary)
            }
        }
        KkPrimaryButton(if (d.sending) "보내는 중…" else "보내기", onClick = {
            viewModel.onIntent(LetterIntent.Send)
            onClose()
        }, enabled = d.canSend)
    }
    if (picking) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.onIntent(LetterIntent.Schedule(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)) }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Text(
        label, style = KkTheme.type.labelM, color = if (selected) c.textOnBrand else c.textPrimary,
        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(if (selected) c.bgBrand else c.bgSubtle)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun Instant.dateText(): String {
    val d = toLocalDateTime(SEOUL)
    return "${d.year}.${d.month.ordinal + 1}.${d.day}"
}
