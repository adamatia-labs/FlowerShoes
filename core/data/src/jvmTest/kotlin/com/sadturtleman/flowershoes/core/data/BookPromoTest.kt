package com.sadturtleman.flowershoes.core.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 추억 책 권유 팝업(#23 결정 3 · 4): D-30 · 전역일 각 한 번, 각자 폰, 이미 산 커플 · 전역 뒤에는 없음. */
class BookPromoTest {

    private val discharge = LocalDate(2028, 2, 19)
    private val none = BookPromoSeen()

    private fun day(today: String, owned: Boolean = false, seen: BookPromoSeen = none) =
        bookPromoDay(LocalDate.parse(today), discharge, owned, seen)

    @Test
    fun `D-30 에 처음 열면 D-30 팝업`() {
        assertEquals(BookPromoDay.D30, day("2028-01-20"))
    }

    @Test
    fun `D-30 을 놓쳤으면 D-8 까지 한 번 띄운다`() {
        assertEquals(BookPromoDay.D30, day("2028-02-11"))
        assertNull(day("2028-02-11", seen = BookPromoSeen(d30 = true)))
    }

    @Test
    fun `전역 한 주 안쪽에는 D-30 팝업을 띄우지 않는다`() {
        assertNull(day("2028-02-12"))
        assertNull(day("2028-02-18"))
    }

    @Test
    fun `D-31 이전에는 없다`() {
        assertNull(day("2028-01-19"))
    }

    @Test
    fun `전역일에는 D-30 을 봤어도 한 번 더`() {
        assertEquals(BookPromoDay.DISCHARGE, day("2028-02-19", seen = BookPromoSeen(d30 = true)))
        assertNull(day("2028-02-19", seen = BookPromoSeen(d30 = true, discharge = true)))
    }

    @Test
    fun `전역 다음 날부터는 없다`() {
        assertNull(day("2028-02-20"))
    }

    @Test
    fun `다시 보지 않기는 두 날 모두 막는다`() {
        assertNull(day("2028-01-20", seen = BookPromoSeen(never = true)))
        assertNull(day("2028-02-19", seen = BookPromoSeen(never = true)))
    }

    @Test
    fun `이미 산 커플에게는 띄우지 않는다`() {
        assertNull(day("2028-01-20", owned = true))
        assertNull(day("2028-02-19", owned = true))
    }

    @Test
    fun `전역일을 모르면 없다`() {
        assertNull(bookPromoDay(LocalDate(2028, 1, 20), null, owned = false, seen = none))
    }
}
