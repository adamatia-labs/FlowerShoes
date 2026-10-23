package com.sadturtleman.flowershoes.feature.drawing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.CanvasSpec
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.DrawingRasterizer
import com.sadturtleman.flowershoes.core.drawing.Element
import com.sadturtleman.flowershoes.core.drawing.EraserStroke
import com.sadturtleman.flowershoes.core.drawing.PhotoBackground
import com.sadturtleman.flowershoes.core.drawing.Rect
import com.sadturtleman.flowershoes.core.drawing.PointBuffer
import com.sadturtleman.flowershoes.core.drawing.ReplayTimeline
import com.sadturtleman.flowershoes.core.drawing.StickerElement
import com.sadturtleman.flowershoes.core.drawing.Stroke
import com.sadturtleman.flowershoes.core.drawing.StrokeProcessing
import com.sadturtleman.flowershoes.core.drawing.TemplateGuide
import com.sadturtleman.flowershoes.core.drawing.Tool
import com.sadturtleman.flowershoes.core.drawing.Transform
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.drawingui.StickerIds
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

enum class EditorTool { PEN, HIGHLIGHTER, ERASER, STICKER }

/** 아래 메뉴바(Editor Menu Bar): 펜 · 스티커 · 사진 · 틀. 위 패널 내용만 바뀐다. */
enum class EditorMenu(val label: String) { PEN("펜"), STICKER("스티커"), PHOTO("사진"), TEMPLATE("틀") }

/** 편집 → 보내기(그림만 보내기). 편지에 넣는 그림은 편집에서 바로 끝난다. */
enum class EditorStage { EDIT, SEND }

/** 팔레트(Figma Palette): gray900 · rose500 · rose300 · amber500 · olive500 · sky500 · violet500 · gray0. */
val PEN_COLORS: List<Long> = listOf(
    0xFF15171A, 0xFFED4773, 0xFFFF99BB, 0xFFF2A93B, 0xFF748736, 0xFF3B9BF5, 0xFF8B6CF0, 0xFFFFFFFF,
)

val PEN_COLOR_NAMES: List<String> = listOf("검정", "장미", "분홍", "노랑", "올리브", "하늘", "보라", "흰색")

