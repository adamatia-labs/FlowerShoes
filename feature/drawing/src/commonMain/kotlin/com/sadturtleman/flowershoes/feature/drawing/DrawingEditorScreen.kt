package com.sadturtleman.flowershoes.feature.drawing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.drawingui.STICKERS
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DrawingEditorRoute(
    onSaved: (DrawingMeta) -> Unit,
    onClose: () -> Unit,
    viewModel: DrawingEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it is DrawingEvent.Saved) onSaved(it.meta) }
    }
    DrawingEditorScreen(state, viewModel::onIntent, onClose)
}

@Composable
fun DrawingEditorScreen(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, onClose: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = KkSpacing.screen, vertical = KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("닫기", style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.clickable(onClick = onClose).padding(KkSpacing.sm))
            Spacer(Modifier.weight(1f))
            Text(
                "되돌리기", style = KkTheme.type.labelM,
                color = if (state.canUndo) c.textPrimary else c.textTertiary,
                modifier = Modifier.clickable(enabled = state.canUndo) { onIntent(DrawingIntent.Undo) }.padding(KkSpacing.sm),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            WidgetShape.entries.forEach { shape ->
                Pill(if (shape == WidgetShape.SQUARE) "정사각 2×2" else "가로 4×2", state.shape == shape) { onIntent(DrawingIntent.SetShape(shape)) }
            }
        }
        // 캔버스. 손가락 좌표를 논리 좌표(가로 1000)로 바꿔 넘긴다.
        var widthPx = 1
        Box(
            Modifier.fillMaxWidth().border(1.dp, c.borderDefault, RoundedCornerShape(KkRadius.lg))
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(state.shape) {
                    awaitEachGesture {
                        val scale = state.shape.width.toFloat() / size.width
                        val down = awaitFirstDown()
                        onIntent(DrawingIntent.Down((down.position.x * scale).toInt(), (down.position.y * scale).toInt()))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            onIntent(DrawingIntent.Move((change.position.x * scale).toInt(), (change.position.y * scale).toInt()))
                            change.consume()
                        }
                        onIntent(DrawingIntent.Up)
                    }
                },
        ) {
            DrawingView(state.preview, showGuides = true)
        }
        // 메뉴바: 펜 · 형광펜 · 지우개 · 스티커
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Pill("펜", state.tool == EditorTool.PEN) { onIntent(DrawingIntent.SetTool(EditorTool.PEN)) }
            Pill("형광펜", state.tool == EditorTool.HIGHLIGHTER) { onIntent(DrawingIntent.SetTool(EditorTool.HIGHLIGHTER)) }
            Pill("지우개", state.tool == EditorTool.ERASER) { onIntent(DrawingIntent.SetTool(EditorTool.ERASER)) }
            Pill("스티커", state.tool == EditorTool.STICKER) { onIntent(DrawingIntent.SetTool(EditorTool.STICKER)) }
        }
        if (state.tool == EditorTool.STICKER) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                STICKERS.forEach { (id, glyph) -> Pill(glyph, state.sticker == id) { onIntent(DrawingIntent.SetSticker(id)) } }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PEN_COLORS.forEach { argb ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(argb.toInt()))
                            .border(if (state.color == argb) 3.dp else 0.dp, c.borderBrand, CircleShape)
                            .clickable(role = Role.RadioButton) { onIntent(DrawingIntent.SetColor(argb)) },
                    )
                }
                Spacer(Modifier.size(KkSpacing.sm))
                BrushSize.entries.forEach { b -> Pill(b.name, state.brush == b) { onIntent(DrawingIntent.SetBrush(b)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            Pill("틀 없음", state.template == null) { onIntent(DrawingIntent.SetTemplate(null)) }
            Pill("하트 틀", state.template == "heart") { onIntent(DrawingIntent.SetTemplate("heart")) }
            Pill("액자 틀", state.template == "frame") { onIntent(DrawingIntent.SetTemplate("frame")) }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("그리는 과정도 함께 보내기", style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
            Switch(state.replayEnabled, onCheckedChange = { onIntent(DrawingIntent.SetReplay(it)) })
        }
        KkPrimaryButton(if (state.saving) "저장 중…" else "완료", onClick = { onIntent(DrawingIntent.Done) }, enabled = state.canUndo && !state.saving)
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Text(
        label,
        style = KkTheme.type.labelM,
        color = if (selected) c.textOnBrand else c.textPrimary,
        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full))
            .background(if (selected) c.bgBrand else c.bgSubtle)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
