package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkCollapseToggle
import com.sadturtleman.flowershoes.core.designsystem.KkDatePicker
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkListRow
import com.sadturtleman.flowershoes.core.designsystem.KkRowTrailing
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkEmptyState
import com.sadturtleman.flowershoes.core.designsystem.KkEventTag
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.sadturtleman.flowershoes.core.designsystem.KkColors
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CalendarRoute(viewModel: CalendarViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarScreen(state, viewModel::onIntent)
}

/**
 * 캘린더(Figma 3 · 캘린더): 다음 만남 카드 · 한 주 띠(접힘) 또는 달력(펼침) · 범례 · 이번 주 / 고른 날 일정.
 * 일정이 하나도 없으면 빈 화면(Empty State). 달은 달력을 옆으로 밀어 넘긴다.
 */
@Composable
fun CalendarScreen(state: CalendarState, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(if (state.expanded) KkSpacing.sm else KkSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("캘린더", style = KkTheme.type.titleL, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "+ 추가", style = KkTheme.type.labelM, color = c.textBrand,
                modifier = Modifier.clip(RoundedCornerShape(KkRadius.sm))
                    .clickable(role = Role.Button) { onIntent(CalendarIntent.NewEvent(state.selected)) }
                    .padding(vertical = KkSpacing.md, horizontal = KkSpacing.xs),
            )
        }
        state.nextMeet?.let { NextMeet(it, state.today, compact = state.expanded) }
        if (state.expanded) MonthCard(state, onIntent) else WeekStrip(state, onIntent)
        if (!state.empty) Legend()
        KkCollapseToggle(state.expanded, { onIntent(CalendarIntent.ToggleExpanded) }, Modifier.fillMaxWidth())
        when {
            state.empty -> KkEmptyState(
                title = "등록된 일정이 없어요",
                description = "휴가·외박·면회를 등록하면 다음 만남까지 D-day를 알려 드려요",
                icon = KkIcons.Calendar,
                action = "일정 추가",
                onAction = { onIntent(CalendarIntent.NewEvent(state.selected)) },
            )
            !state.expanded && state.selected == state.today -> {
                Text("이번 주", style = KkTheme.type.titleS, color = c.textPrimary)
                if (state.thisWeek.isEmpty()) NoEvents("이번 주에는 일정이 없어요")
                state.thisWeek.forEach { e -> EventRow(e) { onIntent(CalendarIntent.Edit(e)) } }
            }
            else -> {
                val d = state.selected
                Text("${d.month.number}월 ${d.day}일 (${d.weekdayKo()})", style = KkTheme.type.titleS, color = c.textPrimary)
                val list = state.eventsOn(d)
                if (list.isEmpty()) NoEvents("이 날은 일정이 없어요")
                list.forEach { e -> EventRow(e) { onIntent(CalendarIntent.Edit(e)) } }
            }
        }
        Spacer(Modifier.height(KkSpacing.lg))
    }
    state.draft?.let { EventEditor(it, onIntent) }
}

/** 다음 만남 카드(Next Meet): "다음 만남 · 외박" / D-3 / "10월 24일(토) 09:00 · 1박 2일". */
@Composable
private fun NextMeet(e: Event, today: LocalDate, compact: Boolean) {
    val c = KkTheme.colors
    val left = today.daysUntil(e.start.date)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.xl)).background(c.bgBrand)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = KkSpacing.xl, vertical = if (compact) KkSpacing.lg else KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        Text("다음 만남 · ${e.type.label()}", style = KkTheme.type.labelM, color = c.textOnBrand)
        Text(if (left > 0) "D-$left" else "D-DAY", style = KkTheme.type.dday, color = c.textOnBrand)
        val time = e.start.time.takeIf { it != LocalTime(0, 0) }?.let { " ${it.hhmm()}" } ?: ""
        val stay = e.nightsLabel()?.let { " · $it" } ?: ""
        Text(
            "${e.start.date.month.number}월 ${e.start.date.day}일(${e.start.date.weekdayKo()})$time$stay",
            style = KkTheme.type.bodyS, color = c.textOnBrand,
        )
    }
}

