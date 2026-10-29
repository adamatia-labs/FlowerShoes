package com.sadturtleman.flowershoes.feature.memory

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * 추억 목록의 월 페이지 한 장(Figma 추억 · 목록 `367:82`)에 붙이는 물건:
 * 폴라로이드(장 표지) · 작은 봉투 · 만남 꼬리표 + 아래 한 줄. 아무것도 없으면 점선 빈 페이지.
 */
data class MonthPage(
    /** 작은 봉투 글자: 제목 붙은 가장 최근 편지 제목, 없으면 "편지". 편지가 없으면 null. */
    val envelope: String?,
    /** 만남 꼬리표: 그 달 가장 최근 만남. */
    val meet: MeetCard?,
    /** 아래 한 줄: (날짜, 가장 최근 글 있는 편지의 글). */
    val lastLine: Pair<String, String>?,
    val empty: Boolean,
)

fun monthPage(m: MonthDetail): MonthPage = MonthPage(
    envelope = if (m.letters.isEmpty()) null else m.letters.firstNotNullOfOrNull { it.title?.takeIf(String::isNotBlank) } ?: "편지",
    meet = m.meets.maxByOrNull { it.date },
    lastLine = m.letters.firstOrNull { it.text.isNotBlank() }?.let { it.date.md() to it.text },
    empty = m.letters.isEmpty() && m.meets.isEmpty(),
)

/** 한 달 타임라인의 하루(Figma 추억 · 한 달 `367:266`): 날짜 · 요일 옆에 그날 붙은 물건. */
data class TimelineDay(val date: LocalDate, val items: List<TimelineItem>)

sealed interface TimelineItem {
    /** 그림만은 폴라로이드, 나머지는 봉투. */
    data class Letter(val letter: MonthLetter) : TimelineItem
    data class Meet(val meet: MeetCard) : TimelineItem
}

/** 최근 날부터. 같은 날엔 만남 꼬리표 다음 편지(최근 것 먼저). */
fun monthTimeline(m: MonthDetail): List<TimelineDay> {
    val meets = m.meets.groupBy { it.date }
    val letters = m.letters.groupBy { it.date }
    return (meets.keys + letters.keys).sortedDescending().map { d ->
        TimelineDay(d, meets[d].orEmpty().map { TimelineItem.Meet(it) } + letters[d].orEmpty().map { TimelineItem.Letter(it) })
    }
}

/** 봉투 표시(design.md): 소인 = 내가 보낸 편지 · 봉인 = 안 읽은 편지 · 폴라로이드 = 그림이 든 편지 · 우표 = 그 밖의 받은 편지. */
enum class LetterMark { Sent, Seal, Drawing, Stamp }

fun MonthLetter.mark(): LetterMark = when {
    mine -> LetterMark.Sent
    unread -> LetterMark.Seal
    kind != LetterKind.TEXT -> LetterMark.Drawing
    else -> LetterMark.Stamp
}

/** 우표 글자: 편지 날 기준 전역 D-day("D-479"). 전역일을 모르거나 지났으면 날짜("10.28"). */
fun stampText(date: LocalDate, discharge: LocalDate?): String {
    val left = discharge?.let { date.daysUntil(it) }
    return when {
        left == null || left < 0 -> date.md()
        left == 0 -> "D-DAY"
        else -> "D-$left"
    }
}
