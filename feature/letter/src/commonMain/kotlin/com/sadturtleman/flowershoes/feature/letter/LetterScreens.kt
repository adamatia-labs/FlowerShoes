package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.designsystem.KkPageFlip
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.decodeToImageBitmap
import com.sadturtleman.flowershoes.core.designsystem.KkLetterFont
import com.sadturtleman.flowershoes.core.designsystem.family
import com.sadturtleman.flowershoes.core.designsystem.size
import com.sadturtleman.flowershoes.core.designsystem.KkSheetOption
import com.sadturtleman.flowershoes.core.model.Letter
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.core.data.SEOUL
import com.sadturtleman.flowershoes.core.designsystem.KkButton
import com.sadturtleman.flowershoes.core.designsystem.KkButtonStyle
import com.sadturtleman.flowershoes.core.designsystem.KkColors
import com.sadturtleman.flowershoes.core.designsystem.KkDatePicker
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkEmptyState
import com.sadturtleman.flowershoes.core.designsystem.KkMenu
import com.sadturtleman.flowershoes.core.designsystem.KkMenuItem
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sadturtleman.flowershoes.core.designsystem.KkScheduleSheet
import com.sadturtleman.flowershoes.core.designsystem.KkToastType
import com.sadturtleman.flowershoes.core.designsystem.hhmm
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSheetHeader
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkStatusChip
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.drawingui.ReplayPlayer
import com.sadturtleman.flowershoes.core.drawingui.STICKERS
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.sadturtleman.flowershoes.core.designsystem.KkButtonSize
import com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeMark
import com.sadturtleman.flowershoes.core.designsystem.KkEnvelopeRow
import com.sadturtleman.flowershoes.core.designsystem.KkLetterSize
import com.sadturtleman.flowershoes.core.designsystem.KkPolaroid
import com.sadturtleman.flowershoes.core.designsystem.KkPostmark
import com.sadturtleman.flowershoes.core.designsystem.KkPostmarkInk
import com.sadturtleman.flowershoes.core.designsystem.KkSkeleton
import com.sadturtleman.flowershoes.core.designsystem.KkUnderlineTabs
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.sadturtleman.flowershoes.core.drawingui.ReplayCanvas
import com.sadturtleman.flowershoes.core.drawingui.drawDocument
import com.sadturtleman.flowershoes.core.drawingui.rememberReplayState
import kotlinx.datetime.toInstant

// ── 편지함 ────────────────────────────────────────────────────────────────

/**
 * 편지함(Figma 4 · 편지함 `360:2613`). 편지를 열면 같은 자리에서 읽기로 바뀐다.
 * 편지 쓰기 버튼은 "편지 쓰기 / 그림 그리기" 메뉴를 연다(그림만 그려도 그림 한 장짜리 편지).
 */
@Composable
fun LetterInboxRoute(
    onWriteLetter: () -> Unit,
    onDraw: () -> Unit,
    onOpenDrafts: () -> Unit,
    onReply: () -> Unit,
    onReplyDrawing: () -> Unit = onDraw,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        // 편지를 열면 편지함 장이 넘어가고, 닫으면 다시 덮인다.
        KkPageFlip(state.open, pageKey = { it?.letter?.id ?: "inbox" }, isForward = { _, to -> to != null }) { open ->
            when {
                open != null && open.drawingOnly -> DrawingLetterScreen(open, viewModel::onIntent, onReplyDrawing)
                open != null -> LetterReadScreen(open, viewModel::onIntent, onReply, onReplyDrawing)
                else -> LetterInboxScreen(state, viewModel::onIntent, onWriteLetter, onDraw, onOpenDrafts)
            }
        }
        ToastHost(viewModel.toasts, viewModel::toastShown) { viewModel.onIntent(LetterIntent.Open(it)) }
    }
}

