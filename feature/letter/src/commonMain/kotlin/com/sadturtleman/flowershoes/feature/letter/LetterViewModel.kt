package com.sadturtleman.flowershoes.feature.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.LetterDraftStore
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.model.Role
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/** 편지지(Figma Paper Picker). [id] 는 [Letter.paper] 에 그대로 저장한다. */
/** 편지지(화면 기능명세 2-10): 무료 다섯 + 꽃신 평생 다섯([lifetime]). 받는 사람은 이용권 없이도 그대로 보인다. */
enum class Paper(val id: String, val label: String, val lifetime: Boolean = false) {
    PLAIN("plain", "기본"),
    BLOSSOM("blossom", "벚꽃"),
    OLIVE("olive", "올리브"),
    GRID("grid", "모눈"),
    NIGHT("night", "밤하늘"),
    MANUSCRIPT("manuscript", "원고지", lifetime = true),
    KRAFT("kraft", "크라프트", lifetime = true),
    MAIL("mail", "군사우편", lifetime = true),
    NOTE("note", "줄노트", lifetime = true),
    SKY("sky", "수채 하늘", lifetime = true),
    ;

    companion object {
        fun of(id: String?): Paper = entries.firstOrNull { it.id == id } ?: PLAIN
    }
}

/** 편지함 분류(밑줄 탭). */
enum class LetterFilter(val label: String) { ALL("전체"), RECEIVED("받은 편지"), SENT("보낸 편지"), DRAWING("그림") }

/** 편지함 봉투 한 줄: 실제 편지 첫 줄 + 보낸 사람 · 날짜 + 봉투 표시([envelope]). */
data class LetterCard(
    val letter: Letter,
    val mine: Boolean,
    /** 봉투 위 첫 줄: 제목 → 본문 첫 줄 → "그림 n장". */
    val title: String,
    val drawingOnly: Boolean,
    val unread: Boolean,
    /** "민준 · 오늘 오후 9:12" · "보낸 편지 · 10.19". */
    val meta: String,
    val thumb: DrawingDocument?,
    val envelope: Envelope,
    /** 공개 날짜(서울) — 월 머리로 묶는다. */
    val date: LocalDate,
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
    /** 바깥(그림 · 사진 · 스티커 넣기, 쓰던 편지 불러오기)에서 글을 바꿀 때마다 오른다 — 입력 칸이 이때만 따라 맞춘다. */
    @kotlinx.serialization.Transient val rev: Int = 0,
    @kotlinx.serialization.Transient val sending: Boolean = false,
) {
    val canSend: Boolean get() = !sending && (plainText.isNotBlank() || drawings.isNotEmpty() || photos.isNotEmpty())
    val canAddDrawing: Boolean get() = drawings.size + photos.size < Letter.MAX_ATTACHMENTS
    val canAddPhoto: Boolean get() = drawings.size + photos.size < Letter.MAX_ATTACHMENTS
    val isEmpty: Boolean get() = title.isBlank() && plainText.isBlank() && drawings.isEmpty() && photos.isEmpty()

    companion object {
        const val MAX_CHARS = 1000
    }
}

/** 한 줄 알림. [openLetterId] 가 있으면 "보기" 로 그 편지를 연다. */
data class LetterToast(val message: String, val openLetterId: String? = null, val kind: Kind = Kind.DEFAULT) {
    enum class Kind { DEFAULT, SUCCESS, ERROR }
}

/**
 * 편지 쓰기를 끝낸 뒤(Figma 편지 쓰기 · 보냄). 바로 보낸 편지는 봉투 + "민준에게 보냈어요" 화면, 예약은 알림만 남기고 닫는다.
 */
sealed interface ComposeDone {
    /**
     * @param line 봉투 위 첫 줄.
     * @param meta "지수 · 오늘 오후 10:03".
     * @param stamp 우표 글자(D-day). 혼자 모드면 "대기".
     * @param queued 혼자 모드 — 연결하면 한꺼번에 간다. [queuedCount] 는 그때 갈 편지 수.
     */
    data class Sent(val line: String, val meta: String, val stamp: String, val partner: String, val queued: Boolean = false, val queuedCount: Int = 0) : ComposeDone

