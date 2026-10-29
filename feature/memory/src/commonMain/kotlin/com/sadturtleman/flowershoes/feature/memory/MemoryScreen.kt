@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeMark
import com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeRow
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import com.sadturtleman.flowershoes.core.designsystem.KkPolaroid
import com.sadturtleman.flowershoes.core.designsystem.KkPolaroidSize
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSkeleton
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkSpinner
import com.sadturtleman.flowershoes.core.designsystem.KkTape
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.model.DayLine
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.viewmodel.koinViewModel

/**
 * 추억(Figma 6 · 추억): 목록 · 비어 있음 · 로딩 · 한 달 · 책으로 보기 · 그날의 한 줄(보기 · 쓰기).
 * [book] 은 책으로 보기의 PDF 결제 · 만들기(플랫폼). 없으면 미리보기만 보인다.
 */
@Composable
fun MemoryRoute(
    onOpenLetter: (String) -> Unit,
    onWriteLetter: () -> Unit = {},
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    book: MemoryBookActions? = null,
    viewModel: MemoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    // 그날의 사진 + 칸: 어느 만남에 붙일지 기억해 두고 사진을 고른다.
    var photoFor by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    val pick = photoPicker?.invoke { bytes -> photoFor?.let { viewModel.onIntent(MemoryIntent.AddMeetPhoto(it, bytes)) } }
    val addPhoto: ((String) -> Unit)? = pick?.let { p -> { eventId -> photoFor = eventId; p(false) } }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.notices.collect { notice = it } }
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }
    val onIntent = viewModel::onIntent
    BackHandler(enabled = state.stack.isNotEmpty()) { onIntent(MemoryIntent.Back) }
    Box(Modifier.fillMaxSize()) {
        KkPageFlip(state.top, pageKey = { it?.toString() ?: "home" }, isForward = { from, to -> state.stack.size > 0 && to != null && (from == null || to != from) }) { page ->
            when (page) {
                null -> MemoryScreen(state, photos, onIntent, onWriteLetter)
                is MemoryPage.Month -> state.months[page.ym]?.let { MonthScreen(it, photos, onIntent, onOpenLetter) }
                is MemoryPage.Meet -> state.meet(page.eventId)?.let { MeetDetailScreen(it, photos, onIntent) { id -> removing = page.eventId to id } }
                is MemoryPage.DayLine -> {
                    val meet = state.meet(page.eventId)
                    if (meet != null) DayLineWriteScreen(meet, photos, onIntent, addPhoto)
                    // 지워졌거나 아직 시작하지 않은 일정(알림 · 홈 카드가 늦게 눌림)이면 닫는다.
                    else if (!state.loading) LaunchedEffect(page) { onIntent(MemoryIntent.Back) }
                }
                MemoryPage.Book -> BookScreen(state, photos, book, onChooseCover = { onIntent(MemoryIntent.ChooseCover(it)) }, onBack = { onIntent(MemoryIntent.Back) }, onNotice = { notice = it })
            }
        }
        removing?.let { (eventId, id) ->
            KkModalSheet({ removing = null }) {
                KkSheetHeader("사진 빼기", "연인 폰에서도 빠져요")
                KkButton("빼기", { onIntent(MemoryIntent.RemoveMeetPhoto(eventId, id)); removing = null }, Modifier.fillMaxWidth(), danger = true)
            }
        }
        notice?.let { KkToast(it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg)) }
    }
}

/**
 * 추억 · 목록(Figma 367:82): 위 막대(제목 + 책 아이콘 → 책으로 보기) · [+ 그날의 한 줄] · 테이프 붙은 월 페이지(최근 달부터).
 * 비어 있으면 첫 추억 안내 + 이번 달 점선 빈 페이지, 읽는 중엔 페이지 스켈레톤.
 */
@Composable
fun MemoryScreen(state: MemoryState, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, onWriteLetter: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        MemoryTopBar(onBook = state.progress?.let { { onIntent(MemoryIntent.Open(MemoryPage.Book)) } })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState(), enabled = !state.loading)
                .padding(horizontal = KkSpacing.xxl, vertical = KkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.xl),
        ) {
            if (state.loading) {
                repeat(2) { PageSkeleton() }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { KkSpinner(c.accent, 32.dp) }
                return@Column
            }
            state.latestMeet?.let { id -> DayLineButton { onIntent(MemoryIntent.Open(MemoryPage.DayLine(id))) } }
            if (state.empty) {
                EmptyMemory(onWriteLetter)
                state.today?.let { EmptyPage(YearMonth.of(it), Modifier.padding(horizontal = KkSpacing.lg)) }
                return@Column
            }
            state.chapters.forEachIndexed { i, ch ->
                val month = state.months[ch.ym] ?: return@forEachIndexed
                val page = monthPage(month)
                if (page.empty) EmptyPage(ch.ym)
                else MonthPageCard(ch, month, page, photos, tilt = if (i % 2 == 0) 1f else -1.2f) { onIntent(MemoryIntent.Open(MemoryPage.Month(ch.ym))) }
            }
            Spacer(Modifier.height(KkSpacing.sm))
        }
    }
}

