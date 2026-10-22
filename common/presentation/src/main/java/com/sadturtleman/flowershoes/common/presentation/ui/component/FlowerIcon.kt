package com.sadturtleman.flowershoes.common.presentation.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.sadturtleman.flowershoes.common.presentation.R
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme

/**
 * 아이콘 라이브러리 (Figma: C01 · Icon).
 *
 * 원본은 24×24 그리드에 1.6px 스트로크로 그려진 22개 아이콘이며,
 * Figma 에서 내보낸 SVG 를 그대로 벡터 드로어블로 옮겼다.
 * 스트로크 색은 `color/icon/primary` 에 바인딩되어 있으므로
 * 사용처에서는 [FlowerIcon] 의 `tint` 로 색만 바꾼다.
 */
object FlowerIcons {
    @DrawableRes val Search: Int = R.drawable.ic_search
    @DrawableRes val Home: Int = R.drawable.ic_home
    @DrawableRes val Bookmark: Int = R.drawable.ic_bookmark
    @DrawableRes val User: Int = R.drawable.ic_user
    @DrawableRes val ArrowLeft: Int = R.drawable.ic_arrow_left
    @DrawableRes val Filter: Int = R.drawable.ic_filter
    @DrawableRes val Close: Int = R.drawable.ic_close
    @DrawableRes val ChevronRight: Int = R.drawable.ic_chevron_right
    @DrawableRes val ChevronDown: Int = R.drawable.ic_chevron_down
    @DrawableRes val Share: Int = R.drawable.ic_share
    @DrawableRes val Grid: Int = R.drawable.ic_grid
    @DrawableRes val Heart: Int = R.drawable.ic_heart
    @DrawableRes val Image: Int = R.drawable.ic_image
    @DrawableRes val MapPin: Int = R.drawable.ic_map_pin
    @DrawableRes val Clock: Int = R.drawable.ic_clock
    @DrawableRes val Sort: Int = R.drawable.ic_sort
    @DrawableRes val Check: Int = R.drawable.ic_check
    @DrawableRes val Plus: Int = R.drawable.ic_plus
    @DrawableRes val More: Int = R.drawable.ic_more
    @DrawableRes val Layers: Int = R.drawable.ic_layers
    @DrawableRes val Ruler: Int = R.drawable.ic_ruler
}

/**
 * 디자인 시스템 아이콘 한 개를 그린다.
 *
 * @param id [FlowerIcons] 의 상수
 * @param contentDescription 장식용이면 null
 * @param tint 기본값은 `color/icon/primary`
 * @param size 기본 24dp. 버튼·칩 안에서는 20dp / 16dp 를 넘긴다.
 */
@Composable
fun FlowerIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = FlowerTheme.colors.iconPrimary,
    size: Dp = FlowerTheme.size.icon,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
