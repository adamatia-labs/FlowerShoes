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

    private class MemoryDrafts : LetterDraftStore {
        val rows = MutableStateFlow<List<StoredDraft>>(emptyList())
        override fun observe(): Flow<List<StoredDraft>> = rows
        override suspend fun save(id: String, json: String, atMs: Long) {
            rows.value = rows.value.filterNot { it.id == id } + StoredDraft(id, json, atMs)
        }
        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        vm = LetterViewModel(
            jisu.letters, AttachmentStore(db, FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-letter-test"), ProfileRepository(db, jisu.writer, jisu.identity),
            MemoryDrafts(), bizLogger = logger,
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
        until { it.open?.letter?.id == letter.id }
        jisu.letters.observeReadLetterIds().first { letter.id in it }
        jisu.broken = true
        coroutineScope {
            val warning = async(start = CoroutineStart.UNDISPATCHED) { vm.toasts.first { it.kind == LetterToast.Kind.ERROR } }
            vm.onIntent(LetterIntent.DrawingSeen("drawing"))
            assertEquals("그림 확인 기록을 저장하지 못했어요", warning.await().message)
        }
        assertEquals(letter.id, vm.state.value.open?.letter?.id)
        assertNull(db.receiptDao().get(ReceiptEntity.DRAWING.name, drawing.id, "jisu"))
        assertEquals(1, db.syncDao().log("dev-jisu:app").size)
        jisu.broken = false
        vm.onIntent(LetterIntent.DrawingSeen(drawing.id))
        withTimeout(5_000) { while (jisu.letters.isFirstView(drawing.id)) kotlinx.coroutines.yield() }
        assertEquals(2, db.syncDao().log("dev-jisu:app").size)
    }
}
