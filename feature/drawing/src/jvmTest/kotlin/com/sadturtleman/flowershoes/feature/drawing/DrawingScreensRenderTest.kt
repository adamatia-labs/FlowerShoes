package com.sadturtleman.flowershoes.feature.drawing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.PointBuffer
import com.sadturtleman.flowershoes.core.drawing.Stroke
import com.sadturtleman.flowershoes.core.drawing.Tool
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 그림 편집 · 편지에 붙이기 · 그림만 보내기를 build/render 에 그린다(Figma 5 · 그림 그리기와 눈으로 맞춰 보는 용도). */
class DrawingScreensRenderTest {

    private val drawn = DrawingEditorState(
        elements = listOf(
            Stroke("s1", 0, Tool.PEN, PEN_COLORS[0], BrushSize.M, PointBuffer(intArrayOf(200, 400, 600, 800), intArrayOf(300, 600, 300, 600), intArrayOf(0, 10, 20, 30))),
        ),
        partnerName = "민준",
        caption = "오늘의 나",
    )

    private fun render(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        val png = ImageComposeScene(width = 360 * 2, height = 760 * 2, density = Density(2f)) {
            KkotsinTheme(theme = AppTheme.entries.first(), dark = dark) { content() }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }

    @Test
    fun `그림 화면들을 그린다`() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            render("drawing_empty_$mode", dark) { DrawingEditorScreen(DrawingEditorState(), {}, {}) }
            render("drawing_drawn_$mode", dark) { DrawingEditorScreen(drawn, {}, {}) }
            render("drawing_attach_$mode", dark) { DrawingSendScreen(drawn.copy(stage = EditorStage.SEND), {}, forLetter = true) }
            render("drawing_send_$mode", dark) { DrawingSendScreen(drawn.copy(stage = EditorStage.SEND), {}, canSave = true) }
        }
    }
}
