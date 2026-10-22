package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme

/** 버튼 스타일 (Figma: C05 · Button → Style). */
enum class FlowerButtonStyle {
    /** 화면당 1개로 제한한다. 필터 적용 · 검색 실행 같은 주요 액션. */
    Primary,

    /** 보조 액션. 표면 위 테두리 버튼. */
    Secondary,

    /** 배경 없는 텍스트 버튼. */
    Ghost,
}

/** 버튼 크기 (Figma: C05 · Button → Size). */
enum class FlowerButtonSize {
    /** 바텀시트 · 풀폭 CTA */
    L,

    /** 인라인 액션 */
    M,
}

/**
 * 디자인 시스템 버튼 (Figma: C05 · Button).
 *
 * Figma 의 `State` variant 중 Pressed 는 [interactionSource] 의 눌림 상태로,
 * Disabled 는 [enabled] 로 대응된다. Disabled 는 원본대로 불투명도 40% 로 표현한다.
 *
 * Primary 는 Figma 에서 Default 와 Pressed 의 배경색이 동일하게 정의되어 있어
 * 컨테이너 색 변화가 없다. 대신 Android 표준 리플이 눌림 피드백을 담당한다.
 */
@Composable
fun FlowerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FlowerButtonStyle = FlowerButtonStyle.Primary,
    size: FlowerButtonSize = FlowerButtonSize.L,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = FlowerTheme.colors
    val spacing = FlowerTheme.spacing
    val pressed by interactionSource.collectIsPressedAsState()

    val isLarge = size == FlowerButtonSize.L
    val shape = if (isLarge) FlowerTheme.shapes.md else FlowerTheme.shapes.sm
    val horizontalPadding = if (isLarge) spacing.xxl else spacing.lg
    val verticalPadding = if (isLarge) spacing.lg else spacing.md
    val iconSize = if (isLarge) FlowerTheme.size.iconSm else FlowerTheme.size.iconXs
    val textStyle = if (isLarge) {
        FlowerTheme.typography.labelL
    } else {
        FlowerTheme.typography.labelM
    }

    val container = when (style) {
        FlowerButtonStyle.Primary -> colors.bgBrand
        FlowerButtonStyle.Secondary -> if (pressed) colors.bgSurfaceSunken else colors.bgSurface
        FlowerButtonStyle.Ghost -> if (pressed) colors.bgBrandSubtle else Color.Transparent
    }
    val borderColor = when (style) {
        FlowerButtonStyle.Secondary -> if (pressed) colors.borderStrong else colors.borderDefault
        else -> null
    }
    val contentColor = when (style) {
        FlowerButtonStyle.Primary -> colors.textOnBrand
        FlowerButtonStyle.Secondary -> colors.textPrimary
        FlowerButtonStyle.Ghost -> colors.textBrand
    }

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(container)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = rememberDebouncedClick(onClick = onClick),
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            FlowerIcon(
                id = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                size = iconSize,
            )
        }
        Text(
            text = label,
            style = textStyle,
            color = contentColor,
        )
    }
}

/** Figma 의 Disabled variant 가 쓰는 불투명도. */
private const val DISABLED_ALPHA = 0.4f

@Preview(name = "Button · Light", showBackground = true, backgroundColor = 0xFFF7F4EE)
@Preview(
    name = "Button · Dark",
    showBackground = true,
    backgroundColor = 0xFF14110F,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FlowerButtonPreview() {
    FlowerShoesTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowerButtonStyle.entries.forEach { style ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowerButton(
                        label = "필터 적용하기",
                        onClick = {},
                        style = style,
                        size = FlowerButtonSize.L,
                        leadingIcon = FlowerIcons.Filter,
                    )
                    FlowerButton(
                        label = "필터 적용하기",
                        onClick = {},
                        style = style,
                        size = FlowerButtonSize.M,
                        enabled = false,
                    )
                }
            }
        }
    }
}
