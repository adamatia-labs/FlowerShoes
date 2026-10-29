package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.military.koreanName
import com.sadturtleman.flowershoes.core.military.promotionName
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.koreanName
import com.sadturtleman.flowershoes.core.data.SEOUL
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

// 홈 v2(Figma 2 · 홈 350:686)에 들어갈 글자. 화면은 이것만 그린다.

/** 머리말 날짜: "10월 28일 수요일". */
internal fun dateLine(today: LocalDate) = "${today.monthDay()} ${weekday(today)}요일"

/** 머리말 제목. 연결 뒤 "지수 & 민준"(내 이름 먼저), 혼자 모드 곰신 "지수의 꽃신" · 군인 "전역일 계산기". */
internal fun homeTitle(linked: Boolean, role: Role, myName: String, partnerName: String): String = when {
    linked -> "$myName & $partnerName"
    role == Role.SOLDIER -> "전역일 계산기"
    else -> "${myName}의 꽃신"
}

/** 진급 일정 한 줄(혼자 모드 군인): "상병(예정)" · "2026.12.20 · D-53". */
data class RankRow(val name: String, val detail: String)

/** 이병 다음 계급부터. 지난 진급은 "지남", 남은 진급은 "예정"을 붙인다(#46). */
internal fun rankRows(t: ServiceTimeline, today: LocalDate): List<RankRow> = t.ranks.drop(1).map { step ->
    val left = today.daysUntil(step.from)
    if (left < 0) RankRow(step.rank.koreanName, "${step.from.dotted()} · 지남")
    else RankRow(step.rank.expectedName, "${step.from.dotted()} · ${if (left == 0) "오늘" else "D-$left"}")
}

/** 복무 단계: 입대 전 · 복무 중 · 전역 당일 · 보관 모드(전역 뒤). */
enum class HomePhase { BEFORE_ENLIST, SERVING, DISCHARGE_DAY, ARCHIVE }

/**
 * 홈 위쪽 큰 손글씨 D-day. "민준이 전역까지" + "D-479" + 복무 진행(입대 · 진행률 · 전역).
 * 입대 전 · 보관 모드는 진행 대신 [detail] 한 줄("공군 · 입대 2026.11.09 (월)").
 */
data class HomeHero(
    val phase: HomePhase,
    val caption: String,
    val value: String,
    val progress: Float = 0f,
    val start: String = "",
    val end: String = "",
    val percent: String = "",
    /** 군인 테마 개월 칸 수(전체 복무 개월). */
    val months: Int = 0,
    val detail: String? = null,
)

/**
 * 큰 D-day. 곰신은 연인 이름("민준이 전역까지"), 군인은 내 것이라 "전역까지"(혼자면 "민준 전역까지").
 * 연인 이름을 모르면(혼자 모드 곰신) 이름 없이 쓴다. 입대일이 없으면 null.
 */
internal fun homeHero(t: ServiceTimeline?, branch: Branch?, today: LocalDate, role: Role, myName: String, partnerName: String?): HomeHero? {
    if (t == null) return null
    val soldierName = if (role == Role.SOLDIER) myName.takeIf { partnerName == null } else partnerName
    val subject = soldierName?.let { if (role == Role.GOMSIN) "${it.withSubject()} " else "$it " }.orEmpty()
    val toEnlist = today.daysUntil(t.enlistDate)
    if (toEnlist > 0) {
        return HomeHero(
            HomePhase.BEFORE_ENLIST, "${subject}입대까지", "D-$toEnlist",
            detail = listOfNotNull(branch?.koreanName, "입대 ${t.enlistDate.dotted()} (${weekday(t.enlistDate)})").joinToString(" · "),
        )
    }
    val left = t.daysLeft(today)
    if (left < 0) {
        return HomeHero(
            HomePhase.ARCHIVE, "함께 견딘", "${t.totalDays}일",
            detail = "입대 ${t.enlistDate.dotted()} – 전역 ${t.dischargeDate.dotted()} · 보관 모드",
        )
    }
    val p = t.progress(today).coerceIn(0.0, 1.0)
    val greeting = soldierName?.takeIf { role == Role.GOMSIN }?.let { "${it.withVocative()}, 고생했어" } ?: "고생했어"
    return HomeHero(
        phase = if (left == 0) HomePhase.DISCHARGE_DAY else HomePhase.SERVING,
        caption = if (left == 0) greeting else "${subject}전역까지",
        value = if (left == 0) "D-DAY" else "D-$left",
        progress = p.toFloat(),
        start = "입대 ${t.enlistDate.dotted()}",
        end = "전역 ${t.dischargeDate.dotted()}",
        percent = "${(p * 1000).toInt() / 10.0}%",
        months = serviceMonths(t),
    )
}

/** 전체 복무 개월(전역 다음 날까지의 달 수): 18개월 복무 → 18칸. */
internal fun serviceMonths(t: ServiceTimeline): Int {
    val after = t.dischargeDate.plus(DatePeriod(days = 1))
    return ((after.year - t.enlistDate.year) * 12 + (after.month.number - t.enlistDate.month.number)).coerceAtLeast(1)
}

