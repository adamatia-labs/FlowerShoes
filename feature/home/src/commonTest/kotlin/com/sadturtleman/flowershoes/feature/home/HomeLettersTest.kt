package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** 홈 "오늘 온 편지" 줄 · 혼자 모드 "연결되면 보낼 편지 N통". 시각은 서울 기준. */
class HomeLettersTest {
    private val today = LocalDate(2026, 10, 28)

    /** "2026-10-28T12:12:00Z" = 서울 오후 9:12. */
    private fun letter(id: String, author: String, utc: String, vararg blocks: LetterBlock) =
        Letter(id, author, blocks = blocks.toList(), paper = "plain", visibleAt = Instant.parse(utc))

    private val inbox = listOf(
        letter("old", "minjun", "2026-10-27T03:00:00Z", LetterBlock.Text("어제 편지")),
        letter("today", "minjun", "2026-10-28T12:12:00Z", LetterBlock.Text("오늘 당직인데\n네 생각나서 몰래 써")),
        letter("mine", "jisu", "2026-10-28T13:00:00Z", LetterBlock.Text("내가 쓴 편지")),
    )

    @Test fun showsFirstLineOfPartnersLetterReceivedToday() {
        assertEquals(
            HomeLetter("today", "오늘 당직인데", "민준 · 오후 9:12"),
            todayLetter(inbox, partnerId = "minjun", partnerName = "민준", today = today, archive = false),
        )
    }

    @Test fun noLetterTodayIsEmpty() {
        assertNull(todayLetter(inbox, "minjun", "민준", LocalDate(2026, 10, 29), archive = false))
    }

    @Test fun archiveShowsLatestLetterWithDate() {
        assertEquals(
            HomeLetter("today", "오늘 당직인데", "민준 · 10월 28일"),
            todayLetter(inbox, "minjun", "민준", LocalDate(2027, 3, 10), archive = true),
        )
    }

    @Test fun drawingOnlyLetterNamesTheDrawings() {
        val drawing = letter("d", "minjun", "2026-10-27T22:02:00Z", LetterBlock.DrawingRef("a"), LetterBlock.DrawingRef("b"))
        assertEquals(HomeLetter("d", "그림 2장", "민준 · 오전 7:02"), todayLetter(listOf(drawing), "minjun", "민준", today, archive = false))
    }

    @Test fun soloModeListsMyLettersNewestFirst() {
        val mine = listOf(
            letter("a", "jisu", "2026-10-26T05:00:00Z", LetterBlock.Text("휴가 때 뭐 할지 적어봤어")),
            letter("b", "jisu", "2026-10-28T11:40:00Z", LetterBlock.Text("처음 쓰는 편지, 연결되면 같이 읽자")),
            letter("c", "other", "2026-10-28T11:41:00Z", LetterBlock.Text("남의 편지")),
        )
        assertEquals(
            listOf(
                HomeLetter("b", "처음 쓰는 편지, 연결되면 같이 읽자", "지수 · 오늘 오후 8:40"),
                HomeLetter("a", "휴가 때 뭐 할지 적어봤어", "지수 · 10월 26일"),
            ),
            pendingLetters(mine, me = "jisu", myName = "지수", today = today),
        )
    }
}
