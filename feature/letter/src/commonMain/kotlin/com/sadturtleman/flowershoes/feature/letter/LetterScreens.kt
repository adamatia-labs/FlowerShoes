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
import com.sadturtleman.flowershoes.core.designsystem.KkScheduleDialogs
import com.sadturtleman.flowershoes.core.designsystem.KkBanner
import com.sadturtleman.flowershoes.core.designsystem.KkDialog
import com.sadturtleman.flowershoes.core.designsystem.KkDialogHost
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
    /** 혼자 모드 › 연결하기: 초대(연결) 화면. */
    onConnect: () -> Unit = {},
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        // 편지를 열면 편지함 장이 넘어가고, 닫으면 다시 덮인다.
        KkPageFlip(state.open, pageKey = { it?.letter?.id ?: "inbox" }, isForward = { _, to -> to != null }) { open ->
            when {
                open != null && open.drawingOnly -> DrawingLetterScreen(open, viewModel::onIntent, onReplyDrawing)
                open != null -> LetterReadScreen(open, viewModel::onIntent, onReply, onReplyDrawing)
                else -> LetterInboxScreen(state, viewModel::onIntent, onWriteLetter, onDraw, onOpenDrafts, onConnect)
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
    onConnect: () -> Unit = {},
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
        // 혼자 모드(Figma 편지함 · 혼자 모드): 연결 전 안내 + 연결하기. 보조 설명 줄은 없다(design.md).
        if (state.solo && !state.loading) KkBanner(soloBannerTitle(state.partnerName), body = "", action = "연결하기", onAction = onConnect)
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
        Envelope.Waiting -> KkEnvelopeMark.Waiting
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
 * 편지 쓰기(Figma 4 · 편지 쓰기 `361:2907`): 위 막대("민준에게" · 임시저장) → 보낸 사람이 고른 편지지(콘텐츠 색, [Paper.look]) →
 * 편지지 줄(예약했으면 도착 칩) → 도구 → 예약 · 보내기. 보내는 중엔 버튼에 스피너, 보내면 봉투 화면, 못 보내면 쓰던 편지 그대로 오류 알림.
 * 닫기(X)는 쓰던 글이 있으면 "쓰던 편지는 임시저장할까요?"를 묻는다. 뒤로 가기 · 앱 전환은 묻지 않고 저장한다.
 * [scheduleFirst] 면 예약(날짜 → 시간)을 먼저 띄운다(만들기 › 예약 메시지).
 */
@Composable
fun LetterComposeRoute(
    onAddDrawing: () -> Unit,
    onClose: () -> Unit,
    scheduleFirst: Boolean = false,
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    /** 꽃신 평생 화면 열기(평생 안내 시트 › 꽃신 평생 보기). */
    onOpenLifetime: (() -> Unit)? = null,
    /** 보냄 화면 › 편지함으로. */
    onOpenInbox: () -> Unit = onClose,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.draft
    val koin = org.koin.compose.getKoin()
    val lifetimeOn by (koin.getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()?.lifetimeOn
        ?: remember { kotlinx.coroutines.flow.flowOf(false) }).collectAsStateWithLifecycle(false)
    val lifetimeStore = remember { koin.getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>() }
    var price by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lifetimeStore) { price = runCatching { lifetimeStore?.price() }.getOrNull() }
    val focus = LocalFocusManager.current
    var lifetimeSheet by remember { mutableStateOf(false) }
    var paperSheet by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(scheduleFirst) }
    var stickers by remember { mutableStateOf(false) }
    var fonts by remember { mutableStateOf(false) }
    var photoSource by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val pickPhoto = photoPicker?.invoke { viewModel.onIntent(LetterIntent.AddPhoto(it)) }
    // 쓰던 글은 멈추고 2초 뒤 · 화면을 나갈 때(닫기 · 그림판 · 앱 전환) 이 기기에 저장한다.
    DisposableEffect(viewModel) { onDispose { viewModel.onIntent(LetterIntent.LeaveCompose) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onIntent(LetterIntent.LeaveCompose) }
    when (val done = state.done) {
        is ComposeDone.Sent -> {
            LetterSentScreen(done) {
                viewModel.onIntent(LetterIntent.DismissDone)
                onOpenInbox()
            }
            return
        }
        // 예약은 "11월 1일 9시에 도착해요" 알림만 남기고 닫는다(알림은 편지함에서 이어서 보인다).
        ComposeDone.Scheduled -> LaunchedEffect(done) {
            viewModel.onIntent(LetterIntent.DismissDone)
            onClose()
        }
        null -> Unit
    }
    val requestClose = {
        focus.clearFocus()
        if (d.isEmpty || d.sending) onClose() else leaving = true
    }
    Box(Modifier.fillMaxSize().background(KkTheme.colors.canvas)) {
        LetterComposeScreen(
            state = state,
            lifetimeOn = lifetimeOn,
            canPhoto = pickPhoto != null,
            onIntent = viewModel::onIntent,
            onClose = requestClose,
            onSchedule = { focus.clearFocus(); scheduling = true },
            onDrawing = { focus.clearFocus(); onAddDrawing() },
            onPhoto = { focus.clearFocus(); photoSource = true },
            onSticker = { focus.clearFocus(); stickers = true },
            onFont = { focus.clearFocus(); fonts = true },
            onMorePapers = { focus.clearFocus(); paperSheet = true },
        )
        ToastHost(viewModel.toasts, viewModel::toastShown) {}
        if (leaving) {
            // Figma 편지 쓰기 · 나가기 확인: 지우기 · 저장하고 나가기. 바깥을 누르면 계속 쓴다.
            KkDialogHost(onDismiss = { leaving = false }) {
                KkDialog(
                    title = "쓰던 편지는 임시저장할까요?", body = "",
                    cancel = "지우기", onCancel = {
                        leaving = false
                        viewModel.onIntent(LetterIntent.DeleteDraft(state.draftId))
                        onClose()
                    },
                    confirm = "저장하고 나가기", onConfirm = {
                        leaving = false
                        onClose()
                    },
                )
            }
        }
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
        // Figma 편지 쓰기 · 예약: 날짜(모달 캘린더) → 시간(입력형). 고르면 "예약해서 보내기"로 바뀐다.
        KkScheduleDialogs(
            initial = d.revealAt,
            onDismiss = { scheduling = false },
            onPick = { at ->
                viewModel.onIntent(LetterIntent.Schedule(at))
                scheduling = false
            },
        )
    }
    if (photoSource && pickPhoto != null) {
        KkModalSheet({ photoSource = false }) {
            KkSheetHeader("사진")
            KkSheetOption("사진첩에서 고르기", false, { photoSource = false; pickPhoto(false) })
            KkSheetOption("카메라로 찍기", false, { photoSource = false; pickPhoto(true) })
        }
    }
    if (fonts) {
        // 글꼴 시트(화면 기능명세 2-10): 크기(작게 · 보통 · 크게) + 글꼴 11종(꽃신 평생은 "평생" 표시).
        val c = KkTheme.colors
        val curFont = KkLetterFont.of(d.font)
        val curSize = KkLetterSize.of(d.font)
        val choose = { f: KkLetterFont, z: KkLetterSize ->
            viewModel.onIntent(LetterIntent.ChooseFont(KkLetterSize.encode(f, z) ?: KkLetterFont.SANS.id))
        }
        // Figma 편지 쓰기 · 글꼴: 크기 칩 세 개 + 줄마다 "보고 싶은 밤"(그 글꼴) · 이름 · 평생 알약 · 라디오.
        KkModalSheet({ fonts = false }) {
            Text("글꼴", style = KkTheme.type.title, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkLetterSize.entries.forEach { z ->
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
                                fonts = false
                                if (locked) lifetimeSheet = true else choose(f, curSize)
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
    if (paperSheet) {
        PaperSheet(
            Paper.of(d.paper), lifetimeOn,
            onPick = { paperSheet = false; viewModel.onIntent(LetterIntent.ChoosePaper(it)) },
            onLocked = { paperSheet = false; lifetimeSheet = true },
            onDismiss = { paperSheet = false },
        )
    }
    if (lifetimeSheet) {
        LifetimeSheet(
            partner = state.partnerName, price = price ?: LIFETIME_PRICE,
            onOpen = onOpenLifetime?.let { open -> { lifetimeSheet = false; open() } },
            onDismiss = { lifetimeSheet = false },
        )
    }
    if (stickers) {
        KkModalSheet({ stickers = false }) {
            KkSheetHeader("스티커")
            StickerGrid { glyph ->
                val at = d.cursor.coerceIn(0, d.text.length)
                viewModel.onIntent(LetterIntent.EditText(d.text.substring(0, at) + glyph + d.text.substring(at), at + glyph.length, external = true))
                stickers = false
            }
        }
    }
}

/** 스토어 가격을 못 읽었을 때(설정 › 꽃신 평생과 같은 값). */
private const val LIFETIME_PRICE = "7,900원"

/**
 * 편지 쓰기 화면 몸통(시트 · 확인 창 빼고). 화면 상태는 [state] 하나로 정해진다 — 빈 편지지 · 쓰는 중 · 예약 · 설정됨 · 보내는 중.
 */
@Composable
fun LetterComposeScreen(
    state: LetterState,
    lifetimeOn: Boolean,
    canPhoto: Boolean,
    onIntent: (LetterIntent) -> Unit,
    onClose: () -> Unit,
    onSchedule: () -> Unit,
    onDrawing: () -> Unit = {},
    onPhoto: () -> Unit = {},
    onSticker: () -> Unit = {},
    onFont: () -> Unit = {},
    onMorePapers: () -> Unit = {},
) {
    val c = KkTheme.colors
    val d = state.draft
    val paper = Paper.of(d.paper)
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding().imePadding()) {
        KkTopBar(
            "${state.partnerName}에게", onLeading = onClose, leading = KkTopBarLeading.Close,
            modifier = Modifier.padding(horizontal = KkSpacing.xs),
            trailingText = "임시저장", trailingEnabled = !d.isEmpty && !d.sending, onTrailing = { onIntent(LetterIntent.SaveDraft) },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = KkSpacing.xs)) {
            PaperCard(d, paper, state.draftThumbs, state.draftPhotos, onIntent, partner = state.partnerName)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
        ) {
            val at = d.revealAt
            if (at != null) ScheduledChip(at, onEdit = onSchedule, onClear = { onIntent(LetterIntent.Schedule(null)) })
            else PaperRow(paper, onPick = { onIntent(LetterIntent.ChoosePaper(it)) }, onMore = onMorePapers)
            ComposeTools(d, canPhoto = canPhoto, onDrawing = onDrawing, onPhoto = onPhoto, onSticker = onSticker, onFont = onFont)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                KkButton("예약", onSchedule, Modifier.width(100.dp), style = KkButtonStyle.Ghost, enabled = !d.sending)
                KkButton(
                    if (at != null) "예약해서 보내기" else "${state.partnerName}에게 보내기",
                    { onIntent(LetterIntent.Send) }, Modifier.weight(1f),
                    enabled = d.canSend || d.sending, loading = d.sending, loadingText = "보내는 중…",
                )
            }
        }
    }
}

/** 예약 · 설정됨: 시계 + "11월 1일 (일) 오전 9시 도착" 칩. 누르면 다시 고르고, ×는 예약을 지운다. */
@Composable
private fun ScheduledChip(at: LocalDateTime, onEdit: () -> Unit, onClear: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.full)
    Row(
        Modifier.clip(shape).background(c.tag).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clickable(role = Role.Button, onClick = onEdit).heightIn(min = 40.dp).padding(start = 14.dp, end = KkSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically,
        ) {
            KkIcon(KkIcons.Clock, tint = c.ink, size = 16.dp)
            Text(arrivalLabel(at), style = KkTheme.type.labelBold, color = c.ink)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear).semantics { contentDescription = "예약 지우기" },
            contentAlignment = Alignment.Center,
        ) { KkIcon(KkIcons.Close, tint = c.sub, size = 10.dp) }
    }
}