@Composable
fun LetterInboxScreen(
    state: LetterState,
    onIntent: (LetterIntent) -> Unit,
    onWriteLetter: () -> Unit,
    onDraw: () -> Unit,
    onOpenDrafts: () -> Unit,
) {
    val c = KkTheme.colors
    var writeMenu by remember { mutableStateOf(false) }
    // Figma 4 · 편지함: 제목 줄 → 밑줄 탭 → 쓰던 편지 → 월 머리 + 봉투(행은 반듯하게, 장식만 기운다).
    Column(
        Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xxl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("편지함", style = KkTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(KkRadius.sm)).clickable(role = Role.Button) { writeMenu = true }
                        .padding(vertical = KkSpacing.md, horizontal = KkSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KkIcon(KkIcons.Pen, tint = c.accent, size = 16.dp)
                    Text("편지 쓰기", style = KkTheme.type.label, color = c.accent)
                }
                KkMenu(
                    writeMenu, { writeMenu = false },
                    listOf(KkMenuItem("편지 쓰기", KkIcons.Note, onWriteLetter), KkMenuItem("그림 그리기", KkIcons.Brush, onDraw)),
                )
            }
        }
        KkUnderlineTabs(LetterFilter.entries.map { it.label }, state.filter.ordinal, { onIntent(LetterIntent.Filter(LetterFilter.entries[it])) })
        if (state.drafts.isNotEmpty()) DraftRow(state.drafts, onOpenDrafts)
        when {
            state.loading -> repeat(4) { EnvelopeSkeleton() }
            // 빈 상태는 제목 + 동작만(설명 줄 없음).
            state.inbox.isEmpty() -> KkEmptyState(
                title = "아직 주고받은 편지가 없어요",
                description = "",
                icon = KkIcons.Mail,
                action = "편지 쓰기",
                onAction = { writeMenu = true },
            )
            state.visibleInbox.isEmpty() -> Text(
                "이 분류에는 아직 편지가 없어요", style = KkTheme.type.body, color = c.sub,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = KkSpacing.xxxl),
            )
            else -> state.months.forEach { (month, cards) ->
                Text(month, style = KkTheme.type.handL, color = c.ink, modifier = Modifier.padding(top = KkSpacing.sm))
                cards.forEach { card -> InboxEnvelope(card) { onIntent(LetterIntent.Open(card.letter.id)) } }
            }
        }
        Spacer(Modifier.height(KkSpacing.lg))
    }
}

/** 편지함 봉투 한 줄: 봉인 · 폴라로이드(실제 그림) · 소인 · 우표. */
@Composable
private fun InboxEnvelope(card: LetterCard, onClick: () -> Unit) {
    val mark = when (val e = card.envelope) {
        is Envelope.New -> KkEnvelopeMark.Seal(e.stamp)
        Envelope.Drawing -> KkEnvelopeMark.Polaroid(rememberDrawingThumb(card.thumb))
        is Envelope.Sent -> KkEnvelopeMark.Postmark(e.date)
        is Envelope.Received -> KkEnvelopeMark.Stamp(e.stamp)
    }
    KkEnvelopeRow(card.title, card.meta, mark, onClick)
}

/** 미니 폴라로이드에 넣을 그림 한 장(완성본, 42dp 칸). 스티커 · 사진 배경은 이 크기에서 빼고 그린다. */
@Composable
private fun rememberDrawingThumb(doc: DrawingDocument?): ImageBitmap? {
    val density = LocalDensity.current
    val side = with(density) { 84.dp.roundToPx() }
    return remember(doc, side) {
        doc?.let { d ->
            ImageBitmap(side, side).also { bitmap ->
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) { drawDocument(d) }
            }
        }
    }
}

/** 편지함 로딩(Figma 4 · 편지함 / 로딩): 봉투 자리 + Skeleton 두 줄 + 장식 자리. */
@Composable
private fun EnvelopeSkeleton() {
    val c = KkTheme.colors
    Row(
        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(KkRadius.sm)).background(c.surface)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            KkSkeleton(Modifier.fillMaxWidth(0.9f))
            KkSkeleton(Modifier.fillMaxWidth(0.4f))
        }
        Box(Modifier.size(44.dp, 54.dp).clip(RoundedCornerShape(KkRadius.full)).background(c.line))
    }
}

// ── 받은 편지 ─────────────────────────────────────────────────────────────

/**
 * 편지 읽기(Figma 4 · 받은 편지 `363:3666`): 위 막대(누가 보낸 편지 · 보낸 시각) → 보낸 사람이 고른 편지지(+ 소인) → 고마워 · 답장.
 * 편지지는 콘텐츠라 읽는 사람의 테마 · 다크 모드와 무관하게 [paperLook] 색 그대로다. 그림은 폴라로이드로 붙는다.
 */
@Composable
fun LetterReadScreen(open: OpenLetter, onIntent: (LetterIntent) -> Unit, onReply: () -> Unit, onReplyDrawing: () -> Unit = {}) {
    if (open.locked) {
        LockedLetterScreen(open, onIntent)
        return
    }
    val c = KkTheme.colors
    // 읽는 화면은 탭 바 없이 전체(Figma) — 아래 버튼이 제스처 바에 걸리지 않게.
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        ReadTopBar(
            open.title, open.subtitle, onBack = { onIntent(LetterIntent.Close) },
            trailing = if (open.mine) "삭제" else null, onTrailing = { onIntent(LetterIntent.Delete(open.letter.id)) },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = KkSpacing.md, bottom = KkSpacing.lg)) {
            LetterSheet(open, onIntent)
        }
        if (!open.mine) {
            Column(
                Modifier.fillMaxWidth().padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.sm, bottom = KkSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xxl),
            ) {
                // 받은 그림에서 "그림으로 답장"은 바로 그림판(planning.md §8).
                if (open.letter.drawingCount > 0) {
                    KkButton("그림으로 답장", onReplyDrawing, style = KkButtonStyle.Secondary, size = KkButtonSize.M)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                    KkSecondaryButton(
                        if (open.thanked) "고마워를 보냈어요" else "고마워",
                        onClick = { onIntent(LetterIntent.Thanks) }, modifier = Modifier.weight(if (open.thanked) 1.2f else 1f), enabled = !open.thanked,
                    )
                    KkPrimaryButton("답장 쓰기", onClick = onReply, modifier = Modifier.weight(if (open.thanked) 1f else 1.5f))
                }
            }
        }
    }
}