    data object Scheduled : ComposeDone
}

/** 읽는 중인 편지. 그림 문서는 첨부에서 읽어 둔다. */
data class OpenLetter(
    val letter: Letter,
    val mine: Boolean,
    /** 글 없는 그림 한 장(그림만 보내기) — 리플레이 화면으로 연다. */
    val drawingOnly: Boolean = false,
    /** "민준이 그린 그림" · "내가 그린 그림"(Figma 받은 그림). */
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
    /** 위 막대: "민준이 보낸 편지" · "10월 28일 (수) 오후 9:12"(잠긴 편지는 "예약 편지"). */
    val title: String = "",
    val subtitle: String = "",
    /** 보낸 사람 역할 — 편지지 위 소인 문구("군사우편" · "꽃신 우편"). */
    val senderRole: Role = Role.GOMSIN,
    /** 소인 날짜 "2026.10.28". */
    val postmark: String = "",
    /** 그림 id → 폴라로이드 아래 손글씨. */
    val captions: Map<String, String> = emptyMap(),
    /** 이 편지에 고마워를 보냈다("고마워를 보냈어요"). */
    val thanked: Boolean = false,
    /** 공개 전 예약 편지 — "아직 못 열어요". 내용은 열리기 전까지 읽지 않는다. */
    val locked: Boolean = false,
    /** 잠긴 편지가 열리는 때(서울). */
    val opensAt: LocalDateTime? = null,
    /** 잠긴 편지 봉투: 첫 줄 · "민준 · 11월 1일 오전 9시 도착" · 우표. */
    val envelopeLine: String = "",
    val envelopeMeta: String = "",
    val stamp: String = "",
)

