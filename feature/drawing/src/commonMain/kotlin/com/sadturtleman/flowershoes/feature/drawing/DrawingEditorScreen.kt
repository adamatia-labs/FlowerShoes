@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.drawing

import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkScheduleSheet
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkSwitch
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.hhmm
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.drawingui.STICKER_TABS
import com.sadturtleman.flowershoes.core.drawingui.StickerPreview
import com.sadturtleman.flowershoes.core.drawingui.TEMPLATES
import com.sadturtleman.flowershoes.core.drawingui.TemplatePreview
import kotlinx.datetime.LocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * 그림 그리기(Figma 4-1). [forLetter] 면 편지에 넣을 그림(완료 → [onSaved]), 아니면 그림만 보내기(다음 → 보내기 화면 → [onSend]).
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
        else DrawingEditorScreen(state, viewModel::onIntent, { viewModel.onIntent(DrawingIntent.Reset); onClose() }, forLetter, launchPicker)
        notice?.let {
            com.sadturtleman.flowershoes.core.designsystem.KkToast(
                it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.xxxl),
            )
        }
    }
}

private val BRUSH_LABEL = mapOf(BrushSize.S to "얇게", BrushSize.M to "보통", BrushSize.L to "굵게")

@Composable
fun DrawingEditorScreen(
    state: DrawingEditorState,
    onIntent: (DrawingIntent) -> Unit,
    onClose: () -> Unit,
    forLetter: Boolean = true,
    onPhoto: ((fromCamera: Boolean) -> Unit)? = null,
) {
    val c = KkTheme.colors
    var drawingNow by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var shadesOf by remember { mutableStateOf<Long?>(null) }
    var panelHeight by remember { mutableIntStateOf(0) }
    // 그리는 동안엔 도구를 접는다(손을 떼면 다시).
    val chrome by animateFloatAsState(if (drawingNow) 0f else 1f, label = "chrome")
    val close = { if (state.canUndo) leaving = true else onClose() }
    // 뒤로 가기도 닫기와 같다: 그린 게 있으면 지울지 묻는다.
    BackHandler { if (leaving) leaving = false else close() }

    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).statusBarsPadding().padding(horizontal = KkSpacing.lg, vertical = KkSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(if (state.menu == EditorMenu.STICKER) KkSpacing.sm else KkSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KkTopBar(
                    // Figma 최종 정리: 제목은 "그림 보내기"(편지에 넣을 때도).
                    "그림 보내기", onLeading = close, leading = KkTopBarLeading.Close,
                    // Figma 4-1: 다음 → 보내기 단계(편지에 넣을 때는 "편지에 넣을까요?").
                    trailingButton = "다음", trailingEnabled = state.canUndo && !state.saving,
                    onTrailing = { onIntent(DrawingIntent.Next) },
                    modifier = Modifier.alpha(chrome),
                )
                Box(Modifier.alpha(chrome * if (state.canUndo) 0.5f else 1f)) {
                    KkSegmentedControl(
                        listOf("2x2 정사각", "4x2 가로"), state.shape.ordinal,
                        { if (!state.canUndo) onIntent(DrawingIntent.SetShape(WidgetShape.entries[it])) },
                    )
                }
                DrawingCanvas(state, onIntent, onDrawing = { drawingNow = it }) { menu -> onIntent(DrawingIntent.SetMenu(menu)) }
                Row(Modifier.fillMaxWidth().alpha(chrome), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ToolButton(KkIcons.Undo, "되돌리기", selected = false, enabled = state.canUndo) { onIntent(DrawingIntent.Undo) }
                    ToolButton(KkIcons.Redo, "다시 하기", selected = false, enabled = state.canRedo) { onIntent(DrawingIntent.Redo) }
                    // Figma 최종 정리: 되돌리기 · 다시 하기만(손떨림 보정은 늘 켜 둔다).
                }
            }
            ToolPanel(
                state, onIntent, onPhoto, onLongPressColor = { shadesOf = it },
                modifier = Modifier.alpha(chrome).onSizeChanged { panelHeight = it.height },
            )
        }
        if (drawingNow) FocusPill(state, onIntent, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
        if (drawingNow) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(KkRadius.full)).background(c.bgSubtle).padding(horizontal = KkSpacing.md, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.borderStrong))
                Text("손을 떼면 도구가 다시 나와요", style = KkTheme.type.caption, color = c.textSecondary)
            }
        }
        shadesOf?.let { base ->
            val bottom = with(LocalDensity.current) { panelHeight.toDp() } - 96.dp
            BrightnessPopup(base, state.color, Modifier.align(Alignment.BottomStart).padding(start = KkSpacing.xl, bottom = bottom.coerceAtLeast(0.dp))) {
                onIntent(DrawingIntent.SetColor(it))
                shadesOf = null
            }
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
}

