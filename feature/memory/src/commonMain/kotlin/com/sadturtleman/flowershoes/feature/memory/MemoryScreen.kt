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
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkInput
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.data.today
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.model.MeetNote
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.viewmodel.koinViewModel

/**
 * 추억(Figma 5 · 추억): 우리의 책 · 비어 있음 · 한 달 · 책으로 보기 · 만남 상세 · 만남 남기기.
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
    // 만남 사진 + 칸: 어느 만남에 붙일지 기억해 두고 사진을 고른다.
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
                is MemoryPage.Meet -> state.meet(page.eventId)?.let { MeetDetailScreen(it, photos, onIntent, addPhoto) { id -> removing = page.eventId to id } }
                is MemoryPage.Note -> state.meet(page.eventId)?.let { MeetNoteScreen(it, photos, onIntent, addPhoto) }
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
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("추억", style = KkTheme.type.titleL, color = c.textPrimary)
        }
        if (state.loading) return@Column
        if (state.empty) {
            BookCard(null) {}
            Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                Text("편지 · 그림 · 만남이 달마다 한 장씩 모여 책이 돼요", style = KkTheme.type.bodyM, color = c.textSecondary, textAlign = TextAlign.Center)
                KkButton("편지 쓰기", onWriteLetter)
            }
            return@Column
        }
        BookCard(state.progress) { onIntent(MemoryIntent.Open(MemoryPage.Book)) }
        Text("월별 장", style = KkTheme.type.labelM, color = c.textTertiary)
        state.chapters.forEach { ch ->
            ChapterRow(ch, state.months[ch.ym], photos) { onIntent(MemoryIntent.Open(MemoryPage.Month(ch.ym))) }
        }
        Spacer(Modifier.height(KkSpacing.lg))
    }
}

/** 우리의 책 표지 카드: 작은 책 표지 + "세 번째 장 쓰는 중 · 3 / 18개월" + 진행 막대 + 책으로 보기. [progress] 가 null 이면 빈 책. */
@Composable
private fun BookCard(progress: BookProgress?, onOpen: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.bgBrandSubtle)
            .then(if (progress != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg), verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverShape = RoundedCornerShape(6.dp)
        if (progress == null) {
            Box(Modifier.size(84.dp, 112.dp).clip(coverShape).border(1.5.dp, c.borderBrand, coverShape))
        } else {
            Box(Modifier.size(84.dp, 112.dp).clip(coverShape).background(c.bgBrand)) {
                AirmailStripe(Modifier.fillMaxWidth().height(8.dp))
                Text("우리의\n책", style = KkTheme.type.labelM, color = c.textOnBrand, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("우리의 책", style = KkTheme.type.titleS, color = c.textPrimary)
            Text(progress?.label() ?: "첫 장을 채워 보세요", style = KkTheme.type.bodyS, color = c.textSecondary)
            if (progress != null) {
                val f = progress.total?.let { (progress.current.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.bgSurface)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(3.dp)).background(c.bgBrand))
                }
                Text("책으로 보기 ›", style = KkTheme.type.labelM, color = c.textBrand)
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
@Composable
private fun ChapterRow(ch: Chapter, month: MonthDetail?, photos: Map<String, ByteArray?>, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) {
            when (val cover = ch.cover) {
                is ChapterCover.Photo -> PhotoImage(photos[cover.attachmentId], Modifier.fillMaxSize())
                is ChapterCover.Drawing -> month?.letters?.firstOrNull { it.id == cover.letterId }?.thumb?.let { Box(Modifier.size(56.dp)) { DrawingView(it) } }
                null -> KkIcon(KkIcons.Album, tint = c.textBrand)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${ch.ym.month}월 · ${ordinalKo(ch.number)} 장", style = KkTheme.type.titleS, color = c.textPrimary)
                if (ch.writing) Pill("쓰는 중", c.bgBrandSubtle, c.textBrand)
            }
            Text(
                listOfNotNull(
                    "입대".takeIf { ch.enlisted },
                    "편지 ${ch.letters}".takeIf { ch.letters > 0 },
                    "그림 ${ch.drawings}".takeIf { ch.drawings > 0 },
                    "만남 ${ch.meets}".takeIf { ch.meets > 0 },
                ).ifEmpty { listOf("아직 비어 있어요") }.joinToString(" · "),
                style = KkTheme.type.bodyS, color = c.textSecondary,
            )
        }
        Text("›", style = KkTheme.type.titleM, color = c.textTertiary)
    }
}

// ── 한 달 ─────────────────────────────────────────────────────────────────

/** 추억 · 한 달(Figma): 달력(일정 막대 · 편지 점 · 그림 편지 마름모 · 오늘) + 편지(세 형태) + 만남. */
@Composable
private fun MonthScreen(m: MonthDetail, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, onOpenLetter: (String) -> Unit) {
    val c = KkTheme.colors
    var all by remember(m.ym) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding()) {
        KkTopBar("${m.ym.month}월 · ${ordinalKo(m.number)} 장", onLeading = { onIntent(MemoryIntent.Back) }, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            Text(
                listOf("편지 ${m.letters.size}", "그림 ${m.drawings}", "만남 ${m.meets.size}").joinToString(" · "),
                style = KkTheme.type.bodyM, color = c.textSecondary,
            )
            val shape = RoundedCornerShape(KkRadius.lg)
            Box(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(KkSpacing.md)) {
                MonthCalendar(m, today = remember { kotlin.time.Clock.System.today() }, ink = c.textPrimary, muted = c.textTertiary, sunday = c.statusDanger)
            }
            Legend()
            if (m.letters.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("편지 ${m.letters.size}", style = KkTheme.type.titleS, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (m.letters.size > 3) Text(if (all) "접기" else "전체 보기", style = KkTheme.type.labelM, color = c.textBrand, modifier = Modifier.clickable(role = Role.Button) { all = !all })
                }
                (if (all) m.letters else m.letters.take(3)).forEach { l -> LetterRow(l, photos) { onOpenLetter(l.id) } }
            }
            if (m.meets.isNotEmpty()) {
                Text("만남 ${m.meets.size}", style = KkTheme.type.titleS, color = c.textPrimary)
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
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l.kind != LetterKind.TEXT) {
            val s = if (l.kind == LetterKind.DRAWING_ONLY) 56.dp else 44.dp
            Box(Modifier.size(s).clip(RoundedCornerShape(KkRadius.md)).background(c.bgBrandSubtle), contentAlignment = Alignment.Center) {
                when {
                    l.thumb != null -> Box(Modifier.size(s - 6.dp)) { DrawingView(l.thumb) }
                    l.photoId != null -> PhotoImage(photos[l.photoId], Modifier.fillMaxSize())
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(l.meta, style = KkTheme.type.caption, color = c.textTertiary)
            Text(
                if (l.kind == LetterKind.DRAWING_ONLY) "그림 편지" else l.text,
                style = KkTheme.type.bodyM, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
        Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface)
            .clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Pill(meet.tag, c.bgMilitarySubtle, c.textMilitary)
            Text("${meet.title} · ${meet.date.md()}", style = KkTheme.type.labelM, color = c.textPrimary)
        }
        if (meet.photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                meet.photos.take(3).forEach { id -> PhotoImage(photos[id], Modifier.size(96.dp, 72.dp).clip(RoundedCornerShape(KkRadius.md))) }
            }
        }
        meet.notes.firstOrNull()?.let { (who, text) -> Text("$who · $text", style = KkTheme.type.bodyS, color = c.textSecondary) }
    }
}

/** 달력 범례: 편지 · 그림 편지 · 면회 · 휴가 · 진급. */
@Composable
private fun Legend() {
    val c = KkTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("편지") { Box(Modifier.size(6.dp).clip(CircleShape).background(c.heart)) }
        LegendItem("그림 편지") { Box(Modifier.size(6.dp).rotate(45f).background(c.textMilitary)) }
        LegendItem("면회") { Box(Modifier.size(14.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.eventVisit)) }
        LegendItem("휴가") { Box(Modifier.size(14.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.eventVacation)) }
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        mark()
        Text(label, style = KkTheme.type.caption, color = KkTheme.colors.textTertiary)
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
                                .background(if (isToday) c.bgBrand else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$day", style = type.copy(fontSize = type.fontSize * scale),
                                color = when { isToday -> c.textOnBrand; idx % 7 == 0 -> sunday; else -> ink },
                            )
                        }
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = (10 * scale).dp), horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                            if (day in m.letterDays) Box(Modifier.size((6 * scale).dp).clip(CircleShape).background(c.heart))
                            if (day in m.drawingDays) Box(Modifier.size((5 * scale).dp).rotate(45f).background(c.textMilitary))
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
        BarKind.RANK -> it.bgMilitary
    }
}

