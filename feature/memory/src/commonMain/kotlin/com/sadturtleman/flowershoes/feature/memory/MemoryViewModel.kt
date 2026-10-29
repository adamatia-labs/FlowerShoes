package com.sadturtleman.flowershoes.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.BookCoverRepository
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.military.promotionName
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.BookCover
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.DayLine
import com.sadturtleman.flowershoes.core.model.hasDayLine
import com.sadturtleman.flowershoes.core.model.myDayLine
import com.sadturtleman.flowershoes.core.model.partnerDayLine
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** 한 달(책의 한 장). */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, YearMonth::year, YearMonth::month)
    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    val first: LocalDate get() = LocalDate(year, month, 1)
    val length: Int get() = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day

    companion object {
        fun of(d: LocalDate) = YearMonth(d.year, d.month.ordinal + 1)
    }
}

/** 장 표지 썸네일: 그날의 사진이 있으면 사진, 없으면 그 달 첫 그림. */
sealed interface ChapterCover {
    data class Photo(val attachmentId: String) : ChapterCover
    data class Drawing(val letterId: String) : ChapterCover
}

/** 월별 장 목록 한 줄(Figma 추억 › Chapter). */
data class Chapter(
    val ym: YearMonth,
    /** 몇 번째 장(1부터). */
    val number: Int,
    val letters: Int,
    val drawings: Int,
    val meets: Int,
    val cover: ChapterCover?,
    /** 이번 달 = 쓰는 중. */
    val writing: Boolean,
    /** 입대한 달. */
    val enlisted: Boolean,
)

/** 표지 카드(우리의 책): 몇 번째 장을 쓰는 중인지 · 전체 몇 달인지. */
data class BookProgress(val current: Int, val total: Int?, val done: Boolean)

/** 한 달 달력의 일정 막대(면회 · 외박 · 휴가 · 진급). 날짜는 그 달 안으로 자른다. */
data class MonthBar(val from: Int, val to: Int, val label: String, val kind: BarKind)

enum class BarKind { VISIT, OVERNIGHT, VACATION, RANK }

/** 편지 세 형태: 그림만 · 글 사이 그림 · 글만(추억 책도 셋을 그대로 담는다). */
enum class LetterKind { DRAWING_ONLY, INLINE, TEXT }

data class MonthLetter(
    val id: String,
    val date: LocalDate,
    /** "10.19 · 민준". */
    val meta: String,
    val author: String,
    val mine: Boolean,
    val kind: LetterKind,
    /** 본문 글(그림 자리는 빼고). */
    val text: String,
    /** 글 사이 그림: 첫 그림 · 사진 앞 글과 뒤 글. */
    val before: String,
    val after: String,
    /** 첫 그림 문서(그림만 · 글 사이 그림). */
    val thumb: DrawingDocument?,
    /** 그림이 없고 사진만 있으면 첫 사진. */
    val photoId: String?,
    /** 편지 제목(월 페이지 작은 봉투). */
    val title: String? = null,
    /** 받은 편지인데 아직 안 읽음(봉인). */
    val unread: Boolean = false,
    /** 봉투 우표 글자([stampText]). */
    val stamp: String = "",
    /** 첫 그림 PNG 첨부 id(봉투 미니 폴라로이드). */
    val drawingPng: String? = null,
    /** 첫 그림 캡션(폴라로이드 손글씨). */
    val caption: String? = null,
)

/** 만남 카드 · 그날의 한 줄(보기 · 쓰기). */
data class MeetCard(
    val eventId: String,
    val date: LocalDate,
    /** 면회 · 외박 · 휴가(종류 태그). 첫 면회는 제목이 "첫 면회". */
    val tag: String,
    val kind: EventType,
    val title: String,
    /** "10.14 (화) 13:00 – 16:00" · "10.19 – 10.23 · 4박 5일". */
    val time: String,
    val photos: List<String>,
    /** (이름, 한 줄) — 나 먼저. 쓴 사람 것만(빈칸은 보여 주지 않는다). */
    val notes: List<Pair<String, String>>,
    val myNote: String,
    /** "2026.10.19 (월)" · "2026.10.19 (월) – 10.23 (금)". */
    val dateLabel: String = "",
)