/**
 * 캔버스: 한 손가락은 그리기, 두 손가락은 확대(최대 4배) · 이동. 손가락 좌표를 논리 좌표(가로 1000)로 바꿔 넘긴다.
 * 비어 있으면 "손가락으로 바로 그려 보세요" 안내.
 */
@Composable
private fun DrawingCanvas(
    state: DrawingEditorState,
    onIntent: (DrawingIntent) -> Unit,
    onDrawing: (Boolean) -> Unit,
    onOpenMenu: (EditorMenu) -> Unit,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.xl)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var drawing by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(state.shape.width.toFloat() / state.shape.height)
            .kkElevation(KkElevation.Mid, shape).clip(shape).background(Color.White)
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
                    onDrawing(true)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (!transforming) {
                                transforming = true
                                onIntent(DrawingIntent.CancelStroke)
                                onDrawing(false)
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
                    onDrawing(false)
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
            Column(
                Modifier.align(Alignment.Center).padding(KkSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                Text("손가락으로 바로 그려 보세요", style = KkTheme.type.titleS, color = c.textPrimary)
                Row(Modifier.padding(top = KkSpacing.xs), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                    HintChip(KkIcons.Photo, "사진") { onOpenMenu(EditorMenu.PHOTO) }
                    HintChip(KkIcons.Sticker, "스티커") { onOpenMenu(EditorMenu.STICKER) }
                }
            }
        }
        if (state.canUndo && !drawing) {
            Text(
                "두 손가락으로 확대 · 이동", style = KkTheme.type.caption, color = c.textInverse,
                modifier = Modifier.align(Alignment.BottomStart).padding(KkSpacing.md)
                    .clip(RoundedCornerShape(KkRadius.full)).background(c.bgInverse.copy(alpha = 0.85f))
                    .padding(horizontal = KkSpacing.sm, vertical = KkSpacing.xxs),
            )
        }
    }
}

@Composable
private fun HintChip(icon: KkIcons, label: String, onClick: () -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bgBrandSubtle).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = KkSpacing.md, vertical = KkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(icon, tint = c.textBrand, size = 18.dp)
        Text(label, style = KkTheme.type.labelM, color = c.textBrand)
    }
}

/** 그리는 중 위에 뜨는 작은 막대: 되돌리기 · 지금 색 · 굵기. */
@Composable
private fun FocusPill(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, modifier: Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.full)
    Row(
        modifier.kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(horizontal = KkSpacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Undo, tint = c.textPrimary, size = 20.dp, modifier = Modifier.clickable(role = Role.Button) { onIntent(DrawingIntent.Undo) })
        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(state.color.toInt())).border(1.dp, c.borderDefault, CircleShape))
        Text(BRUSH_LABEL.getValue(state.brush), style = KkTheme.type.labelS, color = c.textSecondary)
    }
}

