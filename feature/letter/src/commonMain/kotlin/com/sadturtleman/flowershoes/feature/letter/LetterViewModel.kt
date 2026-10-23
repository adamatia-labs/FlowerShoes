package com.sadturtleman.flowershoes.feature.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import kotlin.time.Clock
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 편지지(Figma Paper Picker). [id] 는 [Letter.paper] 에 그대로 저장한다. */
enum class Paper(val id: String, val label: String) {
    PLAIN("plain", "기본"),
    BLOSSOM("blossom", "벚꽃"),
    OLIVE("olive", "올리브"),
    GRID("grid", "모눈"),
    NIGHT("night", "밤하늘"),
    ;

    companion object {
        fun of(id: String?): Paper = entries.firstOrNull { it.id == id } ?: PLAIN
    }
}

/** 편지함 분류(Segment). */
enum class LetterFilter(val label: String) { ALL("전체"), LETTER("편지"), DRAWING("그림") }

/** 편지함 한 칸. 글 없는 편지(그림만)는 그림을, 글 편지는 줄 그림을 썸네일로 보여 준다. */
data class LetterCard(
    val letter: Letter,
    val mine: Boolean,
    val title: String,
    val drawingOnly: Boolean,
    val unread: Boolean,
    /** "10.21 · 나 → 연인" / "10.19 · 연인". */
    val meta: String,
    val thumb: DrawingDocument?,
)

@Serializable
data class ComposeDraft(
    val title: String = "",
    val text: String = "",
    val paper: String = Paper.BLOSSOM.id,
    val drawings: List<DrawingMeta> = emptyList(),
    /** 예약 공개 시각(서울). null 이면 바로. */
    val revealAt: LocalDateTime? = null,
    /** 붙인 사진(첨부 id, 편지 쓰기 › 사진). */
    val photos: List<String> = emptyList(),
    /** 글꼴 id(편지 쓰기 › 글꼴). */
    val font: String = "sans",
    /** 글 속 그림 · 사진 순서("d:id" · "p:id"). k 번째가 글의 k 번째 자리 글자([OBJ]). */
    val objects: List<String> = emptyList(),
    /** 본문 커서(그림 · 사진을 넣을 자리). */
    @kotlinx.serialization.Transient val cursor: Int = 0,
    /** 바깥(그림 · 사진 · 스티커 넣기, 임시저장 불러오기)에서 글을 바꿀 때마다 오른다 — 입력 칸이 이때만 따라 맞춘다. */
    @kotlinx.serialization.Transient val rev: Int = 0,
    @kotlinx.serialization.Transient val sending: Boolean = false,
) {
    val canSend: Boolean get() = !sending && (plainText.isNotBlank() || drawings.isNotEmpty() || photos.isNotEmpty())
    val canAddDrawing: Boolean get() = drawings.size < Letter.MAX_DRAWINGS
    val canAddPhoto: Boolean get() = photos.size < Letter.MAX_PHOTOS
    val isEmpty: Boolean get() = title.isBlank() && plainText.isBlank() && drawings.isEmpty() && photos.isEmpty()

    companion object {
        const val MAX_CHARS = 1000
    }
}

/** 한 줄 알림. [openLetterId] 가 있으면 "보기" 로 그 편지를 연다. */
data class LetterToast(val message: String, val openLetterId: String? = null)

/** 읽는 중인 편지. 그림 문서는 첨부에서 읽어 둔다. */
data class OpenLetter(
    val letter: Letter,
    val mine: Boolean,
    /** 글 없는 그림 한 장(그림만 보내기) — 리플레이 화면으로 연다. */
    val drawingOnly: Boolean = false,
    /** "민준이 보낸 그림" · "내가 보낸 그림". */
    val drawingTitle: String = "",
    /** "10월 21일 21:05". */
    val sentAt: String = "",
    /** "연인 → 나 · 10.21 (화)". */
    val meta: String,
    val drawings: Map<String, DrawingDocument>,
    /** 처음 보는 그림 — 화면에 들어오면 자동 재생한다. */
    val firstView: Set<String>,
    /** 사진 블록의 원본(JPEG). 아직 받는 중이면 없다. */
    val photos: Map<String, ByteArray> = emptyMap(),
)

