package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 섹션 제목 + 전체보기 액션 (Figma: C08 · SectionHeader).
 *
 * 홈 · 상세의 각 묶음 제목이다. [onActionClick] 이 null 이면 액션 슬롯을 그리지 않는다.
 */
@Composable
fun FlowerSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "전체보기",
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FlowerTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = FlowerTheme.typography.titleM,
            color = FlowerTheme.colors.textPrimary,
        )
        if (onActionClick != null) {
            Row(
                modifier = Modifier.clickableNoIndication(onClick = onActionClick),
                horizontalArrangement = Arrangement.spacedBy(FlowerTheme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    style = FlowerTheme.typography.labelM,
                    color = FlowerTheme.colors.textSecondary,
                )
                FlowerIcon(
                    id = FlowerIcons.ChevronRight,
                    contentDescription = null,
                    tint = FlowerTheme.colors.iconSecondary,
                    size = FlowerTheme.size.iconXs,
                )
            }
        }
    }
}

@Preview(name = "SectionHeader · Light", showBackground = true, backgroundColor = 0xFFF7F4EE)
@Preview(
    name = "SectionHeader · Dark",
    showBackground = true,
    backgroundColor = 0xFF14110F,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FlowerSectionHeaderPreview() {
    FlowerShoesTheme {
        FlowerSectionHeader(
            title = "오늘의 추천",
            onActionClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