/** 읽기 위 막대(Figma Top Bar · Back): 뒤로 + 제목(Label/Bold) · 아래 줄(Caption) + 선택적 글자 동작. */
@Composable
private fun ReadTopBar(title: String, subtitle: String, onBack: () -> Unit, trailing: String? = null, onTrailing: () -> Unit = {}) {
    val c = KkTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = KkSpacing.xs, end = KkSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "뒤로 가기" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(KkIcons.Back, tint = c.ink, size = 15.8.dp, modifier = Modifier.width(8.8.dp)) }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = KkTheme.type.labelBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = KkTheme.type.caption, color = c.sub, maxLines = 1)
        }
        if (trailing != null) {
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onTrailing).padding(horizontal = KkSpacing.xs), contentAlignment = Alignment.Center) {
                Text(trailing, style = KkTheme.type.label, color = c.accent)
            }
        }
    }
}

/** 편지지 한 장: 화면 폭 종이 + 위 오른쪽 소인(보낸 사람 쪽 문구 · 잉크) + 줄 친 글 · 폴라로이드 그림 · 사진. */
@Composable
private fun LetterSheet(open: OpenLetter, onIntent: (LetterIntent) -> Unit) {
    val paper = Paper.of(open.letter.paper)
    val look = paper.look()
    val font = KkLetterFont.of(open.letter.font)
    val size = font.size(15f, KkLetterSize.of(open.letter.font))
    val body = KkTheme.type.body.copy(fontFamily = font.family(), fontSize = size, lineHeight = size * 1.7f, color = look.ink)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 480.dp).kkElevation(KkElevation.Low, RectangleShape)
            .background(look.background).border(1.dp, look.edge),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = KkSpacing.lg, bottom = KkSpacing.xxxl)) {
            // 소인 자리(64)만큼 위를 비운다. 군사우편 편지지는 왼쪽 위에 "군사우편" 표시.
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                if (paper == Paper.MAIL) {
                    Text(
                        "군사우편", style = KkTheme.type.labelBold, color = look.mark, maxLines = 1,
                        modifier = Modifier.border(1.dp, look.mark).padding(horizontal = 10.dp, vertical = KkSpacing.xxs),
                    )
                }
            }
            open.letter.title?.takeIf { it.isNotBlank() }?.let { RuledText(it, body.copy(fontWeight = FontWeight.Bold), look.rule) }
            open.letter.blocks.forEach { block ->
                when (block) {
                    is LetterBlock.Text -> RuledText(block.text, body, look.rule)
                    is LetterBlock.DrawingRef -> {
                        val doc = open.drawings[block.drawingId]
                        Box(Modifier.fillMaxWidth().padding(vertical = KkSpacing.xl), contentAlignment = Alignment.Center) {
                            if (doc == null) {
                                Text("그림을 받는 중이에요", style = KkTheme.type.body, color = look.ink, modifier = Modifier.padding(KkSpacing.xxl))
                            } else {
                                val replay = rememberReplayState(doc, autoPlay = block.drawingId in open.firstView)
                                KkPolaroid(
                                    Modifier.width(160.dp), tilt = 4f, caption = open.captions[block.drawingId],
                                    onReplay = if (replay.playing) null else replay::restart,
                                ) {
                                    // 그림 바탕은 그림 문서가 칠한다(콘텐츠).
                                    ReplayCanvas(doc, replay, Modifier.fillMaxSize(), onFinished = { onIntent(LetterIntent.DrawingSeen(block.drawingId)) })
                                }
                            }
                        }
                    }
                    is LetterBlock.Photo -> {
                        val bytes = open.photos[block.attachmentId]
                        val bitmap = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
                        Box(Modifier.fillMaxWidth().padding(vertical = KkSpacing.md)) {
                            if (bitmap != null) {
                                Image(bitmap, contentDescription = "사진", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                            } else {
                                Box(Modifier.fillMaxWidth().height(160.dp).background(look.rule), contentAlignment = Alignment.Center) {
                                    Text("사진을 받는 중이에요", style = KkTheme.type.body, color = look.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
        KkPostmark(KkPostmarkInk.of(open.senderRole), open.postmark, Modifier.align(Alignment.TopEnd).padding(top = KkSpacing.sm, end = KkSpacing.lg).rotate(-9f))
    }
}

/** 편지지 줄 위의 글: 줄마다 아래에 괘선을 긋는다(글자 크기가 바뀌어도 글줄에 맞는다). */
@Composable
private fun RuledText(text: String, style: TextStyle, rule: Color) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = style, onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().drawBehind {
            val l = layout ?: return@drawBehind
            for (i in 0 until l.lineCount) {
                val y = l.getLineBottom(i)
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        },
    )
}

/**
 * 아직 못 여는 예약 편지(Figma 받은 편지 · 예약 편지 · 아직 못 열어요): 잠긴 봉투 + 열리는 때 + 남은 시간.
 * 열리는 시각이 되면 그 자리에서 편지를 연다.
 */
@Composable
private fun LockedLetterScreen(open: OpenLetter, onIntent: (LetterIntent) -> Unit) {
    val c = KkTheme.colors
    val at = open.opensAt ?: return
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(open.letter.id, at) {
        val opens = at.toInstant(SEOUL)
        while (true) {
            now = Clock.System.now()
            // 시각이 되면 다시 연다. 뷰모델 시계가 조금 늦으면 아직 잠긴 채 돌아오므로 열릴 때까지 몇 초마다 다시 묻는다.
            if (now >= opens) onIntent(LetterIntent.Open(open.letter.id))
            delay(if (now >= opens) 3_000 else 1_000)
        }
    }
    Column(Modifier.fillMaxSize().background(c.canvas).navigationBarsPadding()) {
        ReadTopBar(open.title, open.subtitle, onBack = { onIntent(LetterIntent.Close) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xxl, vertical = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.xxl),
        ) {
            // 누를 수 없는 봉투: 접근성에는 "잠긴 편지, 첫 줄, 보낸 사람 · 도착" 한 덩어리로 읽힌다.
            Box(
                Modifier.clearAndSetSemantics { contentDescription = "잠긴 편지, ${open.envelopeLine}, ${open.envelopeMeta}" },
                contentAlignment = Alignment.Center,
            ) {
                KkEnvelopeRow(open.envelopeLine, open.envelopeMeta, KkEnvelopeMark.Stamp(open.stamp), onClick = {})
                // 봉인 대신 자물쇠: 아직 열 수 없다.
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    KkIcon(KkIcons.Lock, tint = c.canvas, size = 20.dp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                Text("${opensAt(at)}에 열려요", style = KkTheme.type.handL, color = c.ink, textAlign = TextAlign.Center)
                Text(countdown(now.toLocalDateTime(SEOUL), at), style = KkTheme.type.title, color = c.accent, textAlign = TextAlign.Center)
            }
        }
        KkSecondaryButton(
            "열리면 알려 주세요", onClick = { onIntent(LetterIntent.NotifyWhenOpen) },
            modifier = Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, bottom = KkSpacing.lg),
        )
    }
}

// ── 편지 쓰기 ─────────────────────────────────────────────────────────────

/**
 * 편지 쓰기(Figma 4 · 편지 쓰기): 받는 사람 · 편지지 · 제목과 줄 친 본문 · 붙인 그림 · 사진(합쳐 최대 10) · 도구 · 예약 / 바로 보내기.
 * [scheduleFirst] 면 예약 시트를 먼저 띄운다(만들기 › 예약 메시지).
 */
@Composable
fun LetterComposeRoute(
    onAddDrawing: () -> Unit,
    onClose: () -> Unit,
    scheduleFirst: Boolean = false,
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    /** 꽃신 평생 화면 열기(잠긴 편지지를 눌렀을 때). */
    onOpenLifetime: (() -> Unit)? = null,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.draft
    val lifetimeOn by (org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
        ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
    val focus = LocalFocusManager.current
    var lockedPaper by remember { mutableStateOf<Paper?>(null) }
    var lockedFont by remember { mutableStateOf<String?>(null) }
    val c = KkTheme.colors
    val paper = Paper.of(d.paper)
    var scheduling by remember { mutableStateOf(scheduleFirst) }
    var stickers by remember { mutableStateOf(false) }
    var fonts by remember { mutableStateOf(false) }
    var photoSource by remember { mutableStateOf(false) }
    val pickPhoto = photoPicker?.invoke { viewModel.onIntent(LetterIntent.AddPhoto(it)) }
    // 쓰던 글은 멈추고 2초 뒤 · 화면을 나갈 때(닫기 · 그림판 · 앱 전환) 이 기기에 저장한다.
    DisposableEffect(viewModel) { onDispose { viewModel.onIntent(LetterIntent.LeaveCompose) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onIntent(LetterIntent.LeaveCompose) }
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = KkSpacing.xl)) {
            KkTopBar("편지 쓰기", onLeading = onClose, leading = KkTopBarLeading.Close)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = KkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text("받는 사람", style = KkTheme.type.label, color = c.sub)
                    KkStatusChip(state.partnerName, selected = false, onClick = {}, dot = c.danger)
                }
                PaperPicker(paper, lifetimeOn, onLocked = { lockedPaper = it }) { viewModel.onIntent(LetterIntent.ChoosePaper(it)) }
                PaperCard(d, paper, state.draftThumbs, state.draftPhotos, viewModel::onIntent)
                ComposeTools(
                    d, canPhoto = pickPhoto != null,
                    onDrawing = { focus.clearFocus(); onAddDrawing() }, onPhoto = { focus.clearFocus(); photoSource = true },
                    onSticker = { focus.clearFocus(); stickers = true }, onFont = { focus.clearFocus(); fonts = true },
                )
                d.revealAt?.let { at ->
                    Text(
                        "${at.month.ordinal + 1}월 ${at.day}일 ${at.time.hhmm()}에 도착해요 · 그 전에는 상대 화면 어디에도 보이지 않아요",
                        style = KkTheme.type.caption, color = c.sub,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = KkSpacing.sm), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkSecondaryButton("예약 보내기", onClick = { focus.clearFocus(); scheduling = true }, modifier = Modifier.weight(1f), enabled = d.canSend || scheduleFirst)
                KkPrimaryButton("바로 보내기", onClick = {
                    viewModel.onIntent(LetterIntent.Schedule(null))
                    viewModel.onIntent(LetterIntent.Send)
                    onClose()
                }, modifier = Modifier.weight(1f), enabled = d.canSend, loading = d.sending)
            }
        }
        ToastHost(viewModel.toasts, viewModel::toastShown) {}
    }
    val resumable = state.resumable
    if (resumable.isNotEmpty()) {
        ResumeDraftSheet(
            resumable,
            onResume = { viewModel.onIntent(LetterIntent.ResumeDraft(it)) },
            onNew = { viewModel.onIntent(LetterIntent.DismissResume) },
        )
    }
    if (scheduling) {
        KkScheduleSheet(
            initial = d.revealAt,
            confirmLabel = if (d.canSend) null else "이 시각으로 정하기",
            onDismiss = { scheduling = false },
            onPick = { at ->
                viewModel.onIntent(LetterIntent.Schedule(at))
                scheduling = false
                if (d.canSend) {
                    viewModel.onIntent(LetterIntent.Send)
                    onClose()
                }
            },
        )
    }
    if (photoSource && pickPhoto != null) {
        KkModalSheet({ photoSource = false }) {
            KkSheetHeader("사진", "커서 자리에 글자처럼 들어가요 · 그림과 합쳐 최대 ${Letter.MAX_ATTACHMENTS}장")
            KkSheetOption("사진첩에서 고르기", false, { photoSource = false; pickPhoto(false) })
            KkSheetOption("카메라로 찍기", false, { photoSource = false; pickPhoto(true) })
        }
    }
    if (fonts) {
        // 글꼴 시트(화면 기능명세 2-10): 크기(작게 · 보통 · 크게) + 글꼴 11종(꽃신 평생은 "평생" 표시).
        val curFont = com.sadturtleman.flowershoes.core.designsystem.KkLetterFont.of(d.font)
        val curSize = com.sadturtleman.flowershoes.core.designsystem.KkLetterSize.of(d.font)
        val choose = { f: com.sadturtleman.flowershoes.core.designsystem.KkLetterFont, z: com.sadturtleman.flowershoes.core.designsystem.KkLetterSize ->
            viewModel.onIntent(LetterIntent.ChooseFont(com.sadturtleman.flowershoes.core.designsystem.KkLetterSize.encode(f, z) ?: KkLetterFont.SANS.id))
        }
        // Figma 편지 쓰기 · 글꼴: 크기 칩 세 개 + 줄마다 "보고 싶은 밤"(그 글꼴) · 이름 · 평생 알약 · 라디오.
        KkModalSheet({ fonts = false }) {
            Text("글꼴", style = KkTheme.type.title, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                com.sadturtleman.flowershoes.core.designsystem.KkLetterSize.entries.forEach { z ->
                    KkStatusChip(z.label, selected = z == curSize, onClick = { choose(curFont, z) }, dot = if (z == curSize) c.surface else c.danger)
                }
            }
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                KkLetterFont.listed.forEach { f ->
                    val on = curFont == f
                    val locked = f.lifetime && !lifetimeOn
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .clickable(role = Role.RadioButton) {
                                if (locked) { lockedFont = f.label; fonts = false } else { choose(f, curSize); fonts = false }
                            }
                            .semantics { selected = on; contentDescription = f.label + (if (f.lifetime) ", 꽃신 평생" else "") + (if (locked) ", 잠김" else "") },
                        horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("보고 싶은 밤", style = KkTheme.type.body.copy(fontFamily = f.family(), fontSize = f.size(17f)), color = c.ink)
                        Text(f.label, style = KkTheme.type.caption, color = c.sub, modifier = Modifier.weight(1f))
                        if (f.lifetime) {
                            Text(
                                "평생", style = KkTheme.type.label, color = if (locked) c.sub else c.accent,
                                modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(c.bar).padding(horizontal = KkSpacing.sm, vertical = 2.dp),
                            )
                        }
                        com.sadturtleman.flowershoes.core.designsystem.KkRadio(on)
                    }
                }
            }
        }
    }
    lockedFont?.let { name ->
        KkModalSheet({ lockedFont = null }) {
            KkSheetHeader("$name 글꼴", "꽃신 평생 글꼴이에요. 받는 사람은 이용권 없이도 그대로 보여요")
            if (onOpenLifetime != null) KkPrimaryButton("꽃신 평생 보기", { lockedFont = null; onOpenLifetime() }, Modifier.fillMaxWidth())
        }
    }
    if (stickers) {
        KkModalSheet({ stickers = false }) {
            KkSheetHeader("스티커", "커서 자리에 붙여요")
            StickerGrid { glyph ->
                val at = d.cursor.coerceIn(0, d.text.length)
                viewModel.onIntent(LetterIntent.EditText(d.text.substring(0, at) + glyph + d.text.substring(at), at + glyph.length, external = true))
                stickers = false
            }
        }
    }
    lockedPaper?.let { p ->
        KkModalSheet({ lockedPaper = null }) {
            KkSheetHeader("${p.label} 편지지", "꽃신 평생 편지지예요. 받는 사람은 이용권 없이도 그대로 보여요")
            if (onOpenLifetime != null) KkPrimaryButton("꽃신 평생 보기", { lockedPaper = null; onOpenLifetime() }, Modifier.fillMaxWidth())
        }
    }
}

/**
 * 편지지 열 장(가로로 넘김). 고른 것은 브랜드 테두리 + 브랜드 글자. 꽃신 평생 편지지는 "평생" 표시 —
 * 이용권이 없으면([lifetimeOn] false) 누를 때 [onLocked].
 */
@Composable
private fun PaperPicker(selected: Paper, lifetimeOn: Boolean, onLocked: (Paper) -> Unit, onPick: (Paper) -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        Paper.entries.forEach { p ->
            val on = p == selected
            val shape = RoundedCornerShape(KkRadius.md)
            val locked = p.lifetime && !lifetimeOn
            Column(
                Modifier.width(60.dp).clickable(role = Role.RadioButton) { if (locked) onLocked(p) else onPick(p) }
                    .semantics { this.selected = on; contentDescription = "편지지 ${p.label}" + (if (p.lifetime) ", 꽃신 평생" else "") + (if (locked) ", 잠김" else "") },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs),
            ) {
                val bordered = p == Paper.GRID || p == Paper.NIGHT
                Box(
                    Modifier.fillMaxWidth().height(64.dp)
                        .then(if (bordered) Modifier else Modifier.kkElevation(KkElevation.Low, shape))
                        .clip(shape).background(p.background(c))
                        .then(
                            when {
                                on -> Modifier.border(2.dp, c.accent, shape)
                                bordered -> Modifier.border(1.dp, c.line, shape)
                                else -> Modifier
                            },
                        ),
                ) {
                    // 꽃신 평생 편지지: 오른쪽 위 작은 표시(이름은 그대로).
                    if (p.lifetime) {
                        Text(
                            "평생", style = KkTheme.type.label, color = c.surface,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(KkRadius.full))
                                .background(if (locked) c.sub else c.accent).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(p.label, style = KkTheme.type.caption, maxLines = 1, color = if (on) c.accent else c.sub)
            }
        }
    }
}

/** 편지지(Paper): 제목 + 본문. 본문 속 그림 · 사진은 글자처럼 커서 자리에 들어간다(Figma 최종 정리 › 편지 쓰기). */
@Composable
private fun PaperCard(
    d: ComposeDraft,
    paper: Paper,
    thumbs: Map<String, DrawingDocument>,
    photos: Map<String, ByteArray>,
    onIntent: (LetterIntent) -> Unit,
) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    // 꽃신 평생 편지지는 밝은 종이라 글자는 늘 진한 색(다크 모드에서도).
    val ink = when {
        paper == Paper.NIGHT -> c.canvas
        paper.lifetime -> Color(0xFF2E2A26)
        else -> c.ink
    }
    val hint = when {
        paper == Paper.NIGHT -> c.canvas.copy(alpha = 0.5f)
        paper.lifetime -> Color(0xFF7A6E60)
        else -> c.sub
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 341.dp).kkElevation(KkElevation.Mid, shape).clip(shape).background(paper.background(c)).padding(KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        BasicTextField(
            value = d.title,
            onValueChange = { onIntent(LetterIntent.EditTitle(it)) },
            textStyle = KkTheme.type.title.copy(color = ink),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "제목" },
            decorationBox = { inner ->
                Box { if (d.title.isEmpty()) Text("제목", style = KkTheme.type.title, color = hint); inner() }
            },
        )
        val font = KkLetterFont.of(d.font)
        // 글 속 그림이 줄 높이를 키우도록 줄 간격은 글꼴 기본값(고정 줄 높이는 큰 글자를 겹치게 한다).
        val body = KkTheme.type.body.copy(fontFamily = font.family(), fontSize = font.size(16f, com.sadturtleman.flowershoes.core.designsystem.KkLetterSize.of(d.font)), lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp, color = ink)
        var field by remember { mutableStateOf(TextFieldValue(d.text, TextRange(d.cursor.coerceIn(0, d.text.length)))) }
        // 그림 · 사진을 넣었거나 쓰던 편지를 불러온 경우처럼 바깥에서 글이 바뀌면 맞춘다.
        // 직접 치는 글은 이 칸이 원본이다 — 화면에 늦게 도착한 옛 상태로 되돌리면 글자가 빠진다.
        LaunchedEffect(d.rev) {
            if (field.text != d.text) field = TextFieldValue(d.text, TextRange(d.cursor.coerceIn(0, d.text.length)))
        }
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        var selectedObj by remember { mutableStateOf<Int?>(null) }
        val objectStyle = remember(d.objects) { inlineObjectTransformation(d.objects) }
        Box(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = field,
                onValueChange = { v ->
                    field = v
                    selectedObj = null
                    onIntent(LetterIntent.EditText(v.text, v.selection.end))
                },
                textStyle = body,
                cursorBrush = SolidColor(c.accent),
                visualTransformation = objectStyle,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 256.dp).semantics {
                    contentDescription = "편지 내용"
                    // 자리 확보용 투명 글리프를 읽지 않도록 원문을 접근성 값으로 제공한다.
                    editableText = AnnotatedString(field.text)
                },
                decorationBox = { inner ->
                    Box { if (d.text.isEmpty()) Text("마음을 적어 보세요", style = body.copy(color = hint)); inner() }
                },
            )
            // 글 속 자리(OBJ) 위에 그림 · 사진을 그린다. 누르면 고른 테두리 + 빼기.
            val density = LocalDensity.current
            val l = layout
            if (l != null && l.layoutInput.text.length == d.text.length) {
                var k = -1
                d.text.forEachIndexed { i, ch ->
                    if (ch != OBJ) return@forEachIndexed
                    val index = ++k
                    val key = d.objects.getOrNull(index) ?: return@forEachIndexed
                    val drawing = key.startsWith("d:")
                    // 사진과 큰 그림이 같은 줄에 있어도 사진을 줄 전체 높이로 늘리지 않는다.
                    // 공백의 커서 좌표는 줄 끝/서로 다른 크기 span에서 접힐 수 있어 글리프 영역을 쓴다.
                    val line = l.getLineForOffset(i)
                    val glyph = l.getBoundingBox(i)
                    val height = (glyph.width * 0.75f).coerceAtMost(l.getLineBottom(line) - l.getLineTop(line))
                    val r = androidx.compose.ui.geometry.Rect(glyph.left, l.getLineBottom(line) - height, glyph.right, l.getLineBottom(line))
                    val pad = with(density) { (if (drawing) 4.dp else 2.dp).toPx() }
                    Box(
                        Modifier
                            .offset { IntOffset((r.left + pad).toInt(), (r.top + pad).toInt()) }
                            .size(with(density) { (r.width - pad * 2).coerceAtLeast(1f).toDp() }, with(density) { (r.height - pad * 2).coerceAtLeast(1f).toDp() })
                            .clickable(role = Role.Button) { selectedObj = if (selectedObj == index) null else index }
                            .semantics { contentDescription = if (drawing) "글 속 그림" else "글 속 사진" },
                    ) {
                        InlineObject(key, thumbs, photos, Modifier.fillMaxSize())
                        if (selectedObj == index) {
                            Box(Modifier.fillMaxSize().border(1.5.dp, c.accent))
                            listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                                Box(Modifier.align(a).size(8.dp).background(Color.White).border(1.5.dp, c.accent))
                            }
                            RemoveBadge { selectedObj = null; onIntent(LetterIntent.RemoveObject(index)) }
                        }
                    }
                }
            }
        }
    }
}

