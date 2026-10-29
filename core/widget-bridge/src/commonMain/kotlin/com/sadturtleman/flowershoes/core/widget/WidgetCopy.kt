package com.sadturtleman.flowershoes.core.widget

import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.database.DrawingEntity
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.military.promotionName
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Status
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.Path

// 위젯 v2(Figma 9 · 위젯 383:5321)에 들어갈 글자. 위젯은 스냅샷의 글자를 그대로 그린다.

/** 스냅샷을 만드는 데 드는 값. [WidgetSnapshotPublisher] 가 DB 에서 모아 온다. */
internal data class WidgetInputs(
    val theme: String,
    val role: Role?,
    val myName: String,
    val partnerId: String?,
    val partnerName: String?,
    val timeline: ServiceTimeline?,
    /** 오늘 이후 일정(매년 반복 기념일은 그 해로 옮긴 것). */
    val events: List<Event> = emptyList(),
    val status: Status? = null,
    val missYouFromPartnerToday: Int = 0,
    val missYouSentToday: Int = 0,
    val missYouLastSentAtMs: Long? = null,
    /** 공개 시각이 지난 편지(편지함). */
    val letters: List<Letter> = emptyList(),
    val readLetterIds: Set<String> = emptySet(),
    val drawing: DrawingEntity? = null,
    val widgetFrame: String? = null,
)

/** 위젯 "보냈어요" 상태가 보이는 시간. */
const val JUST_SENT_MS = 60_000L

/** [nowMs] 에 위젯이 "보냈어요" 상태인지. 스냅샷이 늦게 다시 써져도 1분이 지나면 기본 상태로 그린다. */
fun WidgetSnapshot.justSent(nowMs: Long): Boolean = missYouJustSentAtMs?.let { nowMs - it in 0 until JUST_SENT_MS } == true

internal fun widgetSnapshot(inputs: WidgetInputs, attachmentsRoot: Path, now: Instant): WidgetSnapshot {
    val today = now.toLocalDateTime(SEOUL).date
    val partner = inputs.partnerId?.takeIf { it.isNotEmpty() }
    val partnerName = inputs.partnerName?.takeIf { partner != null && it.isNotBlank() }
    val hero = inputs.timeline?.let { hero(it, today, inputs.role, inputs.myName, partnerName) }
    val upcoming = inputs.events.filter { it.deletedAt == null && it.start.date >= today }
    fun next(vararg types: EventType) = upcoming.filter { it.type in types }.minByOrNull { it.start }
    val serving = hero?.serving == true
    val meet = next(EventType.VISIT, EventType.OVERNIGHT, EventType.VACATION)
    val drawing = inputs.drawing
    // 상태는 구독한 때의 시각으로 걸러 오므로, 끝난 상태는 여기서 한 번 더 뺀다.
    val status = inputs.status?.takeIf { st -> st.expiresAt.let { it == null || it > now } }
    val letter = letterRow(inputs.letters, partner, partnerName, inputs.readLetterIds, today)
    return WidgetSnapshot(
        theme = inputs.theme,
        ddayCaption = hero?.caption,
        ddayValue = hero?.value,
        ddayFoot = hero?.foot,
        progress = hero?.progress,
        progressStart = hero?.start,
        progressPercent = hero?.percent,
        progressEnd = hero?.end,
        tagLeave = next(EventType.VACATION)?.takeIf { serving }?.let { WidgetTag("다음 휴가", dDay(today, it.start.date)) },
        tagMeet = next(EventType.VISIT, EventType.OVERNIGHT)?.takeIf { serving }?.let { WidgetTag("다음 만남", dDay(today, it.start.date)) },
        tagRank = inputs.timeline?.nextRank(today)?.takeIf { serving }?.let { WidgetTag(it.rank.promotionName, dDay(today, it.from)) },
        partnerName = partnerName,
        partnerStatusCaption = partnerName?.let { "${it.withTopic()} 지금" },
        partnerStatus = status?.label,
        partnerStatusColor = status?.color,
        partnerStatusUntil = status?.let { st ->
            st.expiresAt?.let { "${dayClock(it, today)}까지" } ?: "${dayClock(st.createdAt, today)}부터"
        },
        nextEventDays = meet?.let { today.daysUntil(it.start.date) },
        nextEventValue = meet?.let { dDay(today, it.start.date) },
        nextEventType = meet?.type?.name,
        nextEventLine = meet?.let { "${it.start.date.monthDay()} ${it.title.ifBlank { it.type.koreanLabel() }}" },
        nextEventWhen = meet?.let { e ->
            val time = e.start.takeIf { it.hour != 0 || it.minute != 0 }?.let { " ${it.hour.pad()}:${it.minute.pad()}" }.orEmpty()
            "${weekday(e.start.date)}요일$time"
        },
        unreadLetters = letter?.unread ?: 0,
        letterLine = letter?.line,
        letterMeta = letter?.meta,
        letterId = letter?.id,
        missYouFromPartnerToday = inputs.missYouFromPartnerToday,
        missYouSentToday = inputs.missYouSentToday,
        missYouJustSentAtMs = inputs.missYouLastSentAtMs?.takeIf { now.toEpochMilliseconds() - it in 0 until JUST_SENT_MS },
        linked = partner != null,
        drawingDocPath = drawing?.docAttachmentId?.let { (attachmentsRoot / it).toString() },
        drawingPngPath = drawing?.takeIf { it.pngAttachmentId != it.docAttachmentId }?.let { (attachmentsRoot / it.pngAttachmentId).toString() },
        drawingShape = drawing?.shape,
        drawingLetterId = drawing?.letterId,
        drawingId = drawing?.id,
        drawingCaption = drawing?.caption?.takeIf { it.isNotBlank() },
        drawingMeta = drawing?.let { d ->
            val day = Instant.fromEpochMilliseconds(d.receivedAtMs ?: d.sentAtMs).toLocalDateTime(SEOUL).date
            listOfNotNull(partnerName, relativeDay(day, today)).joinToString(" · ")
        },
        widgetFrame = inputs.widgetFrame,
        updatedAtMs = 0,
    )
}

