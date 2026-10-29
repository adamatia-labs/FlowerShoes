package com.sadturtleman.flowershoes.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DayLineCards
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.DayLine
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.dayLineAsk
import com.sadturtleman.flowershoes.core.model.dayLineCard
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** 그날의 한 줄 카드: "어제 면회, 그날의 한 줄 남길까요?" [남기기]. */
data class DayLinePrompt(val eventId: String, val text: String)

data class HomeState(
    val loading: Boolean = true,
    val role: Role = Role.GOMSIN,
    val linked: Boolean = false,
    /** "10월 28일 수요일". */
    val dateLine: String = "",
    /** 군인 테마 머리말 소인 날짜 "2026.10.28". */
    val stampDate: String = "",
    /** "지수 & 민준" · 혼자 모드 "지수의 꽃신" / "전역일 계산기". */
    val title: String = "",
    /** 큰 D-day. 입대일이 없으면(곰신이 "나중에 넣을게요") null. */
    val hero: HomeHero? = null,
    val tags: List<HomeTag> = emptyList(),
    /** 오늘 온 편지(보관 모드는 최근 편지). 연결 전이면 늘 null. */
    val letter: HomeLetter? = null,
    /** 혼자 모드 곰신: 연결되면 보낼 내 편지(최근 것 먼저). */
    val pendingLetters: List<HomeLetter> = emptyList(),
    /** 혼자 모드 군인: 진급 일정. */
    val ranks: List<RankRow> = emptyList(),
    /** 연인 이름. 연결 전이면 null(초대 문구는 "연인"). */
    val partnerName: String? = null,
    val sentMissYouToday: Int = 0,
    /** 보낸 초대 링크가 아직 유효하다(연결 전 "초대 보냄 · 기다리는 중 · 링크 다시 보내기"). */
    val invitePending: Boolean = false,
    /** 연결 전인데 입대일이 아직 오지 않았다(홈 · 입대 전 350:972): "입대 전에 같이 연결해 두세요" + 초대. */
    val enlistNudge: Boolean = false,
    val dayLine: DayLinePrompt? = null,
)

sealed interface HomeIntent {
    data object SendMissYou : HomeIntent
    /** 그날의 한 줄 카드 닫기(이 일정은 다시 띄우지 않는다). */
    data class DismissDayLine(val eventId: String) : HomeIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    profile: ProfileRepository,
    events: EventRepository,
    private val missYou: MissYouRepository,
    letters: LetterRepository,
    /** 보낸 초대 링크(DeviceSettings.pendingInvite). */
    pendingInvite: Flow<com.sadturtleman.flowershoes.core.data.sync.PendingInvite?> = flowOf(null),
    private val dayLineCards: DayLineCards? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val me = profile.myUserId()

    private data class People(val linked: Boolean, val partnerId: String?, val myRole: Role, val myName: String, val partnerName: String, val anniversary: LocalDate?, val linkedAt: Long?)

    private val people: Flow<People> = combine(profile.observeCouple(), profile.observeUsers(), profile.observeAnniversary()) { couple, users, anniversary ->
        val mine = users.firstOrNull { it.id == me }
        val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
        val partner = partnerId?.let { id -> users.firstOrNull { it.id == id } }
        val myRole = mine?.role?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.GOMSIN
        People(
            linked = partnerId != null,
            partnerId = partnerId,
            myRole = myRole,
            myName = mine?.nickname?.takeIf { it.isNotBlank() } ?: "나",
            partnerName = partner?.nickname?.takeIf { it.isNotBlank() } ?: "연인",
            anniversary = anniversary,
            linkedAt = couple?.linkedAtMs?.takeIf { partnerId != null },
        )
    }

    private val days: Flow<LocalDate> = clock.ticker().map { clock.today() }.distinctUntilChanged()

    /** 다음 휴가 · 다음 만남 태그에 쓸 오늘부터의 일정. */
    private val upcoming = days.flatMapLatest { today -> events.observeRange(today, today.plus(DatePeriod(years = 3))) }

    /** 그날의 한 줄 카드: 끝난 지 사흘 안의 만남 중 내가 아직 안 쓰고 닫지 않은 것. */
    private val dayLine: Flow<DayLinePrompt?> = days.flatMapLatest { today ->
        combine(
            events.observeRange(today.minus(DatePeriod(days = DayLine.CARD_DAYS)), today),
            dayLineCards?.dismissed ?: flowOf(emptySet()),
        ) { list, dismissed -> dayLineCard(list, me, today, dismissed)?.let { DayLinePrompt(it.id, it.dayLineAsk(today)) } }
    }

    val state: StateFlow<HomeState> = combine(
        combine(people, profile.observeTimelines(), upcoming) { p, t, e -> Triple(p, t, e) },
        letters.observeInbox(),
        dayLine,
        missYou.observeTodayFrom(me),
        days,
    ) { (p, timelines, upcomingEvents), inbox, dayLinePrompt, sent, today ->
        val pair = timelines.firstOrNull()
        val t = pair?.second
        val partnerName = p.partnerName.takeIf { p.linked }
        // 혼자 모드에서도 같은 계산: 곰신이 넣어 둔 연인 입대일(PENDING_SOLDIER)도 timelines 에 있다.
        val hero = homeHero(t, pair?.first?.branch, today, p.myRole, p.myName, partnerName)
        val daysTogether = (p.anniversary ?: p.linkedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(com.sadturtleman.flowershoes.core.data.SEOUL).date })
            ?.let { it.daysUntil(today) + 1 }
        HomeState(
            loading = false,
            role = p.myRole,
            linked = p.linked,
            dateLine = dateLine(today),
            stampDate = today.dotted(),
            title = homeTitle(p.linked, p.myRole, p.myName, p.partnerName),
            hero = hero,
            tags = if (hero != null && t != null) homeTags(hero.phase, p.myRole, t, upcomingEvents, today, daysTogether) else emptyList(),
            letter = todayLetter(inbox, p.partnerId, p.partnerName, today, archive = hero?.phase == HomePhase.ARCHIVE),
            pendingLetters = if (!p.linked && p.myRole == Role.GOMSIN) pendingLetters(inbox, me, p.myName, today) else emptyList(),
            ranks = if (!p.linked && p.myRole == Role.SOLDIER && hero?.phase == HomePhase.SERVING) t?.let { rankRows(it, today) }.orEmpty() else emptyList(),
            partnerName = partnerName,
            sentMissYouToday = sent,
            dayLine = dayLinePrompt,
            enlistNudge = needsEnlistNudge(p.linked, t, today),
        )
    }.combine(pendingInvite) { s, invite ->
        s.copy(invitePending = !s.linked && invite != null && invite.expireAtMs > clock.now().toEpochMilliseconds())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.SendMissYou -> viewModelScope.launch { missYou.send() }
            is HomeIntent.DismissDayLine -> viewModelScope.launch { dayLineCards?.dismiss(intent.eventId) }
        }
    }
}

/** 연결 전이고 입대일이 아직이면 홈에서 연결을 강하게 권한다(planning §3 영장 → 입대 전). */
internal fun needsEnlistNudge(linked: Boolean, timeline: ServiceTimeline?, today: LocalDate): Boolean =
    !linked && timeline != null && timeline.enlistDate > today

internal fun weekday(d: LocalDate) = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]

internal fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