/** 접힌 달력: 고른 날이 있는 한 주(Week Strip). 고른 날은 반전색. */
@Composable
private fun WeekStrip(state: CalendarState, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth().monthSwipe(onIntent), horizontalArrangement = Arrangement.SpaceBetween) {
        state.week.forEach { day ->
            val on = day == state.selected
            val shape = RoundedCornerShape(KkRadius.md)
            val events = state.eventsOn(day)
            Column(
                Modifier.width(44.dp)
                    .then(if (on) Modifier else Modifier.kkElevation(KkElevation.Low, shape))
                    .clip(shape)
                    .background(if (on) c.bgInverse else c.bgSurface)
                    .clickable(role = Role.Button) { onIntent(CalendarIntent.Select(day)) }
                    .semantics { contentDescription = day.a11y(events.size, on) }
                    .padding(KkSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs),
            ) {
                Text(day.weekdayKo(), style = KkTheme.type.caption, color = if (on) c.textInverse else c.textTertiary)
                Text("${day.day}", style = KkTheme.type.labelM, color = if (on) c.textInverse else c.textPrimary)
                Dots(events)
            }
        }
    }
}

/** 펼친 달력(Calendar Card · Month Grid). 오늘 = 연한 브랜드 원, 고른 날 = 브랜드 원, 일요일 = 빨강. */
@Composable
private fun MonthCard(state: CalendarState, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Mid, shape).clip(shape).background(c.bgSurface)
            .monthSwipe(onIntent)
            .padding(horizontal = KkSpacing.md, vertical = KkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WEEKDAYS_SUN_FIRST.forEach {
                Box(Modifier.width(44.dp).height(20.dp), contentAlignment = Alignment.TopCenter) {
                    Text(it, style = KkTheme.type.caption, color = c.textTertiary)
                }
            }
        }
        state.cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day ->
                    val inMonth = day.month == state.month.month
                    val selected = day == state.selected
                    val events = if (inMonth) state.eventsOn(day) else emptyList()
                    Column(
                        Modifier.width(44.dp).height(48.dp).then(
                            if (inMonth) Modifier.clip(RoundedCornerShape(KkRadius.sm))
                                .clickable(role = Role.Button) { onIntent(CalendarIntent.Select(day)) }
                                .semantics { contentDescription = day.a11y(events.size, selected) }
                            else Modifier,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs),
                    ) {
                        if (inMonth) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(
                                    when {
                                        selected -> c.bgBrand
                                        day == state.today -> c.bgBrandSubtle
                                        else -> Color.Transparent
                                    },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${day.day}", style = KkTheme.type.labelM,
                                    color = when {
                                        selected -> c.textOnBrand
                                        day.dayOfWeek == DayOfWeek.SUNDAY -> c.statusDanger
                                        else -> c.textPrimary
                                    },
                                )
                            }
                            Dots(events)
                        }
                    }
                }
            }
        }
    }
}

/** 날짜 아래 5pt 점(일정 색). 종류가 여럿이면 최대 3개. */
@Composable
private fun Dots(events: List<Event>) {
    val c = KkTheme.colors
    Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
        events.map { it.type }.distinct().take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(c.eventColor(it))) }
    }
}

/** 범례(Legend): 휴가 · 외박 · 면회 · 훈련 · 당직. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = KkTheme.colors
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EventType.entries.forEach { KkEventTag(it.label(), c.eventColor(it)) }
    }
}

@Composable
private fun NoEvents(text: String) {
    Text(text, style = KkTheme.type.bodyM, color = KkTheme.colors.textTertiary, modifier = Modifier.padding(vertical = KkSpacing.sm))
}

/** 일정 한 줄(Event): 색 막대 · 제목 · 기간 · 종류 태그. */
@Composable
private fun EventRow(e: Event, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(c.eventColor(e.type)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            val title = e.title.ifBlank { e.type.label() }
            Text(e.nightsLabel()?.let { "$title · $it" } ?: title, style = KkTheme.type.bodyM, color = c.textPrimary, maxLines = 1)
            Text(e.rangeLabel(), style = KkTheme.type.caption, color = c.textTertiary, maxLines = 1)
        }
        KkEventTag(e.type.label(), c.eventColor(e.type))
    }
}

