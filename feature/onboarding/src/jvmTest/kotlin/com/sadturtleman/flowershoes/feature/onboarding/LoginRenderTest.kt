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
    @Test fun `Apple 안내를 네 테마와 큰 글씨로 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            for (scale in listOf(1f, 2f)) {
                val png = ImageComposeScene(width = 720, height = 1500, density = Density(2f, scale)) {
                    KkotsinTheme(theme, dark) {
                        OnboardingScreen(OnboardingState(today = LocalDate(2026, 11, 11), appleTokenNotice = true), {})
                    }
                }.use { scene -> scene.render(); scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes }
                val out = File("build/render/apple_notice_${theme.name.lowercase()}_${if (dark) "dark" else "light"}_${scale.toInt()}.png")
                out.parentFile.mkdirs(); out.writeBytes(png)
                assertTrue(out.length() > 0)
            }
        }
    }

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

    @Test
    fun `Figma 기준 크기의 로그인 화면을 그린다`() {
        val png = ImageComposeScene(width = 360 * 3, height = 780 * 3, density = Density(3f)) {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                OnboardingScreen(OnboardingState(today = LocalDate(2026, 10, 29)), onIntent = {})
            }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/login_figma_size.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }

    @Test
    fun `작은 화면에서 글자를 키운 로그인 화면을 그린다`() {
        val png = ImageComposeScene(width = 320 * 2, height = 640 * 2, density = Density(2f, 2f)) {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                OnboardingScreen(OnboardingState(today = LocalDate(2026, 10, 29)), onIntent = {})
            }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/login_large_text.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
