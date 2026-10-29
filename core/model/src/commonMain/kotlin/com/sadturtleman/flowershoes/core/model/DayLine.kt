package com.sadturtleman.flowershoes.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.serialization.Serializable

/** 그날의 한 줄(40자). 빈 글은 지운 줄이다. [at] 은 쓴 시각(ms) — 줄 단위 LWW. */
@Serializable
data class DayLine(val text: String, val at: Long) {
    companion object {
        const val MAX_CHARS = 40

        /** 일정이 끝나고 며칠 동안 홈 카드를 띄우는지. */
        const val CARD_DAYS = 3
    }
}

/** 두 묶음을 줄마다 늦은 쪽으로 합친다. */
fun mergeDayLines(a: Map<String, DayLine>, b: Map<String, DayLine>): Map<String, DayLine> =
    (a.keys + b.keys).associateWith { k -> listOfNotNull(a[k], b[k]).maxBy { it.at } }

/** 실제로 만나는 날(면회 · 외박 · 휴가) — 그날의 한 줄을 남기는 일정. 군인 혼자 등록한 휴가도 포함. */
val EventType.hasDayLine: Boolean get() = this == EventType.VISIT || this == EventType.OVERNIGHT || this == EventType.VACATION

val Event.hasDayLine: Boolean get() = type.hasDayLine

/** 내가 쓴 한 줄(지웠으면 null). */
fun Event.myDayLine(me: String): String? = dayLines[me]?.text?.takeIf { it.isNotBlank() }

/** 상대가 쓴 한 줄(안 썼으면 null — 빈칸을 보여 주지 않는다). */
fun Event.partnerDayLine(me: String): String? =
    dayLines.filterKeys { it != me }.values.filter { it.text.isNotBlank() }.maxByOrNull { it.at }?.text

/**
 * 홈의 그날의 한 줄 카드에 띄울 일정. 끝난 다음 날부터 [DayLine.CARD_DAYS] 일 동안, 내가 아직 안 썼고 닫지 않은 것 중
 * 가장 최근에 끝난 하나. 상대가 썼는지는 보지 않는다.
 */
fun dayLineCard(events: List<Event>, me: String, today: LocalDate, dismissed: Set<String>): Event? =
    events.filter { e ->
        e.deletedAt == null && e.hasDayLine && e.id !in dismissed && e.myDayLine(me) == null &&
            e.end.date.daysUntil(today) in 1..DayLine.CARD_DAYS
    }.maxByOrNull { it.end }

/** 홈 카드 · 알림 문구: "어제 면회, 그날의 한 줄 남길까요?". 끝난 날이 그 전이면 그저께 · 날짜. */
fun Event.dayLineAsk(today: LocalDate): String {
    val day = when (end.date.daysUntil(today)) {
        1 -> "어제"
        2 -> "그저께"
        else -> "${end.date.month.number}월 ${end.date.day}일"
    }
    return "$day ${type.koreanName}, 그날의 한 줄 남길까요?"
}
