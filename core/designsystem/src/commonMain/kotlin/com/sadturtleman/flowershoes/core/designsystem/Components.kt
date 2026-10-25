package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.selection.toggleable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Figma 🧩 Components 를 그대로 옮긴 것. 치수 · 색 · 글자는 각 컴포넌트 설명(Figma)에 적힌 값이다.

/** 포커스 링: 키보드 · 스위치 접근 시 3px 로즈 바깥 테두리(color/border/focus). */
@Composable
private fun Modifier.focusRing(focused: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (focused) border(3.dp, KkTheme.colors.borderFocus, shape) else this

// ── Button ────────────────────────────────────────────────────────────────

enum class KkButtonStyle { Primary, Secondary, Ghost }
enum class KkButtonSize { L, M }

/**
 * 버튼. Primary(한 화면에 하나) · Secondary(보조) · Ghost(취소). L=52(하단 CTA) · M=44.
 * 누르면 한 단계 진하게 + 그림자 없음, Loading 은 라벨 대신 스피너(너비 유지), Disabled 는 40% 투명.
 */
@Composable
fun KkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KkButtonStyle = KkButtonStyle.Primary,
    size: KkButtonSize = KkButtonSize.L,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
) {
    val c = KkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(KkRadius.md)
    val bg = when (style) {
        KkButtonStyle.Primary -> if (danger) c.statusDanger else if (pressed) c.bgBrandPressed else c.bgBrand
        KkButtonStyle.Secondary -> if (pressed) c.bgBrandSubtlePressed else c.bgBrandSubtle
        KkButtonStyle.Ghost -> if (pressed) c.bgPressed else Color.Transparent
    }
    val fg = when (style) {
        KkButtonStyle.Primary -> c.textOnBrand
        KkButtonStyle.Secondary -> c.textBrand
        KkButtonStyle.Ghost -> c.textPrimary
    }
    val elevated = style != KkButtonStyle.Ghost && !pressed && enabled
    Box(
        modifier = modifier
            .height(if (size == KkButtonSize.L) 52.dp else 44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (elevated) Modifier.kkElevation(KkElevation.Low, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .then(if (style == KkButtonStyle.Ghost) Modifier.border(1.dp, c.borderStrong, shape) else Modifier)
            .focusRing(focused, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (size == KkButtonSize.L) KkSpacing.xxl else KkSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            KkSpinner(fg, if (size == KkButtonSize.L) 20.dp else 16.dp)
        } else {
            Text(text, style = if (size == KkButtonSize.L) KkTheme.type.titleS else KkTheme.type.labelM, color = fg, maxLines = 1)
        }
    }
}

@Composable
fun KkSpinner(color: Color, size: Dp = 20.dp) {
    val t = rememberInfiniteTransition()
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart))
    KkIcon(KkIcons.Spinner, tint = color, size = size, modifier = Modifier.rotate(angle))
}

/** 하단 CTA(Primary · L · 꽉 찬 너비). */
@Composable
fun KkPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) =
    KkButton(text, onClick, modifier.fillMaxWidth(), KkButtonStyle.Primary, KkButtonSize.L, enabled, loading)

@Composable
fun KkSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    KkButton(text, onClick, modifier.fillMaxWidth(), KkButtonStyle.Secondary, KkButtonSize.L, enabled)

@Composable
fun KkGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    KkButton(text, onClick, modifier.fillMaxWidth(), KkButtonStyle.Ghost, KkButtonSize.L, enabled)

/** 소셜 로그인 버튼(각 회사 지침 색). 로고 자리는 공식 에셋으로 교체한다. */
enum class KkSocial(val label: String, val bg: Color, val fg: Color, val border: Color?, internal val logo: KkIcons) {
    Kakao("카카오로 계속하기", Color(0xFFFEE500), Color(0xD9000000), null, KkIcons.SocialKakao),
    Apple("Apple로 계속하기", Color.Black, Color.White, null, KkIcons.SocialApple),
    Google("Google로 계속하기", Color.White, Color(0xFF1F1F1F), Color(0xFF747775), KkIcons.SocialGoogle),
}