data class MonthDetail(
    val ym: YearMonth,
    val number: Int,
    val letterDays: Set<Int>,
    val drawingDays: Set<Int>,
    val bars: List<MonthBar>,
    val letters: List<MonthLetter>,
    val meets: List<MeetCard>,
    val drawings: Int,
    /** 그 달의 그날의 한 줄(추억 책이 편지가 모자랄 때 쓴다). 편지가 세 통 이상인 달은 비워 둔다. */
    val dayLines: List<MonthDayLine> = emptyList(),
)

/** 그날의 한 줄 한 사람 것(추억 책 한 달 쪽). [meta] 는 "10.19 · 면회 · 지수". */
data class MonthDayLine(val date: LocalDate, val meta: String, val mine: Boolean, val text: String)

/** 추억 책 한 달 쪽 오른쪽의 칸 하나: 편지 · 그날의 한 줄 · 빈칸. */
sealed interface BookSlot {
    data class Letter(val letter: MonthLetter) : BookSlot
    data class Line(val line: MonthDayLine) : BookSlot
    data object Empty : BookSlot
}

/** 한 달 쪽의 편지 칸 수. */
const val BOOK_SLOTS = 3

/**
 * 한 달 쪽 편지 세 칸(#23 결정 2): 두 사람 편지를 섞어 글이 긴 순, 모자라면 그 달의 그날의 한 줄(날짜 순), 그것도 없으면 빈칸.
 */
fun bookSlots(letters: List<MonthLetter>, dayLines: List<MonthDayLine>): List<BookSlot> {
    val picked = letters.sortedWith(compareByDescending<MonthLetter> { it.text.length }.thenBy { it.date }).take(BOOK_SLOTS).map { BookSlot.Letter(it) }
    val lines = dayLines.sortedBy { it.date }.take(BOOK_SLOTS - picked.size).map { BookSlot.Line(it) }
    return picked + lines + List(BOOK_SLOTS - picked.size - lines.size) { BookSlot.Empty }
}

/** 추억 책 전체 요약(표지 · 요약 쪽). */
data class BookSummary(
    val my: String,
    val partner: String,
    val from: LocalDate?,
    val to: LocalDate?,
    val days: Int?,
    val letters: Int,
    val myLetters: Int,
    val partnerLetters: Int,
    val drawings: Int,
    val meets: Int,
    val visits: Int,
    val overnights: Int,
    val vacations: Int,
    /** 가장 많이 주고받은 달 · 통 수. */
    val busiest: Pair<YearMonth, Int>?,
    /** 지나온 길: (날짜, 이름). */
    val milestones: List<Pair<LocalDate, String>>,
    /** 군인 이름(마지막 장 "수고했어, 민준아."). */
    val soldier: String = partner,
    /** 마지막 장의 한마디: 가장 최근 편지의 글(글, 쓴 사람). */
    val lastWord: Pair<String, String>? = null,
    /** 책을 만든 날(표지 소인). */
    val madeOn: LocalDate? = null,
)

/** 추억 탭 안에서 덮어 여는 화면(뒤로 가기는 하나씩 닫는다). */
sealed interface MemoryPage {
    data class Month(val ym: YearMonth) : MemoryPage
    /** 그날의 한 줄 보기. */
    data class Meet(val eventId: String) : MemoryPage
    /** 그날의 한 줄 쓰기(다음 날 푸시 · 홈 카드 · 일정 상세 · 추억 위쪽 버튼에서). */
    data class DayLine(val eventId: String) : MemoryPage
    data object Book : MemoryPage
}

