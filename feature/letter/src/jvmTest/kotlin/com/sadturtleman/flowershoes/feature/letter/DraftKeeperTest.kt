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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

class DraftKeeperTest {

    private val store = TestDraftStore()

    private fun TestScope.keeper(): DraftKeeper {
        var n = 0
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }
        return DraftKeeper(store, backgroundScope, clock, newId = { "d${++n}" }).also { runCurrent() }
    }

    private fun meta(id: String) = DrawingMeta(id, 0, "SQUARE", null, "png-$id", null, true, 0)

    @Test fun `계정 전환은 입력 중인 초안을 원래 계정에 저장하고 새 편집을 비운다`() = runTest {
        val k = keeper()
        val oldId = k.currentId
        k.edited(ComposeDraft(text = "A의 마지막 입력"))
        store.switchAccount("b")
        runCurrent()
        assertTrue(k.currentId != oldId)
        assertTrue(k.state.value.ready)
        assertTrue(k.drafts.first().isEmpty())
        assertTrue(store.owned("a").single().json.contains("A의 마지막 입력"))
        testScheduler.advanceTimeBy(3_000); runCurrent()
        assertTrue(store.owned("b").isEmpty())
        k.flush(ComposeDraft(text = "B의 편지")); runCurrent()
        assertTrue(store.owned("b").single().json.contains("B의 편지"))
    }

    @Test fun `저장 실패 뒤 같은 내용을 다시 저장할 수 있다`() = runTest {
        val k = keeper()
        val draft = ComposeDraft(text = "잃으면 안 되는 글")
        store.failNextSave = true
        k.flush(draft); runCurrent()
        assertTrue(store.rows.value.isEmpty())
        k.flush(draft); runCurrent()
        assertEquals(1, store.rows.value.size)
    }

    @Test fun `삭제 뒤 예약된 자동 저장이 초안을 다시 만들지 않는다`() = runTest {
        val k = keeper()
        k.edited(ComposeDraft(text = "취소할 글"))
        k.delete(k.currentId)
        testScheduler.advanceTimeBy(3_000); runCurrent()
        assertTrue(store.rows.value.isEmpty())
    }

    @Test fun `저장 중 계정이 바뀌어도 취소된 입력을 원래 편집으로 다시 저장한다`() = runTest {
        val k = keeper()
        var calls = 0
        val suspended = CompletableDeferred<Unit>()
        store.beforeSave = { if (++calls == 1) { suspended.complete(Unit); CompletableDeferred<Unit>().await() } }
        k.edited(ComposeDraft(text = "저장 중이던 입력"))
        testScheduler.advanceTimeBy(2_001); runCurrent()
        assertTrue(suspended.isCompleted)
        store.switchAccount("b"); runCurrent()
        assertEquals(2, calls)
        assertTrue(store.owned("a").single().json.contains("저장 중이던 입력"))
        assertTrue(store.owned("b").isEmpty())
    }

    @Test fun `계정 전환으로 취소된 이어 쓰기는 원래 초안을 삭제하지 않는다`() = runTest {
        val k = keeper()
        k.flush(ComposeDraft(text = "보존할 원본")); runCurrent()
        val id = k.currentId
        k.startNew(); runCurrent()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        store.afterOpen = { if (it.id == id) { started.complete(Unit); release.await() } }
        val opening = backgroundScope.async { k.resume(id) }
        runCurrent(); assertTrue(started.isCompleted)
        store.switchAccount("b"); runCurrent()
        release.complete(Unit); runCurrent()
        assertNull(opening.await())
        assertTrue(store.owned("a").single().json.contains("보존할 원본"))
    }

    @Test fun `저장을 기다리던 옛 이어 쓰기가 새 계정 편집 준비를 막지 않는다`() = runTest {
        val k = keeper()
        k.edited(ComposeDraft(text = "대기 입력"))
        val releaseSave = CompletableDeferred<Unit>()
        store.beforeSave = { releaseSave.await() }
        val resume = backgroundScope.async { k.resume(k.currentId) }
        runCurrent()
        val releaseOpen = CompletableDeferred<Unit>()
        store.afterOpen = { if (it.session.ownerUid == "b") releaseOpen.await() }
        store.switchAccount("b"); runCurrent()
        releaseSave.complete(Unit); runCurrent()
        assertNull(resume.await())
        releaseOpen.complete(Unit); runCurrent()
        assertTrue(k.ready)
        assertEquals("b", k.currentEditor?.session?.ownerUid)
    }

    @Test fun `읽을 수 없는 초안 원문을 지우지 않는다`() = runTest {
        val k = keeper()
        val handle = checkNotNull(store.open(store.session, "unknown"))
        store.save(handle, "{not-valid-json", 1)
        assertNull(k.resume("unknown"))
        assertEquals("{not-valid-json", store.owned("a").single().json)
    }

    @Test fun `옛 전송 완료는 같은 초안의 새 편집을 지우지 않는다`() = runTest {
        val k = keeper()
        k.flush(ComposeDraft(text = "보낸 글")); runCurrent()
        val sent = checkNotNull(k.currentEditor)
        k.startNew(); runCurrent()
        k.resume(sent.id)
        val current = checkNotNull(k.currentEditor)
        k.flush(ComposeDraft(text = "다시 쓴 글")); runCurrent()
        k.sent(sent); runCurrent()
        assertEquals(current, k.currentEditor)
        assertTrue(store.rows.value.single().json.contains("다시 쓴 글"))
    }

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
        val sentEditor = checkNotNull(k.currentEditor)
        k.flush(d.copy(sending = true))
        k.startNew()
        runCurrent()
        k.sent(sentEditor)
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
        runCurrent()
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
        runCurrent()

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
        runCurrent()
        k.flush(ComposeDraft(text = "\n\n오늘은 유난히\n네 생각이"))
        runCurrent()
        k.startNew()
        runCurrent()
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