data class LetterState(
    val inbox: List<LetterCard> = emptyList(),
    val filter: LetterFilter = LetterFilter.ALL,
    val draft: ComposeDraft = ComposeDraft(),
    /** 편지 쓰기 › 붙인 그림 썸네일. */
    val draftThumbs: Map<String, DrawingDocument> = emptyMap(),
    /** 편지 쓰기 › 붙인 사진 원본. */
    val draftPhotos: Map<String, ByteArray> = emptyMap(),
    val open: OpenLetter? = null,
    val partnerName: String = "연인",
) {
    val visibleInbox: List<LetterCard>
        get() = when (filter) {
            LetterFilter.ALL -> inbox
            LetterFilter.LETTER -> inbox.filter { it.letter.hasText }
            LetterFilter.DRAWING -> inbox.filter { it.letter.drawingCount > 0 }
        }
}

sealed interface LetterIntent {
    data class Filter(val filter: LetterFilter) : LetterIntent
    data class EditTitle(val title: String) : LetterIntent
    data class EditText(val text: String, val cursor: Int = text.length, val external: Boolean = false) : LetterIntent

    /** 글 속 k 번째 그림 · 사진 빼기. */
    data class RemoveObject(val index: Int) : LetterIntent
    data class ChoosePaper(val paper: Paper) : LetterIntent
    data class AddDrawing(val meta: DrawingMeta) : LetterIntent
    data class RemoveDrawing(val id: String) : LetterIntent
    /** 편지 쓰기 › 사진: 고른 사진(줄인 JPEG)을 첨부로 두고 붙인다. */
    data class AddPhoto(val jpeg: ByteArray) : LetterIntent
    data class RemovePhoto(val attachmentId: String) : LetterIntent
    data class ChooseFont(val font: String) : LetterIntent
    data class Schedule(val at: LocalDateTime?) : LetterIntent
    data object Send : LetterIntent
    data object SaveDraft : LetterIntent
    data object ResetDraft : LetterIntent
    /** 받은 편지 › 고마워: 짧은 답장을 바로 보낸다. */
    data object Thanks : LetterIntent
    data class Open(val letterId: String) : LetterIntent
    data class DrawingSeen(val drawingId: String) : LetterIntent
    data object Close : LetterIntent
    data class Delete(val letterId: String) : LetterIntent

    /** 그림만 보내기: 그림 한 장 + 한 줄(선택)로 글 없는 편지를 바로 보낸다. 쓰던 편지(임시저장)는 건드리지 않는다. */
    data class SendDrawing(val meta: DrawingMeta, val caption: String?, val revealAt: LocalDateTime?) : LetterIntent
}

