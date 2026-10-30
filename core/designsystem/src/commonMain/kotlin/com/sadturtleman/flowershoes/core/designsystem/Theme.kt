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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.designsystem.res.Res
import com.sadturtleman.flowershoes.core.designsystem.res.black_han_sans
import com.sadturtleman.flowershoes.core.designsystem.res.do_hyeon
import com.sadturtleman.flowershoes.core.designsystem.res.gowun_batang
import com.sadturtleman.flowershoes.core.designsystem.res.gowun_batang_bold
import com.sadturtleman.flowershoes.core.designsystem.res.nanum_pen
import com.sadturtleman.flowershoes.core.designsystem.res.noto_sans_kr
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.compose.resources.Font

/**
 * 글꼴 변수(Figma 테마 v2 font/display · number · hand · body). 굵기는 font/…-style 이다.
 * 곰신: 고운바탕 Bold · 고운바탕 Bold · 나눔손글씨 펜 · Noto Sans KR.
 * 군인: 도현 · 검은고딕 · 나눔손글씨 펜 · Noto Sans KR.
 */
@Immutable
data class KkFonts(
    val display: FontFamily,
    val displayWeight: FontWeight,
    val number: FontFamily,
    val numberWeight: FontWeight,
    val hand: FontFamily,
    val body: FontFamily,
)

/**
 * 텍스트 스타일(Figma "테마 v2/…" 와 1:1). 줄 높이가 Auto 인 스타일은 글꼴 기본 줄 높이를 쓴다.
 * Android 의 한글 기본 글꼴은 기기마다 달라(삼성 · 시스템 Noto) 디자인과 같게 보이도록 글꼴을 싣는다.
 */
@Immutable
data class KkTypography(
    /** D-day/L — 홈의 큰 전역 D-day 숫자. */
    val ddayL: TextStyle,
    /** D-day/M — 태그 안 D-day 숫자. */
    val ddayM: TextStyle,
    /** D-day/S — 휴가 · 진급 같은 작은 D-day. */
    val ddayS: TextStyle,
    /** Title — 화면 제목 · 두 사람 이름. */
    val title: TextStyle,
    /** Hand/L — 손글씨 라벨(전역까지 등). */
    val handL: TextStyle,
    /** Hand/Letter — 편지 본문 · 편지 첫 줄. */
    val handLetter: TextStyle,
    /** Body — 본문. */
    val body: TextStyle,
    /** Label — 라벨 · 태그 이름. */
    val label: TextStyle,
    /** Label/Bold — 섹션 제목(오늘 온 편지 등). */
    val labelBold: TextStyle,
    /** Caption — 날짜 · 보낸 시각 · 진행 라벨. */
    val caption: TextStyle,
) {
    companion object {
        fun of(f: KkFonts) = KkTypography(
            ddayL = style(f.number, 96, f.numberWeight, letterSpacingPercent = -2f),
            ddayM = style(f.number, 52, f.numberWeight, letterSpacingPercent = -2f),
            ddayS = style(f.number, 24, f.numberWeight),
            title = style(f.display, 20, f.displayWeight),
            handL = style(f.hand, 26, FontWeight.Normal),
            handLetter = style(f.hand, 21, FontWeight.Normal, lineHeight = 28.sp),
            body = style(f.body, 15, FontWeight.Normal, lineHeight = 22.sp),
            label = style(f.body, 13, FontWeight.Medium),
            labelBold = style(f.body, 13, FontWeight.Bold),
            caption = style(f.body, 11, FontWeight.Normal),
        )
    }
}

private fun style(
    family: FontFamily,
    size: Int,
    weight: FontWeight,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacingPercent: Float = 0f,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = (letterSpacingPercent / 100f).em,
)

@Composable
private fun notoSansKr(): FontFamily {
    val regular = Font(Res.font.noto_sans_kr, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400)))
    val medium = Font(Res.font.noto_sans_kr, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    val bold = Font(Res.font.noto_sans_kr, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    return remember(regular, medium, bold) { FontFamily(regular, medium, bold) }
}

@Composable
private fun kkFonts(theme: AppTheme): KkFonts {
    val body = notoSansKr()
    val batangRegular = Font(Res.font.gowun_batang, FontWeight.Normal)
    val batangBold = Font(Res.font.gowun_batang_bold, FontWeight.Bold)
    val hand = Font(Res.font.nanum_pen, FontWeight.Normal)
    val doHyeon = Font(Res.font.do_hyeon, FontWeight.Normal)
    val blackHanSans = Font(Res.font.black_han_sans, FontWeight.Normal)
    return remember(theme, body, batangRegular, batangBold, hand, doHyeon, blackHanSans) {
        val batang = FontFamily(batangRegular, batangBold)
        when (theme) {
            AppTheme.GOMSIN -> KkFonts(batang, FontWeight.Bold, batang, FontWeight.Bold, FontFamily(hand), body)
            AppTheme.SOLDIER -> KkFonts(FontFamily(doHyeon), FontWeight.Normal, FontFamily(blackHanSans), FontWeight.Normal, FontFamily(hand), body)
        }
    }
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

private val DefaultFonts = KkFonts(FontFamily.Default, FontWeight.Bold, FontFamily.Default, FontWeight.Bold, FontFamily.Default, FontFamily.Default)

val LocalKkColors = staticCompositionLocalOf { kkColors(AppTheme.GOMSIN, dark = false) }
val LocalKkFonts = staticCompositionLocalOf { DefaultFonts }
val LocalKkTypography = staticCompositionLocalOf { KkTypography.of(DefaultFonts) }
val LocalKkAppTheme = staticCompositionLocalOf { AppTheme.GOMSIN }

object KkTheme {
    val colors: KkColors @Composable get() = LocalKkColors.current
    val fonts: KkFonts @Composable get() = LocalKkFonts.current
    val type: KkTypography @Composable get() = LocalKkTypography.current

    /** 지금 테마(곰신 · 군인). 색 · 글꼴로 안 되는 장식 차이(홈 말린 꽃 · 소인, 진행 길 · 개월 칸)에만 쓴다. */
    val appTheme: AppTheme @Composable get() = LocalKkAppTheme.current
}

/**
 * 앱 테마. [theme] 는 이 기기에만 저장된 값이다(상대 화면에 영향 없음). 기본값은 역할을 따른다.
 * 라이트/다크는 테마와 별개 축이고 시스템 설정을 따른다(설정 › 화면 모드에서 바꾼다).
 */
@Composable
fun KkotsinTheme(
    theme: AppTheme,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = kkColors(theme, dark)
    val fonts = kkFonts(theme)
    val type = remember(fonts) { KkTypography.of(fonts) }
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.surface, background = colors.canvas,
            surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink,
            outline = colors.line, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.surface, background = colors.canvas,
            surface = colors.surface, onSurface = colors.ink, onBackground = colors.ink,
            outline = colors.line, error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalKkColors provides colors, LocalKkFonts provides fonts, LocalKkTypography provides type, LocalKkAppTheme provides theme) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

/**
 * 안의 글자를 글자 크기 설정(시스템 글자 확대)과 상관없이 1배로 그린다. 우표 · 소인 · 봉인처럼 정해진 틀 안의
 * 장식 글자에만 쓴다(design.md 접근성: 장식 글자 예외) — 읽어야 하는 글자는 늘 커져야 한다.
 */
@Composable
fun KkFixedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f), content = content)
}
