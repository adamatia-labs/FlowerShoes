package com.sadturtleman.flowershoes.feature.drawing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.CanvasSpec
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.DrawingRasterizer
import com.sadturtleman.flowershoes.core.drawing.Element
import com.sadturtleman.flowershoes.core.drawing.EraserStroke
import com.sadturtleman.flowershoes.core.drawing.PointBuffer
import com.sadturtleman.flowershoes.core.drawing.ReplayTimeline
import com.sadturtleman.flowershoes.core.drawing.StickerElement
import com.sadturtleman.flowershoes.core.drawing.Stroke
import com.sadturtleman.flowershoes.core.drawing.StrokeProcessing
import com.sadturtleman.flowershoes.core.drawing.TemplateGuide
import com.sadturtleman.flowershoes.core.drawing.Tool
import com.sadturtleman.flowershoes.core.drawing.Transform
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlin.time.Clock
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorTool { PEN, HIGHLIGHTER, ERASER, STICKER }

/** 팔레트(펜 색). 배경은 흰색 고정이라 아주 밝은 색은 넣지 않는다. */
val PEN_COLORS: List<Long> = listOf(
    0xFF15171A, 0xFFC72C4D, 0xFFED4773, 0xFFF2A93B, 0xFF2FB67C, 0xFF3B9BF5, 0xFF8B6CF0, 0xFF566B24,
)

data class DrawingEditorState(
    val shape: WidgetShape = WidgetShape.SQUARE,
    val elements: List<Element> = emptyList(),
    val live: Stroke? = null,
    val liveEraser: EraserStroke? = null,
    val tool: EditorTool = EditorTool.PEN,
    val color: Long = PEN_COLORS[1],
    val brush: BrushSize = BrushSize.M,
    val sticker: String = "heart",
    val template: String? = null,
    val replayEnabled: Boolean = true,
    val saving: Boolean = false,
) {
    val canUndo: Boolean get() = elements.any { it !is TemplateGuide }

    /** 화면에 그릴 문서(그리는 중인 획 포함). */
    val preview: DrawingDocument
        get() = DrawingDocument(
            canvas = CanvasSpec(shape),
            elements = elements + listOfNotNull(live, liveEraser),
            totalDurationMs = 0,
        )
}

sealed interface DrawingIntent {
    data class SetShape(val shape: WidgetShape) : DrawingIntent
    data class SetTool(val tool: EditorTool) : DrawingIntent
    data class SetColor(val argb: Long) : DrawingIntent
    data class SetBrush(val brush: BrushSize) : DrawingIntent
    data class SetSticker(val id: String) : DrawingIntent
    data class SetTemplate(val id: String?) : DrawingIntent
    data class SetReplay(val enabled: Boolean) : DrawingIntent

    /** 논리 좌표(캔버스 가로 1000 기준). */
    data class Down(val x: Int, val y: Int) : DrawingIntent
    data class Move(val x: Int, val y: Int) : DrawingIntent
    data object Up : DrawingIntent
    data object Undo : DrawingIntent
    data object Done : DrawingIntent
}

sealed interface DrawingEvent {
    data class Saved(val meta: DrawingMeta) : DrawingEvent
}

/**
 * 그림 편집기. 편집을 시작한 순간부터 시간을 재서 획마다 시작 오프셋 · 점마다 시각을 남긴다(리플레이용).
 *
 * 되돌리기한 획은 문서에서 지운다 — 리플레이에도 나오지 않는다.
 */