/** 도구 버튼(Tool Button) 48: 고르면 반전색. */
@Composable
private fun ToolButton(icon: KkIcons, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Box(
        Modifier.size(48.dp).then(if (selected) Modifier.kkElevation(KkElevation.Low, shape) else Modifier)
            .clip(shape).background(if (selected) c.bgInverse else c.bgSubtle)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { KkIcon(icon, tint = (if (selected) c.textInverse else c.textPrimary).copy(alpha = if (enabled) 1f else 0.4f)) }
}

/** 아래 도구 패널: 메뉴에 따라 내용이 바뀌고, 맨 아래 메뉴바는 항상 그대로. */
@Composable
private fun ToolPanel(
    state: DrawingEditorState,
    onIntent: (DrawingIntent) -> Unit,
    onPhoto: ((Boolean) -> Unit)?,
    onLongPressColor: (Long) -> Unit,
    modifier: Modifier,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(topStart = KkRadius.xl, topEnd = KkRadius.xl)
    Column(
        modifier.fillMaxWidth().kkElevation(KkElevation.Bar, shape).clip(shape).background(c.bgSurfaceRaised)
            .navigationBarsPadding().padding(start = KkSpacing.lg, end = KkSpacing.lg, top = KkSpacing.lg, bottom = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        when (state.menu) {
            EditorMenu.PEN -> PenPanel(state, onIntent, onLongPressColor)
            EditorMenu.STICKER -> StickerPanel(state, onIntent)
            EditorMenu.PHOTO -> PhotoPanel(state, onIntent, onPhoto)
            EditorMenu.TEMPLATE -> TemplatePanel(state, onIntent)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        MenuBar(state.menu) { onIntent(DrawingIntent.SetMenu(it)) }
    }
}

/**
 * 펜(Figma 그림 / 펜): 도구(펜 · 형광펜 · 크레용 · 수채 · 지우개) + 굵기 3단계, 색 묶음(기본 · 파스텔 · 네온) + 색.
 * 크레용 · 수채 · 파스텔 · 네온은 꽃신 평생 — 이용권이 없으면 눌렀을 때 안내만(그리던 그림은 그대로).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PenPanel(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, onLongPressColor: (Long) -> Unit) {
    val c = KkTheme.colors
    val lifetimeOn by (org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
        ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
    var locked by remember { mutableStateOf<String?>(null) }
    var palette by remember { mutableStateOf(PenPalette.entries.firstOrNull { p -> p.colors.any { state.color in shadesOf(it) } } ?: PenPalette.BASIC) }
    fun tool(t: EditorTool, label: String, lifetime: Boolean) = if (lifetime && !lifetimeOn) locked = label else onIntent(DrawingIntent.SetTool(t))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(KkIcons.Pen, "펜", state.tool == EditorTool.PEN) { tool(EditorTool.PEN, "펜", false) }
        ToolButton(KkIcons.Highlighter, "형광펜", state.tool == EditorTool.HIGHLIGHTER) { tool(EditorTool.HIGHLIGHTER, "형광펜", false) }
        BrushChip("크레용", state.tool == EditorTool.CRAYON, !lifetimeOn) { tool(EditorTool.CRAYON, "크레용", true) }
        BrushChip("수채", state.tool == EditorTool.WATERCOLOR, !lifetimeOn) { tool(EditorTool.WATERCOLOR, "수채 붓", true) }
        ToolButton(KkIcons.Eraser, "지우개", state.tool == EditorTool.ERASER) { tool(EditorTool.ERASER, "지우개", false) }
        Box(Modifier.width(1.dp).height(32.dp).background(c.borderDefault))
        BrushSize.entries.forEach { b ->
            val on = state.brush == b
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (on) c.bgSurface else c.bgSubtle)
                    .then(if (on) Modifier.border(2.dp, c.borderBrand, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onIntent(DrawingIntent.SetBrush(b)) }
                    .semantics { contentDescription = BRUSH_LABEL.getValue(b); selected = on },
                contentAlignment = Alignment.Center,
            ) {
                val d = when (b) { BrushSize.S -> 6.dp; BrushSize.M -> 10.dp; BrushSize.L -> 16.dp }
                Box(Modifier.size(d).clip(CircleShape).background(if (on) c.bgBrand else c.textSecondary))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        PenPalette.entries.forEach { p ->
            BrushChip(p.label, palette == p, p.lifetime && !lifetimeOn) {
                if (p.lifetime && !lifetimeOn) locked = "${p.label} 색" else palette = p
            }
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
        palette.colors.forEachIndexed { i, argb ->
            val on = state.tool != EditorTool.ERASER && state.color in shadesOf(argb)
            val name = if (palette == PenPalette.BASIC) PEN_COLOR_NAMES[i] else "${palette.label} ${i + 1}"
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .then(if (on) Modifier.border(2.dp, c.borderBrand, CircleShape) else Modifier)
                    .combinedClickable(role = Role.RadioButton, onLongClick = { onLongPressColor(argb) }) { onIntent(DrawingIntent.SetColor(argb)) }
                    .semantics { contentDescription = "$name, 길게 누르면 밝기"; selected = on },
                contentAlignment = Alignment.Center,
            ) {
                val inner = if (on) Color(state.color.toInt()) else Color(argb.toInt())
                Box(Modifier.size(if (on) 34.dp else 36.dp).clip(CircleShape).background(inner).border(1.dp, if (argb == 0xFFFFFFFF) c.borderDefault else Color.Transparent, CircleShape))
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

/** 글자 도구 칩(크레용 · 수채 · 색 묶음). 꽃신 평생인데 이용권이 없으면 "평생" 표시. */
@Composable
private fun BrushChip(label: String, on: Boolean, lifetimeLocked: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.height(40.dp).clip(CircleShape).background(if (on) c.bgBrand else c.bgSubtle)
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on }
            .padding(horizontal = KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = KkTheme.type.labelM, color = if (on) c.textOnBrand else c.textPrimary)
        if (lifetimeLocked) Text("평생", style = KkTheme.type.labelS, color = c.textTertiary)
    }
}

/** 밝기 고르기: 길게 누른 색의 밝은 · 어두운 단계. */
@Composable
private fun BrightnessPopup(base: Long, current: Long, modifier: Modifier, onPick: (Long) -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        modifier.kkElevation(KkElevation.High, shape).clip(shape).background(c.bgSurfaceRaised).padding(KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Text("밝기 고르기", style = KkTheme.type.labelS, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
            shadesOf(base).forEachIndexed { i, shade ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(shade.toInt()))
                        .then(if (shade == current) Modifier.border(2.dp, c.textPrimary, CircleShape) else Modifier)
                        .clickable(role = Role.Button) { onPick(shade) }
                        .semantics { contentDescription = "밝기 ${i + 1}단계" },
                )
            }
        }
    }
}

