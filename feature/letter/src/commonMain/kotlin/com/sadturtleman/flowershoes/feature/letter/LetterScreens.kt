package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.decodeToImageBitmap
import com.sadturtleman.flowershoes.core.designsystem.KkLetterFont
import com.sadturtleman.flowershoes.core.designsystem.family
import com.sadturtleman.flowershoes.core.designsystem.size
import com.sadturtleman.flowershoes.core.designsystem.KkSheetOption
import com.sadturtleman.flowershoes.core.model.Letter
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.sadturtleman.flowershoes.core.designsystem.KkModalSheet
import com.sadturtleman.flowershoes.core.designsystem.KkScheduleSheet
import com.sadturtleman.flowershoes.core.designsystem.KkToastType
import com.sadturtleman.flowershoes.core.designsystem.hhmm
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSecondaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkSegmentedControl
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

// ── 편지함 ────────────────────────────────────────────────────────────────

/** 편지함(Figma 4 · 편지함 / 빈 편지함). 편지를 열면 같은 자리에서 읽기로 바뀐다. */
@Composable
fun LetterInboxRoute(
    onCreate: () -> Unit,
    onReply: () -> Unit,
    onReplyDrawing: () -> Unit = onReply,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        val open = state.open
        when {
            open != null && open.drawingOnly -> DrawingLetterScreen(open, viewModel::onIntent, onReplyDrawing)
            open != null -> LetterReadScreen(open, viewModel::onIntent, onReply)
            else -> LetterInboxScreen(state, viewModel::onIntent, onCreate)
        }
        ToastHost(viewModel.toasts, viewModel::toastShown) { viewModel.onIntent(LetterIntent.Open(it)) }
    }
}

@Composable
fun LetterInboxScreen(state: LetterState, onIntent: (LetterIntent) -> Unit, onCreate: () -> Unit) {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.bgCanvas).verticalScroll(rememberScrollState())
            .padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("편지함", style = KkTheme.type.titleL, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                // Figma: 편지가 있으면 "+ 만들기", 빈 편지함은 "+ 편지 쓰기".
                if (state.inbox.isEmpty()) "+ 편지 쓰기" else "+ 만들기", style = KkTheme.type.labelM, color = c.textBrand,
                modifier = Modifier.clip(RoundedCornerShape(KkRadius.sm)).clickable(role = Role.Button, onClick = onCreate)
                    .padding(vertical = KkSpacing.md, horizontal = KkSpacing.xs),
            )
        }
        KkSegmentedControl(LetterFilter.entries.map { it.label }, state.filter.ordinal, { onIntent(LetterIntent.Filter(LetterFilter.entries[it])) })
        when {
            state.inbox.isEmpty() -> KkEmptyState(
                title = "아직 주고받은 편지가 없어요",
                description = "오늘 하루를 그림 한 장이나 짧은 편지로 보내 보세요",
                icon = KkIcons.Mail,
                action = "만들기",
                onAction = onCreate,
            )
            state.visibleInbox.isEmpty() -> Text(
                "이 분류에는 아직 편지가 없어요", style = KkTheme.type.bodyM, color = c.textTertiary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = KkSpacing.xxxl),
            )
            else -> state.visibleInbox.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                    row.forEach { card -> InboxItem(card, Modifier.weight(1f)) { onIntent(LetterIntent.Open(card.letter.id)) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(KkSpacing.lg))
    }
}

/** 편지함 한 칸(Item): 그림만 = 흰 카드 + 그림, 편지 = 올리브 카드 + 줄 그림. 안 읽은 편지는 점. */
@Composable
private fun InboxItem(card: LetterCard, modifier: Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    val drawing = card.drawingOnly
    Column(
        modifier.fillMaxHeight().heightIn(min = 220.dp).kkElevation(KkElevation.Mid, shape).clip(shape)
            .background(if (drawing) c.bgSurface else c.bgMilitarySubtle)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (card.unread) contentDescription = "안 읽은 편지, ${card.title}" }
            .padding(KkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
    ) {
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(KkRadius.md)).background(if (drawing) c.bgCanvas else c.bgSurface),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = card.thumb
            when {
                thumb != null -> Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) { DrawingView(thumb) }
                drawing -> KkIcon(KkIcons.Heart, tint = c.textBrand, size = 32.dp)
                else -> LetterLines(c.textMilitary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (drawing) "그림" else "편지", style = KkTheme.type.labelS, color = if (drawing) c.textBrand else c.textMilitary,
                modifier = Modifier.clip(RoundedCornerShape(KkRadius.full)).background(if (drawing) c.bgBrandSubtle else c.bgSurface)
                    .padding(horizontal = KkSpacing.xs, vertical = KkSpacing.xxs),
            )
            if (card.unread) Box(Modifier.size(6.dp).clip(CircleShape).background(c.heart))
        }
        Text(card.title, style = KkTheme.type.labelM, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.meta, style = KkTheme.type.caption, color = c.textTertiary, maxLines = 1)
    }
}

