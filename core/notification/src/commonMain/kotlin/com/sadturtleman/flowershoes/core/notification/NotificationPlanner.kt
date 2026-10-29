package com.sadturtleman.flowershoes.core.notification

import kotlin.time.Duration.Companion.minutes
import com.sadturtleman.flowershoes.core.data.ExportHistory
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.dayLineAsk
import com.sadturtleman.flowershoes.core.model.hasDayLine
import com.sadturtleman.flowershoes.core.model.myDayLine
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** 예약할 로컬 알림 하나. [id] 가 같으면 같은 알림(다시 예약하면 덮어쓴다). */
data class LocalNotification(
    val id: String,
    val at: Instant,
    val title: String,
    val body: String,
    /** 누르면 열 곳. kkotsin://letter/{id} 등. */
    val deepLink: String,
)

/**
 * 무엇을 언제 알릴지 정한다(설계 8장). 플랫폼을 모르는 순수 계산.
 *
 * - 예약 편지 공개: 받는 폰이 visibleAt 에 알린다(서버 예약이 없다).
 * - 일정 전날 20:00(알림을 켠 일정만). 매년 반복 기념일은 올해 · 내년의 그날 전날.
 * - 기념일: 전역일 · 입대 100일 단위 · 커플 기념일 일정의 100일 단위 · 매년 반복 기념일의 그날 아침.
 * - 그날의 한 줄: 면회 · 외박 · 휴가가 끝난 다음 날 10:00. 두 폰이 각자 띄우고, 이미 쓴 사람의 폰은 띄우지 않는다.
 * - 내 기록 받아 두기: 마지막으로 내보낸 날(없으면 처음 쓴 날)부터 3개월마다 10:00. 지나친 알림은 다음 3개월 째로.
 *
 * iOS 는 앱당 로컬 알림 64개가 한도다. 가까운 [budget](기본 50)개만 예약하고 나머지는 앱 실행 · 백그라운드 새로고침 때
 * 다시 채운다. 14개는 여유분.
 */
class NotificationPlanner(private val budget: Int = 50) {

