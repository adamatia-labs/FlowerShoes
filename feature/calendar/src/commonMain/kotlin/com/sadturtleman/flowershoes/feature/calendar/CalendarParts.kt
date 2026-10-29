package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme

// 캘린더 · 휴가 화면이 함께 쓰는 Figma 조각(Select Card · List Row · Section Header).

/** 고르기 카드(Figma Select Card): 높이 60, 고르면 brand 2 테두리 + 체크 원, 아니면 빈 원. */
@Composable
internal fun SelectCard(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        modifier.height(60.dp).clip(shape).background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(if (selected) Modifier.background(c.accent) else Modifier.border(1.6.dp, c.line, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) KkIcon(KkIcons.Check, tint = c.surface, size = 10.dp)
        }
    }
}

/** 목록 한 줄(Figma List Row · Value): 높이 56, 좌우 24, 아래 선. [onClick] 이 없으면 › 없이. */
@Composable
internal fun ValueRow(title: String, value: String?, onClick: (() -> Unit)?) {
    val c = KkTheme.colors
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = KkSpacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = KkTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = KkTheme.type.label, color = c.sub, maxLines = 1)
            if (onClick != null) KkIcon(KkIcons.ChevronRight, tint = c.sub)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** 구역 머리(Figma Section Header): 굵은 제목 + 오른쪽 brand 동작. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = KkTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, style = KkTheme.type.label, color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(KkRadius.sm)).clickable(role = Role.Button, onClick = onAction)
                    .padding(vertical = KkSpacing.md, horizontal = KkSpacing.xs),
            )
        }
    }
}
