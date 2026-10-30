package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 창 폭 구분(Figma 11 · 큰 화면). 600dp 부터 하단 탭 대신 Nav Rail, 840dp(펼친 폴더블 · 태블릿)부터 편지함 · 캘린더 · 그림판이 2단.
 * Android · iPad 가 같이 쓰도록 [KkWindowSizeProvider] 가 앱 맨 바깥의 폭을 재서 [LocalKkWindowWidth] 로 내려준다.
 */
enum class KkWindowWidth {
    Compact, Medium, Expanded;

    /** 하단 탭 바 대신 Nav Rail. */
    val navRail: Boolean get() = this != Compact

    /** 목록 | 읽기처럼 두 칸으로 나눈다. */
    val twoPane: Boolean get() = this == Expanded

    companion object {
        fun of(width: Dp): KkWindowWidth = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

/** 지금 창의 폭 구분. 감싼 [KkWindowSizeProvider] 가 없으면 폰(Compact). */
val LocalKkWindowWidth = compositionLocalOf { KkWindowWidth.Compact }

/** 이 안의 화면들에 창 폭 구분을 내려준다. 앱 맨 바깥(창 전체)에 한 번 둔다. */
@Composable
fun KkWindowSizeProvider(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalKkWindowWidth provides KkWindowWidth.of(maxWidth)) { content() }
    }
}

/** 큰 화면에서 한 칸짜리 화면 콘텐츠의 최대 폭(design.md). */
val KkContentMaxWidth: Dp = 600.dp

/**
 * 한 칸짜리 화면을 최대 [KkContentMaxWidth] 로 가운데 둔다. 양옆은 바탕색. 폰에서는 그대로 전체 폭이다.
 * 화면이 자기 바탕을 fillMaxSize 로 칠하므로 바깥 바탕도 같은 canvas 로 칠한다.
 * 2단처럼 전체 폭을 쓸 때는 [maxWidth] 를 [Dp.Unspecified] 로 — 같은 자리에서 폭만 바뀌어 접고 펼 때 화면 상태가 남는다.
 */
@Composable
fun KkContentWidth(maxWidth: Dp = KkContentMaxWidth, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(KkTheme.colors.canvas), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = maxWidth)) { content() }
    }
}

/** 두 칸 사이 세로 선(line 1). */
@Composable
fun KkPaneDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(KkTheme.colors.line))
}

/**
 * Nav Rail(Figma `428:15898`): 큰 화면(600dp 이상)에서 하단 탭 바를 대신한다. 폭 80, surface + 오른쪽 선,
 * 칸마다 56×32 표시(고르면 종이 태그색) + 아이콘 + 이름(고르면 Label/Bold ink, 아니면 Caption sub).
 */
@Composable
fun KkNavRail(tabs: List<KkTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    // 칸 79 + 오른쪽 선 1 = Figma 폭 80.
    Row(modifier.fillMaxHeight()) {
        Column(
            Modifier.width(79.dp).fillMaxHeight().background(c.surface).statusBarsPadding().padding(top = KkSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Column(
                    Modifier.fillMaxWidth().height(64.dp)
                        .clickable(role = Role.Tab) { onSelect(i) }
                        .semantics { this.selected = active },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(KkRadius.full)).background(if (active) c.tag else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { KkIcon(tab.icon, tint = if (active) c.ink else c.sub) }
                    Text(tab.label, style = if (active) KkTheme.type.labelBold else KkTheme.type.caption, color = if (active) c.ink else c.sub, maxLines = 1)
                }
            }
        }
        KkPaneDivider()
    }
}

/**
 * 탭 셸: 폰은 콘텐츠 아래 하단 탭 바, 큰 화면은 왼쪽 Nav Rail. [showNav] 가 false 면(읽는 화면 등) 내비게이션 없이 전체.
 * 콘텐츠 위에는 상태 바 여백을 둔다.
 */
@Composable
fun KkNavScaffold(
    tabs: List<KkTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNav: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = KkTheme.colors
    if (LocalKkWindowWidth.current.navRail) {
        Row(modifier.fillMaxSize().background(c.canvas)) {
            if (showNav) KkNavRail(tabs, selected, onSelect)
            Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) { content() }
        }
    } else {
        Column(modifier.fillMaxSize().background(c.canvas)) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) { content() }
            if (showNav) KkTabBar(tabs, selected, onSelect)
        }
    }
}