/** 추억 위 막대: "추억"(Title) + 책 아이콘(책으로 보기). 책이 아직 없으면 아이콘만 흐리게. */
@Composable
private fun MemoryTopBar(onBook: (() -> Unit)?) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = KkSpacing.lg, end = KkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text("추억", style = KkTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(enabled = onBook != null, role = Role.Button) { onBook?.invoke() }
                .semantics { contentDescription = "책으로 보기" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(KkIcons.Book, tint = if (onBook != null) c.ink else c.sub) }
    }
}

/** 추억 위쪽 [+ 그날의 한 줄](Figma 추억 · 목록): 가장 최근 만남의 그날의 한 줄 쓰기. */
@Composable
private fun DayLineButton(onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.full)
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).background(c.surface).border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Plus, tint = c.accent, size = 18.dp)
        Text("그날의 한 줄", style = KkTheme.type.labelBold, color = c.ink)
    }
}

/**
 * 월 페이지(보여 주기만 하는 물건이라 기울이고 모서리에 테이프): "10월"(손글씨) · 편지 · 그림 · 만남 수,
 * 폴라로이드(장 표지) · 작은 봉투 · 만남 꼬리표, 아래 가장 최근 편지 한 줄. 누르면 한 달.
 */
@Composable
private fun MonthPageCard(ch: Chapter, month: MonthDetail, page: MonthPage, photos: Map<String, ByteArray?>, tilt: Float, onClick: () -> Unit) {
    val c = KkTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xs).rotate(tilt)) {
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, RectangleShape).background(c.surface)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
        ) {
            Text("${ch.ym.month}월", style = KkTheme.type.handL, color = c.ink)
            Text(month.counts(), style = KkTheme.type.caption, color = c.sub, modifier = Modifier.padding(top = KkSpacing.sm))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                when (val cover = ch.cover) {
                    is ChapterCover.Photo -> KkPolaroid(Modifier.width(75.dp), tilt = if (tilt > 0) 3f else -2f, size = KkPolaroidSize.Compact) {
                        PhotoImage(photos[cover.attachmentId], Modifier.fillMaxSize())
                    }
                    is ChapterCover.Drawing -> month.letters.firstOrNull { it.id == cover.letterId }?.thumb?.let { doc ->
                        KkPolaroid(Modifier.width(75.dp), tilt = if (tilt > 0) 3f else -2f, size = KkPolaroidSize.Compact) {
                            Box(Modifier.fillMaxSize().background(c.bar)) { DrawingView(doc, Modifier.fillMaxSize()) }
                        }
                    }
                    null -> Unit
                }
                page.envelope?.let { SmallEnvelope(it, tilt = if (tilt > 0) -2f else 2f) }
                page.meet?.let { MeetTicket("${it.date.md()} ${it.tag}", it.title, Modifier.widthIn(min = 104.dp, max = 120.dp), tilt = if (tilt > 0) 2f else -1.5f) }
            }
            page.lastLine?.let { (date, text) ->
                Text(
                    "$date  $text", style = KkTheme.type.caption, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
        KkTape(Modifier.align(Alignment.TopStart).offset(x = (-10).dp, y = (-6).dp).rotate(-40f))
        KkTape(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = 12.dp).rotate(35f))
    }
}

private fun MonthDetail.counts() = "편지 ${letters.size} · 그림 $drawings · 만남 ${meets.size}"

