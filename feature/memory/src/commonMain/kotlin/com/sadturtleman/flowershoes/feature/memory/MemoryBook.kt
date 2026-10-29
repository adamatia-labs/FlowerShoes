@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.data.BookStore
import com.sadturtleman.flowershoes.core.data.PurchaseOutcome
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkIcon
import com.sadturtleman.flowershoes.core.designsystem.KkIcons
import com.sadturtleman.flowershoes.core.designsystem.KkLetterFont
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.KkTopBarLeading
import com.sadturtleman.flowershoes.core.designsystem.family
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.model.BookCover
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** PDF 한 권을 쪽마다 받아 쓰는 플랫폼 작성기(Android: PdfDocument). */
interface BookPdf {
    fun add(page: ImageBitmap)

    /** 화면용 · 인쇄용(재단 여백 3mm) 두 파일: (파일 이름, 바이트). */
    suspend fun finish(): List<Pair<String, ByteArray>>
}

/** 책으로 보기의 플랫폼 기능: 결제([store]) · PDF 작성기 · 파일 공유. 없는 것은 그 버튼이 꺼진다. */
class MemoryBookActions(
    val store: BookStore?,
    val newPdf: (() -> BookPdf?)?,
    val shareFiles: (List<Pair<String, ByteArray>>) -> Unit,
    /** 나 또는 연결된 상대가 산 추억 책(둘 중 한 명이 사면 둘 다 받는다). 없으면 [store] 만 본다. */
    val sharedOwned: kotlinx.coroutines.flow.Flow<Boolean>? = null,
)

/** 추억 책 한 쪽. 표지는 쪽 번호가 없고 요약이 1쪽, 한 달은 왼쪽(달력 · 사진) · 오른쪽(편지 세 통) 두 쪽, 끝에 마지막 장. */
sealed interface BookPage {
    data object Cover : BookPage
    data object Summary : BookPage
    data class Left(val ym: YearMonth) : BookPage
    data class Right(val ym: YearMonth) : BookPage
    data object Last : BookPage
}

/** 책에 들어가는 쪽들(내용이 있는 달만, 오래된 달부터). */
fun MemoryState.bookPages(): List<BookPage> = buildList {
    add(BookPage.Cover)
    add(BookPage.Summary)
    months.values.sortedBy { it.ym }
        .filter { it.letters.isNotEmpty() || it.meets.isNotEmpty() || it.bars.isNotEmpty() || it.dayLines.isNotEmpty() }
        .forEach {
            add(BookPage.Left(it.ym))
            add(BookPage.Right(it.ym))
        }
    add(BookPage.Last)
}

/** 추억 책 PDF 값(수익 모델 확정). 스토어 가격을 못 읽으면 이 값을 보여 준다. */
internal const val BOOK_PRICE = "5,900원"

/** PDF 한 쪽의 픽셀(8인치 · 약 225dpi). */
private const val CAPTURE_PX = 1800

/**
 * 추억 · 책으로 보기(Figma 추억 책): 펼침 미리보기 · 쪽 넘기기 + "PDF로 영구 소장"(화면용 + 포토북 인쇄용) 카드 + 표지 고르기.
 * 앱 안에서 보는 책은 무료, 디자인된 PDF 만 [BOOK_PRICE]. PDF 는 폰에서 만들어 서버로 내용이 나가지 않는다.
 * 표지는 산 사람이 고르고 두 사람이 함께 쓴다 — 산 뒤에도 바꾸면 PDF 를 다시 만든다(#23 결정 1).
 */