data class LetterState(
    /** 편지함을 아직 읽는 중(Skeleton). */
    val loading: Boolean = true,
    val inbox: List<LetterCard> = emptyList(),
    /** 오늘(서울) — 월 머리. */
    val today: LocalDate = LocalDate(1970, 1, 1),
    val filter: LetterFilter = LetterFilter.ALL,
    val draft: ComposeDraft = ComposeDraft(),
    /** 편지 쓰기 › 붙인 그림 썸네일. */
    val draftThumbs: Map<String, DrawingDocument> = emptyMap(),
    /** 편지 쓰기 › 붙인 사진 원본. */
    val draftPhotos: Map<String, ByteArray> = emptyMap(),
    val open: OpenLetter? = null,
    val partnerName: String = "연인",
    /** 쓰던 편지(이 기기에만), 마지막으로 고친 것부터. 지금 쓰는 편지도 저장되면 들어 있다. */
    val drafts: List<DraftSummary> = emptyList(),
    /** 지금 쓰는 편지의 id([drafts] 에서 빼고 "이어 쓸까요?" 를 묻는다). */
    val draftId: String = "",
    /** 편지 쓰기를 새로 열었고 아직 아무것도 고르지 않았다 — 쓰던 편지가 있으면 "이어 쓸까요?" 시트. */
    val resumeOffer: Boolean = false,
    /** 혼자 모드(연결 전): 내가 쓴 편지는 내 폰에 쌓였다가 연결하는 순간 한꺼번에 간다("보낼 편지 N통"). */
    val solo: Boolean = false,
    /** 편지 쓰기를 끝냈다(보냄 화면 · 예약 뒤 닫기). */
    val done: ComposeDone? = null,
) {
    /** "이어 쓸까요?" 시트에 보일 쓰던 편지. 비어 있으면 시트를 띄우지 않는다. */
    val resumable: List<DraftSummary>
        get() = if (resumeOffer) drafts.filter { it.id != draftId } else emptyList()

    val visibleInbox: List<LetterCard>
        get() = when (filter) {
            LetterFilter.ALL -> inbox
            LetterFilter.RECEIVED -> inbox.filter { !it.mine }
            LetterFilter.SENT -> inbox.filter { it.mine }
            LetterFilter.DRAWING -> inbox.filter { it.letter.drawingCount > 0 }
        }

    /** 보이는 편지를 달마다 묶은 것("10월" → 봉투들). 혼자 모드는 "보낼 편지 N통" 한 묶음. */
    val months: List<Pair<String, List<LetterCard>>>
        get() = when {
            solo && visibleInbox.isNotEmpty() -> listOf("보낼 편지 ${visibleInbox.size}통" to visibleInbox)
            else -> groupByMonth(visibleInbox, today) { it.date }
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

    /** 편지 쓰기를 새로 연다(쓰기 메뉴 · 홈 · 답장). 쓰던 편지가 있으면 "이어 쓸까요?" 를 묻는다. */
    data object NewCompose : LetterIntent
    /** 쓰던 편지 [id] 를 이어 쓴다("이어 쓸까요?" 시트 · 쓰던 편지 목록). */
    data class ResumeDraft(val id: String) : LetterIntent
    /** "이어 쓸까요?" › 새로 쓰기 · 닫기. */
    data object DismissResume : LetterIntent
    /** 편지 쓰기 화면을 나간다 — 쓰던 글을 바로 저장한다. */
    data object LeaveCompose : LetterIntent
    /** 편지 쓰기 › 임시저장: 기다리지 않고 저장하고 "임시저장했어요". */
    data object SaveDraft : LetterIntent
    /** 보냄 화면을 닫았다(편지함으로). */
    data object DismissDone : LetterIntent
    /** 쓰던 편지 목록 › 지우기. */
    data class DeleteDraft(val id: String) : LetterIntent
    /** 받은 편지 › 고마워: 짧은 답장을 바로 보낸다. */
    data object Thanks : LetterIntent
    /** 아직 못 여는 편지 › 열리면 알려 주세요: 공개 시각 알림은 이미 잡혀 있다(NotificationPlanner) — 닫고 알린다. */
    data object NotifyWhenOpen : LetterIntent
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
    private val profile: ProfileRepository,
    draftStore: LetterDraftStore,
    private val clock: Clock = Clock.System,
    private val bizLogger: BizLogger,
) : ViewModel() {

    private val me = profile.myUserId()
    private val keeper = DraftKeeper(draftStore, viewModelScope, clock)
    private val ui = MutableStateFlow(LetterState(draftId = keeper.currentId))
    private val thumbs = MutableStateFlow<Map<String, DrawingDocument?>>(emptyMap())

    private val _toasts = MutableSharedFlow<LetterToast>(replay = 1, extraBufferCapacity = 4)
    /** "고마워를 보냈어요" 같은 한 줄 알림. 화면이 바뀌는 사이에 나와도 놓치지 않게 마지막 하나는 남긴다. */
    val toasts: SharedFlow<LetterToast> = _toasts

    private fun toast(message: String, openLetterId: String? = null, kind: LetterToast.Kind = LetterToast.Kind.DEFAULT) {
        _toasts.tryEmit(LetterToast(message, openLetterId, kind))
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

    /** 사람마다 역할(편지지 위 소인). */
    private val roles = profile.observeUsers().map { users ->
        users.mapNotNull { u -> Role.entries.firstOrNull { it.name == u.role }?.let { u.id to it } }.toMap()
    }

    /** 군인의 전역일(우표 D-day). 복무 정보 전이면 null. */
    private val discharge = profile.observeTimelines().map { it.firstOrNull()?.second?.dischargeDate }

    /** 연결 전(혼자 모드) — 내가 쓴 편지는 아직 내 폰에만 있다. */
    private val solo = profile.observeCouple().map { it?.partnerUserId.isNullOrBlank() }

    val state: StateFlow<LetterState> = combine(
        ui, letters.observeInbox(), letters.observeReadLetterIds(), combine(names, discharge, solo, ::Triple), thumbs,
    ) { s, list, read, (people, dischargeDate, alone), th ->
        val (my, partner) = people
        val now = clock.now().toLocalDateTime(SEOUL)
        val cards = list.map { l ->
            val mine = l.authorId == me
            val unread = !mine && l.id !in read
            val queued = mine && alone
            val at = l.visibleAt.toLocalDateTime(SEOUL)
            LetterCard(
                letter = l,
                mine = mine,
                title = l.firstLine(),
                drawingOnly = !l.hasText && l.drawingCount > 0,
                unread = unread,
                meta = if (queued) queuedMeta(my, at) else envelopeMeta(if (mine) my else partner, mine, at, now),
                thumb = th[l.id],
                envelope = envelopeOf(mine, unread, l.drawingCount > 0, at.date, dischargeDate, queued),
                date = at.date,
            )
        }
        s.copy(loading = false, inbox = cards, today = now.date, partnerName = partner, solo = alone)
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
        viewModelScope.launch { keeper.drafts.collect { list -> ui.update { it.copy(drafts = list) } } }
    }

    /** 편지 쓰기의 글 · 편지지 · 첨부 · 예약을 바꾼다. 멈추고 2초 뒤에 쓰던 편지로 저장된다. */
    private fun editDraft(change: (LetterState) -> LetterState) {
        ui.update { change(it).copy(resumeOffer = false) }
        keeper.edited(ui.value.draft)
    }

    fun onIntent(intent: LetterIntent) {
        when (intent) {
            is LetterIntent.Filter -> ui.update { it.copy(filter = intent.filter) }
            is LetterIntent.EditTitle -> editDraft { it.copy(draft = it.draft.copy(title = intent.title.take(40))) }
            is LetterIntent.EditText -> editDraft {
                val d = it.draft.editText(intent.text, intent.cursor)
                it.copy(draft = if (intent.external) d.copy(rev = d.rev + 1) else d)
            }
            is LetterIntent.RemoveObject -> editDraft { it.copy(draft = it.draft.removeObjectAt(intent.index).let { d -> d.copy(rev = d.rev + 1) }) }
            is LetterIntent.ChoosePaper -> editDraft { it.copy(draft = it.draft.copy(paper = intent.paper.id)) }
            is LetterIntent.AddDrawing -> {
                editDraft {
                    if (!it.draft.canAddDrawing) it
                    // 그림은 커서 자리에 한 글자로 들어간다(Figma "그림 · 사진 = 글자").
                    else it.copy(draft = it.draft.copy(drawings = it.draft.drawings + intent.meta.copy(orderInLetter = it.draft.drawings.size)).insertObject(drawingKey(intent.meta.id)))
                }
                loadDraftThumb(intent.meta)
            }
            is LetterIntent.AddPhoto -> viewModelScope.launch {
                if (!ui.value.draft.canAddPhoto) return@launch
                val id = attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, intent.jpeg)
                editDraft { it.copy(draft = it.draft.copy(photos = it.draft.photos + id).insertObject(photoKey(id)), draftPhotos = it.draftPhotos + (id to intent.jpeg)) }
            }
            is LetterIntent.RemovePhoto -> editDraft {
                it.copy(draft = it.draft.removeObjectAt(it.draft.objects.indexOf(photoKey(intent.attachmentId))), draftPhotos = it.draftPhotos - intent.attachmentId)
            }
            is LetterIntent.ChooseFont -> editDraft { it.copy(draft = it.draft.copy(font = intent.font)) }
            is LetterIntent.RemoveDrawing -> editDraft {
                it.copy(draft = it.draft.removeObjectAt(it.draft.objects.indexOf(drawingKey(intent.id))))
            }
            is LetterIntent.Schedule -> editDraft { it.copy(draft = it.draft.copy(revealAt = intent.at)) }
            LetterIntent.Send -> send()
            LetterIntent.NewCompose -> {
                keeper.flush(ui.value.draft)
                keeper.startNew()
                ui.update { it.freshDraft().copy(resumeOffer = true, done = null) }
            }
            is LetterIntent.ResumeDraft -> resume(intent.id)
            LetterIntent.DismissResume -> ui.update { it.copy(resumeOffer = false) }
            // 시트는 그대로 둔다 — 앱을 잠깐 내렸다 와도 "이어 쓸까요?" 가 남는다.
            LetterIntent.LeaveCompose -> keeper.flush(ui.value.draft)
            LetterIntent.SaveDraft -> {
                val d = ui.value.draft
                if (d.isEmpty || d.sending) return
                keeper.flush(d)
                toast("임시저장했어요", kind = LetterToast.Kind.SUCCESS)
            }
            LetterIntent.DismissDone -> ui.update { it.copy(done = null) }
            is LetterIntent.DeleteDraft -> {
                keeper.delete(intent.id)
                if (intent.id == ui.value.draftId) ui.update { it.freshDraft() }
            }
            LetterIntent.Thanks -> {
                val open = ui.value.open?.takeIf { !it.mine && !it.thanked && !it.locked } ?: return
                // 두 번 보내지 않게 버튼부터 "고마워를 보냈어요"로. 보내지 못하면 되돌린다.
                fun thanked(on: Boolean) = ui.update { s -> s.copy(open = s.open?.let { if (it.letter.id == open.letter.id) it.copy(thanked = on) else it }) }
                thanked(true)
                viewModelScope.launch {
                    try {
                        letters.send(letters.newLetter(listOf(LetterBlock.Text("고마워 💗")), paper = Paper.BLOSSOM.id), emptyList())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        thanked(false)
                        toast("고마워를 보내지 못했어요", kind = LetterToast.Kind.ERROR)
                        return@launch
                    }
                    // 하트: 이 편지에 고마워를 눌렀다(추억 타임라인의 ♥ 수). 한 사람에 한 번. 답장은 이미 갔으니 기록이 실패해도 되돌리지 않는다.
                    try {
                        letters.markRead(ReceiptEntity.HEART, open.letter.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                    bizLogger.record(BizView.LETTER_READ, BizEvent.LetterSent(paper = Paper.BLOSSOM.id, font = com.sadturtleman.flowershoes.core.designsystem.KkLetterFont.SANS.id, scheduled = false, drawings = 0, photos = 0, reply = true))
                    toast("${names.first().second}에게 고마워를 보냈어요")
                }
            }
            LetterIntent.NotifyWhenOpen -> {
                ui.update { it.copy(open = null) }
                toast("열리면 알려 드릴게요")
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
                bizLogger.record(BizView.DRAWING_SEND, BizEvent.DrawingSent(scheduled = intent.revealAt != null, withCaption = !intent.caption.isNullOrBlank()))
                recordSoloQueue(BizView.DRAWING_SEND)
                val partner = state.value.partnerName
                if (intent.revealAt != null) toast("${opensAt(intent.revealAt)}에 ${partner}에게 도착해요")
                else toast("${partner}에게 그림을 보냈어요", openLetterId = letter.id)
            }
            is LetterIntent.Delete -> viewModelScope.launch {
                letters.delete(intent.letterId)
                ui.update { it.copy(open = null) }
            }
        }
    }

    /** 쓰던 편지를 편지 쓰기 화면에 펼친다. 붙인 그림 · 사진은 첨부에서 다시 읽는다. */
    private fun resume(id: String) {
        val leaving = ui.value.draft
        if (id != keeper.currentId) keeper.flush(leaving)
        viewModelScope.launch {
            val draft = keeper.resume(id) ?: return@launch
            ui.update { it.freshDraft().copy(draft = draft.normalized().copy(rev = it.draft.rev + 1)) }
            draft.drawings.forEach { loadDraftThumb(it) }
            draft.photos.forEach { pid -> attachments.read(pid)?.let { bytes -> ui.update { s -> s.copy(draftPhotos = s.draftPhotos + (pid to bytes)) } } }
        }
    }

    /** 빈 편지지. 지금 쓰는 편지 id 는 [keeper] 를 따른다. */
    private fun LetterState.freshDraft() = copy(
        draft = ComposeDraft(rev = draft.rev + 1), draftThumbs = emptyMap(), draftPhotos = emptyMap(),
        draftId = keeper.currentId, resumeOffer = false,
    )

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

    /**
     * 보내는 중 → 보냄(봉투 화면) · 예약(알림 + 닫기). 보내지 못하면 쓰던 편지를 그대로 두고 "보내지 못했어요".
     */
    private fun send() {
        val d = ui.value.draft
        if (!d.canSend) return
        ui.update { it.copy(draft = d.copy(sending = true), done = null) }
        val draftId = keeper.currentId
        viewModelScope.launch {
            // 글 · 그림 · 사진의 순서: 편지 쓰기 화면의 글 속 자리 그대로.
            val blocks = d.toBlocks()
            val visibleAt = d.revealAt?.toInstant(SEOUL)?.takeIf { it > clock.now() } ?: clock.now()
            val font = d.font.takeIf { it != com.sadturtleman.flowershoes.core.designsystem.KkLetterFont.SANS.id }
            val letter = letters.newLetter(blocks, paper = d.paper, title = d.title.trim().ifBlank { null }, visibleAt = visibleAt, font = font)
            try {
                letters.send(letter, d.drawings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (keeper.currentId == draftId) {
                    ui.update { s -> s.copy(draft = s.draft.copy(sending = false)) }
                    // 보내는 동안엔 저장하지 않았다 — 쓰던 편지로 다시 남긴다.
                    keeper.edited(ui.value.draft)
                }
                toast("보내지 못했어요", kind = LetterToast.Kind.ERROR)
                return@launch
            }
            bizLogger.record(BizView.LETTER_COMPOSE, BizEvent.LetterSent(paper = d.paper, font = d.font, scheduled = d.revealAt != null, drawings = d.drawings.size, photos = d.photos.size, reply = false))
            recordSoloQueue(BizView.LETTER_COMPOSE)
            // 보낸 편지는 쓰던 편지 목록에서 빠진다. 아직 이 편지를 보고 있으면 빈 편지지로.
            val stillHere = keeper.currentId == draftId
            keeper.sent(draftId)
            // 보내는 사이 화면을 나갔거나 다른 편지를 열었으면 보냄 화면은 띄우지 않는다(다음 편지 쓰기를 가로채지 않게).
            if (stillHere) {
                val done = if (d.revealAt != null) ComposeDone.Scheduled else sentNote(letter)
                ui.update { it.freshDraft().copy(done = done) }
            }
            if (d.revealAt != null) toast("${opensAt(d.revealAt)}에 도착해요")
        }
    }

    /** 보냄 화면의 봉투: 첫 줄 · "지수 · 오늘 오후 10:03" · 우표(혼자 모드면 "대기"). */
    private suspend fun sentNote(letter: Letter): ComposeDone.Sent {
        val (my, partner) = names.first()
        val at = letter.visibleAt.toLocalDateTime(SEOUL)
        val alone = solo.first()
        return ComposeDone.Sent(
            line = letter.firstLine(),
            // 받는 사람 편지함에 보일 봉투 그대로: 보낸 사람(나) 이름 · 시각.
            meta = envelopeMeta(my, mine = false, at = at, now = clock.now().toLocalDateTime(SEOUL)),
            stamp = if (alone) "대기" else stampText(discharge.first(), at.date),
            partner = partner,
            queued = alone,
            queuedCount = if (alone) letters.countMine() else 0,
        )
    }

    /** 혼자 모드(연결 전)면 내 폰에 쌓인 내 편지 수를 남긴다 — 연결하는 순간 한꺼번에 전달되는 양이다. */
    private suspend fun recordSoloQueue(view: String) {
        if (!profile.observeCouple().first()?.partnerUserId.isNullOrBlank()) return
        bizLogger.record(view, BizEvent.SoloLettersQueued(count = letters.countMine()))
    }

    private fun open(letterId: String) {
        viewModelScope.launch {
            // 알림으로 처음 실행하면 화면의 stateIn은 아직 빈 초기값일 수 있어 저장소에서 바로 읽는다.
            val letter = letters.find(letterId) ?: run {
                ui.update { it.copy(open = null) }
                toast("편지를 찾을 수 없어요")
                return@launch
            }
            val mine = letter.authorId == me
            val (my, partner) = names.first()
            val at = letter.visibleAt.toLocalDateTime(SEOUL)
            val senderRole = roleOf(letter.authorId, roles.first())
            if (!mine && letter.visibleAt > clock.now()) {
                // 아직 못 여는 예약 편지: 봉투와 열리는 때만. 글 · 그림 · 사진은 열린 뒤에 읽는다.
                ui.update {
                    it.copy(
                        open = OpenLetter(
                            letter, mine, meta = "", drawings = emptyMap(), firstView = emptySet(),
                            title = readTitle(false, partner), subtitle = "예약 편지", senderRole = senderRole,
                            locked = true, opensAt = at,
                            envelopeLine = letter.firstLine(), envelopeMeta = "$partner · ${opensAt(at)} 도착",
                            stamp = stampText(discharge.first(), at.date),
                        ),
                    )
                }
                return@launch
            }
            val rows = letters.drawingsOf(letterId)
            val docs = rows.mapNotNull { row -> row.docAttachmentId?.let { decode(it) }?.let { row.id to it } }.toMap()
            val first = rows.filter { !mine && it.replayEnabled && letters.isFirstView(it.id) }.map { it.id }.toSet()
            val photos = letter.blocks.filterIsInstance<LetterBlock.Photo>().mapNotNull { b -> attachments.read(b.attachmentId)?.let { b.attachmentId to it } }.toMap()
            val thanked = !mine && letters.thanked(letterId)
            val meta = (if (mine) my else partner) + " · ${at.short()} (${weekday(at.date)})"
            val drawingOnly = !letter.hasText && letter.drawingCount == 1
            ui.update {
                it.copy(
                    open = OpenLetter(
                        letter, mine,
                        drawingOnly = drawingOnly,
                        drawingTitle = if (mine) "내가 그린 그림" else "${partner.withSubject()} 그린 그림",
                        sentAt = "${at.month.ordinal + 1}월 ${at.day}일 ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}",
                        meta = meta, drawings = docs, firstView = first, photos = photos,
                        title = readTitle(mine, partner), subtitle = readWhen(at), senderRole = senderRole,
                        postmark = postmarkDate(at.date),
                        captions = rows.mapNotNull { r -> r.caption?.takeIf { it.isNotBlank() }?.let { r.id to it } }.toMap(),
                        thanked = thanked,
                    ),
                )
            }
            if (!mine) letters.markRead(ReceiptEntity.LETTER, letterId)
        }
    }

    /** 보낸 사람 역할. 그 사람 프로필을 아직 받지 못했으면 다른 사람 역할의 반대로 본다(커플은 곰신 · 군인 한 명씩). */
    private fun roleOf(userId: String, roles: Map<String, Role>): Role =
        roles[userId] ?: roles.entries.firstOrNull { it.key != userId }?.value?.let { if (it == Role.SOLDIER) Role.GOMSIN else Role.SOLDIER } ?: Role.GOMSIN
}

/** 봉투 위 첫 줄: 제목 → 본문 첫 줄 → "그림 n장". */
private fun Letter.firstLine(): String {
    val text = blocks.filterIsInstance<LetterBlock.Text>().firstOrNull()?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty()
    return title?.takeIf { it.isNotBlank() } ?: text.ifBlank { "그림 ${drawingCount}장" }
}

private fun LocalDateTime.short() = "${month.ordinal + 1}.$day"

/** 이름 + 주격 조사: 받침이 있으면 "민준이", 없으면 "지수가". 한글이 아니면 "가". */
internal fun String.withSubject(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "이" else "가"
}

/** 이름 + "과/와": 받침이 있으면 "민준과", 없으면 "지수와". 한글이 아니면 "와". */
internal fun String.withAnd(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "과" else "와"
}

internal fun weekday(d: LocalDate) = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]
