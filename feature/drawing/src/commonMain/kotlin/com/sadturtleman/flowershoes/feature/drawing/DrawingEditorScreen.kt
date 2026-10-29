@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.designsystem.KkDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPolaroid
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkScheduleSheet
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSlider
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.hhmm
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.PhotoBackground
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.drawingui.STICKER_TABS
import com.sadturtleman.flowershoes.core.drawingui.StickerPreview
import com.sadturtleman.flowershoes.core.drawingui.TEMPLATES
import com.sadturtleman.flowershoes.core.drawingui.TemplatePreview
import com.sadturtleman.flowershoes.core.drawingui.stickerLabel
import kotlinx.datetime.LocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * 그림 그리기(Figma 5 · 그림 그리기). [forLetter] 면 편지에 붙일 그림(완료 → 편지에 붙이기 → [onSaved]),
 * 아니면 그림만 보내기(완료 → 그림만 보내기 → [onSend]).
 */
@Composable
fun DrawingEditorRoute(
    onSaved: (DrawingMeta) -> Unit,
    onClose: () -> Unit,
    forLetter: Boolean = true,
    onSend: (DrawingMeta, caption: String?, revealAt: LocalDateTime?) -> Unit = { m, _, _ -> onSaved(m) },
    /** 앨범 · 카메라 고르기(플랫폼). 고른 사진(줄인 JPEG)을 넘겨주는 실행 함수를 돌려준다. 없으면 사진 메뉴가 꺼진다. */
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    /** 사진첩에 PNG 저장(플랫폼). 성공하면 true. */
    saveImage: (suspend (ByteArray) -> Boolean)? = null,
    viewModel: DrawingEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                is DrawingEvent.Saved -> onSaved(it.meta)
                is DrawingEvent.Send -> onSend(it.meta, it.caption, it.revealAt)
                is DrawingEvent.Exported -> notice = if (saveImage?.invoke(it.png) == true) "사진첩에 저장했어요" else "저장하지 못했어요"
            }
        }
    }
    LaunchedEffect(notice) { if (notice != null) { kotlinx.coroutines.delay(2_500); notice = null } }
    val launchPicker = photoPicker?.invoke { viewModel.onIntent(DrawingIntent.SetPhoto(it)) }
    Box(Modifier.fillMaxSize()) {
        if (state.stage == EditorStage.SEND) DrawingSendScreen(state, viewModel::onIntent, canSave = saveImage != null, forLetter = forLetter)
        else DrawingEditorScreen(state, viewModel::onIntent, { viewModel.onIntent(DrawingIntent.Reset); onClose() }, launchPicker)
        notice?.let {
            KkToast(it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxxl))
        }
    }
}

private val BRUSH_LABEL = mapOf(BrushSize.S to "얇게", BrushSize.M to "보통", BrushSize.L to "굵게")

/** 그림 편집(Figma 빈 캔버스 · 그리는 중): 위 닫기 · 그림 · 완료, 가운데 캔버스, 아래 툴바. 도구는 툴바에서 여는 시트로. */
@Composable
fun DrawingEditorScreen(
    state: DrawingEditorState,
    onIntent: (DrawingIntent) -> Unit,
    onClose: () -> Unit,
    onPhoto: ((fromCamera: Boolean) -> Unit)? = null,
) {
    val c = KkTheme.colors
    var leaving by remember { mutableStateOf(false) }
    val close = { if (state.canUndo) leaving = true else onClose() }
    // 뒤로 가기도 닫기와 같다: 그린 게 있으면 지울지 묻는다.
    BackHandler { if (leaving) leaving = false else close() }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        KkTopBar(
            "그림", onLeading = close, leading = KkTopBarLeading.Close,
            trailingText = "완료", trailingEnabled = state.canUndo && !state.saving,
            onTrailing = { onIntent(DrawingIntent.Next) },
            modifier = Modifier.statusBarsPadding().padding(horizontal = KkSpacing.lg),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().background(c.surface).padding(KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 위젯 모양은 그리기 전에만 바꿀 수 있다.
            if (!state.canUndo) {
                KkSegmentedControl(
                    listOf("2x2 정사각", "4x2 가로"), state.shape.ordinal,
                    { onIntent(DrawingIntent.SetShape(WidgetShape.entries[it])) },
                )
            }
            DrawingCanvas(state, onIntent)
        }
        Toolbar(state, onIntent)
    }
    when (state.sheet) {
        EditorSheet.PEN -> PenSheet(state, onIntent)
        EditorSheet.STICKER -> StickerSheet(state, onIntent)
        EditorSheet.PHOTO -> PhotoSheet(state, onIntent, onPhoto)
        EditorSheet.FRAME -> FrameSheet(state, onIntent)
        null -> Unit
    }
    if (leaving) {
        KkDialogHost(onDismiss = { leaving = false }) {
            KkDialog(
                title = "그리던 그림을 지울까요?", body = "나가면 지금 그린 그림이 저장되지 않아요",
                confirm = "나가기", destructive = true,
                onConfirm = { leaving = false; onClose() }, onCancel = { leaving = false },
            )
        }
    }
}

