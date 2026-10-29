package com.sadturtleman.flowershoes.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class DateRangeTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `첫 탭은 시작이고 다음 탭은 끝`() {
        val r = KkDateRange(d("2026-11-01"), d("2026-11-01"), pickingEnd = false)
            .pick(d("2026-11-14"))
            .pick(d("2026-11-18"))
        assertEquals(KkDateRange(d("2026-11-14"), d("2026-11-18"), pickingEnd = false), r)
        assertEquals(5, r.days)
    }

    @Test
    fun `끝을 시작보다 앞에 고르면 그날부터 다시 시작`() {
        val r = KkDateRange(d("2026-11-14"), d("2026-11-14"), pickingEnd = true).pick(d("2026-11-10"))
        assertEquals(KkDateRange(d("2026-11-10"), d("2026-11-10"), pickingEnd = true), r)
    }
}