/** 옆으로 밀어 달 넘기기 + 스크린리더용 이전 달 · 다음 달 동작. */
private fun Modifier.monthSwipe(onIntent: (CalendarIntent) -> Unit): Modifier = this
    .pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total > 120f) onIntent(CalendarIntent.ShiftMonth(-1))
                if (total < -120f) onIntent(CalendarIntent.ShiftMonth(1))
            },
        ) { _, dx -> total += dx }
    }
    .semantics {
        customActions = listOf(
            CustomAccessibilityAction("이전 달") { onIntent(CalendarIntent.ShiftMonth(-1)); true },
            CustomAccessibilityAction("다음 달") { onIntent(CalendarIntent.ShiftMonth(1)); true },
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(draft: EventDraft, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = 시작, false = 끝
    ModalBottomSheet(
        onDismissRequest = { onIntent(CalendarIntent.CloseDraft) },
        containerColor = c.bgSurfaceRaised,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = KkRadius.xl, topEnd = KkRadius.xl),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xl, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            KkSheetHeader(if (draft.id == null) "일정 추가" else "일정 고치기")
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), modifier = Modifier.semantics { contentDescription = "일정 종류" }) {
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
            KkInput(draft.title, { onIntent(CalendarIntent.ChangeDraft(draft.copy(title = it))) }, label = "제목", placeholder = "예) 정기휴가 · 수료식")
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkInput(
                    "${draft.start.short()}(${draft.start.weekdayKo()})", {}, Modifier.weight(1f), label = "시작", onClick = { picking = true },
                    trailing = { KkIcon(KkIcons.ChevronDown, tint = c.textTertiary, size = 20.dp) },
                )
                KkInput(
                    "${draft.end.short()}(${draft.end.weekdayKo()})", {}, Modifier.weight(1f), label = "끝", onClick = { picking = false },
                    error = if (!draft.valid) "끝나는 날이 시작보다 빨라요" else null,
                    trailing = { KkIcon(KkIcons.ChevronDown, tint = c.textTertiary, size = 20.dp) },
                )
            }
            picking?.let { isStart ->
                KkDatePicker(if (isStart) draft.start else draft.end, { d ->
                    onIntent(CalendarIntent.ChangeDraft(if (isStart) draft.copy(start = d, end = maxOf(d, draft.end)) else draft.copy(end = d)))
                    picking = null
                })
            }
            KkListRow(
                "전날 20:00에 알려 주기", trailing = KkRowTrailing.Switch, switchOn = draft.alarm,
                onClick = { onIntent(CalendarIntent.ChangeDraft(draft.copy(alarm = !draft.alarm))) },
            )
            KkPrimaryButton("저장", onClick = { onIntent(CalendarIntent.SaveDraft) }, enabled = draft.valid, modifier = Modifier.fillMaxWidth())
            if (draft.id != null) {
                KkButton("삭제", { onIntent(CalendarIntent.DeleteDraft) }, Modifier.fillMaxWidth(), style = KkButtonStyle.Ghost, danger = true)
            }
        }
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

private fun LocalDate.short() = "${month.number}.${day}"

private val WEEKDAYS_SUN_FIRST = listOf("일", "월", "화", "수", "목", "금", "토")

internal fun LocalDate.weekdayKo() = listOf("월", "화", "수", "목", "금", "토", "일")[dayOfWeek.ordinal]

private fun LocalTime.hhmm() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 여러 날이면 "1박 2일". */
private fun Event.nightsLabel(): String? =
    start.date.daysUntil(end.date).takeIf { it > 0 }?.let { "${it}박 ${it + 1}일" }

/** "10.21(수)" · "10.21(수) 13:00 – 16:00" · "10.24(토) – 10.25(일)". 종일(00:00 – 23:59)이면 시간을 뺀다. */
private fun Event.rangeLabel(): String {
    val allDay = start.time == LocalTime(0, 0) && end.time.hour == 23 && end.time.minute == 59
    fun d(x: LocalDate) = "${x.short()}(${x.weekdayKo()})"
    return when {
        allDay && start.date == end.date -> d(start.date)
        allDay -> "${d(start.date)} – ${d(end.date)}"
        start.date == end.date -> "${d(start.date)} ${start.time.hhmm()} – ${end.time.hhmm()}"
        start.date.daysUntil(end.date) == 1 -> "${d(start.date)} ${start.time.hhmm()} – 다음날 ${end.time.hhmm()}"
        else -> "${d(start.date)} ${start.time.hhmm()} – ${d(end.date)} ${end.time.hhmm()}"
    }
}

private fun LocalDate.a11y(count: Int, selected: Boolean) =
    "${month.number}월 ${day}일 ${weekdayKo()}요일, " + (if (count == 0) "일정 없음" else "일정 ${count}개") + (if (selected) ", 선택됨" else "")