class DrawingEditorViewModel(
    private val attachments: AttachmentStore,
    /** 완성 PNG(위젯 · 알림용). 없는 플랫폼에서는 문서로 그린다. */
    private val rasterizer: DrawingRasterizer? = null,
    @Suppress("unused") private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(DrawingEditorState())
    val state: StateFlow<DrawingEditorState> = _state.asStateFlow()

    private val _events = Channel<DrawingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val startedAt: TimeMark = TimeSource.Monotonic.markNow()
    private var strokeStart = 0L
    private val xs = mutableListOf<Int>()
    private val ys = mutableListOf<Int>()
    private val ts = mutableListOf<Int>()

    private fun nowMs() = startedAt.elapsedNow().inWholeMilliseconds

    fun onIntent(intent: DrawingIntent) {
        when (intent) {
            is DrawingIntent.SetShape -> _state.update { if (it.elements.isEmpty()) it.copy(shape = intent.shape) else it }
            is DrawingIntent.SetTool -> _state.update { it.copy(tool = intent.tool) }
            is DrawingIntent.SetColor -> _state.update { it.copy(color = intent.argb, tool = if (it.tool == EditorTool.ERASER) EditorTool.PEN else it.tool) }
            is DrawingIntent.SetBrush -> _state.update { it.copy(brush = intent.brush) }
            is DrawingIntent.SetSticker -> _state.update { it.copy(sticker = intent.id, tool = EditorTool.STICKER) }
            is DrawingIntent.SetTemplate -> _state.update { s ->
                val rest = s.elements.filterNot { it is TemplateGuide }
                s.copy(
                    template = intent.id,
                    elements = intent.id?.let { listOf(TemplateGuide(newId(), nowMs(), it)) + rest } ?: rest,
                )
            }
            is DrawingIntent.SetReplay -> _state.update { it.copy(replayEnabled = intent.enabled) }
            is DrawingIntent.Down -> down(intent.x, intent.y)
            is DrawingIntent.Move -> move(intent.x, intent.y)
            DrawingIntent.Up -> up()
            DrawingIntent.Undo -> _state.update { s ->
                val i = s.elements.indexOfLast { it !is TemplateGuide }
                if (i < 0) s else s.copy(elements = s.elements.toMutableList().apply { removeAt(i) })
            }
            DrawingIntent.Done -> save()
        }
    }

    private fun down(x: Int, y: Int) {
        val s = _state.value
        if (s.tool == EditorTool.STICKER) {
            _state.update { it.copy(elements = it.elements + StickerElement(newId(), nowMs(), s.sticker, Transform(x.toFloat(), y.toFloat(), 1f, 0f))) }
            return
        }
        strokeStart = nowMs()
        xs.clear(); ys.clear(); ts.clear()
        append(x, y)
    }

    private fun move(x: Int, y: Int) {
        if (_state.value.tool == EditorTool.STICKER || xs.isEmpty()) return
        if (xs.last() == x && ys.last() == y) return
        append(x, y)
    }

    private fun append(x: Int, y: Int) {
        val s = _state.value
        xs += x.coerceIn(0, s.shape.width)
        ys += y.coerceIn(0, s.shape.height)
        ts += (nowMs() - strokeStart).toInt()
        val points = PointBuffer(xs.toIntArray(), ys.toIntArray(), ts.toIntArray())
        _state.update {
            when (it.tool) {
                EditorTool.ERASER -> it.copy(liveEraser = EraserStroke("live", strokeStart, it.brush, points))
                else -> it.copy(
                    live = Stroke("live", strokeStart, if (it.tool == EditorTool.HIGHLIGHTER) Tool.HIGHLIGHTER else Tool.PEN, it.color, it.brush, points),
                )
            }
        }
    }

    private fun up() {
        _state.update { s ->
            val done = s.live?.copy(id = newId()) ?: s.liveEraser?.copy(id = newId())
            s.copy(elements = s.elements + listOfNotNull(done), live = null, liveEraser = null)
        }
        xs.clear(); ys.clear(); ts.clear()
    }

    private fun save() {
        val s = _state.value
        if (s.saving || !s.canUndo) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val doc = StrokeProcessing.prepareForSave(
                DrawingDocument(canvas = CanvasSpec(s.shape), elements = s.elements, totalDurationMs = nowMs()),
            )
            val docId = attachments.put(AttachmentKind.DRAWING_DOC, DrawingCodec.encodeForStorage(doc))
            val pngId = rasterizer?.let { r -> runCatching { attachments.put(AttachmentKind.DRAWING_PNG, r.png(doc)) }.getOrNull() } ?: docId
            val meta = DrawingMeta(
                id = newId(),
                orderInLetter = 0,
                shape = s.shape.name,
                docAttachmentId = docId,
                pngAttachmentId = pngId,
                caption = null,
                replayEnabled = s.replayEnabled,
                replayDurationMs = ReplayTimeline.of(doc).durationMs,
            )
            _state.value = DrawingEditorState()
            _events.send(DrawingEvent.Saved(meta))
        }
    }

    private fun newId() = Uuid.random().toString()
}