/** 글 속 그림 · 사진 한 칸. */
@Composable
private fun InlineObject(key: String, thumbs: Map<String, DrawingDocument>, photos: Map<String, ByteArray>, modifier: Modifier) {
    val c = KkTheme.colors
    if (key.startsWith("d:")) {
        Box(modifier.clip(RoundedCornerShape(KkRadius.sm)).background(Color.White).padding(4.dp), contentAlignment = Alignment.Center) {
            thumbs[key.removePrefix("d:")]?.let { DrawingView(it) } ?: KkIcon(KkIcons.Pen, tint = c.sub)
        }
    } else {
        val bytes = photos[key.removePrefix("p:")]
        val bitmap = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
        Box(modifier.clip(RoundedCornerShape(KkRadius.sm)).background(c.line), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else KkIcon(KkIcons.Photo, tint = c.sub, size = 18.dp)
        }
    }
}

/**
 * 글 속 자리 글자의 모양: 그림은 큰 칸, 사진은 작은 칸. 투명 전각 글리프를 옆으로 늘려 자리만 잡는다 —
 * 글자 수 · 커서 위치는 그대로(1:1).
 */
private fun inlineObjectTransformation(objects: List<String>) = VisualTransformation { text ->
    val b = AnnotatedString.Builder()
    var k = 0
    text.text.forEach { ch ->
        if (ch == OBJ) {
            val drawing = objects.getOrNull(k++)?.startsWith("d:") == true
            b.withStyle(
                if (drawing) SpanStyle(color = Color.Transparent, fontSize = 112.sp, textGeometricTransform = TextGeometricTransform(scaleX = DRAWING_WIDEN))
                else SpanStyle(color = Color.Transparent, fontSize = 26.sp, textGeometricTransform = TextGeometricTransform(scaleX = PHOTO_WIDEN)),
            ) { append(OBJECT_GLYPH) }
        } else {
            b.append(ch)
        }
    }
    TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
}

