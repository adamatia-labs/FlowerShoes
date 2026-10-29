package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.military.Rank
import com.sadturtleman.flowershoes.core.military.RankStep
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 홈 종이 태그 두 장(Figma Paper Tag): 다음 휴가 · 다음 만남 / 진급 / 입대일 · 함께한 날. */
class HomeTagsTest {
    private val today = LocalDate(2026, 10, 28)
    private val timeline = ServiceTimeline(
        enlistDate = LocalDate(2026, 4, 20),
        dischargeDate = LocalDate(2028, 2, 19),
        isOverridden = false,
        ranks = listOf(
            RankStep(Rank.PRIVATE, LocalDate(2026, 4, 20)),
            RankStep(Rank.PRIVATE_FIRST_CLASS, LocalDate(2026, 6, 1)),
            RankStep(Rank.CORPORAL, LocalDate(2026, 12, 20)),
            RankStep(Rank.SERGEANT, LocalDate(2027, 6, 20)),
        ),
        rulesVersion = 2,
    )

    private fun event(type: EventType, start: LocalDate, end: LocalDate = start, deleted: Boolean = false) = Event(
        id = "$type-$start", type = type, title = "", start = LocalDateTime(start.year, start.month, start.day, 0, 0),
        end = LocalDateTime(end.year, end.month, end.day, 0, 0), alarm = false, updatedAt = Instant.fromEpochMilliseconds(0),
        lamport = 0, lastWriterDevice = "d", deletedAt = if (deleted) Instant.fromEpochMilliseconds(1) else null,
    )

    private val events = listOf(
        event(EventType.VISIT, LocalDate(2026, 10, 1)),
        event(EventType.VACATION, LocalDate(2026, 11, 14), LocalDate(2026, 11, 22)),
        event(EventType.VISIT, LocalDate(2026, 11, 8)),
        event(EventType.OVERNIGHT, LocalDate(2026, 11, 2), deleted = true),
        event(EventType.DUTY, LocalDate(2026, 10, 30)),
    )

    @Test fun gomsinSeesNextLeaveAndNextMeeting() {
        assertEquals(
            listOf(HomeTag("다음 휴가", "D-17", hand = false), HomeTag("다음 만남", "11월 8일", hand = true)),
            homeTags(HomePhase.SERVING, Role.GOMSIN, timeline, events, today, daysTogether = 411),
        )
    }

    @Test fun soldierSeesNextLeaveAndExpectedPromotion() {
        assertEquals(
            listOf(HomeTag("다음 휴가", "D-17", hand = false), HomeTag("상병 진급 예정", "D-53", hand = false)),
            homeTags(HomePhase.SERVING, Role.SOLDIER, timeline, events, today, daysTogether = 411),
        )
    }

    @Test fun leaveIsNotRepeatedAsNextMeeting() {
        val onlyLeave = listOf(event(EventType.VACATION, LocalDate(2026, 11, 14), LocalDate(2026, 11, 22)))
        assertEquals(
            listOf(HomeTag("다음 휴가", "D-17", hand = false)),
            homeTags(HomePhase.SERVING, Role.GOMSIN, timeline, onlyLeave, today, daysTogether = 411),
        )
    }

    @Test fun missingPlansDropTheirTag() {
        assertEquals(emptyList(), homeTags(HomePhase.SERVING, Role.GOMSIN, timeline, emptyList(), today, daysTogether = null))
    }

    @Test fun beforeEnlistmentShowsEnlistDayAndDaysTogether() {
        val future = timeline.copy(enlistDate = LocalDate(2026, 11, 9))
        assertEquals(
            listOf(HomeTag("입대일", "11월 9일", hand = true), HomeTag("함께한 날", "D+411", hand = false)),
            homeTags(HomePhase.BEFORE_ENLIST, Role.GOMSIN, future, events, today, daysTogether = 411),
        )
    }

    @Test fun archiveHasNoTags() {
        assertEquals(emptyList(), homeTags(HomePhase.ARCHIVE, Role.GOMSIN, timeline, events, today, daysTogether = 411))
    }
}