/**
 * 편지지 줄(Figma 편지 쓰기): "편지지" + 무료 편지지 견본 + "+5 평생"(편지지 시트). 꽃신 평생 편지지를 골랐으면 그 견본도 줄에 둔다.
 */
@Composable
private fun PaperRow(selected: Paper, onPick: (Paper) -> Unit, onMore: () -> Unit) {
    val c = KkTheme.colors
    val shown = Paper.entries.filter { !it.lifetime } + listOfNotNull(selected.takeIf { it.lifetime })
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xxs), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("편지지", style = KkTheme.type.caption, color = c.sub, modifier = Modifier.padding(end = KkSpacing.sm))
        shown.forEach { p ->
            Box(
                Modifier.size(44.dp).clickable(role = Role.RadioButton) { onPick(p) }
                    .semantics { this.selected = p == selected; contentDescription = "편지지 ${p.label}" },
                contentAlignment = Alignment.Center,
            ) { PaperSwatch(p, on = p == selected) }
        }
        Text(
            "+${Paper.entries.count { it.lifetime }} 평생", style = KkTheme.type.caption, color = c.sub,
            modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(KkRadius.sm)).clickable(role = Role.Button, onClick = onMore)
                .padding(horizontal = KkSpacing.xs, vertical = 15.dp),
        )
    }
}