/** 월 페이지의 작은 봉투(paper/tag 78×54 · 편지 아이콘 · 편지 제목). */
@Composable
private fun SmallEnvelope(label: String, tilt: Float) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.sm)
    Column(
        Modifier.rotate(tilt).size(78.dp, 54.dp).kkElevation(KkElevation.Low, shape).clip(shape).background(c.tag).padding(top = KkSpacing.sm, start = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        KkIcon(KkIcons.Mail, tint = c.accent, size = 22.dp)
        Text(label, style = KkTheme.type.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** 만남 꼬리표(paper/tag · 구멍 · 절취선 · 위 테이프): 굵은 [title] + [sub] 한 줄. 태그 위 글자는 ink 만. */
@Composable
private fun MeetTicket(title: String, sub: String?, modifier: Modifier = Modifier, tilt: Float = 0f) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.sm)
    Box(modifier.rotate(tilt)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).height(IntrinsicSize.Min).kkElevation(KkElevation.Low, shape).clip(shape).background(c.tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(start = KkSpacing.sm).size(8.dp).clip(CircleShape).background(c.canvas))
            Box(Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp).width(1.dp).fillMaxHeight().background(c.line))
            Column(Modifier.padding(start = KkSpacing.sm, end = KkSpacing.sm, top = KkSpacing.sm, bottom = KkSpacing.sm), verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
                Text(title, style = KkTheme.type.labelBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) Text(sub, style = KkTheme.type.caption, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        KkTape(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).rotate(-4f))
    }
}

/** 점선 빈 페이지(아직 아무것도 붙이지 않은 달): "10월"(손글씨, sub) + "아직 붙인 게 없어요" + 위 테이프. */
@Composable
private fun EmptyPage(ym: YearMonth, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Box(modifier.fillMaxWidth().padding(horizontal = KkSpacing.xs).rotate(1.5f)) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 150.dp).dashedBorder(c.line, KkRadius.sm).padding(start = 17.dp, top = 13.dp, end = 17.dp, bottom = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            Text("${ym.month}월", style = KkTheme.type.handL, color = c.sub)
            Text("아직 붙인 게 없어요", style = KkTheme.type.caption, color = c.sub)
        }
        KkTape(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).rotate(4f))
    }
}

/** 추억 · 비어 있음: 책 아이콘 · "첫 추억은 첫 편지부터"(손글씨) · [편지 쓰기]. */
@Composable
private fun EmptyMemory(onWriteLetter: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 50.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        KkIcon(KkIcons.Book, tint = c.ink, size = 56.dp)
        Text("첫 추억은 첫 편지부터", style = KkTheme.type.handL, color = c.ink, textAlign = TextAlign.Center)
        KkButton("편지 쓰기", onWriteLetter, style = KkButtonStyle.Secondary, size = KkButtonSize.M)
    }
}

/** 월 페이지 스켈레톤(Figma 추억 · 로딩): 월 · 수 · 물건 셋 · 한 줄 자리. */
@Composable
private fun PageSkeleton() {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.sm)).background(c.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KkSkeleton(Modifier.size(60.dp, 20.dp))
        KkSkeleton(Modifier.width(150.dp).height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = KkSpacing.sm), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(92.dp).clip(RoundedCornerShape(KkRadius.sm)).background(c.line).clearAndSetSemantics {}) }
        }
        KkSkeleton(Modifier.fillMaxWidth(0.8f).height(10.dp))
    }
}

// ── 한 달 ─────────────────────────────────────────────────────────────────

/**
 * 추억 · 한 달(Figma 367:266): "2026년 10월" + 편지 · 그림 · 만남 수, 날짜별 타임라인(최근 날부터).
 * 편지는 봉투(봉인 · 미니 폴라로이드 · 소인 · 우표), 그림만 보낸 편지는 폴라로이드, 만남은 꼬리표.
 */
@Composable
internal fun MonthScreen(m: MonthDetail, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    val days = remember(m) { monthTimeline(m) }
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = KkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { onIntent(MemoryIntent.Back) }.semantics { contentDescription = "뒤로 가기" },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.Back, tint = c.ink, size = 15.8.dp, modifier = Modifier.width(8.8.dp)) }
            Column(Modifier.weight(1f).padding(start = KkSpacing.xs)) {
                Text("${m.ym.year}년 ${m.ym.month}월", style = KkTheme.type.labelBold, color = c.ink, maxLines = 1)
                Text(m.counts(), style = KkTheme.type.caption, color = c.sub, maxLines = 1)
            }
        }
        val line = c.line
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .drawBehind { drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(TIMELINE_X.toPx() - 1.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
                .padding(start = KkSpacing.lg, end = 14.dp, top = KkSpacing.lg, bottom = KkSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.xl),
        ) {
            days.forEach { day -> TimelineDayRow(day, photos, onIntent, onOpenLetter) }
        }
    }
}