private class Hero(
    val caption: String,
    val value: String,
    val foot: String? = null,
    val serving: Boolean = false,
    val progress: Float? = null,
    val start: String? = null,
    val percent: String? = null,
    val end: String? = null,
)

/**
 * 큰 D-day(홈 v2 와 같은 규칙). 곰신은 연인 이름("민준이 전역까지"), 군인은 내 것이라 "전역까지"(혼자면 "민준 전역까지").
 * 아래 줄은 진행률 + 다음 진급("예정", #46). 군인은 "복무 28.6%".
 */
private fun hero(t: ServiceTimeline, today: LocalDate, role: Role?, myName: String, partnerName: String?): Hero {
    val soldierName = if (role == Role.SOLDIER) myName.takeIf { partnerName == null && it.isNotBlank() } else partnerName
    val subject = soldierName?.let { if (role == Role.SOLDIER) "$it " else "${it.withSubject()} " }.orEmpty()
    val toEnlist = today.daysUntil(t.enlistDate)
    if (toEnlist > 0) return Hero("${subject}입대까지", "D-$toEnlist", foot = "입대 ${t.enlistDate.dotted()}")
    val left = t.daysLeft(today)
    if (left < 0) return Hero("함께 견딘", "${t.totalDays}일", foot = "전역 ${t.dischargeDate.dotted()}")
    val p = t.progress(today).coerceIn(0.0, 1.0)
    val percent = "${(p * 1000).toInt() / 10.0}%"
    val rank = t.nextRank(today)?.let { "${it.rank.expectedName} ${dDay(today, it.from)}" }
    return Hero(
        caption = "${subject}전역까지",
        value = if (left == 0) "D-DAY" else "D-$left",
        foot = listOfNotNull(if (role == Role.SOLDIER) "복무 $percent" else percent, rank).joinToString(" · "),
        serving = true,
        progress = p.toFloat(),
        start = "입대 ${t.enlistDate.dotted()}",
        percent = percent,
        end = "전역 ${t.dischargeDate.dotted()}",
    )
}

private class LetterRow(val id: String, val unread: Int, val line: String, val meta: String)

/** 연인이 보낸 편지 중 안 읽은 것이 있으면 "새 편지 N통"(가장 늦은 것의 시각), 없으면 가장 최근 편지의 첫 줄. */
private fun letterRow(letters: List<Letter>, partner: String?, partnerName: String?, read: Set<String>, today: LocalDate): LetterRow? {
    if (partner == null) return null
    val received = letters.filter { it.authorId == partner && it.deletedAt == null }
    val unread = received.filter { it.id !in read }
    val latest = (unread.ifEmpty { received }).maxByOrNull { it.visibleAt } ?: return null
    val at = latest.visibleAt.toLocalDateTime(SEOUL)
    val meta = listOfNotNull(partnerName, if (at.date == today) clockTime(at) else at.date.monthDay()).joinToString(" · ")
    return LetterRow(latest.id, unread.size, if (unread.isNotEmpty()) "새 편지 ${unread.size}통" else latest.firstLine(), meta)
}

private fun Letter.firstLine(): String =
    blocks.filterIsInstance<LetterBlock.Text>().firstNotNullOfOrNull { b -> b.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } }
        ?: title?.takeIf { it.isNotBlank() }
        ?: if (drawingCount > 0) "그림 ${drawingCount}장" else "사진 ${photoCount}장"

private fun dDay(today: LocalDate, date: LocalDate) = today.daysUntil(date).let { if (it == 0) "D-DAY" else "D-$it" }

private fun relativeDay(day: LocalDate, today: LocalDate) = when (day.daysUntil(today)) {
    0 -> "오늘"
    1 -> "어제"
    else -> day.monthDay()
}

/** 상태 시각: 오늘이면 "08:00", 내일이면 "내일 08:00", 그 밖은 "11월 8일 08:00". */
private fun dayClock(at: Instant, today: LocalDate): String {
    val t = at.toLocalDateTime(SEOUL)
    val clock = "${t.hour.pad()}:${t.minute.pad()}"
    return when (today.daysUntil(t.date)) {
        0 -> clock
        1 -> "내일 $clock"
        else -> "${t.date.monthDay()} $clock"
    }
}

/** "오후 9:12" · "오전 7:02". */
private fun clockTime(at: LocalDateTime): String {
    val h = at.hour % 12
    return "${if (at.hour < 12) "오전" else "오후"} ${if (h == 0) 12 else h}:${at.minute.pad()}"
}

private fun Int.pad() = toString().padStart(2, '0')

private fun LocalDate.monthDay() = "${month.number}월 ${day}일"

private fun LocalDate.dotted() = "$year.${month.number.pad()}.${day.pad()}"

private fun weekday(d: LocalDate) = when (d.dayOfWeek) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

private fun String.hasFinalConsonant(): Boolean {
    val last = lastOrNull() ?: return false
    return last in '가'..'힣' && (last - '가') % 28 != 0
}

/** 받침이 있으면 "민준이", 없으면 "현우"(홈 v2 와 같다). */
private fun String.withSubject(): String = if (hasFinalConsonant()) "${this}이" else this

/** "민준은" · "지수는". */
private fun String.withTopic(): String = if (hasFinalConsonant()) "${this}은" else "${this}는"
