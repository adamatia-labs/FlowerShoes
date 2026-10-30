package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.SEOUL
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant

/**
 * 편지함 봉투 표시(design.md): 봉인("새") = 안 읽은 편지 · 폴라로이드 = 그림이 든 편지 · 소인("보냄") = 내가 보낸 편지 · 우표(D-day) = 그 밖의 받은 편지.
 * 혼자 모드(연결 전)에서 내가 쓴 편지는 "대기" 우표 — 연결하는 순간 한꺼번에 간다.
 * 화면은 이것을 [com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeMark] 로 그린다(폴라로이드에는 실제 그림).
 */
sealed interface Envelope {
    data class New(val stamp: String) : Envelope
    data object Drawing : Envelope
    data class Sent(val date: String) : Envelope
    data class Received(val stamp: String) : Envelope
    data object Waiting : Envelope
}

/**
 * 봉투 표시. 앞의 것이 이긴다: 내가 보냄(연결 전이면 [queued] 대기) → 안 읽음 → 그림 → 그 밖. 우표는 편지를 받은 날([on])의 전역 D-day.
 */
internal fun envelopeOf(mine: Boolean, unread: Boolean, hasDrawing: Boolean, on: LocalDate, discharge: LocalDate?, queued: Boolean = false): Envelope = when {
    mine && queued -> Envelope.Waiting
    mine -> Envelope.Sent(postmarkDate(on))
    unread -> Envelope.New(stampText(discharge, on))
    hasDrawing -> Envelope.Drawing
    else -> Envelope.Received(stampText(discharge, on))
}

/** 우표 글자: "D-479" · "D-DAY" · "D+3". 전역일을 모르면(복무 정보 전) 날짜 "10.24". */
internal fun stampText(discharge: LocalDate?, on: LocalDate): String {
    discharge ?: return mmdd(on)
    val left = on.daysUntil(discharge)
    return when {
        left > 0 -> "D-$left"
        left == 0 -> "D-DAY"
        else -> "D+${-left}"
    }
}

/** 소인 날짜 "2026.10.19". */
internal fun postmarkDate(d: LocalDate) = "${d.year}.${mmdd(d)}"

/** 봉투 아래 줄: "민준 · 오늘 오후 9:12" · "민준 · 10.27" · "보낸 편지 · 06.20" · 지난해는 "2025.12.31". */
internal fun envelopeMeta(sender: String, mine: Boolean, at: LocalDateTime, now: LocalDateTime): String {
    val `when` = when {
        at.date == now.date -> "오늘 ${clockText(at)}"
        at.year == now.year -> mmdd(at.date)
        else -> postmarkDate(at.date)
    }
    return (if (mine) "보낸 편지" else sender) + " · " + `when`
}

/** 보낼 편지(혼자 모드) 봉투 아래 줄: "지수 · 10.27 씀". */
internal fun queuedMeta(me: String, at: LocalDateTime) = "$me · ${mmdd(at.date)} 씀"

/** 혼자 모드 편지함 안내 띠: "아직 민준과 연결 전이에요". */
internal fun soloBannerTitle(partner: String) = "아직 ${partner.withAnd()} 연결 전이에요"

/** 예약해 둔 편지(편지 쓰기 › 예약 · 설정됨): "11월 1일 (일) 오전 9시 도착". */
internal fun arrivalLabel(at: LocalDateTime): String {
    val minute = if (at.minute == 0) "" else " ${at.minute}분"
    return "${at.month.ordinal + 1}월 ${at.day}일 (${weekday(at.date)}) ${ampm(at)} ${hour12(at)}시$minute 도착"
}

/** 월 머리: 올해는 "10월", 지난해는 "2025년 12월". */
internal fun monthLabel(d: LocalDate, today: LocalDate): String =
    if (d.year == today.year) "${d.month.ordinal + 1}월" else "${d.year}년 ${d.month.ordinal + 1}월"

/** 순서를 지키며 같은 달끼리 묶는다(편지함은 최신순). */
internal fun <T> groupByMonth(items: List<T>, today: LocalDate, dateOf: (T) -> LocalDate): List<Pair<String, List<T>>> {
    val out = mutableListOf<Pair<String, MutableList<T>>>()
    items.forEach { item ->
        val label = monthLabel(dateOf(item), today)
        if (out.lastOrNull()?.first == label) out.last().second += item else out += label to mutableListOf(item)
    }
    return out
}

/** 읽기 화면 위 막대 제목: "민준이 보낸 편지" · "내가 보낸 편지". */
internal fun readTitle(mine: Boolean, partner: String) = if (mine) "내가 보낸 편지" else "${partner.withSubject()} 보낸 편지"

/** 읽기 화면 위 막대 아래 줄: "10월 28일 (수) 오후 9:12". */
internal fun readWhen(at: LocalDateTime) = "${at.month.ordinal + 1}월 ${at.day}일 (${weekday(at.date)}) ${clockText(at)}"

/** 예약 편지가 열리는 때: "11월 1일 오전 9시" · "11월 1일 오후 9시 30분". */
internal fun opensAt(at: LocalDateTime): String {
    val minute = if (at.minute == 0) "" else " ${at.minute}분"
    return "${at.month.ordinal + 1}월 ${at.day}일 ${ampm(at)} ${hour12(at)}시$minute"
}

/** 열릴 때까지 남은 시간: "3일 11시간 48분" · "11시간 48분" · "1분"(분은 올림). */
internal fun countdown(now: LocalDateTime, at: LocalDateTime): String {
    val ms = (at.toInstant(SEOUL) - now.toInstant(SEOUL)).inWholeMilliseconds.coerceAtLeast(0)
    val minutes = (ms + 59_999) / 60_000
    val d = minutes / (24 * 60)
    val h = minutes / 60 % 24
    val m = minutes % 60
    return buildList {
        if (d > 0) add("${d}일")
        if (d > 0 || h > 0) add("${h}시간")
        add("${m}분")
    }.joinToString(" ")
}

private fun mmdd(d: LocalDate) = "${(d.month.ordinal + 1).toString().padStart(2, '0')}.${d.day.toString().padStart(2, '0')}"

private fun ampm(at: LocalDateTime) = if (at.hour < 12) "오전" else "오후"

private fun hour12(at: LocalDateTime) = (at.hour % 12).let { if (it == 0) 12 else it }

/** "오후 9:12". */
private fun clockText(at: LocalDateTime) = "${ampm(at)} ${hour12(at)}:${at.minute.toString().padStart(2, '0')}"