/** 타임라인 선의 x(화면 왼쪽에서). 날짜 칸 40 + 점 칸 가운데. */
private val TIMELINE_X = 64.dp

@Composable
private fun TimelineDayRow(day: TimelineDay, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(40.dp).padding(top = 2.dp)) {
            Text(day.date.md(), style = KkTheme.type.labelBold, color = c.ink, maxLines = 1, softWrap = false)
            Text(weekday(day.date), style = KkTheme.type.caption, color = c.sub)
        }
        Box(Modifier.width(16.dp).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
        }
        Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            day.items.forEach { item ->
                when (item) {
                    is TimelineItem.Meet -> item.meet.let { meet ->
                        MeetTicket(
                            meet.title, meet.notes.firstOrNull()?.let { (who, text) -> "$who · $text" } ?: meet.time,
                            // 누르는 목록 안이라 반듯하게(design.md).
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { onIntent(MemoryIntent.Open(MemoryPage.Meet(meet.eventId))) },
                        )
                    }
                    is TimelineItem.Letter -> item.letter.let { l ->
                        if (l.kind == LetterKind.DRAWING_ONLY) DrawingLetter(l, photos) { onOpenLetter(l.id) }
                        else KkEnvelopeRow(l.text.ifBlank { "그림 편지" }, "${l.author} · ${l.date.md()}", l.envelopeMark(photos), { onOpenLetter(l.id) })
                    }
                }
            }
        }
    }
}

/** 봉투 오른쪽 장식([LetterMark] → [KkEnvelopeMark]). */
@Composable
private fun MonthLetter.envelopeMark(photos: Map<String, ByteArray?>): KkEnvelopeMark = when (mark()) {
    LetterMark.Sent -> KkEnvelopeMark.Postmark(date.dotted())
    LetterMark.Seal -> KkEnvelopeMark.Seal(stamp)
    LetterMark.Stamp -> KkEnvelopeMark.Stamp(stamp)
    LetterMark.Drawing -> {
        val bytes = photos[drawingPng ?: photoId]
        KkEnvelopeMark.Polaroid(remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } })
    }
}

private fun kotlinx.datetime.LocalDate.dotted() =
    "$year.${(month.ordinal + 1).toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

/** 그림만 보낸 편지: 폴라로이드(실제 그림 + 캡션) 옆에 "그림 · 민준이 보냄" · [그린 순서대로 다시 보기](편지에서 재생). */
@Composable
private fun DrawingLetter(l: MonthLetter, photos: Map<String, ByteArray?>, onOpen: () -> Unit) {
    val c = KkTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg), verticalAlignment = Alignment.Top) {
        Box {
            KkPolaroid(
                Modifier.width(140.dp).padding(top = KkSpacing.sm).clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = "그림 편지" },
                tilt = 2.5f, caption = l.caption,
            ) {
                Box(Modifier.fillMaxSize().background(c.bar)) {
                    when {
                        l.thumb != null -> DrawingView(l.thumb, Modifier.fillMaxSize())
                        l.drawingPng != null -> PhotoImage(photos[l.drawingPng], Modifier.fillMaxSize())
                    }
                }
            }
            // 안 읽은 그림 편지: 봉투처럼 "새" 봉인을 모서리에 건다.
            if (l.mark() == LetterMark.Seal) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp).size(28.dp).clip(CircleShape).background(c.accent).clearAndSetSemantics { contentDescription = "새 편지" },
                    contentAlignment = Alignment.Center,
                ) { Text("새", style = KkTheme.type.labelBold, color = c.canvas) }
            }
        }
        Column(Modifier.padding(top = 22.dp)) {
            Text("그림 · ${if (l.mine) "내가" else l.author.subject()} 보냄", style = KkTheme.type.caption, color = c.sub)
            Text(
                "그린 순서대로 다시 보기", style = KkTheme.type.caption, color = c.accent,
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpen).wrapContentHeight(),
            )
        }
    }
}

/**
 * 한 달 달력(앱 · 추억 책 공용). 칸 = 날짜 + 아래 표시(편지 점 · 그림 편지 마름모), 일정은 칸 아래 막대.
 * 크기는 너비에 맞춰 늘고 준다(책 쪽에서는 더 크게 그린다).
 */
