package com.sadturtleman.flowershoes.feature.calendar

import com.sadturtleman.flowershoes.core.designsystem.KkAdSlot
import com.sadturtleman.flowershoes.core.designsystem.KkAd
import kotlinx.datetime.LocalDateTime
import com.sadturtleman.flowershoes.core.designsystem.KkChoice
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
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
                description = "", // Figma 빈 캘린더: 제목 + 버튼만
                icon = KkIcons.Calendar,
                action = "일정 추가",
                onAction = { onIntent(CalendarIntent.NewEvent(state.selected)) },
            )
            !state.expanded && state.selected == state.today -> {
                Text("이번 주", style = KkTheme.type.titleS, color = c.textPrimary)
                if (state.thisWeek.isEmpty()) NoEvents("이번 주에는 일정이 없어요")
                state.thisWeek.forEach { e -> EventRow(e) { onIntent(CalendarIntent.Edit(e)) } }
                // 광고 지면(Figma 9): 이번 주 일정 목록 아래 네이티브 배너 1개.
                KkAd(KkAdSlot.CALENDAR)
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
        // 범례(Figma): 휴가 · 외박 · 면회 · 훈련 · 당직 — 기타는 색이 없어 빼 둔다.
        EventType.entries.filter { it != EventType.OTHER }.forEach { KkEventTag(it.label(), c.eventColor(it)) }
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

/** Figma 일정 추가 칩 순서: 면회 · 외박 · 휴가 · 훈련 · 당직 · 기타. */
private val EDITOR_TYPES = listOf(EventType.VISIT, EventType.OVERNIGHT, EventType.VACATION, EventType.TRAINING, EventType.DUTY, EventType.OTHER)
private val EDITOR_MINUTES = listOf(0, 10, 20, 30, 40, 50)

/**
 * 일정 추가 · 고치기(Figma 최종 정리 › 캘린더 · 일정 추가): 전체 화면.
 * 종류 칩 → 제목 → [하루 종일 · 시작 · 끝 · 알림] 카드 → 메모 → 저장.
 */
@Composable
private fun EventEditor(draft: EventDraft, onIntent: (CalendarIntent) -> Unit) {
    val c = KkTheme.colors
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = 시작, false = 끝
    var alarmSheet by remember { mutableStateOf(false) }
    fun change(d: EventDraft) = onIntent(CalendarIntent.ChangeDraft(d))
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onIntent(CalendarIntent.CloseDraft) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding().imePadding()) {
            KkTopBar(
                if (draft.id == null) "일정 추가" else "일정 고치기", onLeading = { onIntent(CalendarIntent.CloseDraft) },
                leading = KkTopBarLeading.Close,
                trailingText = if (draft.id != null) "삭제" else null, onTrailing = { onIntent(CalendarIntent.DeleteDraft) },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
                    modifier = Modifier.semantics { contentDescription = "일정 종류" },
                ) {
                    EDITOR_TYPES.forEach { t ->
                        val on = draft.type == t
                        KkStatusChip(t.label(), selected = on, onClick = { change(draft.copy(type = t)) }, dot = if (on) Color.White else c.statusWarning)
                    }
                }
                KkInput(draft.title, { change(draft.copy(title = it)) }, label = "제목", placeholder = "첫 면회")
                val shape = RoundedCornerShape(KkRadius.lg)
                Column(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)) {
                    KkListRow("하루 종일", trailing = KkRowTrailing.Switch, switchOn = draft.allDay, onClick = { change(draft.copy(allDay = !draft.allDay)) })
                    fun at(d: LocalDate, t: LocalTime) = "${d.mmdd()} (${d.weekdayKo()})" + if (draft.allDay) "" else " ${t.hhmm()}"
                    KkListRow("시작", value = at(draft.start, draft.startTime), onClick = { picking = true })
                    KkListRow("끝", value = at(draft.end, draft.endTime), onClick = { picking = false })
                    KkListRow("알림", value = if (draft.alarm) "전날 20:00" else "없음", onClick = { alarmSheet = true })
                }
                if (!draft.valid) Text("끝나는 때가 시작보다 빨라요", style = KkTheme.type.bodyS, color = c.statusDanger)
                KkInput(draft.memo, { change(draft.copy(memo = it)) }, label = "메모", placeholder = "신분증 챙기기")
            }
            Box(Modifier.fillMaxWidth().padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.sm, bottom = 14.dp)) {
                KkPrimaryButton("저장", onClick = { onIntent(CalendarIntent.SaveDraft) }, enabled = draft.valid, modifier = Modifier.fillMaxWidth())
            }
        }
        picking?.let { isStart ->
            var date by remember(isStart) { mutableStateOf(if (isStart) draft.start else draft.end) }
            var time by remember(isStart) { mutableStateOf(if (isStart) draft.startTime else draft.endTime) }
            KkModalSheet({ picking = null }) {
                KkSheetHeader(if (isStart) "시작" else "끝")
                KkDatePicker(date, { date = it })
                if (!draft.allDay) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                        (0..23).forEach { h ->
                            KkStatusChip("${h.toString().padStart(2, '0')}시", selected = time.hour == h, onClick = { time = LocalTime(h, time.minute) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                        EDITOR_MINUTES.forEach { m ->
                            KkStatusChip("${m.toString().padStart(2, '0')}분", selected = time.minute == m, onClick = { time = LocalTime(time.hour, m) })
                        }
                    }
                }
                KkPrimaryButton("확인", onClick = {
                    change(
                        if (isStart) {
                            val start = LocalDateTime(date, time)
                            // 시작을 끝 뒤로 옮기면 끝도 같은 길이만큼 따라간다.
                            if (draft.copy(start = date, startTime = time).valid) draft.copy(start = date, startTime = time)
                            else draft.copy(start = date, startTime = time, end = start.date, endTime = time)
                        } else draft.copy(end = date, endTime = time),
                    )
                    picking = null
                }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (alarmSheet) {
            KkModalSheet({ alarmSheet = false }) {
                KkSheetHeader("알림")
                KkChoice("없음", !draft.alarm, { change(draft.copy(alarm = false)); alarmSheet = false }, radio = true)
                KkChoice("전날 20:00", draft.alarm, { change(draft.copy(alarm = true)); alarmSheet = false }, radio = true)
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
    EventType.OTHER -> textTertiary
}

internal fun EventType.label() = when (this) {
    EventType.VACATION -> "휴가"
    EventType.OVERNIGHT -> "외박"
    EventType.VISIT -> "면회"
    EventType.TRAINING -> "훈련"
    EventType.DUTY -> "당직"
    EventType.OTHER -> "기타"
}

private fun LocalDate.short() = "${month.number}.${day}"

/** "11.01". */
private fun LocalDate.mmdd() = "${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

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