// ── 만남 ─────────────────────────────────────────────────────────────────

/** 만남 상세(Figma): 태그 · 시간, 사진 그리드, 둘의 한 줄 메모, [사진 추가] [메모 쓰기]. */
@Composable
private fun MeetDetailScreen(
    meet: MeetCard,
    photos: Map<String, ByteArray?>,
    onIntent: (MemoryIntent) -> Unit,
    addPhoto: ((String) -> Unit)?,
    onRemovePhoto: (String) -> Unit,
) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding()) {
        KkTopBar(meet.title, onLeading = { onIntent(MemoryIntent.Back) }, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Pill(meet.tag, c.bgMilitarySubtle, c.textMilitary)
                Text(meet.time, style = KkTheme.type.bodyS, color = c.textSecondary)
            }
            PhotoGrid(meet.photos.ifEmpty { listOf("", "", "", "") }, photos) { id -> if (id.isNotEmpty()) onRemovePhoto(id) }
            val shape = RoundedCornerShape(KkRadius.lg)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(KkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                if (meet.notes.isEmpty()) Text("아직 남긴 한 줄이 없어요", style = KkTheme.type.bodyM, color = c.textTertiary)
                meet.notes.forEach { (who, text) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                        Text(who, style = KkTheme.type.labelM, color = c.textSecondary, modifier = Modifier.width(40.dp))
                        Text(text, style = KkTheme.type.bodyM, color = c.textPrimary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(KkSpacing.xl), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            KkSecondaryButton("사진 추가", { addPhoto?.invoke(meet.eventId) }, Modifier.weight(1f), enabled = addPhoto != null)
            KkPrimaryButton(if (meet.myNote.isBlank()) "메모 쓰기" else "메모 고치기", { onIntent(MemoryIntent.Open(MemoryPage.Note(meet.eventId))) }, Modifier.weight(1f))
        }
    }
}

/** 사진 두 칸 그리드. 빈 문자열은 빈 칸(자리표시). 누르면 [onClick]. */
@Composable
private fun PhotoGrid(ids: List<String>, photos: Map<String, ByteArray?>, onClick: (String) -> Unit) {
    val c = KkTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        ids.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                row.forEach { id ->
                    Box(
                        Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)
                            .then(if (id.isNotEmpty()) Modifier.clickable(role = Role.Button) { onClick(id) }.semantics { contentDescription = "만남 사진" } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (id.isEmpty()) KkIcon(KkIcons.Album, tint = c.textTertiary) else PhotoImage(photos[id], Modifier.fillMaxSize())
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 만남 남기기("오늘 어땠어요?", Figma): 사진 고르기 + 한 줄 메모(40자) + [저장] [다음에]. */
@Composable
private fun MeetNoteScreen(meet: MeetCard, photos: Map<String, ByteArray?>, onIntent: (MemoryIntent) -> Unit, addPhoto: ((String) -> Unit)?) {
    val c = KkTheme.colors
    var text by remember(meet.eventId) { mutableStateOf(meet.myNote) }
    Column(Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding()) {
        KkTopBar("오늘 어땠어요?", onLeading = { onIntent(MemoryIntent.Back) }, leading = KkTopBarLeading.Close, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Pill(meet.tag, c.bgMilitarySubtle, c.textMilitary)
                Text("${meet.title} · ${meet.date.md()} (${weekday(meet.date)})", style = KkTheme.type.titleS, color = c.textPrimary)
            }
            Text("사진", style = KkTheme.type.labelS, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                meet.photos.takeLast(3).forEach { id -> PhotoImage(photos[id], Modifier.size(64.dp).clip(RoundedCornerShape(KkRadius.md))) }
                if (addPhoto != null) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(KkRadius.md)).border(1.dp, c.borderDefault, RoundedCornerShape(KkRadius.md))
                            .clickable(role = Role.Button) { addPhoto(meet.eventId) }.semantics { contentDescription = "사진 추가" },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", style = KkTheme.type.titleM, color = c.textTertiary) }
                }
            }
            KkInput(
                text, { if (it.length <= MeetNote.MAX_CHARS) text = it }, label = "한 줄 메모", placeholder = "드디어 얼굴 봤다",
                helper = "${text.length} / ${MeetNote.MAX_CHARS}",
            )
        }
        Column(Modifier.fillMaxWidth().padding(KkSpacing.xl), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            KkPrimaryButton("저장", { onIntent(MemoryIntent.SaveNote(meet.eventId, text)) }, Modifier.fillMaxWidth())
            KkSecondaryButton("다음에", { onIntent(MemoryIntent.Back) }, Modifier.fillMaxWidth())
        }
    }
}

// ── 공용 ─────────────────────────────────────────────────────────────────

/** 첨부 JPEG 한 장(못 읽었으면 연한 자리표시). */
@Composable
internal fun PhotoImage(bytes: ByteArray?, modifier: Modifier) {
    val bitmap: ImageBitmap? = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    if (bitmap != null) Image(bitmap, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(KkTheme.colors.bgSubtle))
}

@Composable
internal fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text, style = KkTheme.type.labelS, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(bg).padding(horizontal = KkSpacing.xs, vertical = KkSpacing.xxs),
    )
}