/**
 * 스티커(Figma 그림 / 스티커): 가로로 넘기는 분류 칩(하트 · 꽃 · 표정 · 일상 · 글자 · D-day · 군 생활 · 계절 · 기념일)
 * + 56 칸 서랍(가로로 넘김). 스티커는 모두 무료라 잠긴 팩 · 광고 띠가 없다.
 */
@Composable
private fun StickerPanel(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    val current = state.stickerTab.coerceIn(STICKER_TABS.indices)
    val tab = STICKER_TABS[current]
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        STICKER_TABS.forEachIndexed { i, t -> StickerChip(t.label, i == current) { onIntent(DrawingIntent.SetStickerTab(i)) } }
    }
    val items = if (tab.dday) state.ddayStickers else tab.ids
    if (items.isEmpty()) {
        Text("복무 정보를 넣으면 D-day 스티커가 생겨요", style = KkTheme.type.bodyS, color = c.textTertiary, modifier = Modifier.padding(vertical = KkSpacing.lg))
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        items.forEach { id ->
            val on = state.sticker == id
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)
                    .then(if (on) Modifier.border(2.dp, c.borderBrand, RoundedCornerShape(KkRadius.md)) else Modifier)
                    .clickable(role = Role.RadioButton) { onIntent(DrawingIntent.SetSticker(id)) }
                    .semantics {
                        contentDescription = "스티커 ${com.sadturtleman.flowershoes.core.drawingui.stickerLabel(id)}"
                        selected = on
                    },
                contentAlignment = Alignment.Center,
            ) {
                StickerPreview(id, Modifier.size(48.dp))
            }
        }
    }
}