/** 편지지 견본(30×38): 종이 색 + 괘선 네 줄. 고른 것은 accent 2 테두리. 색은 편지지 그대로(테마 · 다크 무관). */
@Composable
private fun PaperSwatch(p: Paper, on: Boolean, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 30.dp, height: androidx.compose.ui.unit.Dp = 38.dp) {
    val look = p.look()
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier.size(width, height).clip(shape).background(look.background)
            .border(if (on) 2.dp else 1.dp, if (on) KkTheme.colors.accent else look.edge, shape)
            .drawBehind {
                val inset = size.width * 0.1f
                for (i in 1..4) {
                    val y = size.height * (0.2f + 0.19f * (i - 1))
                    drawLine(look.rule, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
                }
            },
    )
}

/** 편지지 시트(Figma 편지 쓰기 · 편지지 시트): 무료 · 꽃신 평생 두 줄. 잠긴 편지지는 자물쇠 — 누르면 평생 안내. */
@Composable
private fun PaperSheet(selected: Paper, lifetimeOn: Boolean, onPick: (Paper) -> Unit, onLocked: () -> Unit, onDismiss: () -> Unit) {
    val c = KkTheme.colors
    KkModalSheet(onDismiss) {
        KkSheetHeader("편지지")
        listOf("무료" to Paper.entries.filter { !it.lifetime }, "꽃신 평생" to Paper.entries.filter { it.lifetime }).forEach { (title, papers) ->
            Text(title, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.padding(top = KkSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                papers.forEach { p ->
                    val locked = p.lifetime && !lifetimeOn
                    Column(
                        Modifier.width(60.dp).clickable(role = Role.RadioButton) { if (locked) onLocked() else onPick(p) }
                            .semantics { this.selected = p == selected; contentDescription = "편지지 ${p.label}" + if (locked) ", 잠김" else "" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(KkSpacing.xs),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PaperSwatch(p, on = p == selected, width = 44.dp, height = 56.dp)
                            if (locked) KkIcon(KkIcons.Lock, tint = c.ink, size = 18.dp)
                        }
                        Text(p.label, style = KkTheme.type.caption, color = if (p == selected) c.accent else c.sub, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * 평생 안내 시트(Figma 편지 쓰기 · 평생 안내 시트): 잠긴 편지지 · 글꼴을 눌렀을 때. 가격 · 혜택 다섯 줄 · 꽃신 평생 보기 · 다음에.
 */
@Composable
private fun LifetimeSheet(partner: String, price: String, onOpen: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = KkTheme.colors
    KkModalSheet(onDismiss) {
        KkSheetHeader("꽃신 평생")
        Text("한 번이면 평생, 둘이 함께", style = KkTheme.type.handL, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(price, style = KkTheme.type.ddayS, color = c.accent)
            Text("한 번 결제 · 구독 아님", style = KkTheme.type.caption, color = c.sub)
        }
        Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
            listOf("광고 제거", "손글씨 글꼴 7종", "편지지 5종", "펜 · 색 · 위젯 꾸미기", "연결된 ${partner}도 함께").forEach { perk ->
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    KkIcon(KkIcons.Check, tint = c.sub, size = 18.dp)
                    Text(perk, style = KkTheme.type.body, color = c.ink)
                }
            }
        }
        if (onOpen != null) KkPrimaryButton("꽃신 평생 보기", onOpen, Modifier.padding(top = KkSpacing.sm))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(KkRadius.full)).clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("다음에", style = KkTheme.type.labelBold, color = c.accent) }
    }
}

/**
 * 보냄(Figma 편지 쓰기 · 보냄): 보낸 봉투(보여 주기만 하는 물건이라 기운다) + "민준에게 보냈어요" + 편지함으로.
 * 혼자 모드면 우표는 "대기", 제목은 "보낼 편지 N통" — 연결하는 순간 한꺼번에 간다.
 */
@Composable
fun LetterSentScreen(done: ComposeDone.Sent, onInbox: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding().padding(horizontal = KkSpacing.xl)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KkSpacing.xxxl, Alignment.CenterVertically),
        ) {
            Box(Modifier.fillMaxWidth().rotate(-6f).clearAndSetSemantics {}) {
                KkEnvelopeRow(done.line, done.meta, if (done.queued) KkEnvelopeMark.Waiting else KkEnvelopeMark.Stamp(done.stamp), onClick = {})
            }
            Text(
                if (done.queued) "보낼 편지 ${done.queuedCount}통" else "${done.partner}에게 보냈어요",
                style = KkTheme.type.handL, color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = if (done.queued) "보낼 편지 ${done.queuedCount}통, ${done.line}" else "${done.partner}에게 보냈어요, ${done.line}" },
            )
        }
        KkPrimaryButton("편지함으로", onInbox, Modifier.padding(bottom = KkSpacing.lg))
    }
}

