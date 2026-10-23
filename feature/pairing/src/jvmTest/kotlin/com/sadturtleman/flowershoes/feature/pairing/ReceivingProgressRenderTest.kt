package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.data.sync.RelinkCounts
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 민준 · 넘겨받는 중(Figma 118:1459). 실기기에서는 기록이 적으면 1–2초만 보여 화면을 붙잡기 어려워,
 * Figma 와 같은 숫자(편지 128 완료 · 그림 42 중 일부 · 일정 대기)로 그려 build/render 에 남긴다.
 */
class ReceivingProgressRenderTest {
    @Test
    fun `넘겨받는 중 화면을 그린다`() {
        val ui = PairingUi(
            available = true,
            state = PairingState.Linked("c"),
            expected = RelinkCounts(letters = 128, drawings = 42, events = 30),
            received = RelinkCounts(letters = 128, drawings = 2, events = 0),
            receiveStartedMs = System.currentTimeMillis() - 100_000,
        )
        val png = ImageComposeScene(width = 390 * 3, height = 844 * 3, density = Density(3f)) {
            KkotsinTheme(accent = com.sadturtleman.flowershoes.core.model.ThemeAccent.ROSE) { ReceivingProgress(ui) }
        }.use { scene ->
            scene.render() // 첫 프레임에 글꼴을 불러온다
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/receiving_progress.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
