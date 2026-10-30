package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 테마 v2 토큰 짝의 대비(design.md 접근성: 글자 4.5:1, 18pt 이상 · 굵은 글자와 글자 아닌 표시 3:1)를 4개 모드에서 본다.
 * 짝은 화면이 실제로 겹쳐 쓰는 것만: 글자 색 / 바탕 색.
 */
class ThemeV2ContrastTest {

    private fun ratio(fg: Color, bg: Color): Double {
        val a = fg.luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b).toDouble() / minOf(a, b)
    }

    /** 본문 · 라벨 · 캡션 글자(4.5:1). */
    private fun KkColors.textPairs() = mapOf(
        "ink/canvas" to (ink to canvas), "ink/surface" to (ink to surface), "ink/bar" to (ink to bar), "ink/tag" to (ink to tag),
        "sub/canvas" to (sub to canvas), "sub/surface" to (sub to surface), "sub/bar" to (sub to bar),
        // 글자 버튼 · 탭 · 진행 % · "편지 쓰기".
        "accent/canvas" to (accent to canvas), "accent/surface" to (accent to surface), "accent/bar" to (accent to bar),
        // 주 버튼 글자(canvas, Figma Button) · 고른 칩 글자(surface).
        "canvas/accent" to (canvas to accent), "canvas/danger" to (canvas to danger), "surface/accent" to (surface to accent),
        "danger/canvas" to (danger to canvas), "danger/surface" to (danger to surface),
        "success/surface" to (success to surface),
        "loveOn/love" to (loveOn to love),
    )

    /** 글자가 아닌 표시(일정 점 · 소인 테두리 · 장식 식물 · 하트, 3:1). */
    private fun KkColors.graphicPairs() = buildMap {
        for ((name, color) in listOf(
            "stamp" to stamp, "plant" to plant, "eventVacation" to eventVacation, "eventOvernight" to eventOvernight,
            "eventVisit" to eventVisit, "eventTraining" to eventTraining, "eventDuty" to eventDuty, "eventAnniversary" to eventAnniversary,
        )) {
            put("$name/canvas", color to canvas)
            put("$name/surface", color to surface)
        }
        // 보고싶어 · 보낸 뒤: bar 위 love 하트.
        put("love/bar", love to bar)
    }

    private fun check(minimum: Double, pairs: KkColors.() -> Map<String, Pair<Color, Color>>) {
        val failures = AppTheme.entries.flatMap { theme ->
            listOf(false, true).flatMap { dark ->
                kkColors(theme, dark).pairs().mapNotNull { (name, pair) ->
                    val r = ratio(pair.first, pair.second)
                    "${theme.name} ${if (dark) "dark" else "light"} $name = ${"%.2f".format(r)}".takeIf { r < minimum }
                }
            }
        }
        assertTrue(failures.isEmpty(), "대비 $minimum:1 미만:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `글자 토큰 짝은 4개 모드 모두 4_5 대 1 이상`() = check(4.5) { textPairs() }

    @Test
    fun `일정 점 · 소인 · 식물은 4개 모드 모두 바탕과 3 대 1 이상`() = check(3.0) { graphicPairs() }
}