@Composable
fun KkSocialButton(kind: KkSocial, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(KkRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(kind.bg)
            .then(kind.border?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(kind.logo, tint = null, size = 20.dp)
        Text(kind.label, style = KkTheme.type.labelM, color = kind.fg)
    }
}

/** 플랫폼 공유 시트(초대 코드 보내기 등). 앱 셸이 Android · iOS 구현을 꽂는다. */
val LocalKkShare = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

// ── Card ──────────────────────────────────────────────────────────────────

/** 기본 정보 카드. 반경 12, 여백 20, Elevation/Mid. */
@Composable
fun KkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(KkSpacing.xl),
    spacing: Dp = KkSpacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Mid, shape)
            .clip(shape)
            .background(c.bgSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

// ── Chips · Tags ──────────────────────────────────────────────────────────

/** 상태 · 필터 칩. 선택되면 brand 배경. 테두리 대신 Elevation/Low, 누르면 그림자 없음. */
@Composable
fun KkStatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    enabled: Boolean = true,
) {
    val c = KkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(KkRadius.full)
    val bg = when {
        !enabled -> c.bgSubtle
        selected -> c.bgBrand
        pressed -> c.bgPressed
        else -> c.bgSurface
    }
    Row(
        modifier = modifier
            .then(if (enabled && !pressed) Modifier.kkElevation(KkElevation.Low, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .focusRing(focused, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) c.textOnBrand else dot))
        Text(label, style = KkTheme.type.labelM, color = if (!enabled) c.textTertiary else if (selected) c.textOnBrand else c.textPrimary)
    }
}

/** 일정 종류 태그(휴가 · 외박 · 면회 · 훈련 · 당직). 점 색은 color/event/... 와 1:1. */
@Composable
fun KkEventTag(label: String, dot: Color, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(KkRadius.full))
            .background(c.bgSubtle)
            .padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, style = KkTheme.type.labelS, color = c.textSecondary)
    }
}

/** 군 관련 배지(계급 · 군종). 테마를 따라 올리브 또는 카키. */
@Composable
fun KkMilitaryBadge(text: String, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Text(
        text,
        style = KkTheme.type.labelS,
        color = c.textMilitary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(KkRadius.full))
            .background(c.bgMilitarySubtle)
            .padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
    )
}

// ── Input ─────────────────────────────────────────────────────────────────

/**
 * 입력. 위 라벨(Label/S) + 48 높이 칸(반경 10, Elevation/Low) + 아래 도움말(Caption).
 * Focused: brand 1.5 테두리, Error: danger 1.5 테두리 + 빨간 도움말, Disabled: subtle 배경.
 * [onClick] 을 주면 읽기 전용(날짜 · 군종처럼 시트를 여는 칸).
 */
@Composable
fun KkInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = KkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(KkRadius.md)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
        if (label != null) Text(label, style = KkTheme.type.labelS, color = if (enabled) c.textSecondary else c.textTertiary)
        val border = when {
            !enabled -> Modifier.border(1.dp, c.borderDefault, shape)
            error != null -> Modifier.border(1.5.dp, c.statusDanger, shape)
            focused -> Modifier.border(1.5.dp, c.borderBrand, shape)
            else -> Modifier.border(1.dp, c.borderDefault, shape)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (enabled) Modifier.kkElevation(KkElevation.Low, shape) else Modifier)
                .clip(shape)
                .background(if (enabled) c.bgSurface else c.bgSubtle)
                .then(border)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = KkSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (onClick != null) {
                    Text(value.ifEmpty { placeholder }, style = KkTheme.type.bodyL, color = if (value.isEmpty() || !enabled) c.textTertiary else c.textPrimary, maxLines = 1)
                } else {
                    if (value.isEmpty()) Text(placeholder, style = KkTheme.type.bodyL, color = c.textTertiary, maxLines = 1)
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = KkTheme.type.bodyL.copy(color = if (enabled) c.textPrimary else c.textTertiary),
                        cursorBrush = SolidColor(c.bgBrand),
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            trailing?.invoke(this)
        }
        when {
            error != null -> Text(error, style = KkTheme.type.caption, color = c.statusDanger)
            helper != null -> Text(helper, style = KkTheme.type.caption, color = c.textTertiary)
        }
    }
}

