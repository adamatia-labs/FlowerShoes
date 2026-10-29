package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.LetterDraftStore
import com.sadturtleman.flowershoes.core.data.StoredDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

class DraftKeeperTest {

    private class FakeStore : LetterDraftStore {
        val rows = MutableStateFlow<List<StoredDraft>>(emptyList())
        override fun observe(): Flow<List<StoredDraft>> = rows
        override suspend fun save(id: String, json: String, atMs: Long) {
            rows.value = (rows.value.filterNot { it.id == id } + StoredDraft(id, json, atMs)).sortedByDescending { it.updatedAtMs }
        }
        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    private val store = FakeStore()

    private fun TestScope.keeper(): DraftKeeper {
        var n = 0
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        return DraftKeeper(store, backgroundScope, clock, newId = { "d${++n}" })
    }

    private fun meta(id: String) = DrawingMeta(id, 0, "SQUARE", null, "png-$id", null, true, 0)

    @Test
    fun `글을 멈추고 2초가 지나면 저장한다`() = runTest {
        val k = keeper()
        k.edited(ComposeDraft(text = "민준아"))
        testScheduler.advanceTimeBy(1_500)
        k.edited(ComposeDraft(text = "민준아, 오늘은"))
        testScheduler.advanceTimeBy(1_999)
        runCurrent()
        assertEquals(emptyList(), store.rows.value)

        testScheduler.advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("민준아, 오늘은"), k.drafts.first().map { it.firstLine })
    }

    @Test
    fun `화면을 나가면 기다리지 않고 저장한다`() = runTest {
        val k = keeper()
        val d = ComposeDraft(text = "첫 휴가 때 하고 싶은 것들")
        k.edited(d)
        k.flush(d)
        runCurrent()
        assertEquals(1, store.rows.value.size)
    }

    @Test
    fun `빈 편지는 남기지 않고 다 지우면 목록에서 빠진다`() = runTest {
        val k = keeper()
        k.flush(ComposeDraft(paper = Paper.NIGHT.id))
        runCurrent()
        assertEquals(emptyList(), store.rows.value)

        k.flush(ComposeDraft(text = "보고 싶어"))
        runCurrent()
        k.flush(ComposeDraft())
        runCurrent()
        assertEquals(emptyList(), store.rows.value)
    }

    @Test
    fun `보낸 편지는 목록에서 빠지고 보내는 중에 나가도 다시 생기지 않는다`() = runTest {
        val k = keeper()
        val d = ComposeDraft(text = "잘 자")
        k.flush(d)
        runCurrent()
        val id = k.currentId
        k.flush(d.copy(sending = true))
        k.startNew()
        k.sent(id)
        runCurrent()
        assertEquals(emptyList(), store.rows.value)
    }

    @Test
    fun `새로 쓰면 쓰던 편지와 따로 남고 마지막에 고친 것이 위다`() = runTest {
        val k = keeper()
        k.flush(ComposeDraft(text = "첫 번째"))
        runCurrent()
        testScheduler.advanceTimeBy(1_000)
        k.startNew()
        k.flush(ComposeDraft(text = "두 번째"))
        runCurrent()
        assertEquals(listOf("두 번째", "첫 번째"), k.drafts.first().map { it.firstLine })
    }

    @Test
    fun `이어 쓰면 그림 · 사진 · 편지지 · 글꼴 · 예약 시간이 그대로 돌아오고 같은 편지에 저장한다`() = runTest {
        val k = keeper()
        val d = ComposeDraft(
            title = "첫 휴가",
            text = "가${OBJ}나${OBJ}",
            paper = Paper.KRAFT.id,
            drawings = listOf(meta("g1")),
            photos = listOf("p1"),
            font = "hand",
            objects = listOf("d:g1", "p:p1"),
            revealAt = LocalDateTime(2026, 11, 3, 21, 0),
        )
        k.flush(d)
        runCurrent()
        val id = store.rows.value.single().id
        k.startNew()

        val back = k.resume(id)
        assertEquals(d, back)

        k.flush(back!!.copy(text = back.text + "다"))
        runCurrent()
        assertEquals(listOf(id), store.rows.value.map { it.id })
    }

    @Test
    fun `없는 편지는 이어 쓸 수 없다`() = runTest {
        assertNull(keeper().resume("nope"))
    }

    @Test
    fun `목록 첫 줄은 제목 · 본문 첫 줄 · 그림 순으로 고른다`() = runTest {
        val k = keeper()
        k.flush(ComposeDraft(title = "제목", text = "본문"))
        runCurrent()
        k.startNew()
        k.flush(ComposeDraft(text = "\n\n오늘은 유난히\n네 생각이"))
        runCurrent()
        k.startNew()
        k.flush(ComposeDraft(text = "$OBJ", drawings = listOf(meta("g")), objects = listOf("d:g")))
        runCurrent()
        assertEquals(setOf("제목", "오늘은 유난히", "그림 1장"), k.drafts.first().map { it.firstLine }.toSet())
    }

    @Test
    fun `고친 시각은 오늘 · 어제면 시각까지 그 전이면 날짜만`() {
        val now = LocalDateTime(2026, 10, 28, 9, 0)
        assertEquals("오늘 오전 8:05", draftWhen(LocalDateTime(2026, 10, 28, 8, 5), now))
        assertEquals("어제 오후 11:40", draftWhen(LocalDateTime(2026, 10, 27, 23, 40), now))
        assertEquals("어제 오후 12:00", draftWhen(LocalDateTime(2026, 10, 27, 12, 0), now))
        assertEquals("10월 25일", draftWhen(LocalDateTime(2026, 10, 25, 20, 0), now))
        assertTrue(draftWhen(LocalDateTime(2026, 10, 28, 0, 30), now).startsWith("오늘 오전 12:30"))
    }
}
