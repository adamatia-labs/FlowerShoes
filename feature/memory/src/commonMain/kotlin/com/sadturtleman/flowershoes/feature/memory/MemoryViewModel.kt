package com.sadturtleman.flowershoes.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.DrawingRasterizer
import com.sadturtleman.flowershoes.core.drawing.PhotoBackground
import com.sadturtleman.flowershoes.core.military.koreanName
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** 추억 분류(Segment): 전체 · 그림 · 편지 · 만남. */
enum class MemoryFilter(val label: String) { ALL("전체"), DRAWING("그림"), LETTER("편지"), MEET("만남") }

/** 타임라인 한 칸. 레일 점 색: 그림 · 만남 = 로즈, 편지 · 기념일 = 올리브. */
sealed interface MemoryEntry {
    val date: LocalDate

    /** 그림(글 없는 편지, 또는 그림이 든 편지의 첫 그림). */
    data class Drawing(override val date: LocalDate, val letterId: String, val title: String, val meta: String, val thumb: DrawingDocument?, val hearts: Int = 0) : MemoryEntry
    data class Letter(override val date: LocalDate, val letterId: String, val title: String, val preview: String, val hearts: Int = 0) : MemoryEntry

    /** 지난 휴가 · 외박 · 면회(캘린더에 적은 것). [photos] 는 만남 사진 첨부 id(최대 3). */
    data class Meet(override val date: LocalDate, val label: String, val title: String, val eventId: String = "", val photos: List<String> = emptyList()) : MemoryEntry

    /** 입대 N일 · 진급 · 만난 지 N일. */
    data class Anniversary(override val date: LocalDate, val title: String) : MemoryEntry
}

data class MemoryStats(val daysTogether: Int?, val drawings: Int, val letters: Int, val meets: Int)

/** 그림 상세(추억 / 그림 상세). */
data class DrawingDetail(
    val letterId: String,
    val drawingId: String,
    val doc: DrawingDocument,
    val mine: Boolean,
    /** "10월 21일 (수)". */
    val dateTitle: String,
    val title: String,
    /** "연인이 나에게 · 18:40 도착". */
    val meta: String,
    val reply: Reply?,
) {
    /** 이 그림 다음에 상대가 보낸 편지(답장). */
    data class Reply(val initial: String, val who: String, val text: String)
}

data class MemoryState(
    val filter: MemoryFilter = MemoryFilter.ALL,
    val stats: MemoryStats = MemoryStats(null, 0, 0, 0),
    val entries: List<MemoryEntry> = emptyList(),
    val loading: Boolean = true,
    val detail: DrawingDetail? = null,
) {
    val visible: List<MemoryEntry>
        get() = when (filter) {
            MemoryFilter.ALL -> entries
            MemoryFilter.DRAWING -> entries.filterIsInstance<MemoryEntry.Drawing>()
            MemoryFilter.LETTER -> entries.filterIsInstance<MemoryEntry.Letter>()
            MemoryFilter.MEET -> entries.filter { it is MemoryEntry.Meet || it is MemoryEntry.Anniversary }
        }
    val empty: Boolean get() = !loading && entries.none { it !is MemoryEntry.Anniversary }
}

sealed interface MemoryIntent {
    data class Filter(val filter: MemoryFilter) : MemoryIntent
    data class OpenDrawing(val letterId: String) : MemoryIntent
    data object CloseDrawing : MemoryIntent
    data object PinToWidget : MemoryIntent
    data object Export : MemoryIntent
    /** 만남 카드에 사진을 붙인다(줄인 JPEG). 일정에 실려 상대 폰에도 간다. */
    data class AddMeetPhoto(val eventId: String, val jpeg: ByteArray) : MemoryIntent
    data class RemoveMeetPhoto(val eventId: String, val attachmentId: String) : MemoryIntent
}

/**
 * 추억: 편지 · 그림 · 지난 만남 · 기념일을 시간 거꾸로. 예약 편지는 공개 전이라 여기에도 없다(편지함과 같은 쿼리).
 */