    /**
     * [events] 는 매년 반복 기념일을 원래 날 그대로 받는다(해마다 옮기는 건 여기서).
     * [dayLineFor] 는 이 폰 사용자 id — 그날의 한 줄 알림을 누구 기준으로 거를지. null 이면 그날의 한 줄 알림을 띄우지 않는다.
     */
    fun plan(
        now: Instant,
        partnerName: String,
        scheduledLetters: List<Letter>,
        events: List<Event>,
        timeline: ServiceTimeline?,
        eventEve: Boolean = true,
        anniversaryHundreds: Boolean = true,
        anniversaryYears: Boolean = true,
        dayLineFor: String? = null,
        /** 내 기록 내보내기 기록. null 이면(알림을 껐으면) 권유하지 않는다. */
        exports: ExportHistory? = null,
    ): List<LocalNotification> {
        val out = mutableListOf<LocalNotification>()
        val today = now.toLocalDate()
        val live = events.filter { it.deletedAt == null }

        // 보낸 폰 시계 기준이라 몇 초~몇십 초 미래인 일반 편지가 있다 — 그건 예약이 아니다.
        scheduledLetters.filter { it.visibleAt > now + CLOCK_SKEW && it.deletedAt == null }.forEach { l ->
            out += LocalNotification(
                id = "letter-${l.id}",
                at = l.visibleAt,
                title = "예약 편지가 열렸어요",
                body = "${partnerName}이(가) 미리 써 둔 편지를 지금 읽을 수 있어요",
                deepLink = "kkotsin://letter/${l.id}",
            )
        }

        if (eventEve) live.filter { it.alarm }.forEach { original ->
            original.occurrencesIn(LocalDate(today.year, 1, 1), LocalDate(today.year + 1, 12, 31)).forEach { e ->
                val at = LocalDateTime(e.start.date.minus(DatePeriod(days = 1)), LocalTime(20, 0)).toInstant(SEOUL)
                if (at > now) {
                    out += LocalNotification(
                        id = if (original.repeats) "event-${e.id}-${e.start.year}" else "event-${e.id}",
                        at = at,
                        title = "내일은 ${e.title.ifBlank { e.type.koreanName }}",
                        body = if (e.type == EventType.VISIT || e.type == EventType.VACATION) "드디어 만나는 날이에요" else "내일 일정이 있어요",
                        deepLink = "kkotsin://calendar/${e.start.date}",
                    )
                }
            }
        }

        timeline?.let { t ->
            dayAt9(t.dischargeDate)?.takeIf { it > now }?.let {
                out += LocalNotification("dday-discharge", it, "전역을 축하해요!", "기다림이 꽃신이 되는 날이에요", "kkotsin://home")
            }
            // 입대일로부터 100일 단위(100 · 200 · …). 다음 것 몇 개만.
            val served = t.enlistDate.daysUntil(today) + 1
            var n = ((served / 100) + 1) * 100
            repeat(3) {
                val date = t.enlistDate.plus(DatePeriod(days = n - 1))
                if (date < t.dischargeDate) {
                    dayAt9(date)?.takeIf { it > now }?.let { at ->
                        out += LocalNotification("dday-$n", at, "복무 ${n}일째", "벌써 ${n}일을 함께 버텼어요", "kkotsin://home")
                    }
                }
                n += 100
            }
        }

        val couple = live.firstOrNull { it.id == Event.COUPLE_ANNIVERSARY_ID && it.type == EventType.ANNIVERSARY }
        couple?.start?.date?.takeIf { anniversaryHundreds }?.let { a ->
            // 사귄 날을 1일로 센 100일 단위(100 · 200 · …). 다음 것 몇 개만.
            val days = a.daysUntil(today) + 1
            var n = ((days / 100) + 1) * 100
            repeat(3) {
                dayAt9(a.plus(DatePeriod(days = n - 1)))?.takeIf { it > now }?.let { at ->
                    out += LocalNotification("anniv-d$n", at, "우리 ${n}일", "오늘은 함께한 지 ${n}일째예요", "kkotsin://home")
                }
                n += 100
            }
        }

        if (anniversaryYears) live.filter { it.repeats }.forEach { original ->
            original.occurrencesIn(LocalDate(today.year, 1, 1), LocalDate(today.year + 1, 12, 31)).forEach { e ->
                val years = e.start.year - original.start.year
                if (years <= 0) return@forEach
                dayAt9(e.start.date)?.takeIf { it > now }?.let { at ->
                    out += if (original.id == Event.COUPLE_ANNIVERSARY_ID) {
                        LocalNotification("anniv-${e.start.year}", at, "${years}주년이에요", "오늘은 우리 기념일", "kkotsin://home")
                    } else {
                        LocalNotification(
                            "anniv-${e.id}-${e.start.year}", at, "오늘은 ${e.title.ifBlank { e.type.koreanName }}", "${years}년째 돌아온 날",
                            "kkotsin://calendar/${e.start.date}",
                        )
                    }
                }
            }
        }

        if (dayLineFor != null) live.filter { it.hasDayLine && it.myDayLine(dayLineFor) == null }.forEach { e ->
            val nextDay = e.end.date.plus(DatePeriod(days = 1))
            val at = LocalDateTime(nextDay, LocalTime(10, 0)).toInstant(SEOUL)
            if (at > now) {
                out += LocalNotification(
                    id = "dayline-${e.id}",
                    at = at,
                    title = e.dayLineAsk(nextDay),
                    body = e.title.ifBlank { e.type.koreanName },
                    deepLink = "kkotsin://dayline/${e.id}",
                )
            }
        }

        exports?.let { h ->
            val since = (h.lastExportAtMs ?: h.firstRunAtMs)?.let { Instant.fromEpochMilliseconds(it).toLocalDate() } ?: return@let
            fun at(months: Int) = LocalDateTime(since.plus(DatePeriod(months = months)), LocalTime(10, 0)).toInstant(SEOUL)
            var months = EXPORT_EVERY_MONTHS
            while (at(months) <= now) months += EXPORT_EVERY_MONTHS
            out += LocalNotification(
                id = "export-reminder",
                at = at(months),
                title = "내 기록 받아 두기",
                body = if (h.lastExportAtMs != null) "마지막으로 내보낸 지 ${months}개월이 지났어요" else "아직 내 기록을 내보낸 적이 없어요",
                deepLink = "kkotsin://settings/device",
            )
        }

        return out.sortedBy { it.at }.distinctBy { it.id }.take(budget)
    }

    private fun dayAt9(date: LocalDate): Instant? = LocalDateTime(date, LocalTime(9, 0)).toInstant(SEOUL)

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(SEOUL).date

    private companion object {
        /** 이보다 가까운 미래의 편지는 두 폰 시계 차이로 본다. */
        val CLOCK_SKEW = 2.minutes

        const val EXPORT_EVERY_MONTHS = 3
    }
}

/** 플랫폼 예약기. Android: AlarmManager + 수신기, iOS: UNUserNotificationCenter. */
interface NotificationScheduler {
    /** 지금 예약된 것을 [notifications] 로 맞춘다(없는 것은 취소, 바뀐 것은 다시 예약). */
    suspend fun replaceAll(notifications: List<LocalNotification>)
}
