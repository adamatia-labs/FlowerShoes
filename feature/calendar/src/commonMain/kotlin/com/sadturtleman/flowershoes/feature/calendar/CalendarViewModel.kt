package com.sadturtleman.flowershoes.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.LeaveRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 편집 중인 일정. 날짜 단위로 고른다(시간은 휴가 · 외박이 대부분 하루 단위라 기본 종일). */
data class EventDraft(
    val id: String?,
    val type: EventType = EventType.VISIT,
    val title: String = "",
    val start: LocalDate,
    val end: LocalDate,
    val alarm: Boolean = true,
    /** 하루 종일이면 00:00 – 23:59 로 저장하고 시간 칸을 숨긴다. */
    val allDay: Boolean = false,
    val startTime: LocalTime = LocalTime(9, 0),
    val endTime: LocalTime = LocalTime(18, 0),
    val memo: String = "",
    /** 매년 반복(기념일만, 기본 켬). */
    val repeatYearly: Boolean = true,
    /** 고치는 중인 원래 일정(원래 날 그대로). 휴가 일정이면 그 일수를 돌려받고 센다. */
    val original: Event? = null,
) {
    /** 기념일은 하루짜리 종일 일정이다(날짜 하나만 고른다). */
    private val singleDay: Boolean get() = type == EventType.ANNIVERSARY
    val startAt: LocalDateTime get() = LocalDateTime(start, if (allDay || singleDay) LocalTime(0, 0) else startTime)
    val endAt: LocalDateTime get() = LocalDateTime(if (singleDay) start else end, if (allDay || singleDay) LocalTime(23, 59) else endTime)
    val valid: Boolean get() = endAt >= startAt

    /**
     * 커플 기념일(홈 함께한 날 · 100일 알림이 기대고 있다). 지우기는 설정 › 기념일에서만, 종류 · 매년 반복도 바꾸지 않는다
     * — 바꾸면 기념일이 사라지는 것과 같다. 날짜 · 제목 · 메모만 고친다.
     */
    val coupleAnniversary: Boolean get() = id == Event.COUPLE_ANNIVERSARY_ID

    val deletable: Boolean get() = id != null && !coupleAnniversary

    /** 종류 바꾸기: 휴가 · 기념일은 하루 단위라 종일로. */
    fun withType(t: EventType): EventDraft =
        if (coupleAnniversary) this else copy(type = t, allDay = allDay || t == EventType.VACATION || t == EventType.ANNIVERSARY)

    fun toggleRepeat(): EventDraft = if (coupleAnniversary) this else copy(repeatYearly = !repeatYearly)

    /** 휴가면 이 기간을 쓴 뒤 남는 휴가(일정 추가에서 휴가 = 휴가 사용 등록). 아니면 null. */
    fun leaveUse(pool: LeavePool): LeaveUse? {
        if (type != EventType.VACATION) return null
        return LeaveUse(start.daysUntil(end) + 1, pool.remainingAfter(start, end, replacing = original))
    }
}

/** "5일 사용 · 남은 휴가 13일". 넘치면 [overdrawn] — 막지 않고 빨갛게 보인다. */
data class LeaveUse(val days: Int, val remainingAfter: Int) {
    val overdrawn: Boolean get() = remainingAfter < 0
    val label: String get() = "${days}일 사용 · 남은 휴가 ${remainingAfter}일"
}

data class CalendarState(
    val month: LocalDate,
    val selected: LocalDate,
    val today: LocalDate = selected,
    val expanded: Boolean = false,
    val monthEvents: List<Event> = emptyList(),
    /** 오늘부터 1년(진행 중인 것 포함): 다음 만남 · 이번 주 목록. */
    val upcoming: List<Event> = emptyList(),
    val draft: EventDraft? = null,
    /** 휴가 하나의 풀(맨 위 휴가 요약 띠 · 일정 추가 › 휴가). 읽기 전에는 null. */
    val pool: LeavePool? = null,
    /** 연결된 연인 이름(일정 지우기 확인 창). 혼자 모드면 null. */
    val partnerName: String? = null,
) {
    /** 달력 칸: 앞뒤 달 날짜를 채워 일요일 시작 6주 이하로. */
    val cells: List<LocalDate>
        get() {
            val first = month
            val lead = first.dayOfWeek.ordinal.let { (it + 1) % 7 } // 일요일 = 0
            val start = first.minus(DatePeriod(days = lead))
            val last = first.plus(1, DateTimeUnit.MONTH).minus(DatePeriod(days = 1))
            val count = ((lead + last.day + 6) / 7) * 7
            return (0 until count).map { start.plus(DatePeriod(days = it)) }
        }

    /** 접힌 상태에서 보이는 한 주(선택한 날이 있는 주). */
    val week: List<LocalDate> get() = cells.chunked(7).firstOrNull { selected in it } ?: cells.take(7)

    fun eventsOn(date: LocalDate): List<Event> = monthEvents.filter { date in it.start.date..it.end.date }

    /** 다음 만남(휴가 · 외박 · 면회). 진행 중이면 그것. */
    val nextMeet: Event?
        get() = upcoming.filter { it.type in MEET_TYPES && it.end.date >= today }.minByOrNull { it.start }

    /** 오늘부터 일주일 안에 걸친 일정(접힌 달력 › 이번 주). */
    val thisWeek: List<Event>
        get() = today.plus(DatePeriod(days = 7)).let { last -> upcoming.filter { it.end.date >= today && it.start.date <= last } }

    /** 일정이 하나도 없다(빈 캘린더 화면). */
    val empty: Boolean get() = monthEvents.isEmpty() && upcoming.isEmpty()

    /** 이 달에 잡힌 휴가 · 외박 일수(겹치는 날은 한 번만). */
    val leaveDaysThisMonth: Int
        get() = monthEvents.filter { it.type == EventType.VACATION || it.type == EventType.OVERNIGHT }
            .flatMap { e -> (0..e.start.date.daysUntil(e.end.date)).map { e.start.date.plus(DatePeriod(days = it)) } }
            .filter { it.month == month.month && it.year == month.year }
            .toSet().size
}