class MemoryViewModel(
    private val letters: LetterRepository,
    private val events: EventRepository,
    profile: ProfileRepository,
    private val attachments: AttachmentStore,
    private val settings: DeviceSettings,
    private val rasterizer: DrawingRasterizer? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val me = profile.myUserId()
    private val today = clock.today()
    private val ui = MutableStateFlow(MemoryState())
    private val docs = MutableStateFlow<Map<String, DrawingDocument?>>(emptyMap())

    /** 만남 사진 원본(첨부 id → JPEG). 화면이 썸네일로 쓴다. */
    private val _photos = MutableStateFlow<Map<String, ByteArray?>>(emptyMap())
    val photos: StateFlow<Map<String, ByteArray?>> = _photos

    /** 그림 상세 › 저장 · 공유에 쓸 완성 PNG. */
    private val _exports = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
    val exports: SharedFlow<ByteArray> = _exports

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val notices: SharedFlow<String> = _notices

    private val names = combine(profile.observeCouple(), profile.observeUsers()) { couple, users ->
        val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
        val my = users.firstOrNull { it.id == me }?.nickname?.takeIf { it.isNotBlank() } ?: "나"
        val partner = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
        val since = couple?.anniversary?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: couple?.linkedAtMs?.takeIf { partnerId != null }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(SEOUL).date }
        Triple(my, partner, since)
    }

    val state: StateFlow<MemoryState> = combine(
        ui,
        combine(letters.observeInbox(), letters.observeHearts()) { l, h -> l to h },
        events.observeRange(today.minus(DatePeriod(years = 3)), today),
        combine(names, profile.observeTimelines()) { n, t -> n to t.firstOrNull()?.second },
        docs,
    ) { s, (ls, hearts), es, (n, timeline), thumbs ->
        val (my, partner, since) = n
        val drawingEntries = ls.filter { it.drawingCount > 0 && !it.hasText }.map { l ->
            MemoryEntry.Drawing(l.date(), l.id, l.title?.takeIf { it.isNotBlank() } ?: "그림 한 장", if (l.authorId == me) my else partner, thumbs[l.id], hearts[l.id] ?: 0)
        }
        val letterEntries = ls.filter { it.hasText }.map { l ->
            val text = l.blocks.filterIsInstance<LetterBlock.Text>().joinToString(" ") { it.text }.replace('\n', ' ')
            MemoryEntry.Letter(l.date(), l.id, l.title?.takeIf { it.isNotBlank() } ?: text.take(24), if (l.title.isNullOrBlank()) text.drop(24).take(60) else text.take(60), hearts[l.id] ?: 0)
        }
        val meets = es.filter { it.type in MEETINGS && it.end.date <= today }.sortedBy { it.start }
        val firstVisit = meets.firstOrNull { it.type == EventType.VISIT }?.id
        val meetEntries = meets.map { e ->
            val kind = if (e.id == firstVisit) "첫 면회" else e.type.label()
            MemoryEntry.Meet(e.start.date, "만남 · $kind", listOfNotNull(e.title.ifBlank { e.type.label() }, e.durationLabel()).joinToString(" · "), e.id, e.photoIds)
        }
        val anniversaries = buildList {
            timeline?.let { t ->
                listOf(30, 50, 100, 200, 300, 365, 500).forEach { n ->
                    val d = t.enlistDate.plus(DatePeriod(days = n - 1))
                    if (d <= today && d <= t.dischargeDate) add(MemoryEntry.Anniversary(d, "입대 ${n}일"))
                }
                t.ranks.drop(1).filter { it.from <= today }.forEach { add(MemoryEntry.Anniversary(it.from, "${it.rank.koreanName} 진급")) }
                if (t.dischargeDate <= today) add(MemoryEntry.Anniversary(t.dischargeDate, "전역"))
            }
            since?.let { start ->
                (1..20).map { it * 100 }.forEach { n ->
                    val d = start.plus(DatePeriod(days = n - 1))
                    if (d <= today) add(MemoryEntry.Anniversary(d, "만난 지 ${n}일"))
                }
            }
        }
        val all = (drawingEntries + letterEntries + meetEntries + anniversaries).sortedByDescending { it.date }
        s.copy(
            loading = false,
            entries = all,
            stats = MemoryStats(
                daysTogether = since?.let { it.daysUntil(today) + 1 },
                drawings = ls.sumOf { it.drawingCount },
                letters = letterEntries.size,
                meets = meetEntries.size,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryState())

    init {
        // 만남 사진: 처음 보이는 첨부를 한 번씩 읽어 둔다(상대가 붙인 사진은 받은 뒤에 생긴다 — 없으면 다음에 다시).
        viewModelScope.launch {
            state.collect { st ->
                st.entries.filterIsInstance<MemoryEntry.Meet>().flatMap { it.photos }.filter { _photos.value[it] == null }.forEach { id ->
                    attachments.read(id)?.let { bytes -> _photos.update { it + (id to bytes) } }
                }
            }
        }
        // 그림 썸네일: 편지의 첫 그림 문서를 한 번씩 읽어 둔다.
        viewModelScope.launch {
            letters.observeInbox().collect { list ->
                list.filter { it.drawingCount > 0 && it.id !in docs.value }.forEach { l ->
                    val doc = firstDrawing(l.id)?.second
                    docs.update { it + (l.id to doc) }
                }
            }
        }
    }

    fun onIntent(intent: MemoryIntent) {
        when (intent) {
            is MemoryIntent.Filter -> ui.update { it.copy(filter = intent.filter) }
            is MemoryIntent.OpenDrawing -> viewModelScope.launch { openDrawing(intent.letterId) }
            MemoryIntent.CloseDrawing -> ui.update { it.copy(detail = null) }
            MemoryIntent.PinToWidget -> viewModelScope.launch {
                val d = ui.value.detail ?: return@launch
                settings.pinWidgetDrawing(d.drawingId, clock.now().toEpochMilliseconds())
                _notices.tryEmit("위젯에 다시 띄웠어요")
            }
            is MemoryIntent.AddMeetPhoto -> viewModelScope.launch {
                val e = events.get(intent.eventId) ?: return@launch
                if (e.photoIds.size >= MAX_MEET_PHOTOS) return@launch
                val id = attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, intent.jpeg)
                _photos.update { it + (id to intent.jpeg) }
                events.save(e.copy(photoIds = e.photoIds + id))
            }
            is MemoryIntent.RemoveMeetPhoto -> viewModelScope.launch {
                val e = events.get(intent.eventId) ?: return@launch
                events.save(e.copy(photoIds = e.photoIds - intent.attachmentId))
            }
            MemoryIntent.Export -> viewModelScope.launch {
                val d = ui.value.detail ?: return@launch
                val r = rasterizer ?: return@launch
                val photos = d.doc.elements.filterIsInstance<PhotoBackground>().mapNotNull { p -> attachments.read(p.attachmentId)?.let { p.attachmentId to it } }.toMap()
                runCatching { r.png(d.doc, photos) }.getOrNull()?.let { _exports.tryEmit(it) }
            }
        }
    }

    private suspend fun firstDrawing(letterId: String): Pair<String, DrawingDocument>? {
        val row = letters.drawingsOf(letterId).minByOrNull { it.orderInLetter } ?: return null
        val bytes = row.docAttachmentId?.let { attachments.read(it) } ?: return null
        val doc = (runCatching { DrawingCodec.decode(bytes) }.getOrNull() as? DrawingCodec.Result.Ok)?.document ?: return null
        return row.id to doc
    }

    private suspend fun openDrawing(letterId: String) {
        val all = letters.observeInbox().first()
        val letter = all.firstOrNull { it.id == letterId } ?: return
        val (drawingId, doc) = firstDrawing(letterId) ?: return
        val (my, partner) = names.first().let { it.first to it.second }
        val mine = letter.authorId == me
        val at = letter.visibleAt.toLocalDateTime(SEOUL)
        // 받은 그림이 이 폰 위젯에 떠 있던 시간(1시간 넘을 때만 적는다).
        val widgetHours = if (mine) 0L else (settings.widgetShownMs { clock.now().toEpochMilliseconds() }.first()[drawingId] ?: 0L) / 3_600_000
        // 답장: 이 그림 뒤 사흘 안에 상대가 보낸 첫 편지.
        val reply = all.filter { it.authorId != letter.authorId && it.visibleAt > letter.visibleAt && it.visibleAt.minus(letter.visibleAt).inWholeDays < 3 && it.hasText }
            .minByOrNull { it.visibleAt }
            ?.let { r ->
                val who = if (r.authorId == me) my else partner
                val t = r.visibleAt.toLocalDateTime(SEOUL)
                DrawingDetail.Reply(who.take(1), "$who · ${t.time.hhmm()}", r.blocks.filterIsInstance<LetterBlock.Text>().firstOrNull()?.text?.lineSequence()?.first().orEmpty())
            }
        ui.update {
            it.copy(
                detail = DrawingDetail(
                    letterId, drawingId, doc, mine,
                    dateTitle = "${at.month.ordinal + 1}월 ${at.day}일 (${weekday(at.date)})",
                    title = letter.title?.takeIf { t -> t.isNotBlank() } ?: "그림 한 장",
                    meta = (if (mine) "${my.subject()} ${partner}에게" else "${partner.subject()} ${my}에게") + " · ${at.time.hhmm()} 도착" +
                        (if (widgetHours > 0) " · 위젯에 ${widgetHours}시간 표시됨" else ""),
                    reply = reply,
                ),
            )
        }
    }

    private companion object {
        val MEETINGS = setOf(EventType.VACATION, EventType.OVERNIGHT, EventType.VISIT)
        const val MAX_MEET_PHOTOS = 3
    }
}

private fun Letter.date(): LocalDate = visibleAt.toLocalDateTime(SEOUL).date

internal fun EventType.label() = when (this) {
    EventType.VACATION -> "휴가"
    EventType.OVERNIGHT -> "외박"
    EventType.VISIT -> "면회"
    EventType.TRAINING -> "훈련"
    EventType.DUTY -> "당직"
    EventType.OTHER -> "기타"
}

/** "1박 2일" · 같은 날 시간이 있으면 "3시간". 종일 하루면 없음. */
private fun Event.durationLabel(): String? {
    val nights = start.date.daysUntil(end.date)
    if (nights > 0) return "${nights}박 ${nights + 1}일"
    val allDay = start.time == LocalTime(0, 0) && end.time.hour == 23
    if (allDay) return null
    val minutes = (end.time.toSecondOfDay() - start.time.toSecondOfDay()) / 60
    return if (minutes >= 60) "${minutes / 60}시간" else "${minutes}분"
}

internal fun LocalTime.hhmm() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

internal fun weekday(d: LocalDate) = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]

/** 이름 + 주격 조사("민준이" · "지수가"). "나" 는 "내가". */
internal fun String.subject(): String {
    if (this == "나") return "내가"
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "이" else "가"
}
