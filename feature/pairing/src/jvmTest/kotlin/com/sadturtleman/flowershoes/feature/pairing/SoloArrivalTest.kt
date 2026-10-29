package com.sadturtleman.flowershoes.feature.pairing

import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** 연결 순간 "훈련소 동안 쓴 편지 N통 도착"(planning §3 혼자 모드). */
class SoloArrivalTest {
    private val linkedAt = Instant.parse("2026-11-14T03:00:00Z")

    private fun letter(id: String, author: String, at: Instant, deleted: Boolean = false) =
        Letter(id, author, blocks = listOf(LetterBlock.Text("보고 싶어")), paper = "plain", visibleAt = at, deletedAt = if (deleted) at else null)

    @Test
    fun `연결 전에 상대가 쓴 편지만 센다`() {
        val letters = listOf(
            letter("a", "jisu", linkedAt - 30.days),
            letter("b", "jisu", linkedAt - 1.hours),
            letter("c", "jisu", linkedAt + 1.hours),
            letter("d", "me", linkedAt - 2.days),
            letter("e", "jisu", linkedAt - 3.days, deleted = true),
        )
        assertEquals(2, soloLetterCount(letters, partnerId = "jisu", linkedAt = linkedAt))
    }

    @Test
    fun `연결한 지 일주일이 지나면 띄우지 않는다`() {
        val letters = listOf(letter("a", "jisu", linkedAt - 1.days))
        assertEquals(1, arrivalToShow(letters, "jisu", linkedAt, now = linkedAt + 6.days))
        assertEquals(0, arrivalToShow(letters, "jisu", linkedAt, now = linkedAt + 8.days))
    }

    @Test
    fun `도착 문구`() {
        assertEquals("훈련소 동안 쓴 편지 23통 도착", arrivalTitle(23))
    }
}
