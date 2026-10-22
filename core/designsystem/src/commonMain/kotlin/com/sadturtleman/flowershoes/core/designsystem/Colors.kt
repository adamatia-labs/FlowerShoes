package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.sadturtleman.flowershoes.core.model.ThemeAccent

/**
 * Figma 🎨 Foundations 의 원시 팔레트(primitive). 화면 코드는 이것을 직접 쓰지 않고 [KkColors] 를 쓴다.
 */
object Palette {
    val Rose50 = Color(0xFFFFF2F7)
    val Rose100 = Color(0xFFFFE0EC)
    val Rose300 = Color(0xFFFF99BB)
    val Rose500 = Color(0xFFED4773)
    val Rose600 = Color(0xFFC72C4D)
    val Rose900 = Color(0xFF47070E)

    val Olive50 = Color(0xFFF7F7EB)
    val Olive300 = Color(0xFFB3BF77)
    val Olive500 = Color(0xFF748736)
    val Olive600 = Color(0xFF566B24)
    val Olive900 = Color(0xFF182409)

    val Khaki50 = Color(0xFFF9F4EA)
    val Khaki600 = Color(0xFF7D5A22)

    val Navy50 = Color(0xFFEFF3FB)
    val Navy300 = Color(0xFF93ACE0)
    val Navy600 = Color(0xFF2E4F99)
    val Navy900 = Color(0xFF0E1A36)

    val Gray0 = Color(0xFFFFFFFF)
    val Gray50 = Color(0xFFF7F7F8)
    val Gray100 = Color(0xFFEFEFF1)
    val Gray200 = Color(0xFFE1E2E5)
    val Gray300 = Color(0xFFC9CBD0)
    val Gray400 = Color(0xFFA2A6AE)
    val Gray450 = Color(0xFFB5B8BE)
    val Gray500 = Color(0xFF656A74)
    val Gray600 = Color(0xFF5A5F69)
    val Gray700 = Color(0xFF40444C)
    val Gray800 = Color(0xFF25282D)
    val Gray900 = Color(0xFF15171A)

    val Sky500 = Color(0xFF3B9BF5)
    val Violet500 = Color(0xFF8B6CF0)
    val Amber500 = Color(0xFFF2A93B)
    val Green500 = Color(0xFF2FB67C)
    val Danger = Color(0xFFC8343A)
}

/**
 * 의미 색(semantic). Figma 변수 이름과 1:1 이다(bg/brand → [bgBrand]).
 *
 * 테마가 바꾸는 것은 강조색(brand)과 그림자 색, 그리고 올리브 테마의 군 색(카키)뿐이다.
 * "마음" 요소(보고싶어 · 하트)는 [heart] 로 모든 테마에서 로즈 고정이다.
 */
@Immutable
data class KkColors(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgSubtle: Color,
    val bgBrand: Color,
    val bgBrandSubtle: Color,
    val bgMilitary: Color,
    val bgMilitarySubtle: Color,
    val bgInverse: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnBrand: Color,
    val textBrand: Color,
    val textMilitary: Color,
    val textInverse: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    val borderBrand: Color,
    val shadowKey: Color,
    val shadowAmbient: Color,
    val isDark: Boolean,
) {
    val heart: Color get() = Palette.Rose600
    val heartSubtle: Color get() = if (isDark) Palette.Rose900 else Palette.Rose50

    val statusSuccess: Color get() = Palette.Green500
    val statusWarning: Color get() = Palette.Amber500
    val statusDanger: Color get() = Palette.Danger

    val eventVacation: Color get() = Palette.Sky500
    val eventOvernight: Color get() = Palette.Violet500
    val eventVisit: Color get() = Palette.Rose500
    val eventTraining: Color get() = bgMilitary
    val eventDuty: Color get() = Palette.Amber500
}

private data class Accent(
    val brand: Color,
    val brandDark: Color,
    val textBrand: Color,
    val textBrandDark: Color,
    val subtle: Color,
    val subtleDark: Color,
    val border: Color,
    val canvas: Color,
    val shadowTint: Color,
)