@Composable
internal fun MonthCalendar(
    m: MonthDetail,
    today: kotlinx.datetime.LocalDate?,
    ink: Color,
    muted: Color,
    sunday: Color,
    font: androidx.compose.ui.text.font.FontFamily? = null,
    scale: Float = 1f,
) {
    val c = KkTheme.colors
    val offset = m.ym.first.dayOfWeek.ordinal.let { (it + 1) % 7 } // 일요일 시작
    val rows = (offset + m.ym.length + 6) / 7
    val type = KkTheme.type.caption.let { if (font != null) it.copy(fontFamily = font) else it }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cw = maxWidth / 7
        val ch = (44 * scale).dp
        Column {
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    Text(d, style = type.copy(fontSize = type.fontSize * scale), color = if (i == 0) sunday else muted, textAlign = TextAlign.Center, modifier = Modifier.width(cw))
                }
            }
            Box(Modifier.fillMaxWidth().height(ch * rows)) {
                for (day in 1..m.ym.length) {
                    val idx = offset + day - 1
                    val x = cw * (idx % 7)
                    val y = ch * (idx / 7)
                    val isToday = today != null && today.year == m.ym.year && today.month.ordinal + 1 == m.ym.month && today.day == day
                    Box(Modifier.offset(x, y).size(cw, ch)) {
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = (2 * scale).dp).size((24 * scale).dp).clip(CircleShape)
                                .background(if (isToday) c.accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$day", style = type.copy(fontSize = type.fontSize * scale),
                                color = when { isToday -> c.surface; idx % 7 == 0 -> sunday; else -> ink },
                            )
                        }
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = (10 * scale).dp), horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                            if (day in m.letterDays) Box(Modifier.size((6 * scale).dp).clip(CircleShape).background(c.stamp))
                            if (day in m.drawingDays) Box(Modifier.size((5 * scale).dp).rotate(45f).background(c.accent))
                        }
                    }
                }
                m.bars.forEach { b ->
                    var d = b.from
                    while (d <= b.to) {
                        val idx = offset + d - 1
                        val rowEnd = minOf(b.to, d + (6 - idx % 7))
                        Box(
                            Modifier.offset(cw * (idx % 7) + 2.dp, ch * (idx / 7) + ch - (6 * scale).dp)
                                .size(cw * (rowEnd - d + 1) - 4.dp, (4 * scale).dp).clip(RoundedCornerShape(2.dp)).background(b.kind.color()),
                        )
                        d = rowEnd + 1
                    }
                }
            }
        }
    }
}

@Composable
internal fun BarKind.color(): Color = KkTheme.colors.let {
    when (this) {
        BarKind.VISIT -> it.eventVisit
        BarKind.OVERNIGHT -> it.eventOvernight
        BarKind.VACATION -> it.eventVacation
        BarKind.RANK -> it.accent
    }
}

// ── 그날의 한 줄 ─────────────────────────────────────────────────────────

/**
 * 그날의 한 줄 · 보기(Figma 6 · 추억): 만남 꼬리표 · 둘의 한 줄(쓴 사람 것만) · 사진 폴라로이드. [수정] 은 쓰기 화면.
 * 사진을 누르면 빼기.
 */
@Composable
private fun MeetDetailScreen(
    meet: MeetCard,
    photos: Map<String, ByteArray?>,
    onIntent: (MemoryIntent) -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        KkTopBar(
            meet.title, onLeading = { onIntent(MemoryIntent.Back) }, modifier = Modifier.padding(horizontal = KkSpacing.lg),
            trailingText = "수정", onTrailing = { onIntent(MemoryIntent.Open(MemoryPage.DayLine(meet.eventId))) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            MeetTag(meet)
            meet.notes.forEachIndexed { i, (who, text) ->
                LineNote("${who}의 한 줄", text, if (i % 2 == 0) -1.2f else 1.2f)
            }
            if (meet.notes.isEmpty()) {
                KkButton("그날의 한 줄 남기기", { onIntent(MemoryIntent.Open(MemoryPage.DayLine(meet.eventId))) }, Modifier.fillMaxWidth(), style = KkButtonStyle.Secondary)
            }
            meet.photos.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.xl)) {
                    pair.forEachIndexed { i, id ->
                        Polaroid(photos[id], if ((row + i) % 2 == 0) 3f else -3f, Modifier.weight(1f).clickable(role = Role.Button) { onRemovePhoto(id) }.semantics { contentDescription = "사진" })
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 만남 꼬리표(종이 태그, 보여 주기만): 날짜 · 제목 · 시간 · 사진 수. */
@Composable
private fun MeetTag(meet: MeetCard) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.sm)
    Box(Modifier.fillMaxWidth().rotate(1f)) {
        Row(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.tag).padding(horizontal = 12.dp, vertical = KkSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.canvas))
            Box(Modifier.width(1.dp).height(68.dp).background(c.line))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(meet.date.md(), style = KkTheme.type.ddayS, color = c.stamp)
                Text(meet.title, style = KkTheme.type.labelBold, color = c.ink)
                Text(listOfNotNull(meet.time, "사진 ${meet.photos.size}".takeIf { meet.photos.isNotEmpty() }).joinToString(" · "), style = KkTheme.type.caption, color = c.sub)
            }
        }
        KkTape(Modifier.align(Alignment.TopEnd).padding(end = 28.dp).offset(y = (-10).dp).rotate(-4f))
    }
}

