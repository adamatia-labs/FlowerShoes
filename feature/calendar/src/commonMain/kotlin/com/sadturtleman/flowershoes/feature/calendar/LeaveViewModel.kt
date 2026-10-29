package com.sadturtleman.flowershoes.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.LeaveRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
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
import kotlinx.datetime.plus

/** 휴가 일정 한 번: 지났으면 사용 완료, 오늘 걸치면 사용 중, 앞으로면 사용 예정. */
enum class LeaveUseState(val label: String) { PLANNED("사용 예정"), ONGOING("사용 중"), DONE("사용 완료") }

/** 휴가 하나의 풀 + 받은 휴가(출처 내역) + 휴가 일정(사용 내역). */
data class LeaveState(
    val pool: LeavePool = LeavePool(emptyList(), 0),
    val uses: List<Event> = emptyList(),
    val today: LocalDate,
    val partnerName: String? = null,
) {
    val total: Int get() = pool.total

    /** 오늘 뒤에 잡힌 날(사용 예정일). 오늘 걸친 일정은 남은 날만 예정으로 센다. */
    val planned: Int get() = uses.sumOf { e -> plannedDays(e) }
    val usedDays: Int get() = pool.used - planned

    /** 초과 사용이면 음수(빨간색). */
    val remaining: Int get() = pool.remaining

    fun stateOf(e: Event): LeaveUseState = when {
        e.end.date < today -> LeaveUseState.DONE
        e.start.date <= today -> LeaveUseState.ONGOING
        else -> LeaveUseState.PLANNED
    }

    fun grant(id: String): LeaveGrant? = pool.grant(id)

    private fun plannedDays(e: Event): Int = when {
        e.start.date > today -> e.dayCount
        e.end.date > today -> e.end.date.toEpochDays().toInt() - today.toEpochDays().toInt()
        else -> 0
    }
}

/** 휴가 추가 · 수정 칸. */
data class LeaveDraft(
    val id: String? = null,
    val kind: LeaveKind = LeaveKind.REWARD,
    val name: String = "",
    val days: Int = 1,
    val grantedOn: LocalDate,
) {
    val valid: Boolean get() = name.isNotBlank() && days in 1..MAX_DAYS

    companion object {
        const val MAX_DAYS = 60
    }
}

/** 휴가 관리(Figma 3 · 휴가 관리 · 사용 등록 · 추가). 출처를 고르지 않고 기간만 골라 풀에서 쓴다. */
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

    val state: StateFlow<LeaveState> = combine(leave.observePool(), leave.observeUses(), day, partner) { pool, uses, today, p ->
        LeaveState(pool, uses, today, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaveState(today = clock.today()))

    fun newDraft(): LeaveDraft = LeaveDraft(grantedOn = clock.today())

    fun editDraft(g: LeaveGrant): LeaveDraft = LeaveDraft(g.id, g.kind, g.name, g.days, g.grantedOn)

    fun save(d: LeaveDraft) {
        if (!d.valid) return
        viewModelScope.launch {
            val old = d.id?.let { id -> state.value.grant(id) }
            leave.save(
                LeaveGrant(
                    id = d.id ?: leave.newId(), kind = d.kind, name = d.name.trim(), days = d.days, grantedOn = d.grantedOn,
                    updatedAt = clock.now(), lamport = old?.lamport ?: 0, lastWriterDevice = old?.lastWriterDevice.orEmpty(),
                ),
            )
        }
    }

    fun delete(id: String) = viewModelScope.launch { leave.delete(id) }

    /** 사용 등록 기본 날짜: 내일 하루. */
    fun defaultRange(): Pair<LocalDate, LocalDate> {
        val from = clock.today().plus(DatePeriod(days = 1))
        return from to from
    }

    fun use(from: LocalDate, to: LocalDate, notify: Boolean) {
        viewModelScope.launch { leave.use(from, to, notify && state.value.partnerName != null) }
    }
}
