package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme

/**
 * 뱃지 색조 (Figma: C03 · Badge → Tone).
 */
enum class FlowerBadgeTone {
    /** 특별 표시 (한정 · 프리미엄 등) */
    Gold,

    /** 브랜드 강조 */
    Brand,

    /** 보조 분류 */
    Accent,

    /** 기타 */
    Neutral,
}

/**
 * 짧은 속성 라벨 (Figma: C03 · Badge).
 *
 * 색조만 바꿔 재사용한다. 크기·모양은 모든 색조가 동일하다.
 */
@Composable
fun FlowerBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: FlowerBadgeTone = FlowerBadgeTone.Gold,
) {
    val colors = FlowerTheme.colors
    val background = when (tone) {
        FlowerBadgeTone.Gold -> colors.bgGoldSubtle
        FlowerBadgeTone.Brand -> colors.bgBrandSubtle
        FlowerBadgeTone.Accent -> colors.bgAccentSubtle
        FlowerBadgeTone.Neutral -> colors.bgSurfaceSunken
    }
    val content = when (tone) {
        FlowerBadgeTone.Gold -> colors.textGold
        FlowerBadgeTone.Brand -> colors.textBrand
        FlowerBadgeTone.Accent -> colors.textAccent
        FlowerBadgeTone.Neutral -> colors.textSecondary
    }

    Row(
        modifier = modifier
            .background(background, FlowerTheme.shapes.full)
            .padding(
                horizontal = FlowerTheme.spacing.sm,
                vertical = FlowerTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FlowerTheme.typography.labelS,
            color = content,
        )
    }
}

@Preview(name = "Badge · Light", showBackground = true, backgroundColor = 0xFFF7F4EE)
@Preview(
    name = "Badge · Dark",
    showBackground = true,
    backgroundColor = 0xFF14110F,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FlowerBadgePreview() {
    FlowerShoesTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowerBadge("국보", tone = FlowerBadgeTone.Gold)
            FlowerBadge("추천", tone = FlowerBadgeTone.Brand)
            FlowerBadge("조선", tone = FlowerBadgeTone.Accent)
            FlowerBadge("공공누리", tone = FlowerBadgeTone.Neutral)
        }
    }
}
