package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** "훈련소 동안 쓴 편지 N통 도착" 창을 build/render 에 그린다(Figma 에 따로 없는 순간이라 눈으로 대조). */
class ArrivalRenderTest {
    @Test
    fun `도착 창을 그린다`() {
        for (theme in AppTheme.entries) {
            val png = ImageComposeScene(width = 390 * 2, height = 844 * 2, density = Density(2f)) {
                KkotsinTheme(theme = theme, dark = false) { ArrivalDialog(23, {}, {}) }
            }.use { scene ->
                scene.render()
                scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            val out = File("build/render/solo_arrival_${theme.name.lowercase()}.png").apply { parentFile.mkdirs(); writeBytes(png) }
            assertTrue(out.length() > 0)
        }
    }
}