/**
 * 편지지(Paper): 화면 폭 종이 + 제목 + 줄 친 본문. 색은 읽기 화면과 같은 [Paper.look] — 쓰는 사람의 테마 · 다크 모드와 무관하다.
 * 본문 속 그림 · 사진은 글자처럼 커서 자리에 들어간다(Figma 최종 정리 › 편지 쓰기).
 */
@Composable
private fun PaperCard(
    d: ComposeDraft,
    paper: Paper,
    thumbs: Map<String, DrawingDocument>,
    photos: Map<String, ByteArray>,
    onIntent: (LetterIntent) -> Unit,
    partner: String = "",
) {
    val c = KkTheme.colors
    val look = paper.look()
    val ink = look.ink
    val hint = look.ink.copy(alpha = 0.5f)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 480.dp).kkElevation(KkElevation.Low, RectangleShape).background(look.background).border(1.dp, look.edge)
            .padding(horizontal = KkSpacing.xxl, vertical = KkSpacing.xl),
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 380.dp).drawBehind {
                    // 괘선: 글줄마다, 글이 끝난 뒤로는 같은 간격으로 종이 끝까지.
                    val l = layout ?: return@drawBehind
                    val stroke = 1.dp.toPx()
                    var y = 0f
                    for (i in 0 until l.lineCount) {
                        y = l.getLineBottom(i)
                        drawLine(look.rule, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                    }
                    val step = if (l.lineCount > 0) l.getLineBottom(0) - l.getLineTop(0) else 0f
                    if (step > 0f) while (y + step <= size.height) {
                        y += step
                        drawLine(look.rule, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                    }
                }.semantics {
                    contentDescription = "편지 내용"
                    // 자리 확보용 투명 글리프를 읽지 않도록 원문을 접근성 값으로 제공한다.
                    editableText = AnnotatedString(field.text)
                },
                decorationBox = { inner ->
                    Box { if (d.text.isEmpty() && partner.isNotEmpty()) Text("${partner}에게…", style = body.copy(color = hint)); inner() }
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
            type = when {
                t.kind == LetterToast.Kind.ERROR -> KkToastType.Error
                t.kind == LetterToast.Kind.SUCCESS || t.openLetterId != null -> KkToastType.Success
                else -> KkToastType.Default
            },
            action = t.openLetterId?.let { "보기" },
            onAction = { t.openLetterId?.let(onOpen); current = null },
        )
    }
}