@Composable
internal fun BookScreen(
    state: MemoryState,
    photos: Map<String, ByteArray?>,
    actions: MemoryBookActions?,
    onChooseCover: (BookCover) -> Unit,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val c = KkTheme.colors
    val pages = remember(state) { state.bookPages() }
    // 펼침: 표지 · 요약 · 마지막 장은 한 쪽씩, 그 사이는 왼쪽 + 오른쪽.
    val spreads = remember(pages) { listOf(listOf(pages[0]), listOf(pages[1])) + pages.drop(2).dropLast(1).chunked(2) + listOf(listOf(pages.last())) }
    var spread by remember { mutableStateOf(0) }
    val store = actions?.store
    val owned by (actions?.sharedOwned ?: store?.owned ?: remember { MutableStateFlow(false) }).collectAsState(initial = false)
    var price by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) { price = store?.price() }
    var busy by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<Int?>(null) }
    var pdf by remember { mutableStateOf<BookPdf?>(null) }
    // PDF 에 그릴 표지: 방금 고른 값이 상태에 돌아오기 전에 첫 쪽을 그려도 고른 표지로.
    var pdfCover by remember { mutableStateOf(state.cover) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun make(cover: BookCover) {
        val writer = actions?.newPdf?.invoke() ?: run { onNotice("이 기기에서는 아직 PDF 를 만들 수 없어요"); return }
        pdfCover = cover
        pdf = writer
        capture = 0
    }

    fun buy(cover: BookCover) {
        if (store == null) { onNotice("결제는 앱 출시와 함께 열려요"); return }
        if (busy) return
        busy = true
        scope.launch {
            when (val r = store.buy()) {
                PurchaseOutcome.Purchased -> { onNotice("추억 책이 열렸어요. PDF 를 만들게요"); make(cover) }
                PurchaseOutcome.NothingToRestore, PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> onNotice(r.message)
            }
            busy = false
        }
    }

    BackHandler(enabled = picking) { picking = false }
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        if (picking) {
            CoverPicker(
                state, owned, price ?: BOOK_PRICE, busy = busy || capture != null,
                onClose = { picking = false },
                onConfirm = { cover ->
                    onChooseCover(cover)
                    picking = false
                    if (owned) make(cover) else buy(cover)
                },
            )
        } else Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            KkTopBar("우리의 책", onLeading = onBack, modifier = Modifier.padding(horizontal = KkSpacing.lg))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.md),
            ) {
                val sp = spreads[spread.coerceIn(spreads.indices)]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.Center) {
                    if (sp.size == 1) BookPageView(sp[0], state, photos, Modifier.fillMaxWidth(0.62f))
                    else sp.forEach { p -> BookPageView(p, state, photos, Modifier.weight(1f)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", style = KkTheme.type.title, color = c.sub,
                        modifier = Modifier.clickable(enabled = spread > 0, role = Role.Button) { spread-- }.padding(KkSpacing.sm))
                    Text(sp.pageLabel(pages), style = KkTheme.type.label, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("›", style = KkTheme.type.title, color = c.sub,
                        modifier = Modifier.clickable(enabled = spread < spreads.size - 1, role = Role.Button) { spread++ }.padding(KkSpacing.sm))
                }
                Spacer(Modifier.height(KkSpacing.lg))
                val shape = RoundedCornerShape(KkRadius.lg)
                Column(
                    Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface).padding(KkSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
                ) {
                    Text("PDF로 영구 소장", style = KkTheme.type.labelBold, color = c.ink)
                    listOf("화면용 + 포토북 인쇄용", "${pages.size - 1}쪽 · 8×8인치", "연인도 함께 받아요").forEach { b ->
                        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                            Text(b, style = KkTheme.type.body, color = c.sub)
                        }
                    }
                    val label = when {
                        capture != null -> "PDF 만드는 중… ${(capture ?: 0) + 1} / ${pages.size}"
                        owned -> "PDF 받기"
                        else -> "${price ?: BOOK_PRICE}에 PDF 받기"
                    }
                    // 사기 전에는 표지부터 고른다(Figma 표지 고르기). 산 뒤에는 지금 표지로 바로 만든다.
                    KkPrimaryButton(label, { if (owned) make(state.cover) else picking = true }, Modifier.fillMaxWidth(), enabled = capture == null, loading = busy)
                    if (owned) {
                        Text("표지 바꾸기", style = KkTheme.type.label, color = c.accent,
                            modifier = Modifier.clickable(enabled = capture == null, role = Role.Button) { picking = true }.padding(vertical = KkSpacing.xs))
                    }
                    // 디버그 빌드만: 결제 없이 PDF 를 만들어 본다(만들기 · 공유 흐름 확인용).
                    if (!owned && org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.DevFlags>()?.freeBookPdf == true) {
                        Text("개발용 · 결제 없이 PDF 만들기", style = KkTheme.type.label, color = c.accent,
                            modifier = Modifier.clickable(enabled = capture == null, role = Role.Button) { make(state.cover) }.padding(vertical = KkSpacing.xs))
                    }
                    // 구매 전 미리보기를 주고, 만들어 받은 뒤에는 청약철회가 제한된다(약관 제8조의3).
                    Text("구매 전 책 전체를 미리 볼 수 있어요 · PDF 를 받은 뒤에는 환불이 제한돼요", style = KkTheme.type.caption, color = c.sub)
                }
                Text("원본만 받기 · 설정 › 내 기록 내보내기", style = KkTheme.type.caption, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        // PDF: 쪽마다 화면 밖에서 한 번 그려 비트맵으로 받는다(그리기는 하되 보이지 않게).
        capture?.let { i ->
            val layer = rememberGraphicsLayer()
            val size = with(LocalDensity.current) { CAPTURE_PX.toDp() }
            Box(
                Modifier.wrapContentSize(unbounded = true).requiredSize(size)
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
            ) { BookPageView(pages[i], state.copy(cover = pdfCover), photos, Modifier.fillMaxSize()) }
            LaunchedEffect(i) {
                delay(400) // 사진 · 스티커 · 글꼴이 읽히길 기다린다.
                runCatching { pdf?.add(layer.toImageBitmap()) }.onFailure { println("BookPdf: 쪽 ${i + 1} 실패 — $it") }
                if (i + 1 < pages.size) {
                    capture = i + 1
                } else {
                    val files = runCatching { pdf?.finish() }.onFailure { println("BookPdf: 마무리 실패 — $it") }.getOrNull().orEmpty()
                    capture = null
                    pdf = null
                    if (files.isEmpty()) onNotice("PDF 를 만들지 못했어요") else actions?.shareFiles?.invoke(files)
                }
            }
        }
    }
}

