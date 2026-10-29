package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 온보딩 0 · 로그인. 소셜 버튼(카카오 → Apple → Google)을 각 사 가이드와 대조하려고 build/render 에 남긴다. */
class LoginRenderTest {
    @Test
    fun `로그인 화면을 밝은 · 어두운 테마로 그린다`() {
        listOf(false, true).forEach { dark ->
            val png = ImageComposeScene(width = 390 * 3, height = 844 * 3, density = Density(3f)) {
                KkotsinTheme(theme = AppTheme.GOMSIN, dark = dark) {
                    OnboardingScreen(OnboardingState(today = LocalDate(2026, 10, 29)), onIntent = {})
                }
            }.use { scene ->
                scene.render() // 첫 프레임에 글꼴을 불러온다
                scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            val out = File("build/render/login_${if (dark) "dark" else "light"}.png").apply { parentFile.mkdirs(); writeBytes(png) }
            assertTrue(out.length() > 0)
        }
    }
}