// 전각 공백은 iOS 텍스트 레이아웃에서 줄 끝 너비가 접힌다. 비공백 글리프로 한 칸을 보장한다.
private const val OBJECT_GLYPH = 'Ｍ'
/** 자리 글자를 옆으로 늘려 칸 비율을 맞춘다(그림 ≈ 4:3, 사진 ≈ 4:3). */
private const val DRAWING_WIDEN = 1.75f
private const val PHOTO_WIDEN = 1.5f

/** 도구(Figma 편지 쓰기): 그림 · 사진 · 스티커 · 글꼴 아이콘 + 글자 수. */
@Composable
private fun ComposeTools(d: ComposeDraft, canPhoto: Boolean, onDrawing: () -> Unit, onPhoto: () -> Unit, onSticker: () -> Unit, onFont: () -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ToolButton("그림", d.canAddDrawing, onDrawing) { KkIcon(KkIcons.Pen, tint = c.ink) }
        if (canPhoto) ToolButton("사진", d.canAddPhoto, onPhoto) { KkIcon(KkIcons.Photo, tint = c.ink) }
        ToolButton("스티커", true, onSticker) { KkIcon(KkIcons.Sticker, tint = c.ink) }
        ToolButton("글꼴", true, onFont) { Text("T", style = KkTheme.type.title, color = c.ink) }
        Spacer(Modifier.weight(1f))
        Text("${d.text.length} / ${ComposeDraft.MAX_CHARS}", style = KkTheme.type.caption, color = c.sub)
    }
}

