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
        // 편지를 열면 편지함 장이 넘어가고, 닫으면 다시 덮인다.
        KkPageFlip(state.open, pageKey = { it?.letter?.id ?: "inbox" }, isForward = { _, to -> to != null }) { open ->
            when {
                open != null && open.drawingOnly -> DrawingLetterScreen(open, viewModel::onIntent, onReplyDrawing)
                open != null -> LetterReadScreen(open, viewModel::onIntent, onReply)
                else -> LetterInboxScreen(state, viewModel::onIntent, onCreate)
            }
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
                "+ 편지 쓰기", style = KkTheme.type.labelM, color = c.textBrand,
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
        // Figma 최종 정리: 종류 알약 없이 제목 · "10.21 · 지수". 안 읽은 편지만 제목 앞 점.
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (card.unread) Box(Modifier.size(6.dp).clip(CircleShape).background(c.heart))
            Text(card.title, style = KkTheme.type.labelM, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
                PaperCard(d, paper, state.draftThumbs, state.draftPhotos, viewModel::onIntent)
                ComposeTools(
                    d, canPhoto = pickPhoto != null,
                    onDrawing = onAddDrawing, onPhoto = { photoSource = true }, onSticker = { stickers = true }, onFont = { fonts = true },
                )
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
            KkSheetHeader("사진", "커서 자리에 글자처럼 들어가요 · 최대 ${Letter.MAX_PHOTOS}장")
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
            KkSheetHeader("스티커", "커서 자리에 붙여요")
            StickerGrid { glyph ->
                val at = d.cursor.coerceIn(0, d.text.length)
                viewModel.onIntent(LetterIntent.EditText(d.text.substring(0, at) + glyph + d.text.substring(at), at + glyph.length, external = true))
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
    val ink = if (paper == Paper.NIGHT) c.textInverse else c.textPrimary
    val hint = if (paper == Paper.NIGHT) c.textInverse.copy(alpha = 0.5f) else c.textTertiary
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
        // 글 속 그림이 줄 높이를 키우도록 줄 간격은 글꼴 기본값(고정 줄 높이는 큰 글자를 겹치게 한다).
        val body = KkTheme.type.bodyM.copy(fontFamily = font.family(), fontSize = font.size(16f), lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp, color = ink)
        var field by remember { mutableStateOf(TextFieldValue(d.text, TextRange(d.cursor.coerceIn(0, d.text.length)))) }
        // 그림 · 사진을 넣었거나 임시저장을 불러온 경우처럼 바깥에서 글이 바뀌면 맞춘다.
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
                cursorBrush = SolidColor(c.bgBrand),
                visualTransformation = objectStyle,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 256.dp).semantics { contentDescription = "편지 내용" },
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
                    // 자리 글자가 실제로 차지한 칸: 앞뒤 커서 위치(자간 포함) × 그 줄 높이.
                    val line = l.getLineForOffset(i)
                    val left = l.getHorizontalPosition(i, usePrimaryDirection = true)
                    val right = if (i + 1 < d.text.length && l.getLineForOffset(i + 1) == line) l.getBoundingBox(i + 1).left
                    else l.getLineRight(line)
                    val r = androidx.compose.ui.geometry.Rect(left, l.getLineTop(line), right, l.getLineBottom(line))
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
                            Box(Modifier.fillMaxSize().border(1.5.dp, c.borderBrand))
                            listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
                                Box(Modifier.align(a).size(8.dp).background(Color.White).border(1.5.dp, c.borderBrand))
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
            thumbs[key.removePrefix("d:")]?.let { DrawingView(it) } ?: KkIcon(KkIcons.Pen, tint = c.textTertiary)
        }
    } else {
        val bytes = photos[key.removePrefix("p:")]
        val bitmap = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
        Box(modifier.clip(RoundedCornerShape(KkRadius.sm)).background(c.bgSubtle), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else KkIcon(KkIcons.Photo, tint = c.textSecondary, size = 18.dp)
        }
    }
}

/**
 * 글 속 자리 글자의 모양: 그림은 큰 칸, 사진은 작은 칸. 투명 전각 공백을 옆으로 늘려 자리만 잡는다 —
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
            ) { append(IDEOGRAPHIC_SPACE) }
        } else {
            b.append(ch)
        }
    }
    TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
}

private const val IDEOGRAPHIC_SPACE = '　'
/** 자리 글자를 옆으로 늘려 칸 비율을 맞춘다(그림 ≈ 4:3, 사진 ≈ 4:3). */
private const val DRAWING_WIDEN = 1.75f
private const val PHOTO_WIDEN = 1.5f

/** 도구(Figma 편지 쓰기): 그림 · 사진 · 스티커 · 글꼴 아이콘 + 글자 수. */
@Composable
private fun ComposeTools(d: ComposeDraft, canPhoto: Boolean, onDrawing: () -> Unit, onPhoto: () -> Unit, onSticker: () -> Unit, onFont: () -> Unit) {
    val c = KkTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ToolButton("그림", d.canAddDrawing, onDrawing) { KkIcon(KkIcons.Pen, tint = c.textPrimary) }
        if (canPhoto) ToolButton("사진", d.canAddPhoto, onPhoto) { KkIcon(KkIcons.Photo, tint = c.textPrimary) }
        ToolButton("스티커", true, onSticker) { KkIcon(KkIcons.Sticker, tint = c.textPrimary) }
        ToolButton("글꼴", true, onFont) { Text("T", style = KkTheme.type.titleM, color = c.textPrimary) }
        Spacer(Modifier.weight(1f))
        Text("${d.text.length} / ${ComposeDraft.MAX_CHARS}", style = KkTheme.type.caption, color = c.textTertiary)
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

