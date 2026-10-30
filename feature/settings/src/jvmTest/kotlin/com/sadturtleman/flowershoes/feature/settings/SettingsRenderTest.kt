package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Figma 10 · 다크 확인(386:5350) "설정 · 무료 · 위"를 4개 모드 · 글자 200% 로 build/render 에 그린다. */
class SettingsRenderTest {
    private val today = LocalDate(2026, 10, 28)
    private val service = Service("s", Branch.AIR_FORCE, LocalDate(2026, 4, 20), 1)

    private val state = SettingsState(
        role = Role.GOMSIN, myName = "지수", partnerName = "민준", linked = true,
        anniversary = LocalDate(2025, 9, 13), daysTogether = 411,
        service = service, timeline = ServiceCalculator().timeline(service), today = today,
    )

    @Test
    fun `설정을 4개 모드와 글자 200%로 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            render("settings_main_${theme.name.lowercase()}_${if (dark) "dark" else "light"}", theme, dark)
        }
        render("settings_main_font200", AppTheme.GOMSIN, dark = false, fontScale = 2f)
    }

    private fun render(name: String, theme: AppTheme, dark: Boolean, fontScale: Float = 1f) {
        val png = ImageComposeScene(width = 360 * 2, height = 760 * 2, density = Density(2f, fontScale)) {
            KkotsinTheme(theme = theme, dark = dark) {
                SettingsMainScreen(state.copy(theme = theme), "1.0.0", "카카오", "7,900원", {}, {}, {}, {}, {})
            }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
