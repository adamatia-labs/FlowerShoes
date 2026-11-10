package com.sadturtleman.flowershoes.feature.pairing

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

private val SEOUL = TimeZone.of("Asia/Seoul")

private fun String.hasBatchim(): Boolean {
    val last = lastOrNull() ?: return false
    return last in '가'..'힣' && (last - '가') % 28 != 0
}

/** "민준이랑" · "지수랑". */
internal fun String.withRang(): String = this + if (hasBatchim()) "이랑" else "랑"

/** "민준이" · "지수가". */
internal fun String.withSubject(): String = this + if (hasBatchim()) "이" else "가"

/** "민준과" · "지수와". */
internal fun String.withAnd(): String = this + if (hasBatchim()) "과" else "와"

/** 링크 만료 안내(연인 초대 · 링크 공유): "내일 오전 9:41까지 쓸 수 있어요". */
internal fun expiryCaption(expireAt: Instant, now: Instant): String {
    val at = expireAt.toLocalDateTime(SEOUL)
    val days = now.toLocalDateTime(SEOUL).date.daysUntil(at.date)
    val day = when (days) {
        0 -> "오늘"
        1 -> "내일"
        else -> "${at.date.month.ordinal + 1}월 ${at.date.day}일"
    }
    val hour12 = (at.hour % 12).let { if (it == 0) 12 else it }
    val time = "${if (at.hour < 12) "오전" else "오후"} $hour12:${at.minute.toString().padStart(2, '0')}"
    return "$day ${time}까지 쓸 수 있어요"
}