/** 한 줄 입력(라벨 없이). */
@Composable
fun KkTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) =
    KkInput(value, onValueChange, modifier, placeholder = placeholder)

// ── Selection controls ────────────────────────────────────────────────────

/** 켜기/끄기 스위치 44×26. 행 전체가 눌리는 영역이어야 한다(KkListRow 의 Switch). */
@Composable
fun KkSwitch(on: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = KkTheme.colors
    Box(
        modifier
            .size(44.dp, 26.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(13.dp))
            .background(if (on) c.bgBrand else c.borderStrong),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 3.dp).size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/** 라디오 22. 선택: border/brand 6 테두리. */
@Composable
fun KkRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(c.bgSurface)
            .border(if (selected) 6.dp else 1.5.dp, if (selected) c.borderBrand else c.borderStrong, CircleShape),
    )
}

/** 체크박스 22. 선택: brand 배경 + 흰 체크. */
@Composable
fun KkCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.sm)
    Box(
        modifier
            .size(22.dp)
            .clip(shape)
            .then(if (checked) Modifier.background(c.bgBrand) else Modifier.background(c.bgSurface).border(1.5.dp, c.borderStrong, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) KkIcon(KkIcons.Check, tint = c.textOnBrand, size = 10.dp)
    }
}

/** 고를 수 있는 큰 카드형 선택지(역할 · 테마 등 화면 디자인에 맞춰 각 화면이 쓴다). */
@Composable
fun KkChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    /** 왼쪽 라디오 원(Figma Radio Card — 연결 해제의 보관 · 지우기). 있으면 제목은 기본 글자색. */
    radio: Boolean = false,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .kkElevation(if (selected) KkElevation.None else KkElevation.Low, shape)
            .clip(shape)
            .background(if (selected) c.bgBrandSubtle else c.bgSurface)
            .then(if (selected) Modifier.border(1.5.dp, c.borderBrand, shape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) KkRadio(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
            Text(label, style = KkTheme.type.titleS, color = if (selected && !radio) c.textBrand else c.textPrimary)
            if (description != null) Text(description, style = if (radio) KkTheme.type.caption else KkTheme.type.bodyS, color = c.textSecondary)
        }
    }
}

// ── Segmented control ─────────────────────────────────────────────────────

/** 분류 탭. subtle 바탕(반경 10, 높이 52, 안쪽 2) 위에 활성 칸만 surface + Elevation/Low. */
@Composable
fun KkSegmentedControl(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(KkRadius.md))
            .background(c.bgSubtle)
            .padding(KkSpacing.xxs),
    ) {
        items.forEachIndexed { i, label ->
            val shape = RoundedCornerShape(KkRadius.sm)
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .then(if (active) Modifier.kkElevation(KkElevation.Low, shape) else Modifier)
                    .clip(shape)
                    .then(if (active) Modifier.background(c.bgSurface) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = KkTheme.type.labelM, color = if (active) c.textPrimary else c.textTertiary)
            }
        }
    }
}

// ── Bars ──────────────────────────────────────────────────────────────────

enum class KkTopBarLeading { Back, Close }

/**
 * 상단 바(높이 48). Leading: 뒤로 · 닫기(44 터치), 가운데 제목(Title/S), Trailing: 글자(brand) · 버튼 · 없음.
 */