/** 추억 책 › 표지 고르기(Figma `422:10147`): 두 표지 미리보기 + 선택 카드 + "이 표지로 만들기". */
@Composable
private fun CoverPicker(state: MemoryState, owned: Boolean, price: String, busy: Boolean, onClose: () -> Unit, onConfirm: (BookCover) -> Unit) {
    val c = KkTheme.colors
    var selected by remember { mutableStateOf(state.cover) }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = KkSpacing.lg)) {
        KkTopBar("표지 고르기", onLeading = onClose, leading = KkTopBarLeading.Close, modifier = Modifier.padding(horizontal = KkSpacing.lg))
        Spacer(Modifier.height(44.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xxl), horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            BookCover.entries.forEach { cover ->
                // 미리보기는 보여 주기만 하는 물건이라 살짝 기울인다(design.md 화면 규칙).
                BookCoverArt(
                    cover, state.summary,
                    Modifier.weight(1f).rotate(if (cover == BookCover.FLOWER) 2f else -1.5f)
                        .shadow(6.dp, RoundedCornerShape(0.dp)).clickable(role = Role.RadioButton) { selected = cover },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xxl), horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
            BookCover.entries.forEach { cover -> CoverOption(cover.label, cover == selected, Modifier.weight(1f)) { selected = cover } }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (owned) "연인 책도 이 표지로" else "PDF $price · 둘 중 한 명이 사면 둘 다",
            style = KkTheme.type.caption, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(KkSpacing.lg))
        KkPrimaryButton("이 표지로 만들기", { onConfirm(selected) }, Modifier.fillMaxWidth().padding(horizontal = KkSpacing.xxl), enabled = !busy, loading = busy)
    }
}

