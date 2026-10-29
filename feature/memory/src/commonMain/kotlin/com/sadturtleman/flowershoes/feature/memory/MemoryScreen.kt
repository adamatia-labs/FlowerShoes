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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
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
 * 추억(Figma 6 · 추억): 우리의 책 · 비어 있음 · 한 달 · 책으로 보기 · 그날의 한 줄(보기 · 쓰기).
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
                is MemoryPage.DayLine -> state.meet(page.eventId)?.let { DayLineWriteScreen(it, photos, onIntent, addPhoto) }
                MemoryPage.Book -> BookScreen(state, photos, book, onBack = { onIntent(MemoryIntent.Back) }, onNotice = { notice = it })
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

/** 추억 첫 화면(Figma 추억 · 책): 우리의 책 표지 카드 + 월별 장. 아무것도 없으면 빈 책 + [편지 쓰기]. */
@Composable
fun MemoryScreen(state: MemoryState, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, onWriteLetter: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("추억", style = KkTheme.type.title, color = c.ink)
        }
        if (state.loading) return@Column
        state.latestMeet?.let { id -> DayLineButton { onIntent(MemoryIntent.Open(MemoryPage.DayLine(id))) } }
        if (state.empty) {
            BookCard(null) {}
            Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                Text("편지 · 그림 · 만남이 달마다 한 장씩 모여 책이 돼요", style = KkTheme.type.body, color = c.sub, textAlign = TextAlign.Center)
                KkButton("편지 쓰기", onWriteLetter)
            }
            return@Column
        }
        BookCard(state.progress) { onIntent(MemoryIntent.Open(MemoryPage.Book)) }
        Text("월별 장", style = KkTheme.type.label, color = c.sub)
        state.chapters.forEach { ch ->
            ChapterRow(ch, state.months[ch.ym], photos) { onIntent(MemoryIntent.Open(MemoryPage.Month(ch.ym))) }
        }
        Spacer(Modifier.height(KkSpacing.lg))
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

/** 우리의 책 표지 카드: 작은 책 표지 + "세 번째 장 쓰는 중 · 3 / 18개월" + 진행 막대 + 책으로 보기. [progress] 가 null 이면 빈 책. */
@Composable
private fun BookCard(progress: BookProgress?, onOpen: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.bar)
            .then(if (progress != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg), verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverShape = RoundedCornerShape(6.dp)
        if (progress == null) {
            Box(Modifier.size(84.dp, 112.dp).clip(coverShape).border(1.5.dp, c.accent, coverShape))
        } else {
            Box(Modifier.size(84.dp, 112.dp).clip(coverShape).background(c.accent)) {
                AirmailStripe(Modifier.fillMaxWidth().height(8.dp))
                Text("우리의\n책", style = KkTheme.type.label, color = c.surface, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("우리의 책", style = KkTheme.type.labelBold, color = c.ink)
            Text(progress?.label() ?: "첫 장을 채워 보세요", style = KkTheme.type.body, color = c.sub)
            if (progress != null) {
                val f = progress.total?.let { (progress.current.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.surface)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(3.dp)).background(c.accent))
                }
                Text("책으로 보기 ›", style = KkTheme.type.label, color = c.accent)
            }
        }
    }
}

private fun BookProgress.label(): String = when {
    done -> "다 쓴 책 · ${current}장"
    total != null -> "${ordinalKo(current)} 장 쓰는 중 · $current / ${total}개월"
    else -> "${ordinalKo(current)} 장 쓰는 중"
}

/** 월별 장 한 줄: 대표 사진(만남 사진 · 첫 그림) + "10월 · 세 번째 장" + 쓰는 중 태그 + 편지 · 그림 · 만남 수. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterRow(ch: Chapter, month: MonthDetail?, photos: Map<String, ByteArray?>, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bar), contentAlignment = Alignment.Center) {
            when (val cover = ch.cover) {
                is ChapterCover.Photo -> PhotoImage(photos[cover.attachmentId], Modifier.fillMaxSize())
                is ChapterCover.Drawing -> month?.letters?.firstOrNull { it.id == cover.letterId }?.thumb?.let { Box(Modifier.size(56.dp)) { DrawingView(it) } }
                null -> KkIcon(KkIcons.Album, tint = c.accent)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${ch.ym.month}월 · ${ordinalKo(ch.number)} 장", style = KkTheme.type.labelBold, color = c.ink)
                if (ch.writing) Pill("쓰는 중", c.bar, c.accent)
            }
            Text(
                listOfNotNull(
                    "입대".takeIf { ch.enlisted },
                    "편지 ${ch.letters}".takeIf { ch.letters > 0 },
                    "그림 ${ch.drawings}".takeIf { ch.drawings > 0 },
                    "만남 ${ch.meets}".takeIf { ch.meets > 0 },
                ).ifEmpty { listOf("아직 비어 있어요") }.joinToString(" · "),
                style = KkTheme.type.body, color = c.sub,
            )
        }
        Text("›", style = KkTheme.type.title, color = c.sub)
    }
}

// ── 한 달 ─────────────────────────────────────────────────────────────────

/** 추억 · 한 달(Figma): 달력(일정 막대 · 편지 점 · 그림 편지 마름모 · 오늘) + 편지(세 형태) + 만남. */
@Composable
private fun MonthScreen(m: MonthDetail, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    var all by remember(m.ym) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        KkTopBar("${m.ym.month}월 · ${ordinalKo(m.number)} 장", onLeading = { onIntent(MemoryIntent.Back) }, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            Text(
                listOf("편지 ${m.letters.size}", "그림 ${m.drawings}", "만남 ${m.meets.size}").joinToString(" · "),
                style = KkTheme.type.body, color = c.sub,
            )
            val shape = RoundedCornerShape(KkRadius.lg)
            Box(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(KkSpacing.md)) {
                MonthCalendar(m, today = remember { kotlin.time.Clock.System.today() }, ink = c.ink, muted = c.sub, sunday = c.danger)
            }
            Legend()
            if (m.letters.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("편지 ${m.letters.size}", style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f))
                    if (m.letters.size > 3) Text(if (all) "접기" else "전체 보기", style = KkTheme.type.label, color = c.accent, modifier = Modifier.clickable(role = Role.Button) { all = !all })
                }
                (if (all) m.letters else m.letters.take(3)).forEach { l -> LetterRow(l, photos) { onOpenLetter(l.id) } }
            }
            if (m.meets.isNotEmpty()) {
                Text("만남 ${m.meets.size}", style = KkTheme.type.labelBold, color = c.ink)
                m.meets.forEach { meet -> MeetSummary(meet, photos) { onIntent(MemoryIntent.Open(MemoryPage.Meet(meet.eventId))) } }
            }
            Spacer(Modifier.height(KkSpacing.lg))
        }
    }
}

