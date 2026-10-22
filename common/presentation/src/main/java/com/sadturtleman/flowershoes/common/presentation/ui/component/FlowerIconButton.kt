package com.sadturtleman.flowershoes.common.presentation.ui.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme

/** 아이콘 버튼 스타일 (Figma: C06 · IconButton → Style). */
enum class FlowerIconButtonStyle {
    /** 툴바 · 앱바용. 배경 없음. */
    Plain,

    /** 보조 액션. 가라앉은 표면 배경. */
    Filled,

    /** 이미지 위에 올라가는 저장 · 공유 버튼. */
    Overlay,
}

/**
 * 아이콘 전용 버튼 (Figma: C06 · IconButton).
 *
 * [buttonSize] 는 **그려지는** 크기다. 터치 영역은 그와 무관하게 항상 48dp 이상으로 잡히므로
 * 카드 위 저장 버튼처럼 작게 그려도 누르기 어려워지지 않는다.
 *
 * @param tint 아이콘 색을 직접 지정한다. null 이면 [style] 에서 유도한다.
 *  히어로 이미지 위 투명 앱바처럼 배경 없이 색만 바꿔야 할 때 쓴다.
 * @param container 배경색을 직접 지정한다. null 이면 [style] 에서 유도한다.
 *  저장된 북마크처럼 "켜짐" 을 색으로 알려야 할 때 쓴다 — Overlay 의 기본 배경이
 *  라이트 · 다크 양쪽에서 어두운 스크림이라 아이콘 색만 바꾸면 대비가 나오지 않는다.
 */
@Composable
fun FlowerIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FlowerIconButtonStyle = FlowerIconButtonStyle.Plain,
    enabled: Boolean = true,
    buttonSize: Dp = FlowerTheme.size.touchMin,
    tint: Color? = null,
    container: Color? = null,
) {
    val colors = FlowerTheme.colors
    val shape = FlowerTheme.shapes.full
    val resolvedContainer = container ?: when (style) {
        FlowerIconButtonStyle.Plain -> Color.Transparent
        FlowerIconButtonStyle.Filled -> colors.bgSurfaceSunken
        FlowerIconButtonStyle.Overlay -> colors.bgScrim
    }
    val resolvedTint = tint ?: when (style) {
        FlowerIconButtonStyle.Plain, FlowerIconButtonStyle.Filled -> colors.iconPrimary
        FlowerIconButtonStyle.Overlay -> colors.iconInverse
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            // 그려지는 크기와 별개로 터치 영역을 48dp 로 넓힌다.
            // 카드 위 저장 버튼은 36dp 라, 손가락이 몇 dp 만 빗나가도 카드 클릭으로 넘어간다.
            .minimumInteractiveComponentSize()
            .size(buttonSize)
            .clip(shape)
            .background(resolvedContainer)
            .debouncedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FlowerIcon(
            id = icon,
            contentDescription = contentDescription,
            tint = resolvedTint,
        )
    }
}

/**
 * [FlowerIconButton] 이 터치 영역을 48dp 로 넓히며 [buttonSize] 바깥에 두르는 여백.
 *
 * 아이콘이 그려지는 자리는 그만큼 안쪽으로 밀린다. 시안 위치를 지켜야 하는 곳
 * (카드 위 저장 버튼 등)은 자기 padding 에서 이 값을 빼면 원래 자리로 돌아온다.
 */
fun flowerIconButtonTouchInset(buttonSize: Dp): Dp =
    ((MIN_TOUCH_TARGET - buttonSize) / 2).coerceAtLeast(0.dp)

/** Material 이 보장하는 최소 터치 영역. minimumInteractiveComponentSize 와 같은 값이다. */
private val MIN_TOUCH_TARGET = 48.dp

@Preview(name = "IconButton · Light", showBackground = true, backgroundColor = 0xFFF7F4EE)
@Preview(
    name = "IconButton · Dark",
    showBackground = true,
    backgroundColor = 0xFF14110F,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FlowerIconButtonPreview() {
    FlowerShoesTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowerIconButton(FlowerIcons.Filter, "필터", {}, style = FlowerIconButtonStyle.Plain)
            FlowerIconButton(FlowerIcons.Sort, "정렬", {}, style = FlowerIconButtonStyle.Filled)
            FlowerIconButton(FlowerIcons.Bookmark, "저장", {}, style = FlowerIconButtonStyle.Overlay)
        }
    }
}
