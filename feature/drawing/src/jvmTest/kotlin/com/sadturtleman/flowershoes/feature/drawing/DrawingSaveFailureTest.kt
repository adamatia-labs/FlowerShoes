package com.sadturtleman.flowershoes.feature.drawing

import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingRasterizer
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okio.FileSystem

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingSaveFailureTest {
    @Test
    fun `용량 초과 저장 실패 후 그림을 고쳐 저장하거나 PNG로 내보낼 수 있다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "drawing-save-${Uuid.random()}"
        val store = AttachmentStore(db, root)
        val png = byteArrayOf(1, 2, 3)
        val vm = DrawingEditorViewModel(store, object : DrawingRasterizer {
            override fun png(doc: DrawingDocument, photos: Map<String, ByteArray>): ByteArray = png
        })
        try {
            vm.onIntent(DrawingIntent.SetTool(EditorTool.STICKER))
            vm.onIntent(DrawingIntent.SetSticker("x".repeat(DrawingCodec.MAX_DOCUMENT_BYTES)))
            vm.onIntent(DrawingIntent.Down(100, 100))
            val original = vm.state.value.elements
            val failure = async { vm.events.first() }
            vm.onIntent(DrawingIntent.Done)
            assertEquals(DrawingEvent.SaveFailed, failure.await())
            assertEquals(original, vm.state.value.elements)
            assertFalse(vm.state.value.saving)
            assertFalse(FileSystem.SYSTEM.exists(root))

            val exported = async { vm.events.first() }
            vm.onIntent(DrawingIntent.Export)
            assertContentEquals(png, assertIs<DrawingEvent.Exported>(exported.await()).png)
            assertEquals(original, vm.state.value.elements)

            vm.onIntent(DrawingIntent.Undo)
            vm.onIntent(DrawingIntent.SetSticker("heart"))
            vm.onIntent(DrawingIntent.Down(100, 100))
            val saved = async { vm.events.first() }
            vm.onIntent(DrawingIntent.Done)
            val meta = assertIs<DrawingEvent.Saved>(saved.await()).meta
            assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(store.read(meta.docAttachmentId!!)!!))
            assertEquals(emptyList(), vm.state.value.elements)
        } finally {
            db.close()
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
            Dispatchers.resetMain()
        }
    }
}