/**
 * 캔버스: 한 손가락은 그리기, 두 손가락은 확대(최대 4배) · 이동. 손가락 좌표를 논리 좌표(가로 1000)로 바꿔 넘긴다.
 * 비어 있으면 "손가락으로 그려 보세요".
 */
@Composable
private fun DrawingCanvas(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.md)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var drawing by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(state.shape.width.toFloat() / state.shape.height)
            .kkElevation(KkElevation.Low, shape).clip(shape).background(Color.White)
            .semantics { contentDescription = "그림 캔버스" }
            .pointerInput(state.shape) {
                awaitEachGesture {
                    val logical = state.shape.width.toFloat() / size.width
                    fun map(p: Offset): Pair<Int, Int> {
                        val q = (p - pan) / zoom
                        return (q.x * logical).toInt() to (q.y * logical).toInt()
                    }
                    val down = awaitFirstDown()
                    var transforming = false
                    map(down.position).let { (x, y) -> onIntent(DrawingIntent.Down(x, y)) }
                    drawing = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (!transforming) {
                                transforming = true
                                onIntent(DrawingIntent.CancelStroke)
                            }
                            val z = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                            val p = pan + event.calculatePan()
                            zoom = z
                            pan = Offset(
                                p.x.coerceIn(size.width * (1 - z), 0f),
                                p.y.coerceIn(size.height * (1 - z), 0f),
                            )
                            if (zoom < 1.03f) { zoom = 1f; pan = Offset.Zero }
                            event.changes.forEach { it.consume() }
                        } else if (!transforming) {
                            map(pressed.first().position).let { (x, y) -> onIntent(DrawingIntent.Move(x, y)) }
                            pressed.first().consume()
                        }
                    }
                    if (!transforming) onIntent(DrawingIntent.Up)
                    drawing = false
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = pan.x; translationY = pan.y
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) { DrawingView(state.preview, showGuides = true) }
        if (!state.canUndo && state.live == null && !drawing) {
            Text("손가락으로 그려 보세요", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.align(Alignment.Center))
        }
        if (state.canUndo && !drawing) {
            Text(
                "두 손가락으로 확대 · 이동", style = KkTheme.type.caption, color = c.canvas,
                modifier = Modifier.align(Alignment.BottomStart).padding(KkSpacing.md)
                    .clip(RoundedCornerShape(KkRadius.full)).background(c.ink.copy(alpha = 0.85f))
                    .padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
            )
        }
    }
}

/**
 * 아래 툴바(Figma toolbar 80): 붓 · 지우개 · 스티커 · 사진 · 틀, 오른쪽에 되돌리기 · 다시.
 * 고른 칸은 종이 태그색 판, 붓 아래 점은 지금 색.
 */
@Composable
private fun Toolbar(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().height(79.dp).padding(start = 14.dp, end = KkSpacing.lg, top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val selected = state.toolbar
            val open = state.sheet
            fun on(item: ToolbarItem) = when (item) {
                ToolbarItem.PHOTO -> open == EditorSheet.PHOTO
                ToolbarItem.FRAME -> open == EditorSheet.FRAME
                else -> item == selected && open != EditorSheet.PHOTO && open != EditorSheet.FRAME
            }
            ToolbarButton(KkIcons.Brush, "붓", on(ToolbarItem.BRUSH), dot = Color(state.color.toInt())) { onIntent(DrawingIntent.Tap(ToolbarItem.BRUSH)) }
            ToolbarButton(KkIcons.Eraser, "지우개", on(ToolbarItem.ERASER)) { onIntent(DrawingIntent.Tap(ToolbarItem.ERASER)) }
            ToolbarButton(KkIcons.Sticker, "스티커", on(ToolbarItem.STICKER)) { onIntent(DrawingIntent.Tap(ToolbarItem.STICKER)) }
            ToolbarButton(KkIcons.Photo, "사진", on(ToolbarItem.PHOTO)) { onIntent(DrawingIntent.Tap(ToolbarItem.PHOTO)) }
            ToolbarButton(KkIcons.Frame, "틀", on(ToolbarItem.FRAME)) { onIntent(DrawingIntent.Tap(ToolbarItem.FRAME)) }
            Spacer(Modifier.weight(1f))
            ToolbarButton(KkIcons.Undo, "되돌리기", false, enabled = state.canUndo) { onIntent(DrawingIntent.Undo) }
            ToolbarButton(KkIcons.Redo, "다시 하기", false, enabled = state.canRedo) { onIntent(DrawingIntent.Redo) }
        }
    }
}

