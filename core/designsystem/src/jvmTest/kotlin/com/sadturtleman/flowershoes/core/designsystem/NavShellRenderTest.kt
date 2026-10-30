package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 탭 셸(Figma 11 · 큰 화면, Nav Rail `428:15898`): 폰은 하단 탭 바, 600dp 부터 Nav Rail, 콘텐츠는 최대 600dp 가운데. */
class NavShellRenderTest {
    private val tabs = listOf(KkTab("홈", KkIcons.Home), KkTab("캘린더", KkIcons.Calendar), KkTab("편지", KkIcons.Mail), KkTab("추억", KkIcons.Album))

    /** [width]×[height] 창에 셸을 그리고 콘텐츠 칸과 가운데 둔 콘텐츠의 크기(dp)를 돌려준다. */
    private fun shell(name: String, width: Int, height: Int, showNav: Boolean = true, dark: Boolean = false): Pair<IntSize, IntSize> {
        var pane = IntSize.Zero
        var content = IntSize.Zero
        val png = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = dark) {
                Box(Modifier.size(width.dp, height.dp)) {
                    KkWindowSizeProvider {
                        KkNavScaffold(tabs, selected = 2, onSelect = {}, showNav = showNav) {
                            Box(Modifier.fillMaxSize().onGloballyPositioned { pane = it.size }) {
                                KkContentWidth {
                                    Box(Modifier.fillMaxSize().background(KkTheme.colors.surface).onGloballyPositioned { content = it.size }) {
                                        Text("콘텐츠", style = KkTheme.type.title, color = KkTheme.colors.ink)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
        return pane to content
    }

    @Test
    fun `폰은 콘텐츠 아래 하단 탭 바`() {
        val (pane, content) = shell("shell_phone", 360, 760)
        assertEquals(360, pane.width)
        assertTrue(pane.height < 760, "탭 바가 아래를 차지한다")
        assertEquals(360, content.width)
    }

    @Test
    fun `큰 화면은 왼쪽 Nav Rail 80 이고 콘텐츠는 최대 600 가운데`() {
        val (pane, content) = shell("shell_tablet", 1280, 800)
        assertEquals(1280 - 80, pane.width)
        assertEquals(800, pane.height)
        assertEquals(600, content.width)
        shell("shell_tablet_dark", 1280, 800, dark = true)
        shell("shell_foldable", 840, 600)
    }

    @Test
    fun `내비게이션을 숨기면 콘텐츠가 전체`() {
        val (pane, _) = shell("shell_tablet_no_nav", 1280, 800, showNav = false)
        assertEquals(1280, pane.width)
    }
}