private val MEET_TYPES = setOf(EventType.VACATION, EventType.OVERNIGHT, EventType.VISIT)

sealed interface CalendarIntent {
    data class Select(val date: LocalDate) : CalendarIntent
    data class ShiftMonth(val delta: Int) : CalendarIntent
    data object ToggleExpanded : CalendarIntent
    data class NewEvent(val on: LocalDate) : CalendarIntent
    data class Edit(val event: Event) : CalendarIntent
    data class ChangeDraft(val draft: EventDraft) : CalendarIntent
    data object SaveDraft : CalendarIntent
    data object DeleteDraft : CalendarIntent
    data object CloseDraft : CalendarIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val events: EventRepository,
    leave: LeaveRepository? = null,
    profile: ProfileRepository? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val partner: Flow<String?> = profile?.let { p ->
        combine(p.observeCouple(), p.observeUsers()) { couple, users ->
            val id = couple?.partnerUserId?.takeIf { it.isNotBlank() } ?: return@combine null
            users.firstOrNull { it.id == id }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
        }
    } ?: flowOf(null)

    private val ui = MutableStateFlow(
        clock.today().let { CalendarState(month = LocalDate(it.year, it.month, 1), selected = it) },
    )

    val state: StateFlow<CalendarState> = combine(
        ui,
        ui.flatMapLatest { s ->
            events.observeRange(s.cells.first(), s.cells.last())
        },
        clock.ticker().map { clock.today() }.distinctUntilChanged().flatMapLatest { today ->
            events.observeRange(today, today.plus(1, DateTimeUnit.YEAR)).map { today to it }
        },
        leave?.observePool() ?: flowOf(null),
        partner,
    ) { s, list, (today, upcoming), pool, partnerName ->
        s.copy(monthEvents = list, today = today, upcoming = upcoming, pool = pool, partnerName = partnerName)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ui.value)

    fun onIntent(intent: CalendarIntent) {
        when (intent) {
            is CalendarIntent.Select -> ui.update {
                it.copy(selected = intent.date, month = LocalDate(intent.date.year, intent.date.month, 1))
            }
            is CalendarIntent.ShiftMonth -> ui.update {
                val m = it.month.plus(intent.delta, DateTimeUnit.MONTH)
                it.copy(month = m, selected = m)
            }
            CalendarIntent.ToggleExpanded -> ui.update { it.copy(expanded = !it.expanded) }
            is CalendarIntent.NewEvent -> ui.update { it.copy(draft = EventDraft(null, start = intent.on, end = intent.on)) }
            is CalendarIntent.Edit -> viewModelScope.launch {
                // 매년 반복 기념일은 그 해로 옮겨 놓은 사본이라 원래 날(원래 해)로 고친다.
                val e = if (intent.event.repeats) events.get(intent.event.id) ?: intent.event else intent.event
                val allDay = e.start.time == LocalTime(0, 0) && e.end.time.hour == 23 && e.end.time.minute == 59
                ui.update { it.copy(draft = EventDraft(
                    e.id, e.type, e.title, e.start.date, e.end.date, e.alarm, allDay, e.start.time, e.end.time, e.memo, e.repeatYearly, original = e,
                )) }
            }
            is CalendarIntent.ChangeDraft -> ui.update { it.copy(draft = intent.draft) }
            CalendarIntent.SaveDraft -> save()
            CalendarIntent.DeleteDraft -> {
                val id = ui.value.draft?.takeIf { it.deletable }?.id
                ui.update { it.copy(draft = null) }
                if (id != null) viewModelScope.launch { events.delete(id) }
            }
            CalendarIntent.CloseDraft -> ui.update { it.copy(draft = null) }
        }
    }

    private fun save() {
        val d = ui.value.draft ?: return
        if (!d.valid) return
        ui.update { it.copy(draft = null) }
        viewModelScope.launch {
            // 고칠 때는 기존 행(매년 반복 · 만남 사진 · LWW 값)을 그대로 두고 칸만 바꾼다.
            val old = d.id?.let { events.get(it) }
            val base = old ?: Event(
                id = events.newId(), type = d.type, title = "", start = d.startAt, end = d.endAt, alarm = d.alarm,
                updatedAt = clock.now(), lamport = 0, lastWriterDevice = "",
            )
            events.save(
                base.copy(
                    type = d.type, title = d.title.trim(), start = d.startAt, end = d.endAt, alarm = d.alarm,
                    memo = d.memo.trim(), repeatYearly = d.repeatYearly, updatedAt = clock.now(),
                ),
            )
        }
    }
}
