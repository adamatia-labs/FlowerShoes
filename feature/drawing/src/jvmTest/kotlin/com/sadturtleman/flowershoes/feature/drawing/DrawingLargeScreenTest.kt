package com.sadturtleman.flowershoes.feature.drawing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkWindowSizeProvider
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 태블릿 그림판(Figma 11 · 그림 그리기 태블릿 가로 `430:11139`): 840dp 부터 아래 툴바 대신 오른쪽 도구 칸. */
@OptIn(ExperimentalTestApi::class)
class DrawingLargeScreenTest {

    @Composable
    private fun Editor(width: Int, height: Int, state: DrawingEditorState = DrawingEditorState(), onIntent: (DrawingIntent) -> Unit = {}) {
        KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
            Box(Modifier.size(width.dp, height.dp)) { KkWindowSizeProvider { DrawingEditorScreen(state, onIntent, {}) } }
        }
    }

    @Test
    fun `폰은 아래 툴바, 굵기 · 색은 펜 시트에`() = runComposeUiTest {
        setContent { Editor(360, 760) }
        waitForIdle()
        onAllNodesWithContentDescription("붓").assertCountEquals(1)
        onAllNodesWithText("굵기").assertCountEquals(0)
    }

    @Test
    fun `태블릿은 도구 · 굵기 · 색 · 되돌리기를 오른쪽 칸에 펼쳐 둔다`() = runComposeUiTest {
        val sent = mutableListOf<DrawingIntent>()
        setContent { Editor(1280, 800, onIntent = { sent += it }) }
        waitForIdle()
        listOf("도구", "굵기", "색", "되돌리기").forEach { onAllNodesWithText(it).assertCountEquals(1) }
        onNodeWithContentDescription("굵게").performClick()
        assertEquals(listOf<DrawingIntent>(DrawingIntent.SetBrush(BrushSize.L)), sent)
    }

    @Test
    fun `그림판을 폰 · 태블릿 폭으로 그린다`() {
        render("drawing_phone", 360, 760)
        render("drawing_tablet", 1280, 800)
        render("drawing_tablet_wide", 1280, 800, DrawingEditorState(shape = com.sadturtleman.flowershoes.core.drawing.WidgetShape.entries.last()))
    }

    private fun render(name: String, width: Int, height: Int, state: DrawingEditorState = DrawingEditorState()) {
        val png = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) { Editor(width, height, state) }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
