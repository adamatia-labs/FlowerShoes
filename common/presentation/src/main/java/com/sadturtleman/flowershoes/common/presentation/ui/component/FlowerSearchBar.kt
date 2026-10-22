package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme

/**
 * 검색 입력 (Figma: C07 · SearchBar).
 *
 *
 * @param onClick 지정하면 입력을 받지 않고 전체가 버튼처럼 동작한다. 홈에서 검색 화면으로 넘기는 진입점용이다.
 *
 * Figma 의 `State` variant 는 질의 유무로 갈린다. [value] 가 비어 있으면 Empty(플레이스홀더),
 * 값이 있으면 Filled 로 테두리가 생기고 우측에 지우기 버튼이 나타난다.
 */
@Composable
fun FlowerSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "검색어를 입력하세요",
    enabled: Boolean = true,
    onSearch: () -> Unit = {},
    onClear: () -> Unit = { onValueChange("") },
    onClick: (() -> Unit)? = null,
) {
    val colors = FlowerTheme.colors
    val spacing = FlowerTheme.spacing
    val shape = FlowerTheme.shapes.md
    val filled = value.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlowerTheme.size.searchBarHeight)
            .clip(shape)
            .background(colors.bgSurfaceSunken)
            .then(
                if (filled) Modifier.border(1.5.dp, colors.borderStrong, shape) else Modifier,
            )
            .then(
                if (onClick != null) {
                    Modifier.debouncedClickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowerIcon(
            id = FlowerIcons.Search,
            contentDescription = null,
            tint = if (filled) colors.iconPrimary else colors.iconSecondary,
            size = FlowerTheme.size.iconSm,
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            // 진입점 모드에서는 입력을 받지 않고 컨테이너의 클릭만 처리한다.
            enabled = enabled && onClick == null,
            singleLine = true,
            textStyle = FlowerTheme.typography.bodyM.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textBrand),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = FlowerTheme.typography.bodyM,
                            color = colors.textTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (filled) {
            FlowerIcon(
                id = FlowerIcons.Close,
                contentDescription = "검색어 지우기",
                modifier = Modifier
                    .clickableNoIndication(enabled = enabled, onClick = onClear),
                tint = colors.iconSecondary,
                size = FlowerTheme.size.iconSm,
            )
        }
    }
}

@Preview(name = "SearchBar · Light", showBackground = true, backgroundColor = 0xFFF7F4EE)
@Preview(
    name = "SearchBar · Dark",
    showBackground = true,
    backgroundColor = 0xFF14110F,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FlowerSearchBarPreview() {
    FlowerShoesTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowerSearchBar(value = "", onValueChange = {})
            FlowerSearchBar(value = "꽃신", onValueChange = {})
        }
    }
}