/** 분류 칩(Figma Sticker Tabs): 알약 + 앞 점. 고른 칩은 brand 바탕 · 흰 점. */
@Composable
private fun StickerChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.clip(CircleShape).background(if (on) c.bgBrand else c.bgSurface)
            .then(if (on) Modifier else Modifier.border(1.dp, c.borderDefault, CircleShape))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = KkSpacing.md, vertical = KkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) c.textOnBrand else c.statusWarning))
        Text(label, style = KkTheme.type.labelM, color = if (on) c.textOnBrand else c.textPrimary)
    }
}

/** 사진(Figma 그림 / 사진): 앨범 · 카메라 + 사진 흐리게(0~60%). 사진을 넣으면 배경으로 깔린다. */
@Composable
private fun PhotoPanel(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, onPhoto: ((Boolean) -> Unit)?) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        PhotoButton(KkIcons.Photo, "앨범에서 고르기", onPhoto != null, Modifier.weight(1f)) { onPhoto?.invoke(false) }
        PhotoButton(KkIcons.Camera, "카메라로 찍기", onPhoto != null, Modifier.weight(1f)) { onPhoto?.invoke(true) }
    }
    if (onPhoto == null) Text("이 기기에서는 사진을 넣을 수 없어요", style = KkTheme.type.caption, color = c.textTertiary)
    val photo = state.photo ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text("사진 흐리게", style = KkTheme.type.labelS, color = c.textSecondary)
        com.sadturtleman.flowershoes.core.designsystem.KkSlider(
            value = photo.dim, onValueChange = { onIntent(DrawingIntent.SetPhotoDim(it)) },
            valueRange = 0f..com.sadturtleman.flowershoes.core.drawing.PhotoBackground.MAX_DIM,
            modifier = Modifier.weight(1f).semantics { contentDescription = "사진 흐리게" },
        )
        Text("빼기", style = KkTheme.type.labelM, color = c.statusDanger, modifier = Modifier.clickable(role = Role.Button) { onIntent(DrawingIntent.RemovePhoto) }.padding(KkSpacing.xs))
    }
}

@Composable
private fun PhotoButton(icon: KkIcons, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.md)
    Row(
        modifier.height(52.dp).kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(icon, tint = c.textPrimary)
        Text(label, style = KkTheme.type.labelM, color = c.textPrimary)
    }
}

@Composable
private fun TemplatePanel(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit) {
    val c = KkTheme.colors
    Text("따라 그릴 틀", style = KkTheme.type.labelS, color = c.textSecondary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        TEMPLATES.forEach { (id, label) ->
            val on = state.template == id
            val shape = RoundedCornerShape(KkRadius.md)
            Column(
                Modifier.weight(1f).clickable(role = Role.RadioButton) { onIntent(DrawingIntent.SetTemplate(if (on) null else id)) }
                    .semantics { contentDescription = "틀 $label"; selected = on },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(shape).background(if (on) c.bgBrandSubtle else c.bgSubtle)
                        .then(if (on) Modifier.border(2.dp, c.borderBrand, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { TemplatePreview(id, Modifier.size(48.dp)) }
                Text(label, style = KkTheme.type.caption, color = if (on) c.textBrand else c.textSecondary)
            }
        }
    }
}

/** 메뉴바(Editor Menu Bar): 고른 칸 위에 3pt 막대. */
@Composable
private fun MenuBar(selected: EditorMenu, onSelect: (EditorMenu) -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp)) {
        EditorMenu.entries.forEach { m ->
            val on = m == selected
            val icon = when (m) { EditorMenu.PEN -> KkIcons.Pen; EditorMenu.STICKER -> KkIcons.Sticker; EditorMenu.PHOTO -> KkIcons.Photo; EditorMenu.TEMPLATE -> KkIcons.Trace }
            Box(
                Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab) { onSelect(m) }.semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Box(Modifier.align(Alignment.TopCenter).width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.bgBrand))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KkIcon(icon, tint = if (on) c.textBrand else c.textSecondary)
                    Text(m.label, style = KkTheme.type.labelS, color = if (on) c.textBrand else c.textTertiary)
                }
            }
        }
    }
}

// ── 보내기 ────────────────────────────────────────────────────────────────

