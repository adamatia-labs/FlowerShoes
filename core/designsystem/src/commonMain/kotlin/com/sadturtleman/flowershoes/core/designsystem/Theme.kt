package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.designsystem.res.Res
import com.sadturtleman.flowershoes.core.designsystem.res.noto_sans_kr
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import org.jetbrains.compose.resources.Font

/**
 * 글자 단계(Figma 텍스트 스타일과 1:1). 글꼴은 Noto Sans KR(가변 글꼴 하나를 굵기별로 쓴다).
 * Android 의 한글 기본 글꼴은 기기마다 달라(삼성 · 시스템 Noto) 디자인과 같게 보이도록 글꼴을 싣는다.
 */
@Immutable
data class KkTypography(
    val display: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val titleS: TextStyle,
    val bodyL: TextStyle,
    val bodyM: TextStyle,
    val bodyS: TextStyle,
    val labelM: TextStyle,
    val labelS: TextStyle,
    val caption: TextStyle,
    val dday: TextStyle,
) {
    companion object {
        fun of(family: FontFamily) = KkTypography(
            display = style(family, 32, FontWeight.Bold, 1.3f, -0.5f),
            titleL = style(family, 24, FontWeight.Bold, 1.35f, -0.3f),
            titleM = style(family, 20, FontWeight.Bold, 1.4f, -0.2f),
            titleS = style(family, 17, FontWeight.Bold, 1.4f, -0.3f),
            bodyL = style(family, 16, FontWeight.Normal, 1.6f, -0.3f),
            bodyM = style(family, 15, FontWeight.Normal, 1.6f, -0.3f),
            bodyS = style(family, 13, FontWeight.Normal, 1.6f, -0.2f),
            labelM = style(family, 14, FontWeight.Medium, 1.4f, 0f),
            labelS = style(family, 12, FontWeight.Medium, 1.4f, 0f),
            caption = style(family, 12, FontWeight.Normal, 1.45f, 0f),
            dday = style(family, 40, FontWeight.Bold, 1.15f, -1f),
        )
    }
}

private fun style(family: FontFamily, size: Int, weight: FontWeight, lineHeight: Float, letterSpacingPx: Float) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = (letterSpacingPx / size).em,
)

@Composable
private fun notoSansKr(): FontFamily {
    val regular = Font(Res.font.noto_sans_kr, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400)))
    val medium = Font(Res.font.noto_sans_kr, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    val bold = Font(Res.font.noto_sans_kr, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    return remember(regular, medium, bold) { FontFamily(regular, medium, bold) }
}

/** 간격 · 반경(Figma spacing · radius). */
object KkSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
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
val LocalKkTypography = staticCompositionLocalOf { KkTypography.of(FontFamily.Default) }

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
    val family = notoSansKr()
    val type = remember(family) { KkTypography.of(family) }
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
    CompositionLocalProvider(LocalKkColors provides colors, LocalKkTypography provides type) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
