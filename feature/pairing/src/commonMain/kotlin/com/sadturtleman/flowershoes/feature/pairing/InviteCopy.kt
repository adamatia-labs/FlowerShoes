package com.sadturtleman.flowershoes.feature.pairing

import com.sadturtleman.flowershoes.core.data.sync.Outbox
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

private val SEOUL = TimeZone.of("Asia/Seoul")

/** 편지 · 그림이 이만큼 이상이면 연결 뒤 "보내는 중" 화면을 띄운다(적으면 조용히 동기화). */
internal const val PROGRESS_THRESHOLD = 10

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

/** "편지 23통 · 그림 4장". */
internal fun Outbox.summary(): String =
    listOfNotNull("편지 ${letters}통".takeIf { letters > 0 }, "그림 ${drawings}장".takeIf { drawings > 0 }).joinToString(" · ")

/** 연결 모달 본문: "혼자 쓴 편지 23통과 그림 4장도 같이 보낼게요." 편지 · 그림이 없으면 null. */
internal fun Outbox.linkedBody(): String? = when {
    letters > 0 && drawings > 0 -> "혼자 쓴 편지 ${letters}통과 그림 ${drawings}장도 같이 보낼게요."
    letters > 0 -> "혼자 쓴 편지 ${letters}통도 같이 보낼게요."
    drawings > 0 -> "혼자 그린 그림 ${drawings}장도 같이 보낼게요."
    else -> null
}

internal fun Outbox.showsProgress(): Boolean = letters + drawings >= PROGRESS_THRESHOLD
