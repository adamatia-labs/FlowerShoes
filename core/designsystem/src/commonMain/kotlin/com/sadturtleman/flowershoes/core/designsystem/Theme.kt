package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.model.ThemeAccent

/**
 * 글자 단계. Figma 는 Noto Sans KR 이다 — Android 의 한글 기본 글꼴이 Noto Sans CJK 라 우선 시스템 글꼴을 쓰고,
 * iOS 에서 차이가 크면 글꼴 파일을 리소스로 넣는다.
 */
@Immutable
data class KkTypography(
    val display: TextStyle = style(32, FontWeight.Bold, 1.3f, -0.5f),
    val titleL: TextStyle = style(24, FontWeight.Bold, 1.35f, -0.3f),
    val titleM: TextStyle = style(20, FontWeight.Bold, 1.4f, -0.2f),
    val titleS: TextStyle = style(17, FontWeight.Bold, 1.4f, -0.3f),
    val bodyL: TextStyle = style(16, FontWeight.Normal, 1.6f, -0.3f),
    val bodyM: TextStyle = style(15, FontWeight.Normal, 1.6f, -0.3f),
    val bodyS: TextStyle = style(13, FontWeight.Normal, 1.6f, -0.2f),
    val labelM: TextStyle = style(14, FontWeight.Medium, 1.4f, 0f),
    val labelS: TextStyle = style(12, FontWeight.Medium, 1.4f, 0f),
    val caption: TextStyle = style(12, FontWeight.Normal, 1.45f, 0f),
    val dday: TextStyle = style(40, FontWeight.Bold, 1.15f, -1f),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Float, letterSpacingPx: Float) = TextStyle(
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = (letterSpacingPx / size).em,
)

/** 간격 · 반경(Figma spacing · radius). */
object KkSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val screen = 20.dp
}

object KkRadius {
    val sm = 8.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 24.dp
    val full = 999.dp
}

val LocalKkColors = staticCompositionLocalOf { kkColors(ThemeAccent.ROSE, dark = false) }
val LocalKkTypography = staticCompositionLocalOf { KkTypography() }

object KkTheme {
    val colors: KkColors @Composable get() = LocalKkColors.current
    val type: KkTypography @Composable get() = LocalKkTypography.current
}

/**
 * 앱 테마. [accent] 는 이 기기에만 저장된 값이다(상대 화면에 영향 없음).
 * 라이트/다크는 테마와 별개 축이고 시스템 설정을 따른다.
 */
@Composable
fun KkotsinTheme(
    accent: ThemeAccent,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = kkColors(accent, dark)
    val material = if (dark) {
        darkColorScheme(
            primary = colors.bgBrand, onPrimary = colors.textOnBrand, background = colors.bgCanvas,
            surface = colors.bgSurface, onSurface = colors.textPrimary, onBackground = colors.textPrimary,
            outline = colors.borderDefault, error = colors.statusDanger,
        )
    } else {
        lightColorScheme(
            primary = colors.bgBrand, onPrimary = colors.textOnBrand, background = colors.bgCanvas,
            surface = colors.bgSurface, onSurface = colors.textPrimary, onBackground = colors.textPrimary,
            outline = colors.borderDefault, error = colors.statusDanger,
        )
    }
    CompositionLocalProvider(LocalKkColors provides colors, LocalKkTypography provides KkTypography()) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