data class MemoryState(
    val loading: Boolean = true,
    val progress: BookProgress? = null,
    val chapters: List<Chapter> = emptyList(),
    val months: Map<YearMonth, MonthDetail> = emptyMap(),
    val summary: BookSummary? = null,
    val stack: List<MemoryPage> = emptyList(),
    /** 추억 책 표지(두 사람 공유). */
    val cover: BookCover = BookCover.DEFAULT,
    /** 이미 시작한 만남(진행 중 포함) — 그날의 한 줄을 쓸 수 있는 일정. 일정 id → 카드. */
    val dayLineMeets: Map<String, MeetCard> = emptyMap(),
    /** 추억 위쪽 [+ 그날의 한 줄] 이 여는 일정: 가장 최근에 끝난(진행 중이면 그것) 만남. */
    val latestMeet: String? = null,
    /** 오늘(ViewModel 시계). 비어 있을 때 이번 달 빈 페이지. */
    val today: LocalDate? = null,
) {
    val empty: Boolean get() = !loading && chapters.none { it.letters + it.meets > 0 }
    val top: MemoryPage? get() = stack.lastOrNull()

    fun meet(eventId: String): MeetCard? =
        dayLineMeets[eventId] ?: months.values.firstNotNullOfOrNull { m -> m.meets.firstOrNull { it.eventId == eventId } }
}

sealed interface MemoryIntent {
    data class Open(val page: MemoryPage) : MemoryIntent
    data object Back : MemoryIntent
    /** 그날의 한 줄(각자 자기 줄). 빈 글이면 지운다. */
    data class SaveDayLine(val eventId: String, val text: String) : MemoryIntent
    /** 그날에 사진을 붙인다(줄인 JPEG). 일정에 실려 상대 폰에도 간다. */
    data class AddMeetPhoto(val eventId: String, val jpeg: ByteArray) : MemoryIntent
    data class RemoveMeetPhoto(val eventId: String, val attachmentId: String) : MemoryIntent
    /** 추억 책 표지를 고른다(두 사람 공유 — 상대 폰의 책도 바뀐다). */
    data class ChooseCover(val cover: BookCover) : MemoryIntent
}

/**
 * 추억 = 달마다 한 장씩 쌓이는 우리의 책(Figma 5 · 추억, 화면 기능명세 2-7).
 * 표지 카드 + 월별 장 → 한 달(달력 · 편지 · 만남) → 그날의 한 줄(보기 · 쓰기), 책으로 보기(펼침 미리보기 + PDF).
 * 예약 편지는 공개 전이라 여기에도 없다(편지함과 같은 쿼리).
 */
