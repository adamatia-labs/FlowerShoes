package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime

/**
 * 하단 시트(Figma Sheet): bg/surface-raised, 위 모서리 24, 좌우 · 위 20 / 아래 40. 손잡이는 [KkSheetHeader] 가 그린다.
 * 내용이 길면 시트 안에서 스크롤한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KkModalSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KkTheme.colors.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = KkRadius.xl, topEnd = KkRadius.xl),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xl, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            content = content,
        )
    }
}

fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 슬라이더(Figma 사진 흐리게): 4pt 트랙 + 20pt 브랜드 손잡이. 트랙 어디를 눌러도 그 값으로. */
@Composable
fun KkSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.height(44.dp)
            .pointerInput(valueRange) {
                detectTapGestures { p ->
                    onValueChange(valueRange.start + (p.x / size.width).coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start))
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures { change, _ ->
                    onValueChange(valueRange.start + (change.position.x / size.width).coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start))
                }
            }
            .semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, valueRange) },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 20.dp
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
        Box(
            Modifier.offset(x = (maxWidth - thumb) * fraction).size(thumb)
                .kkElevation(KkElevation.Low, androidx.compose.foundation.shape.CircleShape)
                .clip(androidx.compose.foundation.shape.CircleShape).background(c.accent),
        )
    }
}
