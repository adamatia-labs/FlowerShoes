package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 편지지 세트(Figma 편지 쓰기 · 편지지 시트 `361:3444`, Letter Paper `341:830`). */
class PaperSetTest {

    @Test
    fun `무료 다섯과 꽃신 평생 다섯은 Figma 편지지 시트 순서다`() {
        assertEquals(
            listOf("로즈 줄노트", "군사우편", "원고지", "크라프트", "하늘 수채"),
            Paper.entries.filter { !it.lifetime }.map { it.label },
        )
        assertEquals(
            listOf("벚꽃 엽서", "밤하늘", "체크 노트", "빈티지 타자", "라벤더 수채"),
            Paper.entries.filter { it.lifetime }.map { it.label },
        )
    }

    @Test
    fun `저장되는 id 는 서로 다르고 모르는 id 는 로즈 줄노트로 연다`() {
        assertEquals(
            listOf("rose", "mail", "manuscript", "kraft", "sky", "blossom", "night", "check", "typewriter", "lavender"),
            Paper.entries.map { it.id },
        )
        assertEquals(Paper.ROSE, Paper.of("plain"))
        assertEquals(Paper.ROSE, Paper.of(null))
        assertEquals(Paper.TYPEWRITER, Paper.of("typewriter"))
    }

    @Test
    fun `새 편지는 로즈 줄노트에 쓴다`() {
        assertEquals(Paper.ROSE.id, ComposeDraft().paper)
    }

    @Test
    fun `무료 편지지 색은 Figma Letter Paper 그대로다`() {
        fun hex(p: Paper) = with(p.look()) { listOf(background, edge, rule).map { "%06X".format(it.toArgb() and 0xFFFFFF) } }
        assertEquals(listOf("FFFDF8", "E4D8C8", "EBCFD3"), hex(Paper.ROSE))
        assertEquals(listOf("F3EEDB", "D8CFB0", "CFC6A2"), hex(Paper.MAIL))
        assertEquals(listOf("FFFFFF", "E6DADA", "E7C9C9"), hex(Paper.MANUSCRIPT))
        assertEquals(listOf("D9C4A3", "C7B08A", "BFA884"), hex(Paper.KRAFT))
        assertEquals(listOf("EEF4F7", "D5E2EA", "C9DBE5"), hex(Paper.SKY))
    }

    private val writing = LetterState(
        loading = false, partnerName = "민준",
        draft = ComposeDraft(title = "보고 싶은 밤", text = "민준아, 오늘은 유난히 네 생각이 많이 났어.\n이번 휴가 때 꼭 같이 가자."),
    )

    /** 편지 쓰기 화면을 2배로 그린다(360×760). 편지지는 위 막대(56) 아래 x 0..360, y 약 60..540. */
    private fun compose(paper: Paper, theme: AppTheme = AppTheme.GOMSIN, dark: Boolean = false): Bitmap =
        ImageComposeScene(width = 720, height = 1520, density = Density(2f)) {
            KkotsinTheme(theme = theme, dark = dark) {
                LetterComposeScreen(writing.copy(draft = writing.draft.copy(paper = paper.id)), lifetimeOn = true, canPhoto = true, onIntent = {}, onClose = {}, onSchedule = {})
            }
        }.use { scene ->
            scene.render()
            Bitmap.makeFromImage(scene.render(1_000_000_000L)).also { bmp ->
                val png = Image.makeFromBitmap(bmp).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File("build/render/paper_${paper.id}${if (dark) "_dark" else ""}.png").apply { parentFile.mkdirs(); writeBytes(png) }
            }
        }

    @Test
    fun `편지지 열 장을 편지 쓰기 화면에 그린다`() {
        for (p in Paper.entries) compose(p)
        compose(Paper.NIGHT, AppTheme.SOLDIER, dark = true)
    }

    @Test
    fun `수채 편지지는 오른쪽 위가 번지고 왼쪽 아래는 종이 색이다`() {
        for (p in listOf(Paper.SKY, Paper.LAVENDER)) {
            val image = compose(p)
            val paper = p.look().background.toArgb()
            // 오른쪽 위(종이 폭 - 64, 위 + 30 근처)와 종이 아래쪽 왼편 빈 곳.
            assertNotEquals("%08X".format(paper), "%08X".format(image.getColor(720 - 128, 2 * (60 + 36))), p.name)
            assertEquals("%08X".format(paper), "%08X".format(image.getColor(60, 2 * 520 + 1)), p.name)
        }
    }

    @Test
    fun `원고지는 글줄마다 세로 칸을 긋고 로즈 줄노트는 긋지 않는다`() {
        fun columns(p: Paper): Int {
            val image = compose(p)
            val rule = p.look().rule.toArgb()
            // 빈 글줄(아래쪽) 한가운데 가로로 훑어 칸 선 색을 센다.
            val y = 2 * 480
            return (0 until image.width).count { image.getColor(it, y) == rule }
        }
        assertTrue(columns(Paper.MANUSCRIPT) >= 10, "원고지 칸")
        assertEquals(0, columns(Paper.ROSE), "로즈 줄노트")
    }
}