@Composable
private fun ToolButton(label: String, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(KkRadius.sm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }.alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun BoxScope.RemoveBadge(onClick: () -> Unit) {
    val c = KkTheme.colors
    Box(
        Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "그림 빼기" },
        contentAlignment = Alignment.Center,
    ) { KkIcon(KkIcons.Close, tint = c.canvas, size = 12.dp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerGrid(onPick: (String) -> Unit) {
    val c = KkTheme.colors
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        STICKERS.values.forEach { glyph ->
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.line).clickable(role = Role.Button) { onPick(glyph) },
                contentAlignment = Alignment.Center,
            ) { Text(glyph, fontSize = 28.sp) }
        }
    }
}

// ── 공용 ──────────────────────────────────────────────────────────────────

/** 한 줄 알림(Toast): 아래에서 3초. 편지를 가리키면 "보기". */
@Composable
private fun BoxScope.ToastHost(toasts: SharedFlow<LetterToast>, onShown: () -> Unit, onOpen: (String) -> Unit) {
    var current by remember { mutableStateOf<LetterToast?>(null) }
    LaunchedEffect(toasts) { toasts.collect { current = it; onShown() } }
    LaunchedEffect(current) { if (current != null) { delay(3_000); current = null } }
    current?.let { t ->
        KkToast(
            t.message,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            type = if (t.openLetterId != null) KkToastType.Success else KkToastType.Default,
            action = t.openLetterId?.let { "보기" },
            onAction = { t.openLetterId?.let(onOpen); current = null },
        )
    }
}

