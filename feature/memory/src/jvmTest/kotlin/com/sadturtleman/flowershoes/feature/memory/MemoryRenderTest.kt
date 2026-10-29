package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 추억 · 목록 · 비어 있음 · 로딩(Figma 367:82)과 추억 · 한 달(367:266)을 build/render 에 그린다. */
class MemoryRenderTest {

    private fun letter(id: String, day: Int, text: String, mine: Boolean = false, kind: LetterKind = LetterKind.TEXT, title: String? = null, unread: Boolean = false) =
        MonthLetter(
            id, LocalDate(2026, 10, day), "10.$day", if (mine) "지수" else "민준", mine, kind, text, text, "", null, null,
            title = title, unread = unread, stamp = "D-479", caption = "민준이 그려준 나".takeIf { kind == LetterKind.DRAWING_ONLY },
        )

    private val october = MonthDetail(
        YearMonth(2026, 10), 7, emptySet(), emptySet(), emptyList(),
        letters = listOf(
            letter("a", 28, "오늘 당직인데 네 생각나서 몰래 써", title = "당직 편지", unread = true),
            letter("b", 27, "", kind = LetterKind.DRAWING_ONLY),
            letter("c", 24, "훈련 끝나고 쓰는 편지"),
            letter("d", 19, "첫 면회 다녀와서", mine = true),
            letter("e", 12, "이번 주는 비가 많이 와"),
        ),
        meets = listOf(
            MeetCard("m", LocalDate(2026, 10, 19), "면회", EventType.VISIT, "첫 면회", "10.19 (월) 13:00 – 16:00", emptyList(), listOf("지수" to "짧은 머리 보고 웃음 참느라 혼났어"), ""),
        ),
        drawings = 3,
    )
    private val september = october.copy(ym = YearMonth(2026, 9), number = 6, letters = october.letters.take(2), meets = emptyList(), drawings = 1)
    private val august = october.copy(ym = YearMonth(2026, 8), number = 5, letters = emptyList(), meets = emptyList(), drawings = 0)

    private fun chapter(m: MonthDetail) = Chapter(m.ym, m.number, m.letters.size, m.drawings, m.meets.size, null, writing = false, enlisted = false)

    private val list = MemoryState(
        loading = false,
        progress = BookProgress(7, 22, false),
        chapters = listOf(october, september, august).map(::chapter),
        months = listOf(october, september, august).associateBy { it.ym },
        latestMeet = "m",
    )

    @Test
    fun `추억 화면들을 그린다`() {
        render("memory_list") { MemoryScreen(list, emptyMap(), {}, {}) }
        render("memory_empty") { MemoryScreen(MemoryState(loading = false), emptyMap(), {}, {}) }
        render("memory_loading") { MemoryScreen(MemoryState(), emptyMap(), {}, {}) }
        render("memory_month") { MonthScreen(october, emptyMap(), {}, {}) }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        val png = ImageComposeScene(width = 360 * 2, height = 760 * 2, density = Density(2f)) {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { content() }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
