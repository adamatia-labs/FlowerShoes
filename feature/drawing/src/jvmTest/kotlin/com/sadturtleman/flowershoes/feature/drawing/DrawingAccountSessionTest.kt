package com.sadturtleman.flowershoes.feature.drawing

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.viewModelScope
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okio.FileSystem
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.test.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DrawingAccountSessionTest {
    @BeforeTest fun mainDispatcher() { Dispatchers.setMain(Dispatchers.Default) }
    @AfterTest fun restoreDispatcher() { Dispatchers.resetMain() }
    @Test fun `새 편집 그림판은 이전 저장 작업과 그림 및 완료 이벤트를 이어받지 않는다`() = runComposeUiTest {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "drawing-session-${Uuid.random()}"
        val attachments = AttachmentStore(db, root)
        val instances = mutableListOf<DrawingEditorViewModel>()
        val savedFor = mutableListOf<String>()
        val session = mutableStateOf("editor-a")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        try {
            setContent {
                KoinApplication(application = { modules(module {
                    viewModel { DrawingEditorViewModel(attachments).also { instances += it } }
                }) }) {
                    val target = session.value
                    KkotsinTheme(AppTheme.GOMSIN) {
                        DrawingEditorRoute(editorSession = target, onSaved = { savedFor += target }, onClose = {})
                    }
                }
            }
            waitForIdle()
            val old = instances.single()
            val lock = scope.launch { db.useWriterConnection { tx -> tx.immediateTransaction { entered.complete(Unit); release.await() } } }
            runBlocking { withTimeout(5_000) { entered.await() } }
            runOnIdle {
                old.onIntent(DrawingIntent.SetTool(EditorTool.STICKER))
                old.onIntent(DrawingIntent.Down(100, 100))
                old.onIntent(DrawingIntent.Done)
            }
            waitUntil { old.state.value.saving }
            runOnIdle { session.value = "editor-b" }
            waitForIdle()
            assertEquals(2, instances.size)
            val fresh = instances.last()
            assertTrue(old.viewModelScope.coroutineContext[Job]!!.isCancelled)
            assertTrue(fresh.state.value.elements.isEmpty())
            release.complete(Unit)
            runBlocking { withTimeout(5_000) { lock.join(); old.viewModelScope.coroutineContext[Job]!!.join() } }
            assertTrue(savedFor.isEmpty())
            runOnIdle {
                fresh.onIntent(DrawingIntent.SetTool(EditorTool.STICKER))
                fresh.onIntent(DrawingIntent.Down(200, 200))
                fresh.onIntent(DrawingIntent.Done)
            }
            waitUntil(timeoutMillis = 10_000) { savedFor.isNotEmpty() }
            assertEquals(listOf("editor-b"), savedFor)
        } finally {
            release.complete(Unit)
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin(); instances.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() } }
            db.close(); FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }
}
