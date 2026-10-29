package com.sadturtleman.flowershoes.core.widget

import com.sadturtleman.flowershoes.core.military.Rank
import com.sadturtleman.flowershoes.core.military.RankStep
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.database.DrawingEntity
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.Path.Companion.toPath

/** 위젯 v2(Figma 9 · 위젯 383:5321)가 그리는 글자. 예시 데이터는 design.md: 지수(곰신) · 민준(군인), 오늘 2026-10-28. */
class WidgetSnapshotTest {
    private val now = Instant.parse("2026-10-28T03:00:00Z") // 서울 정오
    private val timeline = ServiceTimeline(
        enlistDate = LocalDate(2026, 4, 20),
        dischargeDate = LocalDate(2028, 2, 19),
        isOverridden = false,
        ranks = listOf(
            RankStep(Rank.PRIVATE, LocalDate(2026, 4, 20)),
            RankStep(Rank.PRIVATE_FIRST_CLASS, LocalDate(2026, 7, 1)),
            RankStep(Rank.CORPORAL, LocalDate(2026, 12, 20)),
            RankStep(Rank.SERGEANT, LocalDate(2027, 6, 1)),
        ),
        rulesVersion = 1,
    )
    private val gomsin = WidgetInputs(
        theme = "GOMSIN", role = Role.GOMSIN, myName = "지수", partnerId = "u-minjun", partnerName = "민준", timeline = timeline,
    )

    private fun snap(inputs: WidgetInputs) = widgetSnapshot(inputs, "/att".toPath(), now)

    @Test
    fun `곰신 전역 D-day 는 연인 이름 · 진행률 · 다음 진급 예정을 쓴다`() {
        val s = snap(gomsin)
        assertEquals("민준이 전역까지", s.ddayCaption)
        assertEquals("D-479", s.ddayValue)
        assertEquals("28.6% · 상병(예정) D-53", s.ddayFoot)
        assertEquals("입대 2026.04.20", s.progressStart)
        assertEquals("전역 2028.02.19", s.progressEnd)
        assertEquals("28.6%", s.progressPercent)
    }

    private fun event(id: String, type: EventType, start: LocalDateTime, title: String = "") =
        Event(id, type, title, start, start, false, now, 0, "dev")

    @Test
    fun `군인은 내 전역이라 이름 없이 복무 진행률을 쓴다`() {
        val s = snap(gomsin.copy(theme = "SOLDIER", role = Role.SOLDIER, myName = "민준", partnerName = "지수"))
        assertEquals("전역까지", s.ddayCaption)
        assertEquals("복무 28.6% · 상병(예정) D-53", s.ddayFoot)
    }

    @Test
    fun `종이 태그는 다음 휴가 · 다음 만남 · 다음 진급 예정`() {
        val s = snap(
            gomsin.copy(
                events = listOf(
                    event("v", EventType.VACATION, LocalDateTime(2026, 11, 14, 0, 0)),
                    event("m", EventType.VISIT, LocalDateTime(2026, 11, 8, 10, 0), "면회"),
                    event("d", EventType.DUTY, LocalDateTime(2026, 10, 30, 8, 0)),
                ),
            ),
        )
        assertEquals(WidgetTag("다음 휴가", "D-17"), s.tagLeave)
        assertEquals(WidgetTag("다음 만남", "D-11"), s.tagMeet)
        assertEquals(WidgetTag("상병 진급 예정", "D-53"), s.tagRank)
        // 작은 다음 만남 위젯: 당직은 만남이 아니다.
        assertEquals(11, s.nextEventDays)
        assertEquals("VISIT", s.nextEventType)
        assertEquals("11월 8일 면회", s.nextEventLine)
        assertEquals("일요일 10:00", s.nextEventWhen)
    }

    @Test
    fun `상대 상태는 이름 · 상태 · 끝나는 시각`() {
        val status = Status("s", "u-minjun", "당직 중", 0xFF6E7262, createdAt = Instant.parse("2026-10-28T02:00:00Z"), expiresAt = Instant.parse("2026-10-28T23:00:00Z"))
        val s = snap(gomsin.copy(status = status))
        assertEquals("민준은 지금", s.partnerStatusCaption)
        assertEquals("당직 중", s.partnerStatus)
        assertEquals(0xFF6E7262, s.partnerStatusColor)
        assertEquals("내일 08:00까지", s.partnerStatusUntil)
    }

    @Test
    fun `안 읽은 편지가 있으면 새 편지 N통, 다 읽었으면 가장 최근 편지 첫 줄`() {
        val at = Instant.parse("2026-10-27T12:12:00Z") // 서울 오후 9:12
        val letters = listOf(
            Letter("l1", "u-minjun", blocks = listOf(LetterBlock.Text("\n오늘 훈련 끝!\n둘째 줄")), paper = "plain", visibleAt = at),
            Letter("l0", "u-minjun", blocks = listOf(LetterBlock.Text("옛 편지")), paper = "plain", visibleAt = Instant.parse("2026-10-20T00:00:00Z")),
            Letter("me", "u-jisu", blocks = listOf(LetterBlock.Text("내가 쓴 편지")), paper = "plain", visibleAt = now),
        )
        val unread = snap(gomsin.copy(letters = letters, readLetterIds = setOf("l0")))
        assertEquals(1, unread.unreadLetters)
        assertEquals("새 편지 1통", unread.letterLine)
        assertEquals("민준 · 10월 27일", unread.letterMeta)
        assertEquals("l1", unread.letterId)

        val read = snap(gomsin.copy(letters = letters, readLetterIds = setOf("l0", "l1")))
        assertEquals("오늘 훈련 끝!", read.letterLine)
    }

    @Test
    fun `보고싶어는 1분 동안만 보냈어요 상태`() {
        val sent = now.toEpochMilliseconds() - 20_000
        assertEquals(sent, snap(gomsin.copy(missYouSentToday = 3, missYouLastSentAtMs = sent)).missYouJustSentAtMs)
        assertNull(snap(gomsin.copy(missYouLastSentAtMs = now.toEpochMilliseconds() - 61_000)).missYouJustSentAtMs)
    }

    @Test
    fun `받은 그림은 이름과 보낸 사람 · 어제`() {
        val d = DrawingEntity(
            "d1", "c", "l1", 0, "u-minjun", "SQUARE", "doc", "png", "", "민준이 그려준 나", true, 1000, true,
            sentAtMs = Instant.parse("2026-10-27T05:00:00Z").toEpochMilliseconds(), receivedAtMs = null, deletedAtMs = null,
        )
        val s = snap(gomsin.copy(drawing = d))
        assertEquals("민준이 그려준 나", s.drawingCaption)
        assertEquals("민준 · 어제", s.drawingMeta)
        assertEquals("/att/png".toPath().toString(), s.drawingPngPath)
    }

    @Test
    fun `입대일이 없으면 D-day 글자가 없다`() {
        val s = snap(gomsin.copy(timeline = null))
        assertNull(s.ddayValue)
        assertNull(s.tagRank)
    }
}