/** 글 편지 썸네일: 네 줄. */
@Composable
private fun LetterLines(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.xs)) {
        listOf(110, 96, 104, 70).forEach { w -> Box(Modifier.width(w.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color)) }
    }
}

// ── 만들기 시트 ───────────────────────────────────────────────────────────

/** 만들기 시트(Figma 4 · 만들기 시트): 그림만 보내기 · 편지 쓰기 · 예약 메시지. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterCreateSheet(onDismiss: () -> Unit, onDrawOnly: () -> Unit, onCompose: () -> Unit, onScheduled: () -> Unit) {
    val c = KkTheme.colors
    KkModalSheet(onDismiss) {
        KkSheetHeader("무엇을 보낼까요?")
        CreateOption(KkIcons.Heart, c.textBrand, c.bgBrandSubtle, "그림만 보내기", "손으로 그려 보내요. 글 없는 편지로 가고, 위젯에도 떠요") { onDismiss(); onDrawOnly() }
        CreateOption(KkIcons.Mail, c.textMilitary, c.bgMilitarySubtle, "편지 쓰기", "글을 쓰고, 그림도 3장까지 넣을 수 있어요") { onDismiss(); onCompose() }
        CreateOption(KkIcons.Clock, c.textSecondary, c.bgSubtle, "예약 메시지", "폰 사용 시간에 맞춰 도착해요") { onDismiss(); onScheduled() }
    }
}

@Composable
private fun CreateOption(icon: KkIcons, tint: Color, bg: Color, title: String, description: String, onClick: () -> Unit) {
    val c = KkTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(bg).clickable(role = Role.Button, onClick = onClick).padding(KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(icon, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Text(title, style = KkTheme.type.titleS, color = c.textPrimary)
            Text(description, style = KkTheme.type.bodyS, color = c.textSecondary)
        }
    }
}

// ── 받은 편지 ─────────────────────────────────────────────────────────────

/** 편지 읽기(Figma 4 · 받은 편지): 보낸 사람 → 받는 사람 · 날짜, 제목, 글 · 그림(처음 보면 자동 재생). */
@Composable
fun LetterReadScreen(open: OpenLetter, onIntent: (LetterIntent) -> Unit, onReply: () -> Unit) {
    val c = KkTheme.colors
    val paper = Paper.of(open.letter.paper)
    val ink = if (paper == Paper.NIGHT) c.textInverse else c.textPrimary
    // 읽는 화면은 탭 바 없이 전체(Figma) — 아래 버튼이 제스처 바에 걸리지 않게.
    Column(Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding()) {
        KkTopBar(
            "편지", onLeading = { onIntent(LetterIntent.Close) },
            trailingText = if (open.mine) "삭제" else null, onTrailing = { onIntent(LetterIntent.Delete(open.letter.id)) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
        ) {
            val shape = RoundedCornerShape(KkRadius.xl)
            Column(
                Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(paper.background(c)).padding(KkSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            ) {
                Text(open.meta, style = KkTheme.type.caption, color = if (paper == Paper.NIGHT) c.textInverse.copy(alpha = 0.7f) else c.textTertiary)
                open.letter.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = KkTheme.type.titleM, color = ink) }
                val font = KkLetterFont.of(open.letter.font)
                val fontFamily = font.family()
                open.letter.blocks.forEach { block ->
                    when (block) {
                        is LetterBlock.Text -> Text(block.text, style = KkTheme.type.bodyM.copy(fontFamily = fontFamily, fontSize = font.size(15f)), color = ink)
                        is LetterBlock.DrawingRef -> {
                            val doc = open.drawings[block.drawingId]
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)).background(Color.White)
                                    .border(1.dp, c.borderDefault, RoundedCornerShape(KkRadius.lg)).padding(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (doc == null) {
                                    Text("그림을 받는 중이에요", style = KkTheme.type.bodyS, color = c.textTertiary, modifier = Modifier.padding(KkSpacing.xxl))
                                } else {
                                    ReplayPlayer(
                                        doc,
                                        autoPlay = block.drawingId in open.firstView,
                                        onFinished = { onIntent(LetterIntent.DrawingSeen(block.drawingId)) },
                                    )
                                }
                            }
                        }
                        is LetterBlock.Photo -> {
                            val bytes = open.photos[block.attachmentId]
                            val bitmap = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
                            if (bitmap != null) {
                                Image(
                                    bitmap, contentDescription = "사진",
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(KkRadius.lg)),
                                    contentScale = ContentScale.FillWidth,
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(KkRadius.lg)).background(c.bgSubtle),
                                    contentAlignment = Alignment.Center,
                                ) { Text("사진을 받는 중이에요", style = KkTheme.type.bodyS, color = c.textTertiary) }
                            }
                        }
                    }
                }
            }
        }
        if (!open.mine) {
            Row(
                Modifier.fillMaxWidth().padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = KkSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                KkSecondaryButton("고마워", onClick = { onIntent(LetterIntent.Thanks) }, modifier = Modifier.weight(1f))
                KkPrimaryButton("답장 쓰기", onClick = onReply, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── 편지 쓰기 ─────────────────────────────────────────────────────────────

/**
 * 편지 쓰기(Figma 4 · 편지 쓰기): 받는 사람 · 편지지 · 제목과 줄 친 본문 · 붙인 그림(최대 3) · 도구 · 예약 / 바로 보내기.
 * [scheduleFirst] 면 예약 시트를 먼저 띄운다(만들기 › 예약 메시지).
 */
@Composable
fun LetterComposeRoute(
    onAddDrawing: () -> Unit,
    onClose: () -> Unit,
    scheduleFirst: Boolean = false,
    photoPicker: (@Composable (onPicked: (ByteArray) -> Unit) -> ((fromCamera: Boolean) -> Unit))? = null,
    viewModel: LetterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.draft
    val c = KkTheme.colors
    val paper = Paper.of(d.paper)
    var scheduling by remember { mutableStateOf(scheduleFirst) }
    var stickers by remember { mutableStateOf(false) }
    var fonts by remember { mutableStateOf(false) }
    var photoSource by remember { mutableStateOf(false) }
    val pickPhoto = photoPicker?.invoke { viewModel.onIntent(LetterIntent.AddPhoto(it)) }
    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = KkSpacing.xl)) {
            KkTopBar("편지 쓰기", onLeading = onClose, leading = KkTopBarLeading.Close, trailingText = "임시저장", onTrailing = { viewModel.onIntent(LetterIntent.SaveDraft) })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = KkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text("받는 사람", style = KkTheme.type.labelS, color = c.textTertiary)
                    KkStatusChip(state.partnerName, selected = false, onClick = {}, dot = c.statusWarning)
                }
                PaperPicker(paper) { viewModel.onIntent(LetterIntent.ChoosePaper(it)) }
                PaperCard(d, paper, viewModel::onIntent)
                AttachedDrawings(
                    d, state.draftThumbs, state.draftPhotos, onAddDrawing,
                    onRemove = { viewModel.onIntent(LetterIntent.RemoveDrawing(it)) },
                    onRemovePhoto = { viewModel.onIntent(LetterIntent.RemovePhoto(it)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                        KkStatusChip("그림", selected = false, onClick = onAddDrawing, dot = c.statusWarning, enabled = d.canAddDrawing)
                        if (pickPhoto != null) {
                            KkStatusChip("사진", selected = false, onClick = { photoSource = true }, dot = c.statusWarning, enabled = d.canAddPhoto)
                        }
                        KkStatusChip("스티커", selected = false, onClick = { stickers = true }, dot = c.statusWarning)
                        KkStatusChip("글꼴", selected = false, onClick = { fonts = true }, dot = c.statusWarning)
                    }
                    Text("${d.text.length} / ${ComposeDraft.MAX_CHARS}", style = KkTheme.type.caption, color = c.textTertiary, textAlign = TextAlign.End)
                }
                d.revealAt?.let { at ->
                    Text(
                        "${at.month.ordinal + 1}월 ${at.day}일 ${at.time.hhmm()}에 도착해요 · 그 전에는 상대 화면 어디에도 보이지 않아요",
                        style = KkTheme.type.caption, color = c.textSecondary,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = KkSpacing.sm), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkSecondaryButton("예약 보내기", onClick = { scheduling = true }, modifier = Modifier.weight(1f), enabled = d.canSend || scheduleFirst)
                KkPrimaryButton("바로 보내기", onClick = {
                    viewModel.onIntent(LetterIntent.Schedule(null))
                    viewModel.onIntent(LetterIntent.Send)
                    onClose()
                }, modifier = Modifier.weight(1f), enabled = d.canSend, loading = d.sending)
            }
        }
        ToastHost(viewModel.toasts, viewModel::toastShown) {}
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
            KkSheetHeader("사진", "편지 아래에 붙여요 · 최대 ${Letter.MAX_PHOTOS}장")
            KkSheetOption("사진첩에서 고르기", false, { photoSource = false; pickPhoto(false) })
            KkSheetOption("카메라로 찍기", false, { photoSource = false; pickPhoto(true) })
        }
    }
    if (fonts) {
        KkModalSheet({ fonts = false }) {
            KkSheetHeader("글꼴", "받는 사람도 같은 글꼴로 봐요")
            KkLetterFont.entries.forEach { f ->
                val on = d.font == f.id
                val shape = RoundedCornerShape(KkRadius.md)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(if (on) c.bgBrandSubtle else c.bgSurface)
                        .border(if (on) 1.5.dp else 1.dp, if (on) c.borderBrand else c.borderDefault, shape)
                        .clickable(role = Role.RadioButton) { viewModel.onIntent(LetterIntent.ChooseFont(f.id)); fonts = false }
                        .semantics { selected = on }
                        .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(f.label, style = KkTheme.type.labelM, color = if (on) c.textBrand else c.textSecondary)
                    Text("보고 싶은 밤, 오늘도 고생했어", style = KkTheme.type.bodyL.copy(fontFamily = f.family(), fontSize = f.size(17f)), color = c.textPrimary)
                }
            }
        }
    }
    if (stickers) {
        KkModalSheet({ stickers = false }) {
            KkSheetHeader("스티커", "글 끝에 붙여요")
            StickerGrid { glyph ->
                viewModel.onIntent(LetterIntent.EditText(d.text + glyph))
                stickers = false
            }
        }
    }
}

