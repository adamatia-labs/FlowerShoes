package com.sadturtleman.flowershoes.common.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalFlowerColors = staticCompositionLocalOf { flowerLightColors() }
val LocalFlowerTypography = staticCompositionLocalOf { flowerTypography() }
val LocalFlowerSpacing = staticCompositionLocalOf { FlowerSpacing() }
val LocalFlowerShapes = staticCompositionLocalOf { FlowerShapes() }
val LocalFlowerSize = staticCompositionLocalOf { FlowerSize() }
val LocalFlowerElevation = staticCompositionLocalOf { FlowerElevation() }

/**
 * 디자인 토큰 접근점. 화면 코드는 `FlowerTheme.colors.textPrimary` 처럼 쓴다.
 *
 * Material3 의 [MaterialTheme] 도 함께 설정되므로 M3 컴포넌트를 섞어 써도
 * 브랜드 색이 유지된다. 다만 새 코드는 이 토큰을 우선 사용한다.
 */
object FlowerTheme {

    val colors: FlowerColors
        @Composable @ReadOnlyComposable get() = LocalFlowerColors.current

    val typography: FlowerTypography
        @Composable @ReadOnlyComposable get() = LocalFlowerTypography.current

    val spacing: FlowerSpacing
        @Composable @ReadOnlyComposable get() = LocalFlowerSpacing.current

    val shapes: FlowerShapes
        @Composable @ReadOnlyComposable get() = LocalFlowerShapes.current

    val size: FlowerSize
        @Composable @ReadOnlyComposable get() = LocalFlowerSize.current

    val elevation: FlowerElevation
        @Composable @ReadOnlyComposable get() = LocalFlowerElevation.current
}

/**
 * FlowerShoes 디자인 시스템 테마.
 *
 * 안드로이드 12+ 의 dynamic color 는 의도적으로 지원하지 않는다.
 * 브랜드 팔레트가 배경화면 색으로 치환되면 디자인 의도가 깨진다.
 *
 * 팔레트 · 타입 스케일은 EveryMuseum 기본 세팅을 그대로 옮겨 온 상태다.
 * FlowerShoes 시안이 나오면 [FlowerPalette] 와 시맨틱 토큰 값만 바꾸면 된다.
 */
@Composable
fun FlowerShoesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme) {
        if (darkTheme) flowerDarkColors() else flowerLightColors()
    }
    val typography = remember { flowerTypography() }
    val spacing = remember { FlowerSpacing() }
    val shapes = remember { FlowerShapes() }
    val size = remember { FlowerSize() }
    val elevation = remember { FlowerElevation() }

    CompositionLocalProvider(
        LocalFlowerColors provides colors,
        LocalFlowerTypography provides typography,
        LocalFlowerSpacing provides spacing,
        LocalFlowerShapes provides shapes,
        LocalFlowerSize provides size,
        LocalFlowerElevation provides elevation,
        LocalContentColor provides colors.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

/**
 * 시맨틱 토큰을 Material3 [androidx.compose.material3.ColorScheme] 에 얹는다.
 * 디자인 시스템에 없는 슬롯(error 계열 등)은 M3 기본값을 그대로 둔다.
 */
private fun FlowerColors.toMaterialColorScheme(darkTheme: Boolean) =
    (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = bgBrand,
        onPrimary = textOnBrand,
        primaryContainer = bgBrandSubtle,
        onPrimaryContainer = textBrand,
        secondary = textAccent,
        onSecondary = textOnBrand,
        secondaryContainer = bgAccentSubtle,
        onSecondaryContainer = textAccent,
        tertiary = textGold,
        onTertiary = textOnBrand,
        tertiaryContainer = bgGoldSubtle,
        onTertiaryContainer = textGold,
        background = bgCanvas,
        onBackground = textPrimary,
        surface = bgSurface,
        onSurface = textPrimary,
        surfaceVariant = bgSurfaceSunken,
        onSurfaceVariant = textSecondary,
        outline = borderDefault,
        outlineVariant = borderSubtle,
        scrim = bgScrim,
    )

/** 타입 스케일을 Material3 슬롯에 대응시킨다. */
private fun FlowerTypography.toMaterialTypography() = Typography(
    displayLarge = displaySerif,
    displayMedium = displaySerif,
    displaySmall = titleSerifL,
    headlineLarge = titleSerifL,
    headlineMedium = titleSerifM,
    headlineSmall = titleL,
    titleLarge = titleL,
    titleMedium = titleM,
    titleSmall = headline,
    bodyLarge = bodyL,
    bodyMedium = bodyM,
    bodySmall = bodyS,
    labelLarge = labelL,
    labelMedium = labelM,
    labelSmall = labelS,
)