@Composable
fun KkTopBar(
    title: String,
    onLeading: () -> Unit,
    modifier: Modifier = Modifier,
    leading: KkTopBarLeading = KkTopBarLeading.Back,
    trailingText: String? = null,
    trailingButton: String? = null,
    trailingEnabled: Boolean = true,
    onTrailing: () -> Unit = {},
) {
    val c = KkTheme.colors
    Box(modifier.fillMaxWidth().height(48.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onLeading)
                .semantics { contentDescription = if (leading == KkTopBarLeading.Back) "뒤로 가기" else "닫기" },
            contentAlignment = Alignment.Center,
        ) {
            if (leading == KkTopBarLeading.Back) {
                KkIcon(KkIcons.Back, tint = c.textPrimary, size = 15.8.dp, modifier = Modifier.width(8.8.dp))
            } else {
                KkIcon(KkIcons.Close, tint = c.textPrimary, size = 13.8.dp)
            }
        }
        Text(title, style = KkTheme.type.titleS, color = c.textPrimary, modifier = Modifier.align(Alignment.Center), maxLines = 1)
        Box(Modifier.align(Alignment.CenterEnd)) {
            when {
                trailingText != null -> Box(
                    Modifier
                        .height(44.dp)
                        .clickable(enabled = trailingEnabled, role = Role.Button, onClick = onTrailing)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(trailingText, style = KkTheme.type.labelM, color = if (trailingEnabled) c.textBrand else c.textTertiary)
                }
                trailingButton != null -> KkButton(trailingButton, onTrailing, Modifier.widthIn(min = 72.dp), size = KkButtonSize.M, enabled = trailingEnabled)
            }
        }
    }
}

/** 하단 탭 한 칸. */
data class KkTab(val label: String, val icon: KkIcons)

/** 하단 탭 바: 홈 · 캘린더 · 편지 · 추억. surface + Elevation/Bar, 아래는 제스처 바 안전 영역. */
@Composable
fun KkTabBar(tabs: List<KkTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.Bar, androidx.compose.ui.graphics.RectangleShape)
            .background(c.bgSurface)
            .navigationBarsPadding()
            .padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEachIndexed { i, tab ->
            val active = i == selected
            val color = if (active) c.textBrand else c.textTertiary
            Column(
                Modifier
                    .width(72.dp)
                    .height(56.dp)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { stateDescription = if (active) "선택됨" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs, Alignment.CenterVertically),
            ) {
                KkIcon(tab.icon, tint = color)
                Text(tab.label, style = KkTheme.type.labelS, color = color)
            }
        }
    }
}

/** 온보딩 단계 표시. 현재 단계까지 32 길이 brand 막대, 나머지 16 회색. */
@Composable
fun KkStepIndicator(step: Int, total: Int, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val done = i < step
            Box(Modifier.size(if (done) 32.dp else 16.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (done) c.bgBrand else c.borderDefault))
        }
    }
}

/** 이전 이름(온보딩). [current] 는 0부터. */
@Composable
fun KkStepDots(current: Int, total: Int, modifier: Modifier = Modifier) = KkStepIndicator(current + 1, total, modifier)

/** 접고 펼치기("달력 펼치기 ⌄" / "달력 접기 ⌃"). */
@Composable
fun KkCollapseToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, collapsedLabel: String = "달력 펼치기", expandedLabel: String = "달력 접기") {
    val c = KkTheme.colors
    Row(
        modifier
            .height(44.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
            .padding(horizontal = KkSpacing.md, vertical = KkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expanded) expandedLabel else collapsedLabel, style = KkTheme.type.labelS, color = c.textSecondary)
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            KkIcon(if (expanded) KkIcons.ChevronUp else KkIcons.ChevronDown, tint = c.textSecondary, size = 9.2.dp, modifier = Modifier.height(5.2.dp))
        }
    }
}

// ── List row ──────────────────────────────────────────────────────────────

enum class KkRowTrailing { Chevron, Switch, None }

/** 목록 한 줄(설정 등). 최소 높이 52, 좌 16 · 우 12. Trailing: Chevron · Switch · None(값만). */
@Composable
fun KkListRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    trailing: KkRowTrailing = KkRowTrailing.Chevron,
    switchOn: Boolean = false,
    labelColor: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = KkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick == null) Modifier else if (trailing == KkRowTrailing.Switch) {
                Modifier.toggleable(value = switchOn, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            } else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick))
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = KkTheme.type.bodyM, color = labelColor ?: c.textPrimary, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = KkTheme.type.bodyS, color = c.textSecondary, maxLines = 1)
        when (trailing) {
            KkRowTrailing.Chevron -> KkIcon(KkIcons.ChevronRight, tint = c.textSecondary)
            KkRowTrailing.Switch -> KkSwitch(switchOn, enabled = enabled)
            KkRowTrailing.None -> Unit
        }
    }
}