/** 편지지 다섯 장. 고른 것은 브랜드 테두리 + 브랜드 글자. */
@Composable
private fun PaperPicker(selected: Paper, onPick: (Paper) -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        Paper.entries.forEach { p ->
            val on = p == selected
            val shape = RoundedCornerShape(KkRadius.md)
            Column(
                Modifier.weight(1f).clickable(role = Role.RadioButton) { onPick(p) }.semantics { this.selected = on; contentDescription = "편지지 ${p.label}" },
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
                                on -> Modifier.border(2.dp, c.borderBrand, shape)
                                bordered -> Modifier.border(1.dp, c.borderDefault, shape)
                                else -> Modifier
                            },
                        ),
                )
                Text(p.label, style = KkTheme.type.caption, color = if (on) c.textBrand else c.textTertiary)
            }
        }
    }
}

/** 편지지(Paper): 제목 + 32pt 줄 간격 본문. 줄은 편지지 색에 맞춘 괘선. */
@Composable
private fun PaperCard(d: ComposeDraft, paper: Paper, onIntent: (LetterIntent) -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    val ink = if (paper == Paper.NIGHT) c.textInverse else c.textPrimary
    val hint = if (paper == Paper.NIGHT) c.textInverse.copy(alpha = 0.5f) else c.textTertiary
    val rule = paper.rule(c)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 341.dp).kkElevation(KkElevation.Mid, shape).clip(shape).background(paper.background(c)).padding(KkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
    ) {
        BasicTextField(
            value = d.title,
            onValueChange = { onIntent(LetterIntent.EditTitle(it)) },
            textStyle = KkTheme.type.titleM.copy(color = ink),
            singleLine = true,
            cursorBrush = SolidColor(c.bgBrand),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "제목" },
            decorationBox = { inner ->
                Box { if (d.title.isEmpty()) Text("제목", style = KkTheme.type.titleM, color = hint); inner() }
            },
        )
        val font = KkLetterFont.of(d.font)
        val body = KkTheme.type.bodyM.copy(fontFamily = font.family(), fontSize = font.size(16f), lineHeight = 32.sp, letterSpacing = 0.sp, color = ink)
        BasicTextField(
            value = d.text,
            onValueChange = { onIntent(LetterIntent.EditText(it)) },
            textStyle = body,
            cursorBrush = SolidColor(c.bgBrand),
            modifier = Modifier.fillMaxWidth().heightIn(min = 256.dp)
                .drawBehind {
                    val step = 32.dp.toPx()
                    var y = step
                    while (y <= size.height) {
                        if (paper == Paper.GRID) drawLine(rule, Offset(0f, y), Offset(size.width, y), 1f)
                        else drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        y += step
                    }
                    if (paper == Paper.GRID) {
                        var x = step
                        while (x <= size.width) { drawLine(rule, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    }
                }
                .semantics { contentDescription = "편지 내용" },
            decorationBox = { inner ->
                Box { if (d.text.isEmpty()) Text("마음을 적어 보세요", style = body.copy(color = hint)); inner() }
            },
        )
    }
}

