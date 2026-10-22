package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkCard
import com.sadturtleman.flowershoes.core.designsystem.KkColors
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTextField
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarRoute(viewModel: CalendarViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarScreen(state, viewModel::onIntent)
}

@Composable
fun CalendarScreen(state: CalendarState, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.month.year}년 ${state.month.month.ordinal + 1}월", style = KkTheme.type.titleL, color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onIntent(CalendarIntent.ShiftMonth(-1)) }) { Text("이전", color = c.textSecondary) }
            TextButton(onClick = { onIntent(CalendarIntent.ShiftMonth(1)) }) { Text("다음", color = c.textSecondary) }
        }
        KkCard(padding = androidx.compose.foundation.layout.PaddingValues(KkSpacing.md)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, Modifier.weight(1f), style = KkTheme.type.labelS, color = c.textTertiary, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(KkSpacing.xs))
            val rows = if (state.expanded) state.cells.chunked(7) else listOf(state.week)
            rows.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day -> DayCell(day, state, onIntent, Modifier.weight(1f)) }
                }
            }
            Text(
                if (state.expanded) "접기 ▲" else "펼치기 ▼",
                style = KkTheme.type.labelS, color = c.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onIntent(CalendarIntent.ToggleExpanded) }
                    .padding(vertical = KkSpacing.sm),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.selected.month.ordinal + 1}월 ${state.selected.day}일", style = KkTheme.type.titleS, color = c.textPrimary)
            Spacer(Modifier.weight(1f))
            Text("이번 달 휴가 · 외박 ${state.leaveDaysThisMonth}일", style = KkTheme.type.bodyS, color = c.textMilitary)
        }
        val today = state.eventsOn(state.selected)
        if (today.isEmpty()) {
            Text("일정이 없어요", style = KkTheme.type.bodyM, color = c.textTertiary)
        } else {
            today.forEach { e -> EventRow(e) { onIntent(CalendarIntent.Edit(e)) } }
        }
        KkPrimaryButton("일정 추가", onClick = { onIntent(CalendarIntent.NewEvent(state.selected)) })
    }
    state.draft?.let { EventEditor(it, onIntent) }
}

@Composable
private fun DayCell(day: LocalDate, state: CalendarState, onIntent: (CalendarIntent) -> Unit, modifier: Modifier) {
    val c = KkTheme.colors
    val inMonth = day.month == state.month.month
    val selected = day == state.selected
    val events = state.eventsOn(day)
    Column(
        modifier.aspectRatio(0.9f).clip(RoundedCornerShape(KkRadius.sm))
            .background(if (selected) c.bgBrandSubtle else Color.Transparent)
            .clickable(role = Role.Button) { onIntent(CalendarIntent.Select(day)) }
            .semantics { contentDescription = "${day.month.ordinal + 1}월 ${day.day}일, 일정 ${events.size}개" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${day.day}",
            style = KkTheme.type.bodyS,
            color = when {
                selected -> c.textBrand
                !inMonth -> c.textTertiary.copy(alpha = 0.5f)
                else -> c.textPrimary
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
            events.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(c.eventColor(it.type))) }
        }
    }
}

@Composable
private fun EventRow(e: Event, onClick: () -> Unit) {
    val c = KkTheme.colors
    KkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(c.eventColor(e.type)))
            Spacer(Modifier.width(KkSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(e.title.ifBlank { e.type.label() }, style = KkTheme.type.titleS, color = c.textPrimary)
                val range = if (e.start.date == e.end.date) e.start.date.short() else "${e.start.date.short()} – ${e.end.date.short()}"
                Text("${e.type.label()} · $range", style = KkTheme.type.bodyS, color = c.textSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(draft: EventDraft, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = 시작, false = 끝
    ModalBottomSheet(onDismissRequest = { onIntent(CalendarIntent.CloseDraft) }, containerColor = c.bgSurface) {
        Column(Modifier.padding(horizontal = KkSpacing.screen).padding(bottom = KkSpacing.xxl), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            Text(if (draft.id == null) "일정 추가" else "일정 고치기", style = KkTheme.type.titleM, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                EventType.entries.forEach { t ->
                    val on = draft.type == t
                    Text(
                        t.label(),
                        style = KkTheme.type.labelM,
                        color = if (on) c.textOnBrand else c.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full))
                            .background(if (on) c.eventColor(t) else c.bgSubtle)
                            .clickable(role = Role.RadioButton) { onIntent(CalendarIntent.ChangeDraft(draft.copy(type = t))) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            KkTextField(draft.title, { onIntent(CalendarIntent.ChangeDraft(draft.copy(title = it))) }, placeholder = "제목(선택)")
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkSecondaryButton("시작 ${draft.start.short()}", onClick = { picking = true }, modifier = Modifier.weight(1f))
                KkSecondaryButton("끝 ${draft.end.short()}", onClick = { picking = false }, modifier = Modifier.weight(1f))
            }
            if (!draft.valid) Text("끝나는 날이 시작보다 빨라요", style = KkTheme.type.bodyS, color = c.statusDanger)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("전날 20:00에 알려 주기", style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
                Switch(draft.alarm, onCheckedChange = { onIntent(CalendarIntent.ChangeDraft(draft.copy(alarm = it))) })
            }
            KkPrimaryButton("저장", onClick = { onIntent(CalendarIntent.SaveDraft) }, enabled = draft.valid)
            if (draft.id != null) {
                Text(
                    "삭제", style = KkTheme.type.labelM, color = c.statusDanger, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onIntent(CalendarIntent.DeleteDraft) }.padding(KkSpacing.md),
                )
            }
        }
    }
    picking?.let { isStart ->
        val initial = if (isStart) draft.start else draft.end
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                        val next = if (isStart) draft.copy(start = d, end = maxOf(d, draft.end)) else draft.copy(end = d)
                        onIntent(CalendarIntent.ChangeDraft(next))
                    }
                    picking = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("취소") } },
        ) { DatePicker(state = pickerState) }
    }
}

internal fun KkColors.eventColor(type: EventType): Color = when (type) {
    EventType.VACATION -> eventVacation
    EventType.OVERNIGHT -> eventOvernight
    EventType.VISIT -> eventVisit
    EventType.TRAINING -> eventTraining
    EventType.DUTY -> eventDuty
}

internal fun EventType.label() = when (this) {
    EventType.VACATION -> "휴가"
    EventType.OVERNIGHT -> "외박"
    EventType.VISIT -> "면회"
    EventType.TRAINING -> "훈련"
    EventType.DUTY -> "당직"
}

private fun LocalDate.short() = "${month.ordinal + 1}.${day}"