// ── Sheet ─────────────────────────────────────────────────────────────────

/** 하단 시트 본체: surface(띄운 표면), 위 모서리 24, 좌우 · 위 20 / 아래 36. */
@Composable
fun KkSheetSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(topStart = KkRadius.xl, topEnd = KkRadius.xl)
    Column(
        modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.High, shape)
            .clip(shape)
            .background(c.bgSurfaceRaised)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        content = content,
    )
}

/** 시트 머리: 손잡이(40×4) + 제목(Title/M) + 설명(Body/S). */
@Composable
fun KkSheetHeader(title: String, description: String? = null, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.borderStrong))
        }
        Text(title, style = KkTheme.type.titleM, color = c.textPrimary)
        if (description != null) Text(description, style = KkTheme.type.bodyS, color = c.textSecondary)
    }
}

/** 시트 안 선택 한 줄(라디오 포함). 높이 52, 선택되면 brand-subtle 배경. */
@Composable
fun KkSheetOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, meta: String? = null) {
    val c = KkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(KkRadius.md))
            .background(if (selected) c.bgBrandSubtle else c.bgSurface)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = KkTheme.type.bodyL, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (meta != null) Text(meta, style = KkTheme.type.bodyS, color = c.textSecondary)
        KkRadio(selected)
    }
}

// ── Feedback ──────────────────────────────────────────────────────────────

enum class KkToastType { Default, Success, Error }

/** 토스트: inverse 배경, 반경 10, Elevation/High. 탭 바 위 16 에 2~3초. */
@Composable
fun KkToast(message: String, modifier: Modifier = Modifier, type: KkToastType = KkToastType.Default, action: String? = null, onAction: () -> Unit = {}) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.md)
    Row(
        modifier
            .fillMaxWidth()
            .kkElevation(KkElevation.High, shape)
            .clip(shape)
            .background(c.bgInverse)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (type) {
            KkToastType.Default -> KkIcon(KkIcons.ToastInfo, tint = c.textInverse, size = 16.67.dp, modifier = Modifier.padding(1.66.dp))
            KkToastType.Success -> KkIcon(KkIcons.ToastSuccess, tint = null, size = 16.67.dp, modifier = Modifier.padding(1.66.dp))
            KkToastType.Error -> KkIcon(KkIcons.ToastError, tint = null, size = 16.67.dp, modifier = Modifier.padding(1.66.dp))
        }
        Text(message, style = KkTheme.type.bodyM, color = c.textInverse, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = KkTheme.type.labelM, color = c.textInverse, modifier = Modifier.clickable(onClick = onAction))
    }
}

/**
 * 확인 창(312 너비, 반경 24, Elevation/High). 화면 가운데 + 45% 딤은 [KkDialogHost] 가 그린다.
 * [destructive] 면 확인 버튼이 위험 색.
 */
@Composable
fun KkDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancel: String = "취소",
    destructive: Boolean = false,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        modifier
            .width(312.dp)
            .kkElevation(KkElevation.High, shape)
            .clip(shape)
            .background(c.bgSurfaceRaised)
            .padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.xxl, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Text(title, style = KkTheme.type.titleM, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = KkTheme.type.bodyM, color = c.textSecondary, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            KkButton(cancel, onCancel, Modifier.weight(1f), style = KkButtonStyle.Ghost)
            KkButton(confirm, onConfirm, Modifier.weight(1f), danger = destructive)
        }
    }
}

/** 다이얼로그를 화면 가운데 + 45% 딤 위에. */
@Composable
fun KkDialogHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) { content() }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────

