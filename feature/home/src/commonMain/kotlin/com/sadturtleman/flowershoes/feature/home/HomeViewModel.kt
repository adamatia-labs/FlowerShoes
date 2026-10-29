package com.sadturtleman.flowershoes.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.StatusRepository
import com.sadturtleman.flowershoes.core.data.ticker
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.military.koreanName
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Status
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** D-day 카드(Figma D-day Card)에 넣을 글자. 계산은 :core:military 가 끝낸다. */
data class DdayCard(
    val days: String,
    val rank: String,
    val percent: String,
    val date: String,
    val progress: Float,
)

/** 군인 홈의 세 칸(다음 진급 · 남은 휴가 · 복무). */
data class SoldierStats(val nextRank: String?, val leaveLeft: Int?, val servedDay: Int)

/** 다음 휴가 카드: "다음 휴가 · 11.14 (금)" / "정기휴가 9일 · D-24". */
data class NextLeave(val title: String, val detail: String)

/** 상대 상태 위젯(Widget/Status). */
data class PartnerStatus(val name: String, val initial: String, val label: String?, val dot: Long?, val ago: String?, val missYou: Int)

/** 오늘 도착할 메시지 한 줄(Scheduled Message). */
data class ScheduledRow(val id: String, val time: String, val message: String)

data class HomeState(
    val loading: Boolean = true,
    val role: Role = Role.GOMSIN,
    val linked: Boolean = false,
    /** "민준 ♥ 지수" — 군인 먼저. 연결 전이면 null(제목 "홈"). */
    val couple: Pair<String, String>? = null,
    val daysTogether: Int? = null,
    val dday: DdayCard? = null,
    val soldier: SoldierStats? = null,
    val nextLeave: NextLeave? = null,
    val partner: PartnerStatus? = null,
    val sentMissYouToday: Int = 0,
    val scheduled: List<ScheduledRow> = emptyList(),
)

sealed interface HomeIntent {
    data object SendMissYou : HomeIntent
}

private val SEOUL = TimeZone.of("Asia/Seoul")

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    profile: ProfileRepository,
    events: EventRepository,
    private val missYou: MissYouRepository,
    status: StatusRepository,
    letters: LetterRepository,
    leave: com.sadturtleman.flowershoes.core.data.LeaveRepository? = null,
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

    private val partnerFeed: Flow<Pair<Status?, Int>> = people.flatMapLatest { p ->
        if (p.partnerId == null) flowOf(null to 0)
        else combine(status.observeLatest(p.partnerId), missYou.observeTodayFrom(p.partnerId)) { s, n -> s to n }
    }

    private val leaves: Flow<List<Event>> = clock.ticker().flatMapLatest {
        val today = clock.today()
        events.observeRange(LocalDate(today.year - 2, 1, 1), LocalDate(today.year + 3, 12, 31))
    }.map { list -> list.filter { it.type == EventType.VACATION && it.deletedAt == null } }

    /** 받은 휴가(휴가 관리 › 출처 내역). 정기휴가는 군종으로 계산한다. */
    private val granted: Flow<List<LeaveGrant>> = leave?.observeGrants() ?: flowOf(emptyList())

    val state: StateFlow<HomeState> = combine(
        combine(people, profile.observeTimelines(), leaves, granted) { p, t, l, g -> Triple(p, t, l to g) },
        partnerFeed,
        missYou.observeTodayFrom(me),
        letters.observeMyScheduledToday(),
        clock.ticker(),
    ) { (p, timelines, leaveInfo), (partnerStatus, received), sent, scheduled, now ->
        val (vacations, grants) = leaveInfo
        val today = now.toLocalDateTime(SEOUL).date
        val pair = timelines.firstOrNull()
        val t = pair?.second
        val branch = pair?.first?.branch
        val dday = t?.let {
            val left = it.daysLeft(today)
            DdayCard(
                days = when { left > 0 -> "D-$left"; left == 0 -> "D-DAY"; else -> "전역 +${-left}" },
                rank = listOfNotNull(it.rankAt(today)?.rank?.koreanName, branch?.koreanName).joinToString(" · "),
                percent = "복무 ${(it.progress(today).coerceIn(0.0, 1.0) * 1000).toInt() / 10.0}%",
                date = "${it.dischargeDate.dotted()} 전역",
                progress = it.progress(today).toFloat(),
            )
        }
        // 휴가 하나의 풀: 정기(군종) + 받은 휴가 − 휴가 일정 일수. 초과 사용이면 음수.
        val pool = LeavePool.of(branch, grants, vacations)
        val soldier = t?.let {
            val next = it.nextRank(today)
            SoldierStats(
                nextRank = next?.let { n -> "${n.rank.koreanName} · D-${today.daysUntil(n.from)}" },
                leaveLeft = pool.takeIf { p -> p.total > 0 }?.remaining,
                servedDay = it.servedDays(today),
            )
        }
        val nextLeave = vacations.filter { it.start.date >= today }.minByOrNull { it.start }?.let { e ->
            val days = e.start.date.daysUntil(e.end.date) + 1
            NextLeave(
                title = "다음 휴가 · ${e.start.date.month.number}.${e.start.date.day} (${weekday(e.start.date)})",
                detail = "${e.title.ifBlank { "휴가" }} ${days}일 · ${today.daysUntil(e.start.date).let { if (it == 0) "오늘" else "D-$it" }}",
            )
        }
        val soldierName = if (p.myRole == Role.SOLDIER) p.myName else p.partnerName
        val gomsinName = if (p.myRole == Role.SOLDIER) p.partnerName else p.myName
        HomeState(
            loading = false,
            role = p.myRole,
            linked = p.linked,
            couple = if (p.linked) soldierName to gomsinName else null,
            daysTogether = (p.anniversary ?: p.linkedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(SEOUL).date })
                ?.let { it.daysUntil(today) + 1 },
            dday = dday,
            soldier = soldier,
            nextLeave = nextLeave,
            partner = if (p.linked) PartnerStatus(
                name = p.partnerName,
                initial = p.partnerName.take(1),
                label = partnerStatus?.label,
                dot = partnerStatus?.color,
                ago = partnerStatus?.createdAt?.let { ago(it, now) },
                missYou = received,
            ) else null,
            sentMissYouToday = sent,
            scheduled = scheduled.map { l ->
                val at = l.visibleAt.toLocalDateTime(SEOUL)
                ScheduledRow(
                    id = l.id,
                    time = "오늘 ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')} 도착 예정",
                    message = l.blocks.filterIsInstance<LetterBlock.Text>().joinToString(" ") { it.text }.ifBlank { "그림 ${l.drawingCount}장" },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.SendMissYou -> viewModelScope.launch { missYou.send() }
        }
    }
}

internal fun weekday(d: LocalDate) = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]

/** "방금 · 3분 전 · 2시간 전 · 어제 · 3일 전". */
internal fun ago(at: Instant, now: Instant): String {
    val m = (now - at).inWholeMinutes
    return when {
        m < 1 -> "방금"
        m < 60 -> "${m}분 전"
        m < 24 * 60 -> "${m / 60}시간 전"
        m < 48 * 60 -> "어제"
        else -> "${m / (24 * 60)}일 전"
    }
}

internal fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

