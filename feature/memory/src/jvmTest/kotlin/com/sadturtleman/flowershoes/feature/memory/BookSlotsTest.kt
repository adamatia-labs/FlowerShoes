package com.sadturtleman.flowershoes.feature.memory

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** 추억 책 한 달 쪽의 편지 세 칸(#23 결정 2): 두 사람 편지를 섞어 긴 순 → 그날의 한 줄 → 빈칸. */
class BookSlotsTest {

    private fun letter(id: String, text: String, day: Int = 1, mine: Boolean = true) = MonthLetter(
        id, LocalDate(2026, 10, day), "10.$day · ${if (mine) "지수" else "민준"}", if (mine) "지수" else "민준", mine,
        LetterKind.TEXT, text, text, "", null, null,
    )

    private fun line(text: String, day: Int, mine: Boolean = false) =
        MonthDayLine(LocalDate(2026, 10, day), "10.$day · 면회 · ${if (mine) "지수" else "민준"}", mine, text)

    private fun List<BookSlot>.ids() = map {
        when (it) {
            is BookSlot.Letter -> it.letter.id
            is BookSlot.Line -> "line:${it.line.text}"
            BookSlot.Empty -> "-"
        }
    }

    @Test
    fun `편지가 넘치면 두 사람 것을 섞어 긴 세 통`() {
        val slots = bookSlots(
            listOf(letter("a", "짧다"), letter("b", "조금 더 긴 편지", mine = false), letter("c", "가장 길게 쓴 편지 한 통이다"), letter("d", "중간 길이의 글")),
            listOf(line("안 들어간다", 3)),
        )
        assertEquals(listOf("c", "b", "d"), slots.ids())
    }

    @Test
    fun `편지가 모자라면 그 달의 그날의 한 줄로 채운다`() {
        val slots = bookSlots(listOf(letter("a", "편지")), listOf(line("둘째 날", 20), line("첫째 날", 19, mine = true)))
        assertEquals(listOf("a", "line:첫째 날", "line:둘째 날"), slots.ids())
    }

    @Test
    fun `그날의 한 줄도 없으면 빈칸`() {
        assertEquals(listOf("a", "-", "-"), bookSlots(listOf(letter("a", "편지")), emptyList()).ids())
        assertEquals(listOf("-", "-", "-"), bookSlots(emptyList(), emptyList()).ids())
    }
}