/** 빈 상태: 72 원(brand-subtle) 안 32 아이콘 + 제목(Title/S) + 설명(Body/S) + 선택적 M 버튼. */
@Composable
fun KkEmptyState(
    title: String,
    description: String,
    icon: KkIcons,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = KkTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = KkSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) {
            KkIcon(icon, tint = c.textBrand, size = 32.dp)
        }
        Text(title, style = KkTheme.type.titleS, color = c.textPrimary, textAlign = TextAlign.Center)
        if (description.isNotEmpty()) Text(description, style = KkTheme.type.bodyS, color = c.textSecondary, textAlign = TextAlign.Center)
        if (action != null) KkButton(action, onAction, Modifier.widthIn(min = 120.dp), size = KkButtonSize.M)
    }
}

// ── Domain components ─────────────────────────────────────────────────────

/** 전역 D-day 카드(홈 상단). 진행 막대 = 복무 진행률. */
@Composable
fun KkDdayCard(
    days: String,
    rank: String,
    percent: String,
    date: String,
    progress: Float,
    modifier: Modifier = Modifier,
    caption: String = "전역까지",
    onClick: (() -> Unit)? = null,
) {
    val c = KkTheme.colors
    KkCard(modifier, onClick = onClick, spacing = KkSpacing.md) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(caption, style = KkTheme.type.labelM, color = c.textSecondary)
            KkMilitaryBadge(rank)
        }
        Text(days, style = KkTheme.type.dday, color = c.textPrimary)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgSubtle)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.bgMilitary))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(percent, style = KkTheme.type.caption, color = c.textMilitary)
            Text(date, style = KkTheme.type.caption, color = c.textTertiary)
        }
    }
}

/**
 * 보고싶어 버튼(112 원). 한 번 누르면 Sent(heart-subtle 배경 · "보냈어요 · N")로 바뀌고 오늘 보낸 횟수가 쌓인다.
 * "마음" 요소라 테마와 무관하게 로즈.
 */
@Composable
fun KkMissYouButton(sentToday: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val sent = sentToday > 0
    Column(
        modifier
            .size(112.dp)
            .kkElevation(if (sent) KkElevation.Low else KkElevation.Mid, CircleShape)
            .clip(CircleShape)
            .background(if (sent) c.heartSubtle else c.heart)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "보고싶어 보내기, 오늘 ${sentToday}번 보냄" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KkSpacing.xs, Alignment.CenterVertically),
    ) {
        KkIcon(KkIcons.Heart, tint = if (sent) c.textHeart else c.textOnBrand, size = 32.dp)
        Text(if (sent) "보냈어요 · $sentToday" else "보고싶어", style = KkTheme.type.labelM, color = if (sent) c.textHeart else c.textOnBrand)
    }
}

/** 이전 이름(한 줄 하트 버튼). */
@Composable
fun KkHeartButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "보고싶어") {
    val c = KkTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(KkRadius.full))
            .background(c.heart)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = KkSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        KkIcon(KkIcons.Heart, tint = c.textOnBrand, size = 20.dp)
        Text(label, style = KkTheme.type.labelM, color = c.textOnBrand)
    }
}

/** 채운 하트(위젯 · 작은 표시). */
@Composable
fun KkHeartIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.9f)
            cubicTo(w * 0.1f, h * 0.62f, -w * 0.02f, h * 0.32f, w * 0.2f, h * 0.15f)
            cubicTo(w * 0.34f, h * 0.04f, w * 0.46f, h * 0.12f, w * 0.5f, h * 0.24f)
            cubicTo(w * 0.54f, h * 0.12f, w * 0.66f, h * 0.04f, w * 0.8f, h * 0.15f)
            cubicTo(w * 1.02f, h * 0.32f, w * 0.9f, h * 0.62f, w * 0.5f, h * 0.9f)
            close()
        }
        drawPath(path, color)
    }
}

/** 화면 상단 제목 + 설명(온보딩 · 연결 화면). 제목 Title/L, 설명 Body/M. */
@Composable
fun KkScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        Text(title, style = KkTheme.type.titleL, color = KkTheme.colors.textPrimary)
        if (subtitle != null) Text(subtitle, style = KkTheme.type.bodyM, color = KkTheme.colors.textSecondary)
    }
}

/** 세로 여백. */
@Composable
fun KkGap(height: Dp) = Spacer(Modifier.height(height))