/** 붙인 그림(72 썸네일) + 그림 그리기 칸 + "그림 1 / 3". 썸네일의 ×로 뺀다. */
@Composable
private fun AttachedDrawings(
    d: ComposeDraft,
    thumbs: Map<String, DrawingDocument>,
    photos: Map<String, ByteArray>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        d.drawings.forEach { meta ->
            val shape = RoundedCornerShape(KkRadius.md)
            Box(Modifier.size(72.dp)) {
                Box(
                    Modifier.fillMaxSize().kkElevation(KkElevation.Low, shape).clip(shape).background(c.bgSurface).padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    thumbs[meta.id]?.let { DrawingView(it) } ?: KkIcon(KkIcons.Pen, tint = c.textTertiary)
                }
                RemoveBadge { onRemove(meta.id) }
            }
        }
        if (d.canAddDrawing) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)
                    .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = "그림 그리기" },
                contentAlignment = Alignment.Center,
            ) { KkIcon(KkIcons.Pen, tint = c.textPrimary) }
        }
        d.photos.forEach { id ->
            val bitmap = remember(photos[id]) { photos[id]?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
            Box(Modifier.size(72.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle)) {
                    if (bitmap != null) Image(bitmap, contentDescription = "붙인 사진", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                RemoveBadge { onRemovePhoto(id) }
            }
        }
        Text(
            "그림 ${d.drawings.size} / 3" + if (d.photos.isNotEmpty()) " · 사진 ${d.photos.size} / ${Letter.MAX_PHOTOS}" else "",
            style = KkTheme.type.caption, color = c.textTertiary,
        )
    }
}

@Composable
private fun BoxScope.RemoveBadge(onClick: () -> Unit) {
    val c = KkTheme.colors
    Box(
        Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape).background(c.bgInverse.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "그림 빼기" },
        contentAlignment = Alignment.Center,
    ) { KkIcon(KkIcons.Close, tint = c.textInverse, size = 12.dp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerGrid(onPick: (String) -> Unit) {
    val c = KkTheme.colors
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
        STICKERS.values.forEach { glyph ->
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bgSubtle).clickable(role = Role.Button) { onPick(glyph) },
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
    Paper.PLAIN -> c.bgSurface
    Paper.BLOSSOM -> c.bgBrandSubtle
    Paper.OLIVE -> c.bgMilitarySubtle
    Paper.GRID -> c.bgSubtle
    Paper.NIGHT -> c.bgInverse
}

private fun Paper.rule(c: KkColors): Color = when (this) {
    Paper.PLAIN -> c.borderDefault
    Paper.BLOSSOM -> c.borderBrand.copy(alpha = 0.35f)
    Paper.OLIVE -> c.textMilitary.copy(alpha = 0.25f)
    Paper.GRID -> c.borderDefault
    Paper.NIGHT -> c.textInverse.copy(alpha = 0.18f)
}

