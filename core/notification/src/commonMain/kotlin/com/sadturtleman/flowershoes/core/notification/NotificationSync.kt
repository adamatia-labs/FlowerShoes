package com.sadturtleman.flowershoes.core.notification

import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.data.toModel
import com.sadturtleman.flowershoes.core.database.AppDatabase
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * DB 가 바뀔 때마다 로컬 알림을 다시 계획해 플랫폼 예약기에 맞춘다.
 * 앱이 살아 있는 동안 돈다. 앱이 꺼져 있으면 iOS 는 백그라운드 새로고침, Android 는 부팅 수신기가 다시 채운다.
 */
class NotificationSync(
    private val db: AppDatabase,
    private val identity: Identity,
    private val profile: ProfileRepository,
    private val events: EventRepository,
    private val scheduler: NotificationScheduler,
    private val clock: Clock = Clock.System,
    private val planner: NotificationPlanner = NotificationPlanner(),
    /** 알림 설정: 끈 종류(일정 전날 · 기념일 · 예약 편지)는 예약하지 않는다. */
    private val prefs: kotlinx.coroutines.flow.Flow<com.sadturtleman.flowershoes.core.data.NotificationPrefs> =
        kotlinx.coroutines.flow.flowOf(com.sadturtleman.flowershoes.core.data.NotificationPrefs()),
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        val today = clock.today()
        combine(
            combine(db.letterDao().observeAll(), prefs) { l, p -> l to p },
            events.observeRange(today, today.plus(DatePeriod(years = 1))),
            profile.observeTimelines(),
            db.coupleDao().observe(),
            db.userDao().observeAll(),
        ) { (letters, p), upcoming, timelines, couple, users ->
            val partner = couple?.partnerUserId.orEmpty()
            planner.plan(
                now = clock.now(),
                partnerName = users.firstOrNull { it.id == partner }?.nickname?.ifBlank { null } ?: "연인",
                // 예약 편지 공개 알림은 받는 폰이 띄운다 — 내가 쓴 편지는 빼고.
                scheduledLetters = if (p.letters) letters.filter { it.authorId != identity.userId }.map { it.toModel() } else emptyList(),
                events = if (p.eventEve) upcoming else emptyList(),
                timeline = if (p.anniversary) timelines.firstOrNull()?.second else null,
                anniversary = if (p.anniversary) couple?.anniversary?.let(LocalDate::parse) else null,
            )
        }
            .distinctUntilChanged()
            .debounce(1_000)
            .collect { scheduler.replaceAll(it) }
    }
}