internal fun Paper.background(c: KkColors): Color = when (this) {
    Paper.PLAIN -> c.surface
    Paper.BLOSSOM -> c.bar
    Paper.OLIVE -> c.bar
    Paper.GRID -> c.line
    Paper.NIGHT -> c.ink
    // 꽃신 평생 편지지: 테마와 무관한 종이 색(Figma 📖 · 편지지 시안).
    Paper.MANUSCRIPT -> Color(0xFFFFF8EC)
    Paper.KRAFT -> Color(0xFFE9DCC4)
    Paper.MAIL -> Color(0xFFFFFDF7)
    Paper.NOTE -> Color(0xFFFFFFFF)
    Paper.SKY -> Color(0xFFDDEFFA)
}

private fun Paper.rule(c: KkColors): Color = when (this) {
    Paper.PLAIN -> c.line
    Paper.BLOSSOM -> c.accent.copy(alpha = 0.35f)
    Paper.OLIVE -> c.accent.copy(alpha = 0.25f)
    Paper.GRID -> c.line
    Paper.NIGHT -> c.canvas.copy(alpha = 0.18f)
    Paper.MANUSCRIPT -> Color(0x66D9776B)
    Paper.KRAFT -> Color(0xFFCFC3AE)
    Paper.MAIL -> Color(0x55C8463D)
    Paper.NOTE -> Color(0xFFA9C4E8)
    Paper.SKY -> Color(0x99FFFFFF)
}