/** 그림만 보내기 › 보내기(Figma 그림 그리기 / 보내기). */
@Composable
/** 보내기 단계(Figma 그림 그리기 / 보내기). [forLetter] 면 "편지에 넣을까요?"(넣기 → 편지에 붙임), 아니면 연인에게 바로 보내기. */
fun DrawingSendScreen(state: DrawingEditorState, onIntent: (DrawingIntent) -> Unit, canSave: Boolean = false, forLetter: Boolean = false) {
    val c = KkTheme.colors
    var scheduling by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(state.caption.isNotEmpty()) }
    BackHandler { onIntent(DrawingIntent.BackToEdit) }
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.lg, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Text(if (forLetter) "편지에 넣을까요?" else "${state.partnerName}에게 보내기", style = KkTheme.type.titleL, color = c.textPrimary, modifier = Modifier.padding(top = KkSpacing.sm))
        val shape = RoundedCornerShape(KkRadius.xl)
        Box(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(Color.White).padding(KkSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) { DrawingView(state.preview) }
        if (!forLetter) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm, Alignment.CenterHorizontally)) {
            KkStatusChip("위젯에 띄우기", selected = state.showOnWidget, onClick = { onIntent(DrawingIntent.SetWidget(!state.showOnWidget)) }, dot = if (state.showOnWidget) Color.White else c.statusWarning)
            val at = state.revealAt
            KkStatusChip(
                at?.let { "${it.month.ordinal + 1}.${it.day} ${it.time.hhmm()} 예약" } ?: "예약 보내기", selected = at != null,
                onClick = { scheduling = true }, dot = if (at != null) Color.White else c.statusWarning,
            )
        }
        OptionRow(Modifier.clickable(role = Role.Switch) { onIntent(DrawingIntent.SetReplay(!state.replayEnabled)) }) {
            Text(if (forLetter) "그리는 과정도 함께 넣기" else "그리는 과정도 함께 보내기", style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
            KkSwitch(state.replayEnabled)
        }
        if (forLetter) Unit else if (writing) {
            KkInput(state.caption, { onIntent(DrawingIntent.SetCaption(it)) }, label = "함께 보낼 글", placeholder = "예) 오늘 당직 힘내", helper = "${state.caption.length} / 40")
        } else {
            OptionRow(Modifier.clickable(role = Role.Button) { writing = true }) {
                Text("글도 함께 쓸까요? (선택)", style = KkTheme.type.bodyM, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("쓰기", style = KkTheme.type.labelM, color = c.textBrand)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xxxl), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            RoundAction(KkIcons.Close, "취소", big = false, modifier = Modifier.offset(y = 14.dp)) { onIntent(DrawingIntent.BackToEdit) }
            RoundAction(KkIcons.Send, when { state.saving -> if (forLetter) "넣는 중" else "보내는 중"; forLetter -> "넣기"; else -> "보내기" }, big = true) {
                if (!state.saving) onIntent(if (forLetter) DrawingIntent.Done else DrawingIntent.Send)
            }
            RoundAction(KkIcons.Download, "저장", big = false, enabled = canSave, modifier = Modifier.offset(y = 14.dp)) { onIntent(DrawingIntent.Export) }
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

@Composable
private fun OptionRow(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).then(modifier)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 보내기 화면 아래 동그란 버튼: 취소 · 저장 52(흰색), 보내기 80(브랜드). */
@Composable
private fun RoundAction(icon: KkIcons, label: String, big: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = KkTheme.colors
    Column(
        modifier.alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = null, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        Box(
            Modifier.size(if (big) 80.dp else 52.dp).kkElevation(if (big) KkElevation.Mid else KkElevation.Low, CircleShape).clip(CircleShape)
                .background(if (big) c.bgBrand else c.bgSurface),
            contentAlignment = Alignment.Center,
        ) { KkIcon(icon, tint = if (big) c.textOnBrand else c.textPrimary, size = if (big) 32.dp else 24.dp) }
        Text(label, style = if (big) KkTheme.type.labelM else KkTheme.type.caption, color = if (big) c.textBrand else c.textSecondary)
    }
}
