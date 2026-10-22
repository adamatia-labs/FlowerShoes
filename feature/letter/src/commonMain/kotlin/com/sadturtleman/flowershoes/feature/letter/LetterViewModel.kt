package com.sadturtleman.flowershoes.feature.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/** 편지함 한 줄. 글 없는 편지(그림만)는 그림을 크게 보여 준다. */
data class LetterCard(
    val letter: Letter,
    val mine: Boolean,
    val firstLine: String,
    val drawingCount: Int,
)

data class ComposeDraft(
    val text: String = "",
    val drawings: List<DrawingMeta> = emptyList(),
    /** 예약 공개일(그날 오전 9시, 서울). null 이면 바로. */
    val revealOn: LocalDate? = null,
    val sending: Boolean = false,
) {
    val canSend: Boolean get() = !sending && (text.isNotBlank() || drawings.isNotEmpty())
    val canAddDrawing: Boolean get() = drawings.size < Letter.MAX_DRAWINGS
}

/** 읽는 중인 편지. 그림 문서는 첨부에서 읽어 둔다. */
data class OpenLetter(
    val letter: Letter,
    val drawings: Map<String, DrawingDocument>,
    /** 처음 보는 그림 — 화면에 들어오면 자동 재생한다. */
    val firstView: Set<String>,
)

data class LetterState(
    val inbox: List<LetterCard> = emptyList(),
    val filterDrawings: Boolean = false,
    val draft: ComposeDraft = ComposeDraft(),
    val open: OpenLetter? = null,
) {
    val visibleInbox: List<LetterCard> get() = if (filterDrawings) inbox.filter { it.drawingCount > 0 } else inbox
}

sealed interface LetterIntent {
    data class Filter(val drawingsOnly: Boolean) : LetterIntent
    data class EditText(val text: String) : LetterIntent
    data class AddDrawing(val meta: DrawingMeta) : LetterIntent
    data class RemoveDrawing(val id: String) : LetterIntent
    data class Schedule(val on: LocalDate?) : LetterIntent
    data object Send : LetterIntent
    data object ResetDraft : LetterIntent
    data class Open(val letterId: String) : LetterIntent
    data class DrawingSeen(val drawingId: String) : LetterIntent
    data object Close : LetterIntent
    data class Delete(val letterId: String) : LetterIntent
}

class LetterViewModel(
    private val letters: LetterRepository,
    private val attachments: AttachmentStore,
    profile: ProfileRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val me = profile.myUserId()
    private val ui = MutableStateFlow(LetterState())

    val state: StateFlow<LetterState> = combine(ui, letters.observeInbox()) { s, list ->
        s.copy(
            inbox = list.map { l ->
                LetterCard(
                    letter = l,
                    mine = l.authorId == me,
                    firstLine = l.blocks.filterIsInstance<LetterBlock.Text>().firstOrNull()?.text?.lineSequence()?.firstOrNull().orEmpty(),
                    drawingCount = l.drawingCount,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LetterState())

    fun onIntent(intent: LetterIntent) {
        when (intent) {
            is LetterIntent.Filter -> ui.update { it.copy(filterDrawings = intent.drawingsOnly) }
            is LetterIntent.EditText -> ui.update { it.copy(draft = it.draft.copy(text = intent.text)) }
            is LetterIntent.AddDrawing -> ui.update {
                if (!it.draft.canAddDrawing) it
                else it.copy(draft = it.draft.copy(drawings = it.draft.drawings + intent.meta.copy(orderInLetter = it.draft.drawings.size)))
            }
            is LetterIntent.RemoveDrawing -> ui.update {
                it.copy(draft = it.draft.copy(drawings = it.draft.drawings.filterNot { d -> d.id == intent.id }.mapIndexed { i, d -> d.copy(orderInLetter = i) }))
            }
            is LetterIntent.Schedule -> ui.update { it.copy(draft = it.draft.copy(revealOn = intent.on)) }
            LetterIntent.Send -> send()
            LetterIntent.ResetDraft -> ui.update { it.copy(draft = ComposeDraft()) }
            is LetterIntent.Open -> open(intent.letterId)
            is LetterIntent.DrawingSeen -> viewModelScope.launch {
                if (ui.value.open?.letter?.authorId != me) letters.markRead(ReceiptEntity.DRAWING, intent.drawingId)
            }
            LetterIntent.Close -> ui.update { it.copy(open = null) }
            is LetterIntent.Delete -> viewModelScope.launch {
                letters.delete(intent.letterId)
                ui.update { it.copy(open = null) }
            }
        }
    }

    private fun send() {
        val d = ui.value.draft
        if (!d.canSend) return
        ui.update { it.copy(draft = d.copy(sending = true)) }
        viewModelScope.launch {
            // 글과 그림의 순서: 글 다음에 그림을 차례로(편지 쓰기 화면에서 보이는 순서 그대로).
            val blocks = buildList {
                if (d.text.isNotBlank()) add(LetterBlock.Text(d.text.trim()))
                d.drawings.forEach { add(LetterBlock.DrawingRef(it.id)) }
            }
            val visibleAt = d.revealOn?.let { LocalDateTime(it, LocalTime(9, 0)).toInstant(SEOUL) } ?: clock.now()
            letters.send(letters.newLetter(blocks, visibleAt = visibleAt), d.drawings)
            ui.update { it.copy(draft = ComposeDraft()) }
        }
    }

    private fun open(letterId: String) {
        viewModelScope.launch {
            val letter = state.value.inbox.firstOrNull { it.letter.id == letterId }?.letter ?: return@launch
            val rows = letters.drawingsOf(letterId)
            val docs = rows.mapNotNull { row ->
                val bytes = row.docAttachmentId?.let { attachments.read(it) } ?: return@mapNotNull null
                (DrawingCodec.decode(bytes) as? DrawingCodec.Result.Ok)?.let { row.id to it.document }
            }.toMap()
            val first = rows.filter { letter.authorId != me && it.replayEnabled && letters.isFirstView(it.id) }.map { it.id }.toSet()
            ui.update { it.copy(open = OpenLetter(letter, docs, first)) }
            if (letter.authorId != me) letters.markRead(ReceiptEntity.LETTER, letterId)
        }
    }
}
