package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Figma 변수 컬렉션 "테마 v2"(VariableCollectionId:334:122)의 4개 모드 값을 Primitives 까지 풀어 적은 것.
 * Figma 값을 바꾸면 이 표도 Figma 에서 다시 읽어 바꾼다.
 */
class ThemeV2ColorsTest {

    private fun hex(c: Color) = "#%08X".format(c.toArgb()).let { "#" + it.substring(3) + it.substring(1, 3).takeIf { a -> a != "FF" }.orEmpty() }

    private fun KkColors.table() = mapOf(
        "canvas" to canvas, "surface" to surface, "bar" to bar, "tag" to tag, "tape" to tape,
        "ink" to ink, "sub" to sub, "line" to line, "accent" to accent,
        "love" to love, "loveOn" to loveOn, "stamp" to stamp, "plant" to plant,
        "danger" to danger, "success" to success,
        "eventVacation" to eventVacation, "eventOvernight" to eventOvernight, "eventVisit" to eventVisit,
        "eventTraining" to eventTraining, "eventDuty" to eventDuty, "eventAnniversary" to eventAnniversary,
    ).mapValues { hex(it.value) }

    private val lightEvents = mapOf(
        "danger" to "#B3412E", "success" to "#4F7A3A",
        "eventVacation" to "#C2703D", "eventOvernight" to "#6B5BA8", "eventVisit" to "#2F7F86",
        "eventTraining" to "#55623A", "eventDuty" to "#6E7262", "eventAnniversary" to "#B9475E",
    )
    private val darkEvents = mapOf(
        "danger" to "#E8826E", "success" to "#93C27A",
        "eventVacation" to "#E39A6A", "eventOvernight" to "#A397D8", "eventVisit" to "#6CC0C6",
        "eventTraining" to "#9AAE6B", "eventDuty" to "#A9AD9B", "eventAnniversary" to "#E07A8F",
    )

    @Test
    fun 곰신_라이트() = assertEquals(
        mapOf(
            "canvas" to "#F7F1E8", "surface" to "#FFFDF8", "bar" to "#FBF7F1", "tag" to "#D8C1A0", "tape" to "#EFE3B8D9",
            "ink" to "#3B302B", "sub" to "#74665C", "line" to "#E4D8C8", "accent" to "#B9475E",
            "love" to "#B9475E", "loveOn" to "#FFFFFF", "stamp" to "#B9475E", "plant" to "#7C8B5A",
        ) + lightEvents,
        kkColors(AppTheme.GOMSIN, dark = false).table(),
    )

    @Test
    fun 곰신_다크() = assertEquals(
        mapOf(
            "canvas" to "#1F1A17", "surface" to "#2A2420", "bar" to "#2A2420", "tag" to "#4A3D30", "tape" to "#6B6142D9",
            "ink" to "#EFE6DA", "sub" to "#B8AB9E", "line" to "#3A322C", "accent" to "#E07A8F",
            "love" to "#B9475E", "loveOn" to "#FFFFFF", "stamp" to "#E07A8F", "plant" to "#A3B07F",
        ) + darkEvents,
        kkColors(AppTheme.GOMSIN, dark = true).table(),
    )

    @Test
    fun 군인_라이트() = assertEquals(
        mapOf(
            "canvas" to "#ECEADF", "surface" to "#F8F6EE", "bar" to "#F3F2EA", "tag" to "#E3D3A8", "tape" to "#EFE3B8D9",
            "ink" to "#262B20", "sub" to "#62665A", "line" to "#CFCDBE", "accent" to "#55623A",
            "love" to "#A8402F", "loveOn" to "#FFFFFF", "stamp" to "#A8402F", "plant" to "#55623A",
        ) + lightEvents,
        kkColors(AppTheme.SOLDIER, dark = false).table(),
    )

    @Test
    fun 군인_다크() = assertEquals(
        mapOf(
            "canvas" to "#191C15", "surface" to "#23271E", "bar" to "#23271E", "tag" to "#4A4430", "tape" to "#5E5A3ED9",
            "ink" to "#E6E8DC", "sub" to "#B8BCAA", "line" to "#343A2C", "accent" to "#9AAE6B",
            "love" to "#B5503D", "loveOn" to "#FFFFFF", "stamp" to "#D0705E", "plant" to "#9AAE6B",
        ) + darkEvents,
        kkColors(AppTheme.SOLDIER, dark = true).table(),
    )
}