/** 받침이 있으면 "민준이", 없으면 "현우"(부르는 말 앞 이름). 한글이 아니면 그대로. */
internal fun String.withSubject(): String = if (hasFinalConsonant()) "${this}이" else this

/** 받침이 있으면 "민준아", 없으면 "현우야". */
internal fun String.withVocative(): String = if (hasFinalConsonant()) "${this}아" else "${this}야"

private fun String.hasFinalConsonant(): Boolean {
    val last = lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last - '가') % 28 != 0
}

/** 종이 태그 한 장: 라벨 + 값. [hand] 면 손글씨(Hand/L), 아니면 숫자(D-day/S). */
data class HomeTag(val label: String, val value: String, val hand: Boolean)

/**
 * 종이 태그(보여 주기만 하는 물건). 복무 중: 다음 휴가 + 곰신은 다음 만남(면회 · 외박, 휴가는 옆 태그와 겹쳐 뺀다) / 군인은 다음 진급("예정", #46).
 * 입대 전: 입대일 + 함께한 날. 일정이 없으면 그 태그는 뺀다. 보관 모드에는 없다.
 */
internal fun homeTags(phase: HomePhase, role: Role, t: ServiceTimeline, events: List<Event>, today: LocalDate, daysTogether: Int?): List<HomeTag> {
    val live = events.filter { it.deletedAt == null && it.start.date >= today }
    fun next(vararg types: EventType) = live.filter { it.type in types }.minByOrNull { it.start }?.start?.date
    return when (phase) {
        HomePhase.BEFORE_ENLIST -> listOfNotNull(
            HomeTag("입대일", t.enlistDate.monthDay(), hand = true),
            daysTogether?.let { HomeTag("함께한 날", "D+$it", hand = false) },
        )
        HomePhase.SERVING, HomePhase.DISCHARGE_DAY -> listOfNotNull(
            next(EventType.VACATION)?.let { HomeTag("다음 휴가", dDay(today, it), hand = false) },
            if (role == Role.SOLDIER) t.nextRank(today)?.let { HomeTag(it.rank.promotionName, dDay(today, it.from), hand = false) }
            else next(EventType.VISIT, EventType.OVERNIGHT)?.let { HomeTag("다음 만남", it.monthDay(), hand = true) },
        )
        HomePhase.ARCHIVE -> emptyList()
    }
}

private fun dDay(today: LocalDate, date: LocalDate) = today.daysUntil(date).let { if (it == 0) "D-DAY" else "D-$it" }

internal fun LocalDate.monthDay() = "${month.number}월 ${day}일"

/** 편지 한 줄: 실제 첫 줄(자리표시 대신) + "민준 · 오후 9:12". */
data class HomeLetter(val id: String, val line: String, val meta: String)

/**
 * "오늘 온 편지": 연인이 보내 오늘(서울) 도착한 편지 중 가장 늦은 것. 보관 모드는 날짜와 상관없이 가장 최근 편지("최근 편지", 날짜로 표시).
 * [letters] 는 공개 시각이 지난 것(편지함)이다.
 */
internal fun todayLetter(letters: List<Letter>, partnerId: String?, partnerName: String, today: LocalDate, archive: Boolean): HomeLetter? {
    if (partnerId == null) return null
    val latest = letters.filter { it.authorId == partnerId && it.deletedAt == null && (archive || it.visibleAt.seoulDate() == today) }
        .maxByOrNull { it.visibleAt } ?: return null
    val at = latest.visibleAt.toLocalDateTime(SEOUL)
    return HomeLetter(latest.id, latest.firstLine(), "$partnerName · ${if (archive) at.date.monthDay() else clockTime(at)}")
}

/** 혼자 모드: 연결되면 한꺼번에 갈 내 편지(최근 것 먼저). 오늘 쓴 것은 "오늘 오후 8:40", 그 전은 날짜. */
internal fun pendingLetters(letters: List<Letter>, me: String, myName: String, today: LocalDate): List<HomeLetter> =
    letters.filter { it.authorId == me && it.deletedAt == null }.sortedByDescending { it.visibleAt }.map {
        val at = it.visibleAt.toLocalDateTime(SEOUL)
        HomeLetter(it.id, it.firstLine(), "$myName · ${if (at.date == today) "오늘 ${clockTime(at)}" else at.date.monthDay()}")
    }

private fun Letter.firstLine(): String =
    blocks.filterIsInstance<LetterBlock.Text>().firstNotNullOfOrNull { b -> b.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } }
        ?: title?.takeIf { it.isNotBlank() }
        ?: if (drawingCount > 0) "그림 ${drawingCount}장" else "사진 ${photoCount}장"

private fun Instant.seoulDate() = toLocalDateTime(SEOUL).date

/** "오후 9:12" · "오전 7:02". */
private fun clockTime(at: LocalDateTime): String {
    val h = at.hour % 12
    return "${if (at.hour < 12) "오전" else "오후"} ${if (h == 0) 12 else h}:${at.minute.toString().padStart(2, '0')}"
}