/** Figma Select Card: 이름 + 오른쪽 동그라미(고르면 accent 바탕 + 체크, 테두리 2). */
@Composable
private fun CoverOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        modifier.height(60.dp).clip(shape).background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(KkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = KkTheme.type.labelBold, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(22.dp).clip(CircleShape).then(if (selected) Modifier.background(c.accent) else Modifier.border(1.6.dp, c.line, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (selected) KkIcon(KkIcons.Check, tint = c.canvas, size = 16.dp) }
    }
}

internal val BookCover.label: String get() = when (this) {
    BookCover.FLOWER -> "꽃신 편지지"
    BookCover.MILITARY -> "군사우편"
}

private fun List<BookPage>.pageLabel(all: List<BookPage>): String {
    val total = all.size - 1
    val nums = map { all.indexOf(it) }.filter { it > 0 }
    return when {
        first() == BookPage.Cover -> "표지 / ${total}쪽"
        nums.size == 1 -> "${nums[0]}쪽 / ${total}쪽"
        else -> "${nums.first()}–${nums.last()}쪽 / ${total}쪽"
    }
}

// ── 추억 책 쪽(Figma 추억 책 · 속지 `422:10327`) ─────────────────────────────
// 인쇄물이라 테마(곰신 · 군인 · 다크)와 상관없이 같은 색 · 글꼴로 찍는다.

private val PAPER = Color(0xFFFBF7EF)
private val GUTTER = Color(0xFFE9E0CF)
private val INK = Color(0xFF3A2E28)
private val MUTED = Color(0xFF5C5040)
private val RULE = Color(0xFFE4D8C8)
private val NUMBER = Color(0xFF9A3B32)
private val MILITARY_PAPER = Color(0xFFE3D6B8)
private val MILITARY_INK = Color(0xFFA8402F)
private val STRIPE_RED = Color(0xFFB5483E)
private val STRIPE_NAVY = Color(0xFF2F4A6B)
private val FLOWER_PAPER = Color(0xFFF3E3E0)
private val FLOWER_RULE = Color(0xFFE3C1BD)
private val FLOWER_MARGIN = Color(0xFFD9898F)
private val FLOWER_INK = Color(0xFFB9475E)
private val FLOWER_HAND = Color(0xFF4A3530)

/** 오른쪽 쪽의 편지 카드 바탕: 내 편지 · 상대 편지. */
private val CARD_MINE = Color(0xFFF5E4E2)
private val CARD_PARTNER = Color(0xFFEFE6D2)

/** 책 글꼴: 손글씨(나눔손글씨 펜) · 숫자(고운바탕 Bold) · 본문(Noto Sans KR). */
private class BookFonts(val hand: FontFamily, val number: FontFamily, val body: FontFamily)

@Composable
private fun bookFonts() = BookFonts(KkTheme.fonts.hand, KkLetterFont.GOWUN_BATANG.family(), KkTheme.fonts.body)

/** 쪽 하나(정사각). 좌표는 800 기준 값을 쪽 너비에 맞춰 줄인다. */
@Composable
fun BookPageView(page: BookPage, state: MemoryState, photos: Map<String, ByteArray?>, modifier: Modifier = Modifier) {
    if (page == BookPage.Cover) {
        BookCoverArt(state.cover, state.summary, modifier)
        return
    }
    val fonts = bookFonts()
    BoxWithConstraints(modifier.aspectRatio(1f).background(PAPER).clipToBounds()) {
        val s = BookScale(maxWidth, fonts, LocalDensity.current.fontScale)
        when (page) {
            BookPage.Cover -> Unit
            BookPage.Summary -> SummaryPage(state, s)
            is BookPage.Left -> state.months[page.ym]?.let { LeftPage(it, photos, s, state.bookPages().indexOf(page)) }
            is BookPage.Right -> state.months[page.ym]?.let { RightPage(it, photos, s, state.bookPages().indexOf(page)) }
            BookPage.Last -> LastPage(state, s)
        }
        // 제본 쪽 그늘(펼침 가운데).
        if (page is BookPage.Left) At(s, 760, 0, 40, 800) { Box(Modifier.fillMaxSize().background(GUTTER.copy(alpha = 0.6f))) }
        if (page is BookPage.Right) At(s, 0, 0, 40, 800) { Box(Modifier.fillMaxSize().background(GUTTER.copy(alpha = 0.6f))) }
    }
}

