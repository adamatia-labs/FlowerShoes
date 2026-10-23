package com.sadturtleman.flowershoes.core.notification

import kotlin.time.Duration.Companion.minutes
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
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
 * - 일정 전날 20:00(알림을 켠 일정만).
 * - 기념일: 전역일 · 입대 100일 단위 · 커플 기념일(매년).
 *
 * iOS 는 앱당 로컬 알림 64개가 한도다. 가까운 [budget](기본 50)개만 예약하고 나머지는 앱 실행 · 백그라운드 새로고침 때
 * 다시 채운다. 14개는 여유분.
 */
class NotificationPlanner(private val budget: Int = 50) {

    fun plan(
        now: Instant,
        partnerName: String,
        scheduledLetters: List<Letter>,
        events: List<Event>,
        timeline: ServiceTimeline?,
        anniversary: LocalDate?,
    ): List<LocalNotification> {
        val out = mutableListOf<LocalNotification>()

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

        events.filter { it.alarm && it.deletedAt == null }.forEach { e ->
            val at = LocalDateTime(e.start.date.minus(DatePeriod(days = 1)), LocalTime(20, 0)).toInstant(SEOUL)
            if (at > now) {
                out += LocalNotification(
                    id = "event-${e.id}",
                    at = at,
                    title = "내일은 ${e.title.ifBlank { e.type.label() }}",
                    body = if (e.type == EventType.VISIT || e.type == EventType.VACATION) "드디어 만나는 날이에요" else "내일 일정이 있어요",
                    deepLink = "kkotsin://calendar/${e.start.date}",
                )
            }
        }

        val today = now.toLocalDate()
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

        anniversary?.let { a ->
            for (year in today.year..today.year + 1) {
                val date = runCatching { LocalDate(year, a.month, a.day) }.getOrNull() ?: continue
                val years = year - a.year
                if (years <= 0) continue
                dayAt9(date)?.takeIf { it > now }?.let {
                    out += LocalNotification("anniv-$year", it, "${years}주년이에요", "오늘은 우리 기념일", "kkotsin://home")
                }
            }
        }

        return out.sortedBy { it.at }.distinctBy { it.id }.take(budget)
    }

    private fun dayAt9(date: LocalDate): Instant? = LocalDateTime(date, LocalTime(9, 0)).toInstant(SEOUL)

    private fun Instant.toLocalDate(): LocalDate = toLocalDateTime(SEOUL).date

    private fun EventType.label() = when (this) {
        EventType.VACATION -> "휴가"
        EventType.OVERNIGHT -> "외박"
        EventType.VISIT -> "면회"
        EventType.TRAINING -> "훈련"
        EventType.DUTY -> "당직"
    }

    private companion object {
        /** 이보다 가까운 미래의 편지는 두 폰 시계 차이로 본다. */
        val CLOCK_SKEW = 2.minutes
    }
}

/** 플랫폼 예약기. Android: AlarmManager + 수신기, iOS: UNUserNotificationCenter. */
interface NotificationScheduler {
    /** 지금 예약된 것을 [notifications] 로 맞춘다(없는 것은 취소, 바뀐 것은 다시 예약). */
    suspend fun replaceAll(notifications: List<LocalNotification>)
}
