package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.LetterDraftStore
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.StoredDraft
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 편지 쓰기 › 보내기(Figma 보내는 중 · 보냄 · 보내기 실패)와 받은 편지 › 고마워의 되돌리기.
 * 기기 안 저장이 실패하면(여기서는 [Phone.broken]) 쓰던 편지가 그대로 남고, 낙관적으로 바꾼 "고마워를 보냈어요"는 되돌아간다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeSendTest {

    private class Phone(val db: AppDatabase, user: String) {
        var broken = false
        val identity = object : Identity {
            override val userId = user
            override val deviceId = "dev-$user"
            override val keyEpoch get() = if (broken) error("저장소를 쓸 수 없다") else 0
        }
        val writer = SyncWriter(db, identity)
        val letters = LetterRepository(db, writer, identity)
    }

    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val jisu = Phone(db, "jisu")
    private val minjun = Phone(db, "minjun")
    private val logger = object : BizLogger {
        override fun init() = Unit
        override fun makeUUID() = ""
        override fun record(viewName: String, event: BizEvent) = Unit
        override fun destroy() = Unit
    }
    private lateinit var vm: LetterViewModel
    private val drafts = TestDraftStore("jisu")
    private var delayedId: String? = null
    private val loadStarted = CompletableDeferred<Unit>()
    private val releaseLoad = CompletableDeferred<Unit>()
    private var failDelayedLoad = false
    private var delayedDeleteId: String? = null
    private var failDelayedDelete = false
    private val deleteStarted = CompletableDeferred<Unit>()
    private val releaseDelete = CompletableDeferred<Unit>()
    private var delayedReceiptId: String? = null
    private val receiptStarted = CompletableDeferred<Unit>()
    private val releaseReceipt = CompletableDeferred<Unit>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = LetterViewModel(
            jisu.letters, AttachmentStore(db, FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-letter-test"), ProfileRepository(db, jisu.writer, jisu.identity),
            drafts, bizLogger = logger,
            loadLetter = { id ->
                if (id == delayedId) { loadStarted.complete(Unit); releaseLoad.await() }
                if (id == delayedId && failDelayedLoad) error("read failed")
                jisu.letters.find(id)
            },
            deleteLetter = { id ->
                if (id == delayedDeleteId) { deleteStarted.complete(Unit); releaseDelete.await() }
                if (id == delayedDeleteId && failDelayedDelete) error("delete failed")
                jisu.letters.delete(id)
            },
            recordReceipt = { entity, id ->
                if (id == delayedReceiptId) { receiptStarted.complete(Unit); releaseReceipt.await(); error("receipt failed") }
                jisu.letters.markRead(entity, id)
            },
        )
    }

    @AfterTest
    fun tearDown() {
        runBlocking { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        db.close()
        Dispatchers.resetMain()
    }

    /** 화면처럼 상태를 구독하면서 [block] 을 돌린다. */
    private fun withScreen(block: suspend () -> Unit) = runBlocking {
        val screen = launch { vm.state.collect {} }
        try {
            withTimeout(10_000) { block() }
        } finally {
            screen.cancel()
        }
    }

    private suspend fun until(cond: (LetterState) -> Boolean) = vm.state.first { cond(it) }

    @Test fun `열린 편집 화면은 계정 전환에서 비워지고 이전 사진 응답을 받지 않는다`() = withScreen {
        until { !it.loading && it.draftReady }
        vm.onIntent(LetterIntent.EditText("이전 계정의 사적인 글"))
        val old = until { it.draft.plainText.isNotEmpty() }.draftId
        drafts.switchAccount("other-account")
        val changed = until { it.draftId != old && it.draftReady }
        assertTrue(changed.draft.isEmpty)
        assertTrue(changed.draftPhotos.isEmpty()); assertTrue(changed.draftThumbs.isEmpty()); assertTrue(changed.drafts.isEmpty())
        vm.onIntent(LetterIntent.AddPhoto(byteArrayOf(1, 2), old))
        vm.onIntent(LetterIntent.EditText("새 계정 글"))
        assertTrue(until { it.draft.plainText == "새 계정 글" }.draft.photos.isEmpty())
        assertTrue(drafts.owned("jisu").single().json.contains("이전 계정의 사적인 글"))
    }

    @Test
    fun `보내면 보냄 화면 혼자 모드면 대기 우표와 보낼 편지 수`() = withScreen {
        until { !it.loading }
        vm.onIntent(LetterIntent.EditText("입대하는 날 못 한 말"))
        vm.onIntent(LetterIntent.Send)
        val done = assertIs<ComposeDone.Sent>(until { it.done != null }.done)
        assertEquals("입대하는 날 못 한 말", done.line)
        assertTrue(done.queued)
        assertEquals("대기", done.stamp)
        assertEquals(1, done.queuedCount)
        assertTrue(vm.state.value.draft.isEmpty)
    }

    @Test
    fun `보내지 못하면 쓰던 편지가 그대로 남고 오류 알림`() = withScreen {
        until { !it.loading }
        vm.onIntent(LetterIntent.EditText("훈련소 밥은 먹을 만해?"))
        jisu.broken = true
        vm.onIntent(LetterIntent.Send)
        val toast = vm.toasts.first()
        assertEquals(LetterToast.Kind.ERROR, toast.kind)
        val s = until { !it.draft.sending }
        assertEquals("훈련소 밥은 먹을 만해?", s.draft.plainText)
        assertNull(s.done)
    }

    @Test
    fun `고마워를 보내지 못하면 버튼이 되돌아간다`() = withScreen {
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("오늘 당직이야")))
        minjun.letters.send(letter, emptyList())
        vm.onIntent(LetterIntent.Open(letter.id))
        until { it.open?.letter?.id == letter.id }
        jisu.letters.observeReadLetterIds().first { letter.id in it }
        jisu.broken = true
        vm.onIntent(LetterIntent.Thanks)
        val toast = vm.toasts.first { it.kind == LetterToast.Kind.ERROR }
        assertEquals("고마워를 보내지 못했어요", toast.message)
        assertFalse(until { it.open?.thanked == false }.open!!.thanked)
        assertFalse(jisu.letters.thanked(letter.id))
    }

    @Test
    fun `읽음 저장이 실패해도 편지를 열어 두고 안내한다`() = withScreen {
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("안녕")))
        minjun.letters.send(letter, emptyList())
        jisu.broken = true
        coroutineScope {
            val warning = async(start = CoroutineStart.UNDISPATCHED) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } }
            vm.onIntent(LetterIntent.Open(letter.id))
            assertEquals("읽음 표시를 저장하지 못했어요", warning.await().message)
        }
        assertEquals(letter.id, until { it.open?.letter?.id == letter.id }.open?.letter?.id)
        assertNull(db.receiptDao().get(ReceiptEntity.LETTER.name, letter.id, "jisu"))
        assertTrue(db.syncDao().log("dev-jisu:app").isEmpty())
        jisu.broken = false
        vm.onIntent(LetterIntent.Open(letter.id))
        jisu.letters.observeReadLetterIds().first { letter.id in it }
        assertEquals(1, db.syncDao().log("dev-jisu:app").size)
    }

    @Test
    fun `그림 확인 기록이 실패해도 편지를 유지하고 안내한다`() = withScreen {
        val drawing = DrawingMeta("drawing", 0, "SQUARE", null, "png", null, true, 0)
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("안녕"), LetterBlock.DrawingRef(drawing.id)))
        minjun.letters.send(letter, listOf(drawing))
        vm.onIntent(LetterIntent.Open(letter.id))
        val opened = until { it.open?.letter?.id == letter.id }.open!!
        jisu.letters.observeReadLetterIds().first { letter.id in it }
        jisu.broken = true
        coroutineScope {
            val warning = async(start = CoroutineStart.UNDISPATCHED) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } }
            vm.onIntent(LetterIntent.DrawingSeen(letter.id, "drawing", opened.viewSessionId))
            assertEquals("그림 확인 기록을 저장하지 못했어요", warning.await().message)
        }
        assertEquals(letter.id, vm.state.value.open?.letter?.id)
        assertNull(db.receiptDao().get(ReceiptEntity.DRAWING.name, drawing.id, "jisu"))
        assertEquals(1, db.syncDao().log("dev-jisu:app").size)
        jisu.broken = false
        vm.onIntent(LetterIntent.DrawingSeen(letter.id, drawing.id, opened.viewSessionId))
        withTimeout(5_000) { while (jisu.letters.isFirstView(drawing.id)) kotlinx.coroutines.yield() }
        assertEquals(2, db.syncDao().log("dev-jisu:app").size)
    }

    @Test
    fun `다른 편지를 연 뒤 늦게 도착한 그림 재생 완료는 확인 처리하지 않는다`() = withScreen {
        val firstDrawing = DrawingMeta("first-drawing", 0, "SQUARE", null, "png-a", null, true, 0)
        val secondDrawing = DrawingMeta("second-drawing", 0, "SQUARE", null, "png-b", null, true, 0)
        val first = minjun.letters.newLetter(listOf(LetterBlock.DrawingRef(firstDrawing.id)))
        val second = minjun.letters.newLetter(listOf(LetterBlock.DrawingRef(secondDrawing.id)))
        minjun.letters.send(first, listOf(firstDrawing))
        minjun.letters.send(second, listOf(secondDrawing))
        vm.onIntent(LetterIntent.Open(first.id))
        val firstSession = until { it.open?.letter?.id == first.id }.open!!.viewSessionId
        vm.onIntent(LetterIntent.Open(second.id))
        val secondSession = until { it.open?.letter?.id == second.id }.open!!.viewSessionId

        vm.onIntent(LetterIntent.DrawingSeen(first.id, firstDrawing.id, firstSession))
        vm.onIntent(LetterIntent.DrawingSeen(second.id, firstDrawing.id, secondSession))
        val recorded = withTimeoutOrNull(500) {
            while (db.receiptDao().get(ReceiptEntity.DRAWING.name, firstDrawing.id, "jisu") == null) kotlinx.coroutines.yield()
            true
        }
        assertNull(recorded)
        vm.onIntent(LetterIntent.DrawingSeen(second.id, secondDrawing.id, secondSession))
        withTimeout(5_000) { while (jisu.letters.isFirstView(secondDrawing.id)) kotlinx.coroutines.yield() }
        assertTrue(jisu.letters.isFirstView(firstDrawing.id))
    }

    @Test
    fun `같은 편지를 다시 열어도 이전 화면의 재생 완료는 무시한다`() = withScreen {
        val drawing = DrawingMeta("drawing", 0, "SQUARE", null, "png", null, true, 0)
        val letter = minjun.letters.newLetter(listOf(LetterBlock.DrawingRef(drawing.id)))
        minjun.letters.send(letter, listOf(drawing))
        vm.onIntent(LetterIntent.Open(letter.id))
        val firstSession = until { it.open?.letter?.id == letter.id }.open!!.viewSessionId
        vm.onIntent(LetterIntent.Close)
        until { it.open == null }
        vm.onIntent(LetterIntent.Open(letter.id))
        val secondSession = until { it.open?.letter?.id == letter.id && it.open?.viewSessionId != firstSession }.open!!.viewSessionId
        vm.onIntent(LetterIntent.DrawingSeen(letter.id, drawing.id, firstSession))
        val recorded = withTimeoutOrNull(500) {
            while (jisu.letters.isFirstView(drawing.id)) kotlinx.coroutines.yield()
            true
        }
        assertNull(recorded)
        vm.onIntent(LetterIntent.DrawingSeen(letter.id, drawing.id, secondSession))
        withTimeout(5_000) { while (jisu.letters.isFirstView(drawing.id)) kotlinx.coroutines.yield() }
    }

    @Test
    fun `느리게 열린 편지가 나중에 선택한 편지를 덮지 않는다`() = withScreen {
        val first = minjun.letters.newLetter(listOf(LetterBlock.Text("첫 편지")))
        val second = minjun.letters.newLetter(listOf(LetterBlock.Text("두 번째")))
        minjun.letters.send(first, emptyList())
        minjun.letters.send(second, emptyList())
        delayedId = first.id
        vm.onIntent(LetterIntent.Open(first.id))
        loadStarted.await()
        vm.onIntent(LetterIntent.Open(second.id))
        until { it.open?.letter?.id == second.id }
        releaseLoad.complete(Unit)
        val wrong = withTimeoutOrNull(500) { vm.state.first { it.open?.letter?.id == first.id } }
        assertNull(wrong)
        assertNull(db.receiptDao().get(ReceiptEntity.LETTER.name, first.id, "jisu"))
    }

    @Test
    fun `닫은 뒤 늦게 완료된 편지 열기가 화면을 다시 열지 않는다`() = withScreen {
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("닫은 편지")))
        minjun.letters.send(letter, emptyList())
        delayedId = letter.id
        vm.onIntent(LetterIntent.Open(letter.id))
        loadStarted.await()
        vm.onIntent(LetterIntent.Close)
        releaseLoad.complete(Unit)
        val reopened = withTimeoutOrNull(500) { vm.state.first { it.open?.letter?.id == letter.id } }
        assertNull(reopened)
        assertNull(db.receiptDao().get(ReceiptEntity.LETTER.name, letter.id, "jisu"))
    }

    @Test
    fun `찾을 수 없는 이전 요청의 늦은 결과가 현재 편지를 닫지 않는다`() = withScreen {
        val current = minjun.letters.newLetter(listOf(LetterBlock.Text("현재 편지")))
        minjun.letters.send(current, emptyList())
        delayedId = "missing"
        vm.onIntent(LetterIntent.Open("missing"))
        loadStarted.await()
        vm.onIntent(LetterIntent.Open(current.id))
        until { it.open?.letter?.id == current.id }
        releaseLoad.complete(Unit)
        val staleToast = withTimeoutOrNull(500) { vm.toasts.first { it.message == "편지를 찾을 수 없어요" } }
        assertNull(staleToast)
        assertEquals(current.id, vm.state.value.open?.letter?.id)
    }

    @Test
    fun `이전 편지 삭제 완료가 새로 연 편지를 닫지 않는다`() = withScreen {
        val old = jisu.letters.newLetter(listOf(LetterBlock.Text("내 편지")))
        val current = minjun.letters.newLetter(listOf(LetterBlock.Text("현재 편지")))
        jisu.letters.send(old, emptyList())
        minjun.letters.send(current, emptyList())
        vm.onIntent(LetterIntent.Open(old.id))
        until { it.open?.letter?.id == old.id }
        delayedDeleteId = old.id
        vm.onIntent(LetterIntent.Delete(old.id))
        deleteStarted.await()
        vm.onIntent(LetterIntent.Open(current.id))
        until { it.open?.letter?.id == current.id }
        releaseDelete.complete(Unit)
        val closed = withTimeoutOrNull(500) { vm.state.first { it.open == null } }
        assertNull(closed)
        assertEquals(current.id, vm.state.value.open?.letter?.id)
    }

    @Test
    fun `늦은 이전 편지 조회 오류는 현재 편지를 닫거나 오류를 표시하지 않는다`() = withScreen {
        val current = minjun.letters.newLetter(listOf(LetterBlock.Text("현재 편지")))
        minjun.letters.send(current, emptyList())
        delayedId = "slow-error"
        failDelayedLoad = true
        vm.onIntent(LetterIntent.Open("slow-error"))
        loadStarted.await()
        vm.onIntent(LetterIntent.Open(current.id))
        until { it.open?.letter?.id == current.id }
        releaseLoad.complete(Unit)
        val warning = withTimeoutOrNull(500) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } }
        assertNull(warning)
        assertEquals(current.id, vm.state.value.open?.letter?.id)
    }

    @Test
    fun `현재 편지 조회 오류는 안내하고 기존 화면을 유지한다`() = withScreen {
        val current = minjun.letters.newLetter(listOf(LetterBlock.Text("현재 편지")))
        minjun.letters.send(current, emptyList())
        vm.onIntent(LetterIntent.Open(current.id))
        until { it.open?.letter?.id == current.id }
        delayedId = "error"
        failDelayedLoad = true
        coroutineScope {
            val warning = async(start = CoroutineStart.UNDISPATCHED) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } }
            vm.onIntent(LetterIntent.Open("error"))
            loadStarted.await()
            releaseLoad.complete(Unit)
            assertEquals("편지를 불러오지 못했어요", warning.await().message)
        }
        assertEquals(current.id, vm.state.value.open?.letter?.id)
    }

    @Test
    fun `편지를 불러오는 중 삭제하면 늦은 결과가 다시 열리지 않는다`() = withScreen {
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("삭제할 편지")))
        jisu.letters.send(letter, emptyList())
        delayedId = letter.id
        vm.onIntent(LetterIntent.Open(letter.id))
        loadStarted.await()
        vm.onIntent(LetterIntent.Delete(letter.id))
        withTimeout(5_000) { while (jisu.letters.find(letter.id) != null) kotlinx.coroutines.yield() }
        releaseLoad.complete(Unit)
        assertNull(withTimeoutOrNull(500) { vm.state.first { it.open?.letter?.id == letter.id } })
    }

    @Test
    fun `삭제 중 같은 편지를 다시 열어도 삭제가 끝나면 닫힌다`() = withScreen {
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("삭제할 편지")))
        jisu.letters.send(letter, emptyList())
        vm.onIntent(LetterIntent.Open(letter.id))
        val oldSession = until { it.open?.letter?.id == letter.id }.open!!.viewSessionId
        delayedDeleteId = letter.id
        vm.onIntent(LetterIntent.Delete(letter.id))
        deleteStarted.await()
        vm.onIntent(LetterIntent.Open(letter.id))
        until { it.open?.letter?.id == letter.id && it.open?.viewSessionId != oldSession }
        releaseDelete.complete(Unit)
        until { it.open == null }
        assertNull(jisu.letters.find(letter.id))
    }

    @Test
    fun `이전 편지의 늦은 읽음 오류를 현재 편지에 표시하지 않는다`() = withScreen {
        val first = minjun.letters.newLetter(listOf(LetterBlock.Text("첫 편지")))
        val second = minjun.letters.newLetter(listOf(LetterBlock.Text("둘째 편지")))
        minjun.letters.send(first, emptyList())
        minjun.letters.send(second, emptyList())
        delayedReceiptId = first.id
        vm.onIntent(LetterIntent.Open(first.id))
        until { it.open?.letter?.id == first.id }
        receiptStarted.await()
        vm.onIntent(LetterIntent.Open(second.id))
        until { it.open?.letter?.id == second.id }
        releaseReceipt.complete(Unit)
        assertNull(withTimeoutOrNull(500) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } })
        assertEquals(second.id, vm.state.value.open?.letter?.id)
    }

    @Test
    fun `이전 편지의 늦은 삭제 오류를 현재 편지에 표시하지 않는다`() = withScreen {
        val first = jisu.letters.newLetter(listOf(LetterBlock.Text("내 편지")))
        val second = minjun.letters.newLetter(listOf(LetterBlock.Text("둘째 편지")))
        jisu.letters.send(first, emptyList())
        minjun.letters.send(second, emptyList())
        vm.onIntent(LetterIntent.Open(first.id))
        until { it.open?.letter?.id == first.id }
        delayedDeleteId = first.id
        failDelayedDelete = true
        vm.onIntent(LetterIntent.Delete(first.id))
        deleteStarted.await()
        vm.onIntent(LetterIntent.Open(second.id))
        until { it.open?.letter?.id == second.id }
        releaseDelete.complete(Unit)
        assertNull(withTimeoutOrNull(500) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } })
        assertEquals(second.id, vm.state.value.open?.letter?.id)
    }

    @Test
    fun `현재 편지 삭제에 실패하면 편지를 유지하고 안내한다`() = withScreen {
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("내 편지")))
        jisu.letters.send(letter, emptyList())
        vm.onIntent(LetterIntent.Open(letter.id))
        until { it.open?.letter?.id == letter.id }
        delayedDeleteId = letter.id
        failDelayedDelete = true
        coroutineScope {
            val warning = async(start = CoroutineStart.UNDISPATCHED) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } }
            vm.onIntent(LetterIntent.Delete(letter.id))
            deleteStarted.await()
            releaseDelete.complete(Unit)
            assertEquals("편지를 삭제하지 못했어요", warning.await().message)
        }
        assertEquals(letter.id, vm.state.value.open?.letter?.id)
        assertEquals(letter.id, jisu.letters.find(letter.id)?.id)
    }
}

