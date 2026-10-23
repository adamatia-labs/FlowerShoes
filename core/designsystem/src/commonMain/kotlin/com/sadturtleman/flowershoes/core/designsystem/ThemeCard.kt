package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import com.sadturtleman.flowershoes.core.model.koreanName
import com.sadturtleman.flowershoes.core.model.mood

/** 테마 카드(온보딩 테마 · 설정 › 테마): 견본 · 라디오 · 이름(+추천) · 분위기. */
@Composable
fun KkThemeCard(theme: ThemeAccent, selected: Boolean, recommended: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier
            .then(if (selected) Modifier else Modifier.kkElevation(KkElevation.Low, shape))
            .clip(shape)
            .background(if (selected) c.bgBrandSubtle else c.bgSurface)
            .then(if (selected) Modifier.border(2.dp, c.borderBrand, shape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            KkThemeSwatch(theme)
            KkRadio(selected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(theme.koreanName, style = KkTheme.type.titleS, color = c.textPrimary)
            if (recommended) {
                Text(
                    "추천", style = KkTheme.type.caption, color = c.textBrand,
                    modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bgSurface).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(theme.mood, style = KkTheme.type.caption, color = c.textSecondary)
    }
}

/** 테마 견본: 배경색 원 + 강조색 원(27, 1px 테두리), 48×28. */
@Composable
fun KkThemeSwatch(theme: ThemeAccent) {
    val light = kkColors(theme, dark = false)
    val ring = KkTheme.colors.borderDefault
    Canvas(Modifier.size(48.dp, 28.dp)) {
        val r = 13.5.dp.toPx()
        listOf(light.bgCanvas to Offset(14.dp.toPx(), 14.dp.toPx()), light.bgBrand to Offset(34.dp.toPx(), 14.dp.toPx())).forEach { (fill, center) ->
            drawCircle(fill, r, center)
            drawCircle(ring, r, center, style = Stroke(1.dp.toPx()))
        }
    }
}
