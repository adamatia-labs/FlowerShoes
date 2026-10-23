package com.sadturtleman.flowershoes.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.LeaveRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.dayCount
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.LeaveKind
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
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

enum class LeaveSort(val label: String) { NEWEST("최신순"), OLDEST("오래된순"), KIND("휴가 종류순") }

/** 쓴 날 한 번: 지났으면 사용 완료, 오늘 걸치면 사용 중, 앞으로면 사용 예정. */
enum class LeaveUseState(val label: String) { PLANNED("사용 예정"), ONGOING("사용 중"), DONE("사용 완료") }

/** 받은 휴가 한 건 + 쓴 날들. */
data class LeaveCard(val grant: LeaveGrant, val uses: List<Event>, val today: LocalDate) {
    val used: Int get() = uses.sumOf { it.dayCount }
    val remaining: Int get() = (grant.days - used).coerceAtLeast(0)
    fun stateOf(e: Event): LeaveUseState = when {
        e.end.date < today -> LeaveUseState.DONE
        e.start.date <= today -> LeaveUseState.ONGOING
        else -> LeaveUseState.PLANNED
    }
}

data class LeaveState(
    val cards: List<LeaveCard> = emptyList(),
    val sort: LeaveSort = LeaveSort.NEWEST,
    val today: LocalDate,
    val partnerName: String? = null,
) {
    val total: Int get() = cards.sumOf { it.grant.days }

    /** 오늘 뒤에 잡힌 날(사용 예정일). 오늘 걸친 일정은 남은 날만 예정으로 센다. */
    val planned: Int get() = cards.sumOf { c -> c.uses.sumOf { e -> plannedDays(e) } }
    val usedDays: Int get() = cards.sumOf { it.used } - planned
    val remaining: Int get() = (total - planned - usedDays).coerceAtLeast(0)

    private fun plannedDays(e: Event): Int = when {
        e.start.date > today -> e.dayCount
        e.end.date > today -> e.end.date.toEpochDays().toInt() - today.toEpochDays().toInt()
        else -> 0
    }

    val sorted: List<LeaveCard>
        get() = when (sort) {
            LeaveSort.NEWEST -> cards.sortedWith(compareByDescending<LeaveCard> { it.grant.grantedOn }.thenBy { it.grant.name })
            LeaveSort.OLDEST -> cards.sortedWith(compareBy<LeaveCard> { it.grant.grantedOn }.thenBy { it.grant.name })
            LeaveSort.KIND -> cards.sortedWith(compareBy<LeaveCard> { it.grant.kind.ordinal }.thenByDescending { it.grant.grantedOn }.thenBy { it.grant.name })
        }

    fun card(id: String): LeaveCard? = cards.firstOrNull { it.grant.id == id }
}

/** 휴가 추가 · 수정 칸. */
data class LeaveDraft(
    val id: String? = null,
    val kind: LeaveKind = LeaveKind.REGULAR,
    val name: String = "",
    val days: Int = 1,
    val grantedOn: LocalDate,
) {
    val valid: Boolean get() = name.isNotBlank() && days in 1..MAX_DAYS

    companion object {
        const val MAX_DAYS = 60
    }
}

/** 휴가 관리(Figma 3 · 휴가 관리 · 상세 · 사용 등록 · 추가). */
class LeaveViewModel(
    private val leave: LeaveRepository,
    profile: ProfileRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val sort = MutableStateFlow(LeaveSort.NEWEST)
    private val day = clock.ticker().map { clock.today() }.distinctUntilChanged()

    private val partner = combine(profile.observeCouple(), profile.observeUsers()) { couple, users ->
        val id = couple?.partnerUserId?.takeIf { it.isNotBlank() } ?: return@combine null
        users.firstOrNull { it.id == id }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
    }

    val state: StateFlow<LeaveState> = combine(leave.observeGrants(), leave.observeUses(), sort, day, partner) { grants, uses, s, today, p ->
        val byGrant = uses.groupBy { it.leaveGrantId }
        LeaveState(grants.map { g -> LeaveCard(g, byGrant[g.id].orEmpty(), today) }, s, today, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaveState(today = clock.today()))

    fun setSort(s: LeaveSort) {
        sort.value = s
    }

    fun newDraft(): LeaveDraft = LeaveDraft(grantedOn = clock.today())

    fun editDraft(g: LeaveGrant): LeaveDraft = LeaveDraft(g.id, g.kind, g.name, g.days, g.grantedOn)

    fun save(d: LeaveDraft) {
        if (!d.valid) return
        viewModelScope.launch {
            val old = d.id?.let { id -> state.value.card(id)?.grant }
            leave.save(
                LeaveGrant(
                    id = d.id ?: leave.newId(), kind = d.kind, name = d.name.trim(), days = d.days, grantedOn = d.grantedOn,
                    updatedAt = clock.now(), lamport = old?.lamport ?: 0, lastWriterDevice = old?.lastWriterDevice.orEmpty(),
                ),
            )
        }
    }

    fun delete(id: String) = viewModelScope.launch { leave.delete(id) }

    /** 사용 등록 기본 날짜: 내일부터 남은 일수만큼(최대 남은 일수). */
    fun defaultRange(card: LeaveCard): Pair<LocalDate, LocalDate> {
        val from = clock.today().plus(DatePeriod(days = 1))
        return from to from.plus(DatePeriod(days = (card.remaining - 1).coerceAtLeast(0)))
    }

    fun use(card: LeaveCard, from: LocalDate, to: LocalDate, notify: Boolean) {
        viewModelScope.launch { leave.use(card.grant, from, to, notify && state.value.partnerName != null) }
    }
}
