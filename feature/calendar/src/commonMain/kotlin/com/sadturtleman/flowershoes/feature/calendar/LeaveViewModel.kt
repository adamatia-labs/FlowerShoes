package com.sadturtleman.flowershoes.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.LeaveRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.military.LeaveSource
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.LeaveKind
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** 휴가 관리의 한 줄이 가리키는 것. */
sealed interface LeaveTarget {
    data class Regular(val source: LeaveSource.Regular) : LeaveTarget
    data class Grant(val grant: LeaveGrant) : LeaveTarget
    data class Use(val event: Event) : LeaveTarget
}

/** 목록 한 줄(Figma List Row · Value): 제목 · 값 · ›. */
data class LeaveRow(val title: String, val value: String?, val target: LeaveTarget)

/** 휴가 관리(Figma 3 · 휴가 관리): 남은 휴가 / 전체 + 받은 휴가(출처) + 쓴 휴가(휴가 일정). */
data class LeaveState(
    val pool: LeavePool = LeavePool(emptyList(), 0),
    val uses: List<Event> = emptyList(),
    val today: LocalDate,
    val partnerName: String? = null,
    /** 정기휴가를 받은 날로 보인다(입대일). */
    val enlistDate: LocalDate? = null,
) {
    val total: Int get() = pool.total

    /** 초과 사용이면 음수(빨간색). */
    val remaining: Int get() = pool.remaining

    /** 남은 휴가 막대: 전체에 대한 남은 비율(넘치면 0). */
    val remainingFraction: Float get() = if (total <= 0) 0f else (remaining.toFloat() / total).coerceIn(0f, 1f)

    /** 받은 휴가: 정기휴가(계산값, "정기휴가 28일(공군)")가 맨 위, 그 아래 받은 휴가는 "포상휴가 · 2026.09.25" · "4일". */
    val received: List<LeaveRow>
        get() = pool.sources.map { s ->
            when (s) {
                is LeaveSource.Regular -> LeaveRow(s.label, null, LeaveTarget.Regular(s))
                is LeaveSource.Granted -> LeaveRow("${s.grant.kind.label} · ${s.grant.grantedOn.dotted()}", "${s.days}일", LeaveTarget.Grant(s.grant))
            }
        }

    /** 쓴 휴가: 휴가 일정을 날짜순으로 "7월 10일 – 7월 14일" · "5일". */
    val used: List<LeaveRow>
        get() = uses.sortedBy { it.start }.map { e ->
            val title = if (e.start.date == e.end.date) e.start.date.monthDay() else "${e.start.date.monthDay()} – ${e.end.date.monthDay()}"
            LeaveRow(title, "${e.dayCount}일", LeaveTarget.Use(e))
        }

    fun grant(id: String): LeaveGrant? = pool.grant(id)
}

/** 받은 휴가 등록 · 고치기 칸. [memo] 는 비워도 된다(사격 만발 같은 사유). */
data class LeaveDraft(
    val id: String? = null,
    val kind: LeaveKind = LeaveKind.REWARD,
    val memo: String = "",
    val days: Int = 1,
    val grantedOn: LocalDate,
) {
    val valid: Boolean get() = days in 1..MAX_DAYS

    companion object {
        const val MAX_DAYS = 60
    }
}

/** 휴가 관리(Figma 3 · 휴가 관리 · 휴가 상세 · 받은 휴가 등록 · 휴가 쓰기). 출처를 고르지 않고 기간만 골라 풀에서 쓴다. */
class LeaveViewModel(
    private val leave: LeaveRepository,
    profile: ProfileRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val day = clock.ticker().map { clock.today() }.distinctUntilChanged()

    private val partner = combine(profile.observeCouple(), profile.observeUsers()) { couple, users ->
        val id = couple?.partnerUserId?.takeIf { it.isNotBlank() } ?: return@combine null
        users.firstOrNull { it.id == id }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
    }

    private val enlist = profile.observeTimelines().map { it.firstOrNull()?.first?.enlistDate }

    val state: StateFlow<LeaveState> = combine(leave.observePool(), leave.observeUses(), day, partner, enlist) { pool, uses, today, p, e ->
        LeaveState(pool, uses, today, p, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaveState(today = clock.today()))

    fun newDraft(): LeaveDraft = LeaveDraft(grantedOn = clock.today())

    fun editDraft(g: LeaveGrant): LeaveDraft = LeaveDraft(g.id, g.kind, g.name.takeUnless { it == g.kind.label }.orEmpty(), g.days, g.grantedOn)

    fun save(d: LeaveDraft) {
        if (!d.valid) return
        viewModelScope.launch {
            val old = d.id?.let { id -> state.value.grant(id) }
            leave.save(
                LeaveGrant(
                    id = d.id ?: leave.newId(), kind = d.kind, name = d.memo.trim().ifBlank { d.kind.label }, days = d.days, grantedOn = d.grantedOn,
                    updatedAt = clock.now(), lamport = old?.lamport ?: 0, lastWriterDevice = old?.lastWriterDevice.orEmpty(),
                ),
            )
        }
    }

    fun delete(id: String) = viewModelScope.launch { leave.delete(id) }

    /** 휴가 쓰기 기본 기간: 내일 하루. */
    fun defaultRange(): Pair<LocalDate, LocalDate> {
        val from = clock.today().plus(DatePeriod(days = 1))
        return from to from
    }

    fun use(from: LocalDate, to: LocalDate, notify: Boolean) {
        viewModelScope.launch { leave.use(from, to, notify && state.value.partnerName != null) }
    }
}

/** "7월 10일". */
internal fun LocalDate.monthDay() = "${month.number}월 ${day}일"

/** "2026.10.21". */
internal fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
