package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 주 버튼. 화면 아래 "다음" · "보내기" 같은 하나뿐인 주 행동. 높이 52dp(터치 영역 48dp 이상). */
@Composable
fun KkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = KkTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(KkRadius.lg))
            .background(if (enabled) c.bgBrand else c.bgSubtle)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KkTheme.type.titleS, color = if (enabled) c.textOnBrand else c.textTertiary)
    }
}

/** 보조 버튼. 테두리만 있다. */
@Composable
fun KkSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(KkRadius.lg))
            .border(1.dp, c.borderStrong, RoundedCornerShape(KkRadius.lg))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KkTheme.type.labelM, color = c.textPrimary)
    }
}

/** 표면 카드. Elevation/Low 그림자. */
@Composable
fun KkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(KkSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = c.shadowAmbient, spotColor = c.shadowKey)
            .clip(shape)
            .background(c.bgSurface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** 고를 수 있는 칩. 선택되면 강조색 테두리 + 옅은 배경. */
@Composable
fun KkChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.bgBrandSubtle else c.bgSurface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.borderBrand else c.borderDefault, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(KkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        Text(label, style = KkTheme.type.titleS, color = if (selected) c.textBrand else c.textPrimary)
        if (description != null) Text(description, style = KkTheme.type.bodyS, color = c.textSecondary)
    }
}

/** 한 줄 입력. */
@Composable
fun KkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgSurface)
            .border(1.dp, c.borderStrong, shape)
            .padding(horizontal = KkSpacing.lg, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = KkTheme.type.bodyL, color = c.textTertiary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = KkTheme.type.bodyL.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.bgBrand),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 보고싶어 버튼. "마음" 요소라 테마와 무관하게 로즈다. */
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
        KkHeartIcon(color = c.textOnBrand, size = 20.dp)
        Text(label, style = KkTheme.type.labelM, color = c.textOnBrand)
    }
}

/**
 * 하트. 글자(♥)는 기기에 따라 컬러 이모지로 그려져 배경색과 겹치므로 직접 그린다.
 */
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

/** 화면 상단 제목 + 설명. */
@Composable
fun KkScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        Text(title, style = KkTheme.type.titleL, color = KkTheme.colors.textPrimary)
        if (subtitle != null) Text(subtitle, style = KkTheme.type.bodyM, color = KkTheme.colors.textSecondary)
    }
}

/** 단계 표시 점(온보딩). */
@Composable
fun KkStepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 20.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) c.bgBrand else c.borderDefault),
            )
        }
    }
}

/** 군 관련 표시(배지). 테마를 따라 올리브 또는 카키다. */
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