/** 툴바 한 칸: 40 판(고르면 tag) + 24 아이콘, 46 간격. [dot] 은 칸 아래 지금 색 점. */
@Composable
private fun ToolbarButton(icon: KkIcons, label: String, on: Boolean, enabled: Boolean = true, dot: Color? = null, onClick: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(KkRadius.lg)).background(if (on) c.tag else Color.Transparent)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label; selected = on },
            contentAlignment = Alignment.Center,
        ) { KkIcon(icon, tint = if (enabled) c.ink else c.line) }
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot).border(1.dp, c.line, CircleShape))
    }
}

// ── 시트 ──────────────────────────────────────────────────────────────────

/** Figma Sheet Header: 손잡이 + 제목(Label/Bold) + 닫기. */
@Composable
private fun SheetTitle(title: String, onClose: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KkSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.line))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "닫기" },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.Close, tint = c.ink, size = 13.8.dp) }
        }
    }
}

@Composable
private fun DrawingSheet(title: String, onIntent: (DrawingIntent) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val close = { onIntent(DrawingIntent.CloseSheet) }
    KkModalSheet(close) {
        SheetTitle(title, close)
        content()
    }
}

/** 시트 안 한 칸(Figma pen · sticker cell): canvas 바탕 · 모서리 12, 고르면 1.5 accent 테두리. */
@Composable
private fun SheetCell(
    on: Boolean,
    description: String,
    modifier: Modifier,
    role: Role = Role.RadioButton,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.height(78.dp).clip(shape).background(c.canvas)
            .then(if (on) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
            .clickable(role = role, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description; selected = on },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** 네 칸씩 줄 맞춰 놓기(빈 칸은 자리만). */
@Composable
private fun <T> Grid(items: List<T>, columns: Int = 4, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 펜(Figma 펜 고르기): 펜 칸(지금 펜 세트) + 굵기 3단계 + 색 묶음 · 색. 색을 길게 누르면 밝기.
 * 크레용 · 수채 · 파스텔 · 네온은 꽃신 평생 — 이용권이 없으면 눌렀을 때 안내만(그리던 그림은 그대로).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PenSheet(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    val lifetimeOn by (org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
        ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
    var locked by remember { mutableStateOf<String?>(null) }
    var shadesFor by remember { mutableStateOf<Long?>(null) }
    var palette by remember { mutableStateOf(PenPalette.entries.firstOrNull { p -> p.colors.any { state.color in shadesOf(it) } } ?: PenPalette.BASIC) }
    val ink = Color(state.color.toInt())
    DrawingSheet("펜", onIntent) {
        Grid(PEN_CHOICES) { p, modifier ->
            val lockedNow = p.lifetime && !lifetimeOn
            SheetCell(
                state.tool == p.tool, p.label + if (lockedNow) ", 꽃신 평생, 잠김" else "", modifier,
                onClick = { if (lockedNow) locked = p.label else onIntent(DrawingIntent.SetTool(p.tool)) },
            ) {
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    if (p.tool == EditorTool.ERASER) KkIcon(KkIcons.Eraser, tint = c.ink) else PenStroke(p.tool, ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, style = KkTheme.type.caption, color = if (state.tool == p.tool) c.accent else c.ink, textAlign = TextAlign.Center)
                    if (lockedNow) KkIcon(KkIcons.Lock, tint = c.sub, size = 12.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = KkSpacing.sm), horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text("굵기", style = KkTheme.type.labelBold, color = c.ink)
            KkSlider(
                value = state.brush.ordinal.toFloat(),
                onValueChange = { onIntent(DrawingIntent.SetBrush(BrushSize.entries[kotlin.math.round(it).toInt().coerceIn(0, BrushSize.entries.lastIndex)])) },
                valueRange = 0f..BrushSize.entries.lastIndex.toFloat(),
                modifier = Modifier.weight(1f).semantics { contentDescription = "굵기 ${BRUSH_LABEL.getValue(state.brush)}" },
            )
            Text(BRUSH_LABEL.getValue(state.brush), style = KkTheme.type.label, color = c.sub)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("색", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
            PenPalette.entries.forEach { p ->
                PaletteChip(p.label, palette == p, p.lifetime && !lifetimeOn) {
                    if (p.lifetime && !lifetimeOn) locked = "${p.label} 색" else palette = p
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.colors.forEachIndexed { i, argb ->
                val on = state.tool != EditorTool.ERASER && state.color in shadesOf(argb)
                val name = if (palette == PenPalette.BASIC) PEN_COLOR_NAMES[i] else "${palette.label} ${i + 1}"
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .then(if (on) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                        .combinedClickable(role = Role.RadioButton, onLongClick = { shadesFor = argb }) { onIntent(DrawingIntent.SetColor(argb)) }
                        .semantics { contentDescription = "$name, 길게 누르면 밝기"; selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    val inner = if (on) ink else Color(argb.toInt())
                    Box(Modifier.size(26.dp).clip(CircleShape).background(inner).border(1.dp, if (argb == 0xFFFFFFFF) c.line else Color.Transparent, CircleShape))
                }
            }
        }
        shadesFor?.let { base ->
            Text("밝기 고르기", style = KkTheme.type.label, color = c.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shadesOf(base).forEachIndexed { i, shade ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(shade.toInt()))
                            .then(if (shade == state.color) Modifier.border(2.dp, c.ink, CircleShape) else Modifier)
                            .clickable(role = Role.Button) { onIntent(DrawingIntent.SetColor(shade)); shadesFor = null }
                            .semantics { contentDescription = "밝기 ${i + 1}단계" },
                    )
                }
            }
        }
    }
    locked?.let { what ->
        KkModalSheet({ locked = null }) {
            KkSheetHeader(what, "꽃신 평생 꾸미기예요. 설정 › 꽃신 평생에서 열 수 있어요")
            KkPrimaryButton("확인", { locked = null }, Modifier.fillMaxWidth())
        }
    }
}

/** 펜 칸의 견본 획(지금 색으로): 펜은 가늘게, 형광펜은 굵고 비치게, 크레용은 거칠게, 수채는 번지게. */
@Composable
private fun PenStroke(tool: EditorTool, color: Color) {
    Canvas(Modifier.size(42.dp, 16.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.6f)
            cubicTo(size.width * 0.25f, -size.height * 0.1f, size.width * 0.45f, size.height * 1.1f, size.width * 0.7f, size.height * 0.4f)
            quadraticTo(size.width * 0.85f, 0f, size.width, size.height * 0.5f)
        }
        val (w, a, effect) = when (tool) {
            EditorTool.HIGHLIGHTER -> Triple(9.dp.toPx(), 0.45f, null)
            EditorTool.CRAYON -> Triple(5.dp.toPx(), 0.9f, PathEffect.dashPathEffect(floatArrayOf(3f, 1.5f)))
            EditorTool.WATERCOLOR -> Triple(7.dp.toPx(), 0.55f, null)
            else -> Triple(2.dp.toPx(), 1f, null)
        }
        drawPath(path, color.copy(alpha = a), style = DrawStroke(w, cap = StrokeCap.Round, pathEffect = effect))
    }
}

/** 색 묶음 칩(기본 · 파스텔 · 네온). 꽃신 평생인데 이용권이 없으면 자물쇠. */
@Composable
private fun PaletteChip(label: String, on: Boolean, lifetimeLocked: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.clip(CircleShape).background(if (on) c.ink else c.tag)
            .clickable(role = Role.RadioButton, onClick = onClick).semantics {
                selected = on
                contentDescription = label + if (lifetimeLocked) ", 꽃신 평생, 잠김" else ""
            }
            .padding(horizontal = KkSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        Text(label, style = KkTheme.type.label, color = if (on) c.surface else c.ink)
        if (lifetimeLocked) KkIcon(KkIcons.Lock, tint = c.ink, size = 12.dp)
    }
}

/** 스티커(Figma 스티커 서랍): 분류 칩(가로로 넘김) + 네 칸 서랍. 고르면 시트가 닫히고 캔버스를 눌러 붙인다. */
@Composable
private fun StickerSheet(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    val current = state.stickerTab.coerceIn(STICKER_TABS.indices)
    val tab = STICKER_TABS[current]
    DrawingSheet("스티커", onIntent) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            STICKER_TABS.forEachIndexed { i, t -> CategoryChip(t.label, i == current) { onIntent(DrawingIntent.SetStickerTab(i)) } }
        }
        val items = if (tab.dday) state.ddayStickers else tab.ids
        if (items.isEmpty()) {
            Text("복무 정보를 넣으면 D-day 스티커가 생겨요", style = KkTheme.type.body, color = c.sub, modifier = Modifier.padding(vertical = KkSpacing.lg))
        } else {
            Spacer(Modifier.height(KkSpacing.sm))
            Grid(items) { id, modifier ->
                SheetCell(state.sticker == id, "스티커 ${stickerLabel(id)}", modifier, onClick = { onIntent(DrawingIntent.SetSticker(id)) }) {
                    StickerPreview(id, Modifier.size(52.dp))
                }
            }
        }
    }
}

/** 분류 칩(Figma sticker tabs): 고른 칩은 ink 바탕 · surface 글자, 나머지는 종이 태그색. */
@Composable
private fun CategoryChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Text(
        label, style = KkTheme.type.label, color = if (on) c.surface else c.ink,
        modifier = Modifier.clip(CircleShape).background(if (on) c.ink else c.tag)
            .clickable(role = Role.Tab, onClick = onClick).semantics { selected = on }
            .padding(horizontal = KkSpacing.md, vertical = 6.dp),
    )
}

/** 사진: 앨범 · 카메라 + 사진 흐리게(0~60%). 사진을 넣으면 배경으로 깔린다. */
@Composable
private fun PhotoSheet(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, onPhoto: ((Boolean) -> Unit)?) {
    val c = KkTheme.colors
    DrawingSheet("사진", onIntent) {
        Grid(listOf(false, true), columns = 2) { camera, modifier ->
            val label = if (camera) "카메라로 찍기" else "앨범에서 고르기"
            SheetCell(false, label, modifier.alpha(if (onPhoto != null) 1f else 0.4f), role = Role.Button, onClick = { onPhoto?.invoke(camera) }) {
                KkIcon(if (camera) KkIcons.Camera else KkIcons.Photo, tint = c.ink)
                Text(label, style = KkTheme.type.caption, color = c.ink, modifier = Modifier.padding(top = KkSpacing.xs))
            }
        }
        if (onPhoto == null) Text("이 기기에서는 사진을 넣을 수 없어요", style = KkTheme.type.caption, color = c.sub)
        val photo = state.photo ?: return@DrawingSheet
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text("흐리게", style = KkTheme.type.labelBold, color = c.ink)
            KkSlider(
                value = photo.dim, onValueChange = { onIntent(DrawingIntent.SetPhotoDim(it)) },
                valueRange = 0f..PhotoBackground.MAX_DIM,
                modifier = Modifier.weight(1f).semantics { contentDescription = "사진 흐리게" },
            )
            Text("빼기", style = KkTheme.type.label, color = c.danger, modifier = Modifier.clickable(role = Role.Button) { onIntent(DrawingIntent.RemovePhoto) }.padding(KkSpacing.xs))
        }
    }
}

/**
 * 틀(Figma 틀 고르기): 보낼 때 둘러 줄 틀(지금은 없음 · 폴라로이드만) + 따라 그릴 안내선.
 */
@Composable
private fun FrameSheet(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    DrawingSheet("틀", onIntent) {
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            DrawingFrame.entries.forEach { f ->
                val on = state.frame == f
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                    val shape = RoundedCornerShape(KkRadius.lg)
                    Box(
                        Modifier.size(56.dp, 64.dp).clip(shape).background(c.canvas)
                            .then(if (on) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
                            .clickable(role = Role.RadioButton) { onIntent(DrawingIntent.SetFrame(f)) }
                            .semantics { contentDescription = "틀 ${f.label}"; selected = on },
                        contentAlignment = Alignment.Center,
                    ) { FrameThumb(f) }
                    Text(f.label, style = KkTheme.type.caption, color = if (on) c.accent else c.sub)
                }
            }
        }
        Text("따라 그리기", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.padding(top = KkSpacing.sm))
        Grid(TEMPLATES) { (id, label), modifier ->
            val on = state.template == id
            SheetCell(on, "따라 그리기 $label", modifier, onClick = { onIntent(DrawingIntent.SetTemplate(if (on) null else id)) }) {
                TemplatePreview(id, Modifier.size(40.dp))
                Text(label, style = KkTheme.type.caption, color = if (on) c.accent else c.ink)
            }
        }
    }
}

/** 틀 견본: 없음은 점선 칸, 폴라로이드는 흰 틀 + 사진 칸. 폴라로이드 틀은 콘텐츠라 흰색 고정. */
@Composable
private fun FrameThumb(frame: DrawingFrame) {
    val c = KkTheme.colors
    when (frame) {
        DrawingFrame.NONE -> Canvas(Modifier.size(30.dp, 36.dp)) {
            drawRect(c.sub, style = DrawStroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }
        DrawingFrame.POLAROID -> Box(
            Modifier.size(32.dp, 40.dp).kkElevation(KkElevation.Low, RoundedCornerShape(0.dp)).background(Color.White).padding(3.dp),
        ) { Box(Modifier.size(26.dp).background(c.line)) }
    }
}

// ── 보내기 ────────────────────────────────────────────────────────────────

/**
 * 완료 다음(Figma 편지에 붙이기 · 그림만 보내기): 고른 틀로 그림을 보여 주고, 아래 다시 그리기 · 붙이기(보내기).
 * [forLetter] 면 "편지에는 이렇게 붙어요"(붙이기 → 편지에), 아니면 "그림 한 장 편지"(글 · 위젯 · 예약을 고르고 바로 보내기).
 */
@Composable
fun DrawingSendScreen(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, canSave: Boolean = false, forLetter: Boolean = false) {
    val c = KkTheme.colors
    var scheduling by remember { mutableStateOf(false) }
    BackHandler { onIntent(DrawingIntent.BackToEdit) }
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(
            if (forLetter) "편지에 붙이기" else "그림만 보내기", onLeading = { onIntent(DrawingIntent.BackToEdit) },
            modifier = Modifier.padding(horizontal = KkSpacing.lg),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            val caption = state.caption.trim().ifBlank { null }
            when (state.frame) {
                DrawingFrame.POLAROID -> KkPolaroid(Modifier.width(225.dp).padding(top = KkSpacing.sm), tilt = 3f, caption = caption) {
                    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { DrawingView(state.preview) }
                }
                DrawingFrame.NONE -> Box(
                    Modifier.fillMaxWidth().kkElevation(KkElevation.Low, RoundedCornerShape(KkRadius.md)).clip(RoundedCornerShape(KkRadius.md)).background(Color.White),
                ) { DrawingView(state.preview) }
            }
            Text(
                if (forLetter) "편지에는 이렇게 붙어요" else "그림 한 장 편지",
                style = KkTheme.type.handL, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (!forLetter) {
                KkInput(state.caption, { onIntent(DrawingIntent.SetCaption(it)) }, label = "함께 보낼 글", placeholder = "오늘 당직 힘내", helper = "${state.caption.length} / 40")
            }
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                KkStatusChip("그리는 과정도 함께", selected = state.replayEnabled, onClick = { onIntent(DrawingIntent.SetReplay(!state.replayEnabled)) })
                if (!forLetter) {
                    KkStatusChip("위젯에 띄우기", selected = state.showOnWidget, onClick = { onIntent(DrawingIntent.SetWidget(!state.showOnWidget)) })
                    val at = state.revealAt
                    KkStatusChip(
                        at?.let { "${it.month.ordinal + 1}.${it.day} ${it.time.hhmm()} 예약" } ?: "예약 보내기", selected = at != null,
                        onClick = { scheduling = true },
                    )
                }
                if (canSave) KkStatusChip("사진첩에 저장", selected = false, onClick = { onIntent(DrawingIntent.Export) })
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = KkSpacing.lg), verticalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            // Figma Button Text · M: 글자만(높이 40).
            Box(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(KkRadius.full))
                    .clickable(role = Role.Button) { onIntent(DrawingIntent.BackToEdit) },
                contentAlignment = Alignment.Center,
            ) { Text("다시 그리기", style = KkTheme.type.labelBold, color = c.accent) }
            KkPrimaryButton(
                if (forLetter) "편지에 붙이기" else "${state.partnerName}에게 보내기",
                { onIntent(if (forLetter) DrawingIntent.Done else DrawingIntent.Send) },
                loading = state.saving,
            )
        }
    }
    if (scheduling) {
        KkScheduleSheet(
            initial = state.revealAt,
            onDismiss = { scheduling = false },
            onPick = { onIntent(DrawingIntent.Schedule(it)); scheduling = false },
            confirmLabel = "이 시각으로 예약",
        )
    }
}
