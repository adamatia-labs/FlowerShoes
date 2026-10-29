package com.sadturtleman.flowershoes.feature.memory

import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 추억 목록의 월 페이지(Figma 367:82)와 한 달 타임라인(367:266)에 무엇을 붙이는지. */
class MemoryPagesTest {

    private fun letter(
        id: String,
        day: Int,
        text: String = "편지 $id",
        title: String? = null,
        mine: Boolean = false,
        kind: LetterKind = LetterKind.TEXT,
        unread: Boolean = false,
    ) = MonthLetter(
        id, LocalDate(2026, 10, day), "10.$day · ${if (mine) "지수" else "민준"}", if (mine) "지수" else "민준", mine,
        kind, text, text, "", null, null, title = title, unread = unread,
    )

    private fun meet(id: String, day: Int, title: String = "면회", photos: List<String> = emptyList()) = MeetCard(
        id, LocalDate(2026, 10, day), "면회", EventType.VISIT, title, "10.$day (월)", photos, emptyList(), "",
    )

    private fun month(letters: List<MonthLetter> = emptyList(), meets: List<MeetCard> = emptyList()) = MonthDetail(
        YearMonth(2026, 10), 7, emptySet(), emptySet(), emptyList(), letters, meets, drawings = 0,
    )

    @Test
    fun `월 페이지 봉투는 제목 붙은 가장 최근 편지 제목`() {
        // 편지는 최근 것이 앞(MemoryViewModel 이 visibleAt 내림차순으로 넣는다).
        val page = monthPage(month(listOf(letter("c", 28), letter("b", 24, title = "당직 편지"), letter("a", 12, title = "비 오는 날"))))
        assertEquals("당직 편지", page.envelope)
    }

    @Test
    fun `제목 붙은 편지가 없으면 봉투는 그냥 편지`() {
        assertEquals("편지", monthPage(month(listOf(letter("a", 3)))).envelope)
    }

    @Test
    fun `편지가 없는 달엔 봉투가 없다`() {
        assertNull(monthPage(month(meets = listOf(meet("m", 19)))).envelope)
    }

    @Test
    fun `월 페이지 아래 한 줄은 가장 최근 글 있는 편지`() {
        val page = monthPage(month(listOf(letter("d", 28, text = "", kind = LetterKind.DRAWING_ONLY), letter("c", 27, text = "오늘 당직인데 네 생각나서 몰래 써"))))
        assertEquals("10.27" to "오늘 당직인데 네 생각나서 몰래 써", page.lastLine)
    }

    @Test
    fun `월 페이지 꼬리표는 가장 최근 만남`() {
        val page = monthPage(month(meets = listOf(meet("a", 5, "외박"), meet("b", 19, "첫 면회"))))
        assertEquals("b", page.meet?.eventId)
    }

    @Test
    fun `아무것도 없는 달은 빈 페이지`() {
        assertEquals(true, monthPage(month()).empty)
        assertEquals(false, monthPage(month(meets = listOf(meet("m", 1)))).empty)
    }

    @Test
    fun `한 달 타임라인은 최근 날부터, 같은 날엔 만남 다음 편지`() {
        val m = month(
            letters = listOf(letter("c", 28), letter("b", 19, mine = true), letter("a", 12)),
            meets = listOf(meet("m", 19)),
        )
        val days = monthTimeline(m).map { d -> d.date.day to d.items.map { if (it is TimelineItem.Meet) "m:${it.meet.eventId}" else "l:${(it as TimelineItem.Letter).letter.id}" } }
        assertEquals(listOf(28 to listOf("l:c"), 19 to listOf("m:m", "l:b"), 12 to listOf("l:a")), days)
    }

    @Test
    fun `봉투 표시 - 내가 보낸 편지는 소인`() {
        assertEquals(LetterMark.Sent, letter("a", 1, mine = true, unread = true).mark())
    }

    @Test
    fun `봉투 표시 - 안 읽은 받은 편지는 봉인`() {
        assertEquals(LetterMark.Seal, letter("a", 1, unread = true, kind = LetterKind.INLINE).mark())
    }

    @Test
    fun `봉투 표시 - 읽은 그림 편지는 폴라로이드, 나머지는 우표`() {
        assertEquals(LetterMark.Drawing, letter("a", 1, kind = LetterKind.INLINE).mark())
        assertEquals(LetterMark.Stamp, letter("b", 1).mark())
    }

    @Test
    fun `우표 글자는 편지 날 기준 전역 D-day`() {
        assertEquals("D-479", stampText(LocalDate(2026, 10, 28), LocalDate(2028, 2, 19)))
        assertEquals("D-DAY", stampText(LocalDate(2028, 2, 19), LocalDate(2028, 2, 19)))
    }

    @Test
    fun `전역일을 모르거나 지난 편지의 우표는 날짜`() {
        assertEquals("10.28", stampText(LocalDate(2026, 10, 28), null))
        assertEquals("3.1", stampText(LocalDate(2028, 3, 1), LocalDate(2028, 2, 19)))
    }
}
