package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.common.presentation.R
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme

/**
 * 하단 탭 (Figma: C16 · TabBar → Active).
 */
enum class FlowerTab(
    @param:StringRes internal val labelRes: Int,
    @param:DrawableRes internal val icon: Int,
) {
    Home(R.string.flower_tab_home, R.drawable.ic_home),
    // 새 탭은 여기에 한 줄을 더하고, :app 의 appRoutes 에 그 탭의 루트 페이지를 등록한다.
}

/**
 * 하단 내비게이션 (Figma: C16 · TabBar).
 *
 * 활성 탭은 `color/icon/brand` + `color/text/brand`, 비활성은 secondary 계열이다.
 * 높이는 64dp 이며, 홈 인디케이터 영역은 이 컴포넌트가 그리지 않는다.
 * `Scaffold` 의 bottomBar 로 쓰거나 `Modifier.navigationBarsPadding()` 을 함께 적용한다.
 */
@Composable
fun FlowerTabBar(
    selected: FlowerTab,
    onSelect: (FlowerTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<FlowerTab> = FlowerTab.entries,
) {
    val borderColor = FlowerTheme.colors.borderSubtle

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlowerTheme.size.tabBarHeight)
            .background(FlowerTheme.colors.bgSurface)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(0f, stroke / 2f),
                    end = Offset(size.width, stroke / 2f),
                    strokeWidth = stroke,
                )
            }
            .padding(vertical = FlowerTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            FlowerTabItem(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun RowScope.FlowerTabItem(
    tab: FlowerTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FlowerTheme.colors
    val label = stringResource(tab.labelRes)

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = rememberDebouncedClick(onClick = onClick),
            ),
        verticalArrangement = Arrangement.spacedBy(
            FlowerTheme.spacing.xs,
            Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowerIcon(
            id = tab.icon,
            contentDescription = null,
            tint = if (selected) colors.iconBrand else colors.iconSecondary,
        )
        Text(
            text = label,
            style = if (selected) {
                FlowerTheme.typography.labelS.copy(fontWeight = FontWeight.Bold)
            } else {
                FlowerTheme.typography.labelS
            },
            color = if (selected) colors.textBrand else colors.textTertiary,
        )
    }
}

@Preview(name = "TabBar · Light", showBackground = true, backgroundColor = 0xFFF7F4EE)
@Preview(
    name = "TabBar · Dark",
    showBackground = true,
    backgroundColor = 0xFF14110F,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FlowerTabBarPreview() {
    FlowerShoesTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowerTabBar(selected = FlowerTab.Home, onSelect = {})
        }
    }
}