/** 시안 좌표(800) → dp, 글자 크기 → sp(글자 크기 설정과 무관하게 쪽에 맞춘다). */
private class BookScale(val width: Dp, val fonts: BookFonts, private val fontScale: Float) {
    fun u(v: Number): Dp = width * (v.toFloat() / 800f)
    fun sp(v: Number): TextUnit = androidx.compose.ui.unit.TextUnit(u(v).value / fontScale, androidx.compose.ui.unit.TextUnitType.Sp)
    private fun style(family: FontFamily, size: Number, color: Color, bold: Boolean, spacing: Float) = TextStyle(
        fontFamily = family, fontSize = sp(size), color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = androidx.compose.ui.unit.TextUnit(spacing, androidx.compose.ui.unit.TextUnitType.Em),
        lineHeight = sp(size.toFloat() * 1.35f),
    )

    fun hand(size: Number, color: Color) = style(fonts.hand, size, color, false, 0f)
    fun number(size: Number, color: Color) = style(fonts.number, size, color, true, -0.02f)
    fun body(size: Number, color: Color, bold: Boolean = false) = style(fonts.body, size, color, bold, 0f)
}

@Composable
private fun At(s: BookScale, x: Number, y: Number, w: Number? = null, h: Number? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.offset(s.u(x), s.u(y)).then(if (w != null) Modifier.width(s.u(w)) else Modifier).then(if (h != null) Modifier.height(s.u(h)) else Modifier),
    ) { content() }
}

/** 항공우편 띠(빨강 · 남색 사선). 추억 탭의 책 카드도 쓴다. */
@Composable
internal fun AirmailStripe(modifier: Modifier, background: Color = PAPER) {
    Canvas(modifier.background(background)) {
        val h = size.height
        val step = h * 0.6f
        var x = -h
        var i = 0
        while (x < size.width + h) {
            val p = Path().apply { moveTo(x, h); lineTo(x + h * 0.84f, 0f); lineTo(x + h * 0.84f + step * 0.5f, 0f); lineTo(x + step * 0.5f, h); close() }
            drawPath(p, if (i % 2 == 0) STRIPE_RED else STRIPE_NAVY)
            x += step
            i++
        }
    }
}

/** 소인(Figma Postmark): 동그라미 테두리 + 이름 + 날짜. */
@Composable
private fun Postmark(s: BookScale, x: Number, y: Number, size: Number, ink: Color, text: String, date: String) {
    At(s, x, y, size, size) {
        Box(Modifier.fillMaxSize().padding(s.u(size.toFloat() / 32f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(ink.copy(alpha = 0.85f), style = Stroke(width = s.u(size.toFloat() / 32f).toPx())) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = s.body(size.toFloat() * 0.19f, ink, bold = true), maxLines = 1)
                Text(date, style = s.body(size.toFloat() * 0.11f, ink), maxLines = 1)
            }
        }
    }
}

/**
 * 표지(Figma 추억 책 · 표지 고르기 · 속지 · 표지): 꽃신 편지지(분홍 줄 편지지 · 빨간 여백 줄) · 군사우편(크라프트 · 항공우편 띠).
 * "지수와 민준의 / 479일 / 입대 — 전역". 표지 고르기 · 권유 팝업 · PDF 첫 쪽이 같은 그림을 쓴다.
 */