/** 편지 한 줄: 그림만은 큰 그림 + "그림 편지", 글 사이 그림은 작은 그림 + 글, 글만은 글. */
@Composable
private fun LetterRow(l: MonthLetter, photos: Map<String, ByteArray?>, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l.kind != LetterKind.TEXT) {
            val s = if (l.kind == LetterKind.DRAWING_ONLY) 56.dp else 44.dp
            Box(Modifier.size(s).clip(RoundedCornerShape(KkRadius.md)).background(c.bar), contentAlignment = Alignment.Center) {
                when {
                    l.thumb != null -> Box(Modifier.size(s - 6.dp)) { DrawingView(l.thumb) }
                    l.photoId != null -> PhotoImage(photos[l.photoId], Modifier.fillMaxSize())
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(l.meta, style = KkTheme.type.caption, color = c.sub)
            Text(
                if (l.kind == LetterKind.DRAWING_ONLY) "그림 편지" else l.text,
                style = KkTheme.type.body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 만남 카드(한 달 안): 태그 · 제목 · 날짜, 사진 둘, 메모 한 줄. */
@Composable
private fun MeetSummary(meet: MeetCard, photos: Map<String, ByteArray?>, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Column(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Pill(meet.tag, c.bar, c.accent)
            Text("${meet.title} · ${meet.date.md()}", style = KkTheme.type.label, color = c.ink)
        }
        if (meet.photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                meet.photos.take(3).forEach { id -> PhotoImage(photos[id], Modifier.size(96.dp, 72.dp).clip(RoundedCornerShape(KkRadius.md))) }
            }
        }
        meet.notes.firstOrNull()?.let { (who, text) -> Text("$who · $text", style = KkTheme.type.body, color = c.sub) }
    }
}

/** 달력 범례: 편지 · 그림 편지 · 면회 · 휴가 · 진급. */
@Composable
private fun Legend() {
    val c = KkTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("편지") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.stamp)) }
        LegendItem("그림 편지") { Box(Modifier.size(6.dp).rotate(45f).background(c.accent)) }
        LegendItem("면회") { Box(Modifier.size(14.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.eventVisit)) }
        LegendItem("휴가") { Box(Modifier.size(14.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.eventVacation)) }
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        mark()
        Text(label, style = KkTheme.type.caption, color = KkTheme.colors.sub)
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
        Tape(Modifier.align(Alignment.TopEnd).padding(end = 28.dp).offset(y = (-10).dp).rotate(-4f))
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
        Tape(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp))
    }
}

/** 마스킹 테이프(Figma Tape: 56×16, 반투명). */
@Composable
private fun Tape(modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp, 16.dp).background(KkTheme.colors.tape))
}

/** 폴라로이드(사진은 콘텐츠라 흰 틀 그대로). 위에 테이프. */
@Composable
private fun Polaroid(bytes: ByteArray?, tilt: Float, modifier: Modifier = Modifier) {
    Box(modifier.rotate(tilt)) {
        Box(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, RoundedCornerShape(0.dp)).background(Color.White).padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 24.dp)) {
            PhotoImage(bytes, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-5).dp).rotate(4f).size(35.dp, 10.dp).background(KkTheme.colors.tape))
    }
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
                meet.photos.takeLast(2).forEachIndexed { i, id -> Polaroid(photos[id], if (i % 2 == 0) 2f else -2f, Modifier.width(93.dp)) }
                if (addPhoto != null && meet.photos.size < MAX_PHOTOS) {
                    Column(
                        Modifier.size(if (meet.photos.isEmpty()) 96.dp else 80.dp, 96.dp).clip(RoundedCornerShape(KkRadius.sm)).dashedBorder(c.line, KkRadius.sm)
                            .clickable(role = Role.Button) { addPhoto(meet.eventId) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        KkIcon(KkIcons.Plus, tint = c.sub)
                        Text(
                            if (meet.photos.isEmpty()) "사진 추가" else "${meet.photos.size} / $MAX_PHOTOS",
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

/** 그날의 사진(둘이 합쳐). */
private const val MAX_PHOTOS = 10

// ── 공용 ─────────────────────────────────────────────────────────────────

/** 첨부 JPEG 한 장(못 읽었으면 연한 자리표시). */
@Composable
internal fun PhotoImage(bytes: ByteArray?, modifier: Modifier) {
    val bitmap: ImageBitmap? = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    if (bitmap != null) Image(bitmap, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(KkTheme.colors.line))
}

@Composable
internal fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, style = KkTheme.type.label, color = fg, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(bg).padding(horizontal = KkSpacing.xs, vertical = KkSpacing.xxs),
    )
}
