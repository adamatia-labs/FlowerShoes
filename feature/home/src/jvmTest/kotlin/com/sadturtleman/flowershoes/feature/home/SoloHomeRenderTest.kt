package com.sadturtleman.flowershoes.feature.home

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Role
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 혼자 모드 홈(Figma 홈 · 혼자 모드 350:834 · 홈 · 입대 전 350:972)을 build/render 에 그린다. */
class SoloHomeRenderTest {
    private val beforeEnlist = HomeState(
        loading = false,
        role = Role.GOMSIN,
        dday = DdayCard("D-12", "공군", "", "입대 2026.11.09 (월)", 0f, caption = "입대까지"),
        enlistNudge = true,
    )
    private val enlisted = beforeEnlist.copy(
        dday = DdayCard("D-479", "이병 · 공군", "복무 3.2%", "2028.02.19 전역", 0.032f),
        enlistNudge = false,
    )

    @Test
    fun `혼자 모드 홈을 그린다`() {
        render("solo_home_before_enlist", beforeEnlist)
        render("solo_home_before_enlist_invited", beforeEnlist.copy(invitePending = true))
        render("solo_home_enlisted", enlisted)
        render("solo_home_soldier_before_enlist", beforeEnlist.copy(role = Role.SOLDIER))
    }

    private fun render(name: String, state: HomeState) {
        val png = ImageComposeScene(width = 390 * 2, height = 844 * 2, density = Density(2f)) {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { HomeScreen(state, {}, {}, {}) }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