@Composable
fun BookCoverArt(cover: BookCover, summary: BookSummary?, modifier: Modifier = Modifier) {
    val fonts = bookFonts()
    BoxWithConstraints(modifier.aspectRatio(1f).background(if (cover == BookCover.FLOWER) FLOWER_PAPER else MILITARY_PAPER).clipToBounds()) {
        val s = BookScale(maxWidth, fonts, LocalDensity.current.fontScale)
        val made = summary?.madeOn?.dot().orEmpty()
        when (cover) {
            BookCover.FLOWER -> {
                repeat(10) { i -> At(s, 56, 167 + i * 61, 689, 3) { Box(Modifier.fillMaxSize().background(FLOWER_RULE)) } }
                At(s, 100, 0, 5, 800) { Box(Modifier.fillMaxSize().background(FLOWER_MARGIN)) }
                Postmark(s, 502, 83, 206, FLOWER_INK, "꽃신 우편", made)
            }
            BookCover.MILITARY -> {
                At(s, 0, 0, 800, 48) { AirmailStripe(Modifier.fillMaxSize(), MILITARY_PAPER) }
                At(s, 0, 752, 800, 48) { AirmailStripe(Modifier.fillMaxSize(), MILITARY_PAPER) }
                Postmark(s, 502, 83, 206, MILITARY_INK, "군사우편", made)
            }
        }
        if (summary != null) {
            At(s, 111, 320, 640) { Text("${summary.my.and()} ${summary.partner}의", style = s.hand(74, if (cover == BookCover.FLOWER) FLOWER_HAND else INK), maxLines = 1) }
            At(s, 111, 405, 680) { Text("${summary.days ?: 0}일", style = s.number(178, if (cover == BookCover.FLOWER) FLOWER_INK else NUMBER), maxLines = 1) }
            At(s, 111, 640, 640) { Text(summary.period(), style = s.body(34, MUTED), maxLines = 1) }
        }
    }
}

private fun BookSummary.period() = "${from?.dot() ?: ""} — ${to?.dot() ?: ""}"

