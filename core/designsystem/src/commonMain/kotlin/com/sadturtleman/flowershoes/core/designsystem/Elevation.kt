package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Figma Effect 스타일(Elevation/Low · Mid · High · Bar). 각각 그림자 두 겹(ambient + key)이고 값은 Figma 와 같다.
 * 색은 테마 v2 에 그림자 변수가 없어 라이트는 ink 를 옅게, 다크는 검정을 쓴다.
 */
enum class KkElevation { None, Low, Mid, High, Bar }

@Composable
fun Modifier.kkElevation(level: KkElevation, shape: Shape): Modifier {
    val c = KkTheme.colors
    val ambient = if (c.isDark) Color.Black.copy(alpha = 0x4D / 255f) else c.ink.copy(alpha = 0x0D / 255f)
    val key = if (c.isDark) Color.Black.copy(alpha = 0x73 / 255f) else c.ink.copy(alpha = 0x1A / 255f)
    fun Modifier.two(ambientY: Int, ambientBlur: Int, keyY: Int, keyBlur: Int) = this
        .dropShadow(shape, Shadow(radius = ambientBlur.dp, color = ambient, offset = DpOffset(0.dp, ambientY.dp)))
        .dropShadow(shape, Shadow(radius = keyBlur.dp, color = key, offset = DpOffset(0.dp, keyY.dp)))
    return when (level) {
        KkElevation.None -> this
        KkElevation.Low -> two(1, 2, 2, 6)
        KkElevation.Mid -> two(2, 4, 6, 16)
        KkElevation.High -> two(4, 8, 16, 32)
        KkElevation.Bar -> two(-1, 2, -4, 16)
    }
}