/** 한 줄 쪽지(보여 주기만): "지수의 한 줄 · 32/40" + 손글씨. */
@Composable
private fun LineNote(label: String, text: String, tilt: Float) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.sm)
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).rotate(tilt)) {
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
                .padding(start = KkSpacing.lg, end = KkSpacing.lg, top = 12.dp, bottom = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
                Text("${text.length}/${DayLine.MAX_CHARS}", style = KkTheme.type.caption, color = c.sub)
            }
            Text(text, style = KkTheme.type.handLetter, color = c.ink)
        }
        KkTape(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp))
    }
}

/** 작은 폴라로이드(사진은 콘텐츠라 흰 틀 그대로). 위에 테이프. */
@Composable
private fun Polaroid(bytes: ByteArray?, tilt: Float, modifier: Modifier = Modifier) {
    KkPolaroid(modifier, tilt = tilt, size = KkPolaroidSize.Compact) { PhotoImage(bytes, Modifier.fillMaxWidth().aspectRatio(1f)) }
}

/**
 * 그날의 한 줄 · 쓰기(Figma 6 · 추억): 일정 · 날짜(읽기만) · 내 한 줄(40자) · 사진(둘이 합쳐 10) · [남기기].
 * 상대 칸은 두지 않는다(상대가 쓰면 보기 화면에 나온다).
 */
@Composable
private fun DayLineWriteScreen(meet: MeetCard, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, addPhoto: ((String) -> Unit)?) {
    val c = KkTheme.colors
    var text by remember(meet.eventId) { mutableStateOf(meet.myNote) }
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        KkTopBar("그날의 한 줄", onLeading = { onIntent(MemoryIntent.Back) }, leading = KkTopBarLeading.Close, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            KkInput(meet.title, {}, label = "일정", enabled = false)
            KkInput(meet.dateLabel, {}, label = "날짜", enabled = false)
            KkInput(
                text, { if (it.length <= DayLine.MAX_CHARS) text = it }, label = "내 한 줄",
                helper = "${text.length} / ${DayLine.MAX_CHARS}",
            )
            Text("사진", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.padding(top = KkSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                meet.photos.takeLast(2).forEach { id -> Polaroid(photos[id], 0f, Modifier.width(93.dp)) }
                if (addPhoto != null && meet.photos.size < DayLine.MAX_PHOTOS) {
                    Column(
                        Modifier.size(if (meet.photos.isEmpty()) 96.dp else 80.dp, 96.dp).clip(RoundedCornerShape(KkRadius.sm)).dashedBorder(c.line, KkRadius.sm)
                            .clickable(role = Role.Button) { addPhoto(meet.eventId) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        KkIcon(KkIcons.Plus, tint = c.sub)
                        Text(
                            if (meet.photos.isEmpty()) "사진 추가" else "${meet.photos.size} / ${DayLine.MAX_PHOTOS}",
                            style = KkTheme.type.caption, color = c.sub, modifier = Modifier.padding(top = KkSpacing.sm),
                        )
                    }
                }
            }
        }
        KkPrimaryButton("남기기", { onIntent(MemoryIntent.SaveDayLine(meet.eventId, text)) }, Modifier.padding(KkSpacing.xl))
    }
}

/** 점선 테두리(사진 추가 칸). */
private fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
        width = 1.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
    drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()), style = stroke)
}

// ── 공용 ─────────────────────────────────────────────────────────────────

/** 첨부 JPEG 한 장(못 읽었으면 연한 자리표시). */
@Composable
internal fun PhotoImage(bytes: ByteArray?, modifier: Modifier) {
    val bitmap: ImageBitmap? = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    if (bitmap != null) Image(bitmap, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(KkTheme.colors.line))
}