/** 요약(Figma 속지 · 요약): "우리의 479일" + 큰 숫자 넷(함께 센 날 · 편지 · 그림 · 만남). */
@Composable
private fun SummaryPage(state: MemoryState, s: BookScale) {
    val sum = state.summary ?: return
    At(s, 111, 110, 640) { Text("우리의 ${sum.days ?: 0}일", style = s.hand(64, INK)) }
    At(s, 111, 196, 640) { Text(sum.period(), style = s.body(28, MUTED)) }
    listOf("${sum.days ?: 0}일" to "함께 센 날", "${sum.letters}통" to "편지", "${sum.drawings}장" to "그림", "${sum.meets}번" to "만남")
        .forEachIndexed { i, (value, label) ->
            At(s, 111, 290 + i * 118, 600, 118) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = s.number(92, NUMBER), modifier = Modifier.width(s.u(330)), maxLines = 1)
                    Text(label, style = s.body(28, MUTED))
                }
            }
        }
    At(s, 0, 752, 800) { Text("1", style = s.body(22, MUTED), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

/** 한 달 왼쪽(Figma 속지 · 한 달): 달력 + 그날의 사진 한 장(폴라로이드) + 그 달 숫자. */
@Composable
private fun LeftPage(m: MonthDetail, photos: Map<String, ByteArray?>, s: BookScale, number: Int) {
    At(s, 72, 64) { Text("${m.ym.year}년 ${m.ym.month}월", style = s.body(36, INK, bold = true)) }
    At(s, 64, 130, 520) {
        MonthCalendar(m, today = null, ink = INK, muted = MUTED, sunday = NUMBER, font = s.fonts.body, scale = s.u(52).value / 44f)
    }
    val meet = m.meets.firstOrNull { it.photos.isNotEmpty() }
    if (meet != null) {
        At(s, 490, 440, 270, 320) {
            Column(Modifier.fillMaxSize().rotate(-3f).shadow(4.dp).background(Color.White).padding(s.u(14))) {
                PhotoImage(photos[meet.photos.first()], Modifier.fillMaxWidth().weight(1f))
                Text(meet.title, style = s.hand(32, INK), maxLines = 1, modifier = Modifier.padding(top = s.u(10)))
            }
        }
    }
    val stats = listOfNotNull(
        m.meets.groupBy { it.tag }.map { (tag, list) -> "$tag ${list.size}번" }.joinToString(" · ").takeIf { it.isNotEmpty() },
        "편지 ${m.letters.size}통",
        "그림 ${m.drawings}장".takeIf { m.drawings > 0 },
    ).joinToString(" · ")
    At(s, 72, 690, 400) { Text(stats, style = s.body(24, MUTED)) }
    At(s, 0, 752, 800) { Text("$number", style = s.body(22, MUTED), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

/** 한 달 오른쪽: 편지 세 칸(#23 결정 2 — 긴 순 · 그날의 한 줄 · 빈칸). 내 것은 분홍, 상대 것은 크라프트 카드. */
@Composable
private fun RightPage(m: MonthDetail, photos: Map<String, ByteArray?>, s: BookScale, number: Int) {
    val slots = bookSlots(m.letters, m.dayLines)
    slots.forEachIndexed { i, slot ->
        At(s, 96, 64 + i * 224, 640, 200) {
            when (slot) {
                is BookSlot.Letter -> SlotCard(s, slot.letter.mine, slot.letter.meta) {
                    val l = slot.letter
                    if (l.text.isBlank()) Art(l, photos, Modifier.fillMaxHeight())
                    else Text(l.text, style = s.hand(40, INK), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                is BookSlot.Line -> SlotCard(s, slot.line.mine, slot.line.meta) {
                    Text(slot.line.text, style = s.hand(40, INK), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                BookSlot.Empty -> Box(Modifier.fillMaxSize().border(s.u(2), RULE))
            }
        }
    }
    At(s, 0, 752, 800) { Text("$number", style = s.body(22, MUTED), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun SlotCard(s: BookScale, mine: Boolean, meta: String, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(if (mine) CARD_MINE else CARD_PARTNER).padding(horizontal = s.u(28), vertical = s.u(18))) {
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.CenterStart) { body() }
        Box(Modifier.fillMaxWidth().height(s.u(2)).background(RULE))
        Text(meta, style = s.body(24, MUTED), modifier = Modifier.padding(top = s.u(12)), maxLines = 1)
    }
}

/** 마지막 장(Figma 속지 · 마지막 장): "수고했어, 민준아." + 가장 최근 편지 한마디 + 전역일 · 소인. */
@Composable
private fun LastPage(state: MemoryState, s: BookScale) {
    val sum = state.summary ?: return
    At(s, 111, 120, 640) { Text("수고했어, ${sum.soldier.vocative()}.", style = s.hand(64, INK), maxLines = 1) }
    sum.lastWord?.let { (text, author) ->
        At(s, 111, 230, 600) { Text(text, style = s.hand(44, INK), maxLines = 3, overflow = TextOverflow.Ellipsis) }
        At(s, 111, 420, 600) { Text("— $author", style = s.hand(40, INK)) }
    }
    (sum.to ?: sum.madeOn)?.let { d ->
        At(s, 0, 500, 800) { Text(d.dot(), style = s.number(110, NUMBER), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        At(s, 0, 640, 800) { Text("전역", style = s.hand(52, INK), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    val flower = state.cover == BookCover.FLOWER
    Postmark(s, 600, 620, 150, if (flower) FLOWER_INK else MILITARY_INK, if (flower) "꽃신 우편" else "군사우편", sum.madeOn?.dot().orEmpty())
}

@Composable
private fun Art(l: MonthLetter, photos: Map<String, ByteArray?>, modifier: Modifier) {
    when {
        // 그림은 칸 높이에 맞춘 정사각 안에만 그린다(카드 밖으로 넘치지 않게).
        l.thumb != null -> Box(modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).clipToBounds()) { DrawingView(l.thumb, Modifier.fillMaxSize()) }
        l.photoId != null -> PhotoImage(photos[l.photoId], modifier.aspectRatio(4f / 3f, matchHeightConstraintsFirst = true).clipToBounds())
    }
}

private fun kotlinx.datetime.LocalDate.dot() = "$year.${(month.ordinal + 1).toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

/** "지수와" · "민준과". */
internal fun String.and(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "과" else "와"
}

/** 부르는 말("민준아" · "지수야"). */
internal fun String.vocative(): String {
    val last = lastOrNull() ?: return this
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (batchim) "아" else "야"
}