class MemoryViewModel(
    private val letters: LetterRepository,
    private val events: EventRepository,
    profile: ProfileRepository,
    private val attachments: AttachmentStore,
    private val covers: BookCoverRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val me = profile.myUserId()
    private val today = clock.today()
    private val stack = MutableStateFlow<List<MemoryPage>>(emptyList())
    private val docs = MutableStateFlow<Map<String, FirstDrawing?>>(emptyMap())

    /** 그날의 사진 · 편지 사진 원본(첨부 id → JPEG). 화면이 썸네일로 쓴다. */
    private val _photos = MutableStateFlow<Map<String, ByteArray?>>(emptyMap())
    val photos: StateFlow<Map<String, ByteArray?>> = _photos

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val notices: SharedFlow<String> = _notices

    private val names = combine(profile.observeCouple(), profile.observeUsers()) { couple, users ->
        val partnerId = couple?.partnerUserId?.takeIf { it.isNotBlank() }
        val my = users.firstOrNull { it.id == me }?.nickname?.takeIf { it.isNotBlank() } ?: "나"
        val partner = partnerId?.let { id -> users.firstOrNull { it.id == id } }?.nickname?.takeIf { it.isNotBlank() } ?: "연인"
        val myRole = users.firstOrNull { it.id == me }?.role?.let { r -> com.sadturtleman.flowershoes.core.model.Role.entries.firstOrNull { it.name == r } }
        val soldier = if (myRole == com.sadturtleman.flowershoes.core.model.Role.SOLDIER) my else partner
        Triple(my, partner, soldier)
    }

    private val range = events.observeRange(today.minus(DatePeriod(years = 3)), today.plus(DatePeriod(years = 1)))
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** 만남이 있던 달마다 그날의 한 줄(EventRepository.observeDayLinesIn — 추억 책이 편지 대신 채운다). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val monthLines: kotlinx.coroutines.flow.Flow<Map<YearMonth, List<Event>>> = range
        .map { es -> es.filter { it.deletedAt == null && it.hasDayLine }.map { YearMonth.of(it.start.date) }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { yms ->
            if (yms.isEmpty()) flowOf(emptyMap())
            else combine(yms.map { ym -> events.observeDayLinesIn(ym.year, ym.month).map { ym to it } }) { it.toMap() }
        }

    val state: StateFlow<MemoryState> = combine(
        stack,
        letters.observeInbox(),
        range,
        combine(names, profile.observeTimelines(), covers.observe()) { n, t, c -> Triple(n, t.firstOrNull()?.second, c) },
        combine(docs, monthLines, letters.observeReadLetterIds()) { d, l, r -> Triple(d, l, r) },
    ) { pages, ls, es, (n, timeline, cover), (thumbs, lines, read) ->
        build(pages, ls, es, n.first, n.second, timeline, thumbs, lines, read).let { st ->
            st.copy(cover = cover, summary = st.summary?.copy(soldier = n.third))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryState())

    init {
        // 사진: 처음 보이는 첨부를 한 번씩 읽어 둔다(상대가 붙인 사진은 받은 뒤에 생긴다 — 없으면 다음에 다시).
        viewModelScope.launch {
            state.collect { st ->
                val ids = st.months.values.flatMap { m -> m.meets.flatMap { it.photos } + m.letters.flatMap { listOfNotNull(it.photoId, it.drawingPng) } }
                ids.filter { _photos.value[it] == null }.forEach { id ->
                    attachments.read(id)?.let { bytes -> _photos.update { it + (id to bytes) } }
                }
            }
        }
        // 그림 썸네일: 편지의 첫 그림 문서를 한 번씩 읽어 둔다.
        viewModelScope.launch {
            letters.observeInbox().collect { list ->
                list.filter { it.drawingCount > 0 && it.id !in docs.value }.forEach { l ->
                    docs.update { it + (l.id to firstDrawing(l.id)) }
                }
            }
        }
    }

    fun onIntent(intent: MemoryIntent) {
        when (intent) {
            // 같은 화면을 거듭 열면(알림 · 홈 카드를 여러 번) 한 장만 둔다.
            is MemoryIntent.Open -> stack.update { if (it.lastOrNull() == intent.page) it else it + intent.page }
            MemoryIntent.Back -> stack.update { it.dropLast(1) }
            is MemoryIntent.SaveDayLine -> viewModelScope.launch {
                events.writeDayLine(intent.eventId, intent.text)
                stack.update { s -> if (s.lastOrNull() is MemoryPage.DayLine) s.dropLast(1) else s }
            }
            is MemoryIntent.AddMeetPhoto -> viewModelScope.launch {
                val e = events.get(intent.eventId) ?: return@launch
                if (e.photoIds.size >= DayLine.MAX_PHOTOS) {
                    _notices.tryEmit("사진은 둘이 합쳐 ${DayLine.MAX_PHOTOS}장까지예요")
                    return@launch
                }
                val id = attachments.put(AttachmentKind.PHOTO, intent.jpeg)
                _photos.update { it + (id to intent.jpeg) }
                events.save(e.copy(photoIds = e.photoIds + id))
            }
            is MemoryIntent.ChooseCover -> viewModelScope.launch { covers.choose(intent.cover) }
            is MemoryIntent.RemoveMeetPhoto -> viewModelScope.launch {
                val e = events.get(intent.eventId) ?: return@launch
                events.save(e.copy(photoIds = e.photoIds - intent.attachmentId))
            }
        }
    }

    private suspend fun firstDrawing(letterId: String): FirstDrawing? {
        val row = letters.drawingsOf(letterId).minByOrNull { it.orderInLetter } ?: return null
        val doc = row.docAttachmentId?.let { attachments.read(it) }
            ?.let { bytes -> (runCatching { DrawingCodec.decode(bytes) }.getOrNull() as? DrawingCodec.Result.Ok)?.document }
        return FirstDrawing(doc, row.pngAttachmentId.takeIf { it.isNotBlank() }, row.caption?.takeIf { it.isNotBlank() })
    }

    private fun build(
        pages: List<MemoryPage>,
        ls: List<Letter>,
        es: List<Event>,
        my: String,
        partner: String,
        timeline: ServiceTimeline?,
        thumbs: Map<String, FirstDrawing?>,
        lines: Map<YearMonth, List<Event>>,
        read: Set<String>,
    ): MemoryState {
        val meetEvents = es.filter { it.deletedAt == null && it.hasDayLine }.sortedBy { it.start }
        val pastMeets = meetEvents.filter { it.end.date <= today }
        val firstVisit = pastMeets.firstOrNull { it.type == EventType.VISIT }?.id
        val started = meetEvents.filter { it.start.date <= today }
        val dayLineMeets = started.associate { it.id to meetCard(it, it.id == firstVisit, my, partner) }
        val latestMeet = started.maxByOrNull { it.end }?.id
        val nameOf = { id: String -> if (id == me) my else partner }

        // 책의 첫 장: 입대한 달(복무 정보가 없으면 첫 편지 · 만남의 달). 마지막 장: 이번 달(전역했으면 전역한 달).
        val contentStart = (ls.map { it.date() } + pastMeets.map { it.start.date }).minOrNull()
        val start = timeline?.enlistDate ?: contentStart
        if (start == null) return MemoryState(loading = false, stack = pages, dayLineMeets = dayLineMeets, latestMeet = latestMeet, today = today)
        val end = timeline?.dischargeDate?.takeIf { it < today } ?: today
        val firstYm = YearMonth.of(minOf(start, contentStart ?: start))
        val lastYm = YearMonth.of(end)
        val months = generateSequence(firstYm) { it.next() }.takeWhile { it <= lastYm }.toList()

        val details = months.mapIndexed { i, ym ->
            val inMonth = { d: LocalDate -> YearMonth.of(d) == ym }
            val monthLetters = ls.filter { inMonth(it.date()) }.sortedByDescending { it.visibleAt }.map { l ->
                val text = l.blocks.filterIsInstance<LetterBlock.Text>().joinToString(" ") { it.text.trim() }.trim()
                val attachments = l.drawingCount + l.photoCount
                val kind = when {
                    attachments > 0 && text.isBlank() -> LetterKind.DRAWING_ONLY
                    attachments > 0 -> LetterKind.INLINE
                    else -> LetterKind.TEXT
                }
                val d = l.date()
                val cut = l.blocks.indexOfFirst { it !is LetterBlock.Text }.takeIf { it >= 0 } ?: l.blocks.size
                val textOf = { bs: List<LetterBlock> -> bs.filterIsInstance<LetterBlock.Text>().joinToString(" ") { it.text.trim() }.trim() }
                MonthLetter(
                    l.id, d, "${d.month.ordinal + 1}.${d.day} · ${nameOf(l.authorId)}", nameOf(l.authorId), l.authorId == me, kind,
                    text, textOf(l.blocks.take(cut)), textOf(l.blocks.drop(cut)), thumbs[l.id]?.doc,
                    if (l.drawingCount == 0) l.blocks.filterIsInstance<LetterBlock.Photo>().firstOrNull()?.attachmentId else null,
                    title = l.title?.takeIf { it.isNotBlank() },
                    unread = l.authorId != me && l.id !in read,
                    stamp = stampText(d, timeline?.dischargeDate),
                    drawingPng = thumbs[l.id]?.png,
                    caption = thumbs[l.id]?.caption,
                )
            }
            val bars = buildList {
                meetEvents.forEach { e ->
                    val from = maxOf(e.start.date, ym.first)
                    val to = minOf(e.end.date, ym.first.plus(DatePeriod(days = ym.length - 1)))
                    if (from <= to) add(MonthBar(from.day, to.day, e.type.label() + if (e.end.date > e.start.date) " · ${e.start.date.daysUntil(e.end.date) + 1}일" else "", e.type.bar()))
                }
                timeline?.ranks?.drop(1)?.filter { inMonth(it.from) }?.forEach { add(MonthBar(it.from.day, it.from.day, it.rank.promotionName, BarKind.RANK)) }
            }
            val meets = pastMeets.filter { inMonth(it.start.date) }.map { e -> meetCard(e, e.id == firstVisit, my, partner) }
            MonthDetail(
                ym, i + 1,
                letterDays = monthLetters.filter { it.kind != LetterKind.DRAWING_ONLY }.map { it.date.day }.toSet(),
                drawingDays = monthLetters.filter { it.kind != LetterKind.TEXT }.map { it.date.day }.toSet(),
                bars = bars,
                letters = monthLetters,
                meets = meets,
                drawings = ls.filter { inMonth(it.date()) }.sumOf { it.drawingCount },
                dayLines = if (monthLetters.size >= BOOK_SLOTS) emptyList() else lines[ym].orEmpty().flatMap { e ->
                    val d = e.start.date
                    e.dayLines.filterValues { it.text.isNotBlank() }.map { (author, line) ->
                        MonthDayLine(d, "${d.md()} · ${e.type.label()} · ${nameOf(author)}", author == me, line.text)
                    }
                },
            )
        }
        val chapters = details.map { m ->
            Chapter(
                m.ym, m.number, m.letters.size, m.drawings, m.meets.size,
                cover = m.meets.firstNotNullOfOrNull { it.photos.firstOrNull() }?.let { ChapterCover.Photo(it) }
                    ?: m.letters.firstOrNull { it.thumb != null }?.let { ChapterCover.Drawing(it.id) },
                writing = m.ym == YearMonth.of(today) && (timeline == null || timeline.dischargeDate >= today),
                enlisted = timeline?.let { YearMonth.of(it.enlistDate) == m.ym } == true,
            )
        }.reversed()
        val totalMonths = timeline?.let { t -> generateSequence(YearMonth.of(t.enlistDate)) { it.next() }.takeWhile { it <= YearMonth.of(t.dischargeDate) }.count() }
        val busiest = details.maxByOrNull { it.letters.size }?.takeIf { it.letters.isNotEmpty() }?.let { it.ym to it.letters.size }
        val summary = BookSummary(
            my, partner,
            from = timeline?.enlistDate ?: start,
            to = timeline?.dischargeDate,
            days = timeline?.let { minOf(it.totalDays, it.enlistDate.daysUntil(today) + 1) },
            letters = ls.size,
            myLetters = ls.count { it.authorId == me },
            partnerLetters = ls.count { it.authorId != me },
            drawings = ls.sumOf { it.drawingCount },
            meets = pastMeets.size,
            visits = pastMeets.count { it.type == EventType.VISIT },
            overnights = pastMeets.count { it.type == EventType.OVERNIGHT },
            vacations = pastMeets.count { it.type == EventType.VACATION },
            busiest = busiest,
            lastWord = ls.filter { l -> l.blocks.any { it is LetterBlock.Text && it.text.isNotBlank() } }.maxByOrNull { it.visibleAt }?.let { l ->
                l.blocks.filterIsInstance<LetterBlock.Text>().joinToString(" ") { it.text.trim() }.trim() to nameOf(l.authorId)
            },
            madeOn = today,
            milestones = buildList {
                timeline?.let { t ->
                    add(t.enlistDate to "입대")
                    t.ranks.drop(1).forEach { add(it.from to it.rank.expectedName) }
                    add(t.dischargeDate to "전역")
                }
            },
        )
        return MemoryState(
            loading = false,
            progress = BookProgress(details.size, totalMonths, done = timeline?.dischargeDate?.let { it < today } == true),
            chapters = chapters,
            months = details.associateBy { it.ym },
            summary = summary,
            stack = pages,
            dayLineMeets = dayLineMeets,
            latestMeet = latestMeet,
            today = today,
        )
    }

    private fun meetCard(e: Event, firstVisit: Boolean, my: String, partner: String): MeetCard {
        val d = e.start.date
        val days = d.daysUntil(e.end.date)
        val time = when {
            days > 0 -> "${d.md()} – ${e.end.date.md()} · ${days}박 ${days + 1}일"
            e.allDay() -> "${d.md()} (${weekday(d)})"
            else -> "${d.md()} (${weekday(d)}) ${e.start.time.hhmm()} – ${e.end.time.hhmm()}"
        }
        val mine = e.myDayLine(me)
        val theirs = e.partnerDayLine(me)
        val full = { x: LocalDate -> "${x.year}.${x.month.number.toString().padStart(2, '0')}.${x.day.toString().padStart(2, '0')} (${weekday(x)})" }
        return MeetCard(
            e.id, d, e.type.label(), e.type,
            e.title.ifBlank { if (firstVisit) "첫 면회" else e.type.label() }, time, e.photoIds,
            notes = listOfNotNull(mine?.let { my to it }, theirs?.let { partner to it }),
            myNote = mine.orEmpty(),
            dateLabel = if (days > 0) "${full(d)} – ${e.end.date.md()} (${weekday(e.end.date)})" else full(d),
        )
    }

    private companion object {
    }
}

/** 편지의 첫 그림: 문서(썸네일) · PNG 첨부 id(봉투 미니 폴라로이드) · 캡션. */
internal class FirstDrawing(val doc: DrawingDocument?, val png: String?, val caption: String?)

private fun Letter.date(): LocalDate = visibleAt.toLocalDateTime(SEOUL).date

private fun Event.allDay() = start.time == LocalTime(0, 0) && end.time.hour == 23

private fun EventType.bar() = when (this) {
    EventType.VISIT -> BarKind.VISIT
    EventType.OVERNIGHT -> BarKind.OVERNIGHT
    else -> BarKind.VACATION
}

internal fun EventType.label() = when (this) {
    EventType.VACATION -> "휴가"
    EventType.OVERNIGHT -> "외박"
    EventType.VISIT -> "면회"
    EventType.TRAINING -> "훈련"
    EventType.DUTY -> "당직"
    EventType.ANNIVERSARY -> "기념일"
    EventType.OTHER -> "기타"
}

internal fun LocalDate.md() = "${month.ordinal + 1}.$day"

internal fun LocalTime.hhmm() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

internal fun weekday(d: LocalDate) = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]

/** 장 순서 말("첫 번째" · "세 번째" · "스물한 번째"). */
internal fun ordinalKo(n: Int): String {
    val ones = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
    val word = when {
        n == 1 -> "첫"
        n < 10 -> ones[n]
        n == 10 -> "열"
        n < 20 -> "열" + ones[n - 10]
        n == 20 -> "스무"
        n < 30 -> "스물" + ones[n - 20]
        else -> "$n"
    }
    return "$word 번째"
}

/** 이름 + 주격 조사("민준이" · "지수가"). "나" 는 "내가". */
internal fun String.subject(): String = if (this == "나") "내가" else this + if (endsWithBatchim()) "이" else "가"

/** "지수와" · "민준과". */
internal fun String.and(): String = this + if (endsWithBatchim()) "과" else "와"

/** 부르는 말("민준아" · "지수야"). */
internal fun String.vocative(): String = this + if (endsWithBatchim()) "아" else "야"

/** 마지막 글자가 받침 있는 한글인지. */
private fun String.endsWithBatchim(): Boolean = lastOrNull()?.let { it in '가'..'힣' && (it - '가') % 28 != 0 } == true