class LetterViewModel(
    private val letters: LetterRepository,
    private val attachments: AttachmentStore,
    profile: ProfileRepository,
    private val settings: DeviceSettings,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val me = profile.myUserId()
    private val ui = MutableStateFlow(LetterState())
    private val thumbs = MutableStateFlow<Map<String, DrawingDocument?>>(emptyMap())
    private val json = Json { ignoreUnknownKeys = true }

    private val _toasts = MutableSharedFlow<LetterToast>(replay = 1, extraBufferCapacity = 4)
    /** "임시저장했어요" · "고마워를 보냈어요" 같은 한 줄 알림. 화면이 바뀌는 사이에 나와도 놓치지 않게 마지막 하나는 남긴다. */
    val toasts: SharedFlow<LetterToast> = _toasts

    private fun toast(message: String, openLetterId: String? = null) {
        _toasts.tryEmit(LetterToast(message, openLetterId))
    }

    /** 보여 준 알림은 다시 띄우지 않는다. */
    fun toastShown() {
        _toasts.resetReplayCache()
    }

    private val names = combine(profile.observeCouple(), profile.observeUsers()) { couple, users ->
        val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
        val my = users.firstOrNull { it.id == me }?.nickname?.takeIf { it.isNotBlank() } ?: "나"
        val partner = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
        my to partner
    }

    val state: StateFlow<LetterState> = combine(ui, letters.observeInbox(), letters.observeReadLetterIds(), names, thumbs) { s, list, read, (my, partner), th ->
        val cards = list.map { l ->
            val mine = l.authorId == me
            val firstLine = l.blocks.filterIsInstance<LetterBlock.Text>().firstOrNull()?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty()
            LetterCard(
                letter = l,
                mine = mine,
                title = l.title?.takeIf { it.isNotBlank() } ?: firstLine.ifBlank { "그림 ${l.drawingCount}장" },
                drawingOnly = !l.hasText && l.drawingCount > 0,
                unread = !mine && l.id !in read,
                meta = "${l.visibleAt.toLocalDateTime(SEOUL).short()} · " + if (mine) my else partner,
                thumb = th[l.id],
            )
        }
        s.copy(inbox = cards, partnerName = partner)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LetterState())

    init {
        // 편지함 썸네일: 그림이 든 편지의 첫 그림을 한 번씩 읽어 둔다.
        viewModelScope.launch {
            letters.observeInbox().collect { list ->
                list.filter { it.drawingCount > 0 && it.id !in thumbs.value }.forEach { l ->
                    val first = letters.drawingsOf(l.id).minByOrNull { it.orderInLetter }
                    val doc = first?.docAttachmentId?.let { decode(it) }
                    thumbs.update { it + (l.id to doc) }
                }
            }
        }
        // 임시저장한 편지가 있으면 이어 쓴다.
        viewModelScope.launch {
            val saved = settings.letterDraft.first() ?: return@launch
            val draft = runCatching { json.decodeFromString(ComposeDraft.serializer(), saved) }.getOrNull() ?: return@launch
            ui.update { it.copy(draft = draft.normalized().copy(rev = it.draft.rev + 1)) }
            draft.drawings.forEach { loadDraftThumb(it) }
            draft.photos.forEach { id -> attachments.read(id)?.let { bytes -> ui.update { s -> s.copy(draftPhotos = s.draftPhotos + (id to bytes)) } } }
        }
    }

    fun onIntent(intent: LetterIntent) {
        when (intent) {
            is LetterIntent.Filter -> ui.update { it.copy(filter = intent.filter) }
            is LetterIntent.EditTitle -> ui.update { it.copy(draft = it.draft.copy(title = intent.title.take(40))) }
            is LetterIntent.EditText -> ui.update {
                val d = it.draft.editText(intent.text, intent.cursor)
                it.copy(draft = if (intent.external) d.copy(rev = d.rev + 1) else d)
            }
            is LetterIntent.RemoveObject -> ui.update { it.copy(draft = it.draft.removeObjectAt(intent.index).let { d -> d.copy(rev = d.rev + 1) }) }
            is LetterIntent.ChoosePaper -> ui.update { it.copy(draft = it.draft.copy(paper = intent.paper.id)) }
            is LetterIntent.AddDrawing -> {
                ui.update {
                    if (!it.draft.canAddDrawing) it
                    // 그림은 커서 자리에 한 글자로 들어간다(Figma "그림 · 사진 = 글자").
                    else it.copy(draft = it.draft.copy(drawings = it.draft.drawings + intent.meta.copy(orderInLetter = it.draft.drawings.size)).insertObject(drawingKey(intent.meta.id)))
                }
                loadDraftThumb(intent.meta)
            }
            is LetterIntent.AddPhoto -> viewModelScope.launch {
                if (!ui.value.draft.canAddPhoto) return@launch
                val id = attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, intent.jpeg)
                ui.update { it.copy(draft = it.draft.copy(photos = it.draft.photos + id).insertObject(photoKey(id)), draftPhotos = it.draftPhotos + (id to intent.jpeg)) }
            }
            is LetterIntent.RemovePhoto -> ui.update {
                it.copy(draft = it.draft.removeObjectAt(it.draft.objects.indexOf(photoKey(intent.attachmentId))), draftPhotos = it.draftPhotos - intent.attachmentId)
            }
            is LetterIntent.ChooseFont -> ui.update { it.copy(draft = it.draft.copy(font = intent.font)) }
            is LetterIntent.RemoveDrawing -> ui.update {
                it.copy(draft = it.draft.removeObjectAt(it.draft.objects.indexOf(drawingKey(intent.id))))
            }
            is LetterIntent.Schedule -> ui.update { it.copy(draft = it.draft.copy(revealAt = intent.at)) }
            LetterIntent.Send -> send()
            LetterIntent.SaveDraft -> viewModelScope.launch {
                val d = ui.value.draft
                settings.saveLetterDraft(if (d.isEmpty) null else json.encodeToString(ComposeDraft.serializer(), d))
                toast("임시저장했어요")
            }
            LetterIntent.ResetDraft -> ui.update { it.copy(draft = ComposeDraft(), draftThumbs = emptyMap(), draftPhotos = emptyMap()) }
            LetterIntent.Thanks -> viewModelScope.launch {
                // 하트: 이 편지에 고마워를 눌렀다(추억 타임라인의 ♥ 수). 한 사람에 한 번.
                ui.value.open?.letter?.id?.let { letters.markRead(ReceiptEntity.HEART, it) }
                letters.send(letters.newLetter(listOf(LetterBlock.Text("고마워 💗")), paper = Paper.BLOSSOM.id), emptyList())
                toast("고마워를 보냈어요")
            }
            is LetterIntent.Open -> open(intent.letterId)
            is LetterIntent.DrawingSeen -> viewModelScope.launch {
                if (ui.value.open?.mine == false) letters.markRead(ReceiptEntity.DRAWING, intent.drawingId)
            }
            LetterIntent.Close -> ui.update { it.copy(open = null) }
            is LetterIntent.SendDrawing -> viewModelScope.launch {
                val visibleAt = intent.revealAt?.toInstant(SEOUL)?.takeIf { it > clock.now() } ?: clock.now()
                val letter = letters.newLetter(listOf(LetterBlock.DrawingRef(intent.meta.id)), paper = Paper.PLAIN.id, title = intent.caption, visibleAt = visibleAt)
                letters.send(letter, listOf(intent.meta))
                val partner = state.value.partnerName
                if (intent.revealAt != null) toast("${intent.revealAt.month.ordinal + 1}월 ${intent.revealAt.day}일 ${intent.revealAt.hour}시에 ${partner}에게 도착해요")
                else toast("${partner}에게 그림을 보냈어요", openLetterId = letter.id)
            }
            is LetterIntent.Delete -> viewModelScope.launch {
                letters.delete(intent.letterId)
                ui.update { it.copy(open = null) }
            }
        }
    }

    private fun loadDraftThumb(meta: DrawingMeta) {
        val id = meta.docAttachmentId ?: return
        viewModelScope.launch {
            decode(id)?.let { doc -> ui.update { it.copy(draftThumbs = it.draftThumbs + (meta.id to doc)) } }
        }
    }

    private suspend fun decode(attachmentId: String): DrawingDocument? {
        val bytes = attachments.read(attachmentId) ?: return null
        return (DrawingCodec.decode(bytes) as? DrawingCodec.Result.Ok)?.document
    }

    private fun send() {
        val d = ui.value.draft
        if (!d.canSend) return
        ui.update { it.copy(draft = d.copy(sending = true)) }
        viewModelScope.launch {
            // 글 · 그림 · 사진의 순서: 편지 쓰기 화면의 글 속 자리 그대로.
            val blocks = d.toBlocks()
            val visibleAt = d.revealAt?.toInstant(SEOUL)?.takeIf { it > clock.now() } ?: clock.now()
            val font = d.font.takeIf { it != com.sadturtleman.flowershoes.core.designsystem.KkLetterFont.SANS.id }
            letters.send(letters.newLetter(blocks, paper = d.paper, title = d.title.trim().ifBlank { null }, visibleAt = visibleAt, font = font), d.drawings)
            settings.saveLetterDraft(null)
            ui.update { it.copy(draft = ComposeDraft(), draftThumbs = emptyMap(), draftPhotos = emptyMap()) }
            if (d.revealAt != null) toast("${d.revealAt.month.ordinal + 1}월 ${d.revealAt.day}일 ${d.revealAt.hour}시에 도착해요")
        }
    }

    private fun open(letterId: String) {
        viewModelScope.launch {
            val card = state.value.inbox.firstOrNull { it.letter.id == letterId }
            val letter = card?.letter ?: return@launch
            val mine = letter.authorId == me
            val rows = letters.drawingsOf(letterId)
            val docs = rows.mapNotNull { row -> row.docAttachmentId?.let { decode(it) }?.let { row.id to it } }.toMap()
            val first = rows.filter { !mine && it.replayEnabled && letters.isFirstView(it.id) }.map { it.id }.toSet()
            val photos = letter.blocks.filterIsInstance<LetterBlock.Photo>().mapNotNull { b -> attachments.read(b.attachmentId)?.let { b.attachmentId to it } }.toMap()
            val (my, partner) = names.first()
            val at = letter.visibleAt.toLocalDateTime(SEOUL)
            val meta = (if (mine) my else partner) + " · ${at.short()} (${weekday(at.date)})"
            val drawingOnly = !letter.hasText && letter.drawingCount == 1
            ui.update {
                it.copy(
                    open = OpenLetter(
                        letter, mine,
                        drawingOnly = drawingOnly,
                        drawingTitle = if (mine) "내가 보낸 그림" else "${partner.withSubject()} 보낸 그림",
                        sentAt = "${at.month.ordinal + 1}월 ${at.day}일 ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}",
                        meta = meta, drawings = docs, firstView = first, photos = photos,
                    ),
                )
            }
            if (!mine) letters.markRead(ReceiptEntity.LETTER, letterId)
        }
    }
}


private fun LocalDateTime.short() = "${month.ordinal + 1}.$day"

/** 이름 + 주격 조사: 받침이 있으면 "민준이", 없으면 "지수가". 한글이 아니면 "가". */
internal fun String.withSubject(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "이" else "가"
}

internal fun weekday(d: LocalDate) = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]
