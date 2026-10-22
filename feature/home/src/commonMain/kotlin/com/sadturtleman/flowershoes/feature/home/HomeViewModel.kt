package com.sadturtleman.flowershoes.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.Rank
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Event
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** 홈 D-day 카드에 그릴 값. 계산은 :core:military 가 끝내고 화면은 숫자만 받는다. */
data class DdayCard(
    val daysLeft: Int,
    val dischargeDate: LocalDate,
    val progress: Float,
    val servedDays: Int,
    val totalDays: Int,
    val rank: Rank?,
    val nextRank: Rank?,
    val daysToNextRank: Int?,
    val payStep: Int?,
    val isOverridden: Boolean,
)

data class HomeState(
    val loading: Boolean = true,
    val dday: DdayCard? = null,
    val nextEvent: Event? = null,
    val daysToNextEvent: Int? = null,
    val sentMissYouToday: Int = 0,
    /** 아직 연결 전이면 상대 상태 대신 연결 안내를 보여 준다. */
    val linked: Boolean = false,
    /** 오늘 연인에게 받은 보고싶어. */
    val partnerMissYouToday: Int = 0,
)

sealed interface HomeIntent {
    data object SendMissYou : HomeIntent
}

class HomeViewModel(
    profile: ProfileRepository,
    events: EventRepository,
    private val missYou: MissYouRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val me = profile.myUserId()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val partner: kotlinx.coroutines.flow.Flow<Pair<Boolean, Int>> = profile.observePartnerId().flatMapLatest { id ->
        if (id == null) flowOf(false to 0) else missYou.observeTodayFrom(id).map { true to it }
    }

    val state: StateFlow<HomeState> = combine(
        profile.observeTimelines(),
        events.observeNext(),
        missYou.observeTodayFrom(me),
        partner,
    ) { timelines, next, sent, (linked, received) ->
        val today = clock.today()
        HomeState(
            loading = false,
            dday = timelines.firstOrNull()?.second?.toCard(today),
            nextEvent = next,
            daysToNextEvent = next?.let { today.daysUntil(it.start.date) },
            sentMissYouToday = sent,
            linked = linked,
            partnerMissYouToday = received,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.SendMissYou -> viewModelScope.launch { missYou.send() }
        }
    }
}

internal fun ServiceTimeline.toCard(today: LocalDate): DdayCard {
    val next = nextRank(today)
    return DdayCard(
        daysLeft = daysLeft(today),
        dischargeDate = dischargeDate,
        progress = progress(today).toFloat(),
        servedDays = servedDays(today),
        totalDays = totalDays,
        rank = rankAt(today)?.rank,
        nextRank = next?.rank,
        daysToNextRank = next?.let { today.daysUntil(it.from) },
        payStep = payStep(today),
        isOverridden = isOverridden,
    )
}