/** 밝기 고르기: 같은 색의 밝은 쪽 3 · 원래 색 · 어두운 쪽 3. */
fun shadesOf(argb: Long): List<Long> {
    fun mix(t: Float, toward: Int): Long {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        fun ch(v: Int) = (v + (toward - v) * t).roundToInt().coerceIn(0, 255).toLong()
        return (0xFF000000L) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
    return listOf(mix(0.6f, 255), mix(0.4f, 255), mix(0.2f, 255), argb, mix(0.2f, 0), mix(0.4f, 0), mix(0.6f, 0))
}

data class DrawingEditorState(
    val shape: WidgetShape = WidgetShape.SQUARE,
    val elements: List<Element> = emptyList(),
    /** 되돌린 것(다시 하기). 새로 그리면 비운다. */
    val redo: List<Element> = emptyList(),
    val live: Stroke? = null,
    val liveEraser: EraserStroke? = null,
    val tool: EditorTool = EditorTool.PEN,
    val menu: EditorMenu = EditorMenu.PEN,
    val color: Long = PEN_COLORS[1],
    val brush: BrushSize = BrushSize.M,
    val sticker: String = "img:heart/00",
    val stickerTab: Int = 0,
    /** D-day 서랍: 지금 숫자로 만든 알약("D-547" · "D+123"). */
    val ddayStickers: List<String> = emptyList(),
    val template: String? = null,
    /** 손떨림 보정. 켜면 들어오는 점을 부드럽게 따라간다(저장되는 좌표가 보정 후 값). */
    val stabilize: Boolean = true,
    val stage: EditorStage = EditorStage.EDIT,
    // 보내기 화면
    val partnerName: String = "연인",
    val replayEnabled: Boolean = true,
    val showOnWidget: Boolean = true,
    val caption: String = "",
    val revealAt: LocalDateTime? = null,
    val saving: Boolean = false,
) {
    val canUndo: Boolean get() = elements.any { it !is TemplateGuide }

    /** 배경 사진(하나만). */
    val photo: PhotoBackground? get() = elements.filterIsInstance<PhotoBackground>().firstOrNull()
    val canRedo: Boolean get() = redo.isNotEmpty()

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
    data class SetMenu(val menu: EditorMenu) : DrawingIntent
    data class SetColor(val argb: Long) : DrawingIntent
    data class SetBrush(val brush: BrushSize) : DrawingIntent
    data class SetSticker(val id: String) : DrawingIntent
    data class SetStickerTab(val index: Int) : DrawingIntent
    data class SetTemplate(val id: String?) : DrawingIntent
    data object ToggleStabilize : DrawingIntent

    /** 논리 좌표(캔버스 가로 1000 기준). */
    data class Down(val x: Int, val y: Int) : DrawingIntent
    data class Move(val x: Int, val y: Int) : DrawingIntent
    data object Up : DrawingIntent

    /** 두 손가락이 닿았다 — 그리던 획은 버리고 확대 · 이동으로. */
    data object CancelStroke : DrawingIntent
    data object Undo : DrawingIntent
    data object Redo : DrawingIntent

    /** 편지에 넣을 그림: 저장하고 끝. */
    data object Done : DrawingIntent

    /** 그림만 보내기: 보내기 화면으로. */
    data object Next : DrawingIntent
    data object BackToEdit : DrawingIntent
    data class SetReplay(val enabled: Boolean) : DrawingIntent
    data class SetWidget(val enabled: Boolean) : DrawingIntent
    data class SetCaption(val text: String) : DrawingIntent
    data class Schedule(val at: LocalDateTime?) : DrawingIntent
    data object Send : DrawingIntent

    /** 사진을 배경으로(앨범 · 카메라). 바이트는 이미 줄여서 JPEG 로 온다. */
    class SetPhoto(val jpeg: ByteArray) : DrawingIntent
    data class SetPhotoDim(val dim: Float) : DrawingIntent
    data object RemovePhoto : DrawingIntent

    /** 저장: 완성 PNG 를 만들어 사진첩으로. */
    data object Export : DrawingIntent

    /** 나가기: 그리던 것을 버린다(편집기는 앱 화면 하나에 하나라 다음에 열 때 깨끗해야 한다). */
    data object Reset : DrawingIntent
}

sealed interface DrawingEvent {
    /** 편지에 넣을 그림이 저장됐다. */
    data class Saved(val meta: DrawingMeta) : DrawingEvent

    /** 그림만 보내기: 이 그림 한 장으로 글 없는 편지를 보낸다. */
    data class Send(val meta: DrawingMeta, val caption: String?, val revealAt: LocalDateTime?) : DrawingEvent

    /** 사진첩에 저장할 완성 PNG. */
    class Exported(val png: ByteArray) : DrawingEvent
}

/**
 * 그림 편집기. 편집을 시작한 순간부터 시간을 재서 획마다 시작 오프셋 · 점마다 시각을 남긴다(리플레이용).
 *
 * 되돌리기한 획은 문서에서 지운다 — 리플레이에도 나오지 않는다(다시 하기로 되살리면 다시 들어간다).
 */
class DrawingEditorViewModel(
    private val attachments: AttachmentStore,
    /** 완성 PNG(위젯 · 알림용). 없는 플랫폼에서는 문서로 그린다. */
    private val rasterizer: DrawingRasterizer? = null,
    profile: ProfileRepository? = null,
    private val clock: Clock = Clock.System,
    /** D-day 스티커의 휴가 · 면회 · 외박(다음 일정까지). */
    events: com.sadturtleman.flowershoes.core.data.EventRepository? = null,
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

    /** 배경 사진 바이트(완성 PNG 를 그릴 때 쓴다). */
    private val photoBytes = mutableMapOf<String, ByteArray>()

    init {
        // 상대 이름(보내기 제목) · D-day 스티커 숫자.
        if (profile != null) viewModelScope.launch {
            val today0 = clock.today()
            val upcoming = events?.observeRange(today0, LocalDate(today0.year + 2, 12, 31)) ?: kotlinx.coroutines.flow.flowOf(emptyList())
            combine(profile.observeCouple(), profile.observeUsers(), profile.observeTimelines(), upcoming) { couple, users, timelines, evs ->
                val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
                val partner = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
                val today = clock.today()
                val timeline = timelines.firstOrNull()?.second
                val left = timeline?.daysLeft(today)
                val since = (couple?.anniversary?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: couple?.linkedAtMs?.takeIf { partnerId != null }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.of("Asia/Seoul")).date })
                    ?.let { it.daysUntil(today) + 1 }
                fun next(type: com.sadturtleman.flowershoes.core.model.EventType) =
                    evs.filter { it.type == type && it.start.date >= today }.minByOrNull { it.start }?.let { today.daysUntil(it.start.date) }
                fun d(n: Int) = if (n == 0) "D-DAY" else "D-$n"
                // Figma 🌸 스티커 › D-day 8: 전역 · 휴가 · 면회 · 외박 · 진급 · 우리 N일 · 우리 N년 · 전역 D-DAY. 값이 있는 것만.
                val dday = listOfNotNull(
                    left?.takeIf { it >= 0 }?.let { "전역" to d(it) },
                    next(com.sadturtleman.flowershoes.core.model.EventType.VACATION)?.let { "휴가" to d(it) },
                    next(com.sadturtleman.flowershoes.core.model.EventType.VISIT)?.let { "면회" to d(it) },
                    next(com.sadturtleman.flowershoes.core.model.EventType.OVERNIGHT)?.let { "외박" to d(it) },
                    timeline?.nextRank(today)?.let { "진급" to d(today.daysUntil(it.from)) },
                    since?.let { "우리" to "${it}일" },
                    since?.takeIf { it >= 365 }?.let { "우리" to "${it / 365}년" },
                ).map { (label, value) -> StickerIds.dday(label, value) }
                partner to dday
            }.collect { (partner, dday) ->
                _state.update { it.copy(partnerName = partner, ddayStickers = dday) }
            }
        }
    }

    private fun nowMs() = startedAt.elapsedNow().inWholeMilliseconds

    fun onIntent(intent: DrawingIntent) {
        when (intent) {
            is DrawingIntent.SetShape -> _state.update { if (it.canUndo) it else it.copy(shape = intent.shape) }
            is DrawingIntent.SetTool -> _state.update { it.copy(tool = intent.tool) }
            is DrawingIntent.SetMenu -> _state.update {
                it.copy(
                    menu = intent.menu,
                    // 스티커 메뉴에서는 탭이 스티커를 붙인다. 펜 메뉴로 오면 그리던 도구로.
                    tool = when {
                        intent.menu == EditorMenu.STICKER -> EditorTool.STICKER
                        it.tool == EditorTool.STICKER -> EditorTool.PEN
                        else -> it.tool
                    },
                )
            }
            is DrawingIntent.SetColor -> _state.update { it.copy(color = intent.argb, tool = if (it.tool == EditorTool.ERASER || it.tool == EditorTool.STICKER) EditorTool.PEN else it.tool) }
            is DrawingIntent.SetBrush -> _state.update { it.copy(brush = intent.brush) }
            is DrawingIntent.SetSticker -> _state.update { it.copy(sticker = intent.id, tool = EditorTool.STICKER) }
            is DrawingIntent.SetStickerTab -> _state.update { it.copy(stickerTab = intent.index) }
            is DrawingIntent.SetTemplate -> _state.update { s ->
                val rest = s.elements.filterNot { it is TemplateGuide }
                s.copy(
                    template = intent.id,
                    elements = intent.id?.let { listOf(TemplateGuide(newId(), nowMs(), it)) + rest } ?: rest,
                )
            }
            DrawingIntent.ToggleStabilize -> _state.update { it.copy(stabilize = !it.stabilize) }
            is DrawingIntent.Down -> down(intent.x, intent.y)
            is DrawingIntent.Move -> move(intent.x, intent.y)
            DrawingIntent.Up -> up()
            DrawingIntent.CancelStroke -> {
                xs.clear(); ys.clear(); ts.clear()
                _state.update { it.copy(live = null, liveEraser = null) }
            }
            DrawingIntent.Undo -> _state.update { s ->
                val i = s.elements.indexOfLast { it !is TemplateGuide }
                if (i < 0) s else s.copy(elements = s.elements.toMutableList().apply { removeAt(i) }, redo = s.redo + s.elements[i])
            }
            DrawingIntent.Redo -> _state.update { s ->
                val last = s.redo.lastOrNull() ?: return@update s
                s.copy(elements = s.elements + last, redo = s.redo.dropLast(1))
            }
            DrawingIntent.Done -> save { meta -> _events.send(DrawingEvent.Saved(meta)) }
            DrawingIntent.Next -> _state.update { if (it.canUndo) it.copy(stage = EditorStage.SEND) else it }
            DrawingIntent.BackToEdit -> _state.update { it.copy(stage = EditorStage.EDIT) }
            is DrawingIntent.SetReplay -> _state.update { it.copy(replayEnabled = intent.enabled) }
            is DrawingIntent.SetWidget -> _state.update { it.copy(showOnWidget = intent.enabled) }
            is DrawingIntent.SetCaption -> _state.update { it.copy(caption = intent.text.take(40)) }
            is DrawingIntent.Schedule -> _state.update { it.copy(revealAt = intent.at) }
            is DrawingIntent.SetPhoto -> viewModelScope.launch {
                val id = attachments.put(AttachmentKind.PHOTO, intent.jpeg)
                photoBytes[id] = intent.jpeg
                val photo = PhotoBackground(newId(), nowMs(), id, dim = 0.2f, crop = Rect(0, 0, 1000, 1000))
                _state.update { s ->
                    // 사진은 맨 아래(틀 다음)에 하나만.
                    val rest = s.elements.filterNot { it is PhotoBackground }
                    val guides = rest.filterIsInstance<TemplateGuide>()
                    s.copy(elements = guides + photo + rest.filterNot { it is TemplateGuide }, redo = emptyList())
                }
            }
            is DrawingIntent.SetPhotoDim -> _state.update { s ->
                s.copy(elements = s.elements.map { if (it is PhotoBackground) it.copy(dim = intent.dim.coerceIn(0f, PhotoBackground.MAX_DIM)) else it })
            }
            DrawingIntent.RemovePhoto -> _state.update { s -> s.copy(elements = s.elements.filterNot { it is PhotoBackground }) }
            DrawingIntent.Export -> {
                val s = _state.value
                val r = rasterizer ?: return
                viewModelScope.launch {
                    val doc = DrawingDocument(canvas = CanvasSpec(s.shape), elements = s.elements, totalDurationMs = nowMs())
                    val png = runCatching { r.png(doc, photosOf(doc)) }.getOrNull() ?: return@launch
                    _events.send(DrawingEvent.Exported(png))
                }
            }
            DrawingIntent.Reset -> _state.update { DrawingEditorState(partnerName = it.partnerName, ddayStickers = it.ddayStickers) }
            DrawingIntent.Send -> {
                val s = _state.value
                save { meta -> _events.send(DrawingEvent.Send(meta, s.caption.trim().ifBlank { null }, s.revealAt)) }
            }
        }
    }

    private fun down(x: Int, y: Int) {
        val s = _state.value
        if (s.tool == EditorTool.STICKER) {
            _state.update { it.copy(elements = it.elements + StickerElement(newId(), nowMs(), s.sticker, Transform(x.toFloat(), y.toFloat(), 1f, 0f)), redo = emptyList()) }
            return
        }
        strokeStart = nowMs()
        xs.clear(); ys.clear(); ts.clear()
        append(x, y)
    }

    private fun move(x: Int, y: Int) {
        if (_state.value.tool == EditorTool.STICKER || xs.isEmpty()) return
        append(x, y)
    }

    private fun append(rawX: Int, rawY: Int) {
        val s = _state.value
        // 손떨림 보정: 앞 점을 45% 씩 따라간다(지수 이동 평균).
        val (x, y) = if (s.stabilize && xs.isNotEmpty()) {
            (xs.last() + (rawX - xs.last()) * 0.45f).roundToInt() to (ys.last() + (rawY - ys.last()) * 0.45f).roundToInt()
        } else rawX to rawY
        val cx = x.coerceIn(0, s.shape.width)
        val cy = y.coerceIn(0, s.shape.height)
        if (xs.isNotEmpty() && xs.last() == cx && ys.last() == cy) return
        xs += cx
        ys += cy
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
            s.copy(elements = s.elements + listOfNotNull(done), live = null, liveEraser = null, redo = if (done != null) emptyList() else s.redo)
        }
        xs.clear(); ys.clear(); ts.clear()
    }

    private fun save(after: suspend (DrawingMeta) -> Unit) {
        val s = _state.value
        if (s.saving || !s.canUndo) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val doc = StrokeProcessing.prepareForSave(
                DrawingDocument(canvas = CanvasSpec(s.shape), elements = s.elements, totalDurationMs = nowMs()),
            )
            val docId = attachments.put(AttachmentKind.DRAWING_DOC, DrawingCodec.encodeForStorage(doc))
            val pngId = rasterizer?.let { r -> runCatching { attachments.put(AttachmentKind.DRAWING_PNG, r.png(doc, photosOf(doc))) }.getOrNull() } ?: docId
            val meta = DrawingMeta(
                id = newId(),
                orderInLetter = 0,
                shape = s.shape.name,
                docAttachmentId = docId,
                pngAttachmentId = pngId,
                caption = s.caption.trim().ifBlank { null },
                replayEnabled = s.replayEnabled,
                replayDurationMs = ReplayTimeline.of(doc).durationMs,
                showOnWidget = s.showOnWidget,
                photoAttachmentId = s.photo?.attachmentId,
            )
            _state.value = DrawingEditorState(partnerName = s.partnerName, ddayStickers = s.ddayStickers)
            after(meta)
        }
    }

    private suspend fun photosOf(doc: DrawingDocument): Map<String, ByteArray> =
        doc.elements.filterIsInstance<PhotoBackground>().mapNotNull { p ->
            (photoBytes[p.attachmentId] ?: attachments.read(p.attachmentId))?.let { p.attachmentId to it }
        }.toMap()

    private fun newId() = Uuid.random().toString()
}