// 테마 시안 페이지의 값. bgBrand(라이트·다크)와 다크 테두리 일부는 시안 화면에 쓰이지 않아 같은 팔레트 단계로 추정했다.
private fun accent(theme: ThemeAccent): Accent = when (theme) {
    ThemeAccent.ROSE -> Accent(
        Palette.Rose600, Palette.Rose500, Palette.Rose600, Palette.Rose300,
        Palette.Rose50, Palette.Rose900, Palette.Rose500, Color(0xFFFDF6F7), Color(0xFF4A0F20),
    )
    ThemeAccent.OLIVE -> Accent(
        Palette.Olive600, Palette.Olive500, Palette.Olive600, Palette.Olive300,
        Palette.Olive50, Palette.Olive900, Palette.Olive500, Color(0xFFFAF9F3), Color(0xFF2A3B10),
    )
    ThemeAccent.NAVY -> Accent(
        Palette.Navy600, Color(0xFF5A7BC4), Palette.Navy600, Palette.Navy300,
        Palette.Navy50, Palette.Navy900, Color(0xFF4A6DB8), Color(0xFFF7F8FB), Color(0xFF182B57),
    )
    ThemeAccent.MONO -> Accent(
        Palette.Gray800, Palette.Gray200, Palette.Gray800, Palette.Gray200,
        Palette.Gray100, Palette.Gray700, Palette.Gray600, Color(0xFFF8F7F4), Palette.Gray900,
    )
}

fun kkColors(theme: ThemeAccent, dark: Boolean): KkColors {
    val a = accent(theme)
    // 군 색은 기본 올리브, 올리브 테마에서만 카키로 바뀌어 강조색과 겹치지 않는다.
    val khaki = theme == ThemeAccent.OLIVE
    return if (!dark) {
        KkColors(
            bgCanvas = a.canvas, bgSurface = Palette.Gray0, bgSubtle = Palette.Gray100,
            bgBrand = a.brand, bgBrandSubtle = a.subtle,
            bgMilitary = if (khaki) Palette.Khaki600 else Palette.Olive600,
            bgMilitarySubtle = if (khaki) Palette.Khaki50 else Palette.Olive50,
            bgInverse = Palette.Gray900,
            textPrimary = Palette.Gray900, textSecondary = Palette.Gray600, textTertiary = Palette.Gray500,
            textOnBrand = Palette.Gray0, textBrand = a.textBrand,
            textMilitary = if (khaki) Palette.Khaki600 else Palette.Olive600,
            textInverse = Palette.Gray0,
            borderDefault = Palette.Gray200, borderStrong = Palette.Gray300, borderBrand = a.border,
            shadowKey = a.shadowTint.copy(alpha = 0x1A / 255f), shadowAmbient = a.shadowTint.copy(alpha = 0x0D / 255f),
            isDark = false,
        )
    } else {
        KkColors(
            bgCanvas = Palette.Gray900, bgSurface = Palette.Gray800, bgSubtle = Palette.Gray700,
            bgBrand = a.brandDark, bgBrandSubtle = a.subtleDark,
            bgMilitary = if (khaki) Color(0xFF9C7535) else Palette.Olive600,
            bgMilitarySubtle = if (khaki) Color(0xFF2B1F0B) else Palette.Olive900,
            bgInverse = Palette.Gray0,
            textPrimary = Palette.Gray0, textSecondary = Palette.Gray300, textTertiary = Palette.Gray450,
            textOnBrand = if (theme == ThemeAccent.MONO) Palette.Gray900 else Palette.Gray0, textBrand = a.textBrandDark,
            textMilitary = if (khaki) Color(0xFFD9B97A) else Palette.Olive300,
            textInverse = Palette.Gray900,
            borderDefault = Palette.Gray700, borderStrong = Palette.Gray500, borderBrand = a.textBrandDark,
            shadowKey = Color(0x73000000), shadowAmbient = Color(0x4D000000),
            isDark = true,
        )
    }
}
