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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
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
import com.sadturtleman.flowershoes.core.designsystem.KkLetterFont
import com.sadturtleman.flowershoes.core.designsystem.KkPrimaryButton
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.KkTopBar
import com.sadturtleman.flowershoes.core.designsystem.family
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.drawingui.DrawingView
import com.sadturtleman.flowershoes.core.drawingui.StickerPreview
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

/** 추억 책 한 쪽. 표지는 쪽 번호가 없고 요약이 1쪽, 한 달은 왼쪽(달력 · 사진) · 오른쪽(편지) 두 쪽. */
sealed interface BookPage {
    data object Cover : BookPage
    data object Summary : BookPage
    data class Left(val ym: YearMonth) : BookPage
    data class Right(val ym: YearMonth) : BookPage
}

/** 책에 들어가는 쪽들(내용이 있는 달만, 오래된 달부터). */
fun MemoryState.bookPages(): List<BookPage> = buildList {
    add(BookPage.Cover)
    add(BookPage.Summary)
    months.values.sortedBy { it.ym }.filter { it.letters.isNotEmpty() || it.meets.isNotEmpty() || it.bars.isNotEmpty() }.forEach {
        add(BookPage.Left(it.ym))
        add(BookPage.Right(it.ym))
    }
}

/** 추억 책 PDF 값(수익 모델 확정). 스토어 가격을 못 읽으면 이 값을 보여 준다. */
private const val BOOK_PRICE = "5,900원"

/** PDF 한 쪽의 픽셀(8인치 · 약 225dpi). */
private const val CAPTURE_PX = 1800

/**
 * 추억 · 책으로 보기(Figma): 추억 책 펼침 미리보기 · 쪽 넘기기 + "PDF로 영구 소장"(화면용 + 포토북 인쇄용) 카드.
 * 앱 안에서 보는 책은 무료, 디자인된 PDF 만 [BOOK_PRICE]. PDF 는 폰에서 만들어 서버로 내용이 나가지 않는다.
 */
@Composable
internal fun BookScreen(state: MemoryState, photos: Map<String, ByteArray?>, actions: MemoryBookActions?, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val c = KkTheme.colors
    val pages = remember(state) { state.bookPages() }
    // 펼침: 표지 · 요약은 한 쪽씩, 그 뒤는 왼쪽 + 오른쪽.
    val spreads = remember(pages) { listOf(listOf(pages[0]), listOf(pages[1])) + pages.drop(2).chunked(2) }
    var spread by remember { mutableStateOf(spreads.size - 1) }
    val store = actions?.store
    val owned by (actions?.sharedOwned ?: store?.owned ?: remember { MutableStateFlow(false) }).collectAsState(initial = false)
    var price by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) { price = store?.price() }
    var busy by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<Int?>(null) }
    var pdf by remember { mutableStateOf<BookPdf?>(null) }
    val scope = rememberCoroutineScope()

    fun make() {
        val writer = actions?.newPdf?.invoke() ?: run { onNotice("이 기기에서는 아직 PDF 를 만들 수 없어요"); return }
        pdf = writer
        capture = 0
    }

    fun buy() {
        if (store == null) { onNotice("결제는 앱 출시와 함께 열려요"); return }
        if (busy) return
        busy = true
        scope.launch {
            when (val r = store.buy()) {
                PurchaseOutcome.Purchased -> { onNotice("추억 책이 열렸어요. PDF 를 만들게요"); make() }
                PurchaseOutcome.NothingToRestore, PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> onNotice(r.message)
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
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
                    Text("‹", style = KkTheme.type.title, color = if (spread > 0) c.sub else c.sub,
                        modifier = Modifier.clickable(enabled = spread > 0, role = Role.Button) { spread-- }.padding(KkSpacing.sm))
                    Text(sp.pageLabel(pages), style = KkTheme.type.label, color = c.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("›", style = KkTheme.type.title, color = if (spread < spreads.size - 1) c.sub else c.sub,
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
                    KkPrimaryButton(label, { if (owned) make() else buy() }, Modifier.fillMaxWidth(), enabled = capture == null, loading = busy)
                    // 디버그 빌드만: 결제 없이 PDF 를 만들어 본다(만들기 · 공유 흐름 확인용).
                    if (!owned && org.koin.compose.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.DevFlags>()?.freeBookPdf == true) {
                        Text("개발용 · 결제 없이 PDF 만들기", style = KkTheme.type.label, color = c.accent,
                            modifier = Modifier.clickable(enabled = capture == null, role = Role.Button) { make() }.padding(vertical = KkSpacing.xs))
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
            ) { BookPageView(pages[i], state, photos, Modifier.fillMaxSize()) }
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

private fun List<BookPage>.pageLabel(all: List<BookPage>): String {
    val total = all.size - 1
    val nums = map { all.indexOf(it) }.filter { it > 0 }
    return when {
        first() == BookPage.Cover -> "표지 / ${total}쪽"
        nums.size == 1 -> "${nums[0]}쪽 / ${total}쪽"
        else -> "${nums.first()}–${nums.last()}쪽 / ${total}쪽"
    }
}

// ── 추억 책 쪽(Figma 📖 추억 책 · 시안 B 군사우편) ───────────────────────────

private val NAVY = Color(0xFF2B3A55)
private val RED = Color(0xFFC8463D)
private val KRAFT = Color(0xFFE9DCC4)
private val PAPER = Color(0xFFFFFDF7)
private val INK = Color(0xFF2E2A26)
private val MUTED = Color(0xFF7A6E60)
private val RULE = Color(0xFFCFC3AE)

/** 쪽 하나(정사각). 좌표는 800 기준 시안 값을 쪽 너비에 맞춰 줄인다. */
@Composable
fun BookPageView(page: BookPage, state: MemoryState, photos: Map<String, ByteArray?>, modifier: Modifier = Modifier) {
    val font = KkLetterFont.MYEONGJO.family()
    BoxWithConstraints(modifier.aspectRatio(1f).background(if (page == BookPage.Cover) NAVY else KRAFT)) {
        val s = BookScale(maxWidth, font, LocalDensity.current.fontScale)
        when (page) {
            BookPage.Cover -> CoverPage(state, s)
            BookPage.Summary -> SummaryPage(state, s)
            is BookPage.Left -> state.months[page.ym]?.let { LeftPage(it, photos, s, state.bookPages().indexOf(page)) }
            is BookPage.Right -> state.months[page.ym]?.let { RightPage(it, photos, s, state.bookPages().indexOf(page)) }
        }
    }
}

/** 시안 좌표(800) → dp, 글자 크기 → sp(글자 크기 설정과 무관하게 쪽에 맞춘다). */
private class BookScale(val width: Dp, val font: FontFamily, private val fontScale: Float) {
    fun u(v: Number): Dp = width * (v.toFloat() / 800f)
    fun sp(v: Number): TextUnit = androidx.compose.ui.unit.TextUnit(u(v).value / fontScale, androidx.compose.ui.unit.TextUnitType.Sp)
    fun style(size: Number, color: Color, bold: Boolean = false, spacing: Float = 0f) = TextStyle(
        fontFamily = font, fontSize = sp(size), color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = androidx.compose.ui.unit.TextUnit(spacing, androidx.compose.ui.unit.TextUnitType.Em),
        lineHeight = sp(size.toFloat() * 1.6f),
    )
}

@Composable
private fun At(s: BookScale, x: Number, y: Number, w: Number? = null, h: Number? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.offset(s.u(x), s.u(y)).then(if (w != null) Modifier.width(s.u(w)) else Modifier).then(if (h != null) Modifier.height(s.u(h)) else Modifier),
    ) { content() }
}

/** 항공우편 띠(빨강 · 남색 사선). */
@Composable
internal fun AirmailStripe(modifier: Modifier) {
    Canvas(modifier.background(PAPER)) {
        val h = size.height
        var x = -h
        while (x < size.width + h) {
            listOf(RED to 0f, NAVY to 24f).forEach { (col, dx) ->
                val step = h / 28f
                val p = Path().apply {
                    moveTo(x + dx * step, h); lineTo(x + dx * step + h, 0f); lineTo(x + (dx + 22) * step + h, 0f); lineTo(x + (dx + 22) * step, h); close()
                }
                drawPath(p, col)
            }
            x += 48 * h / 28f
        }
    }
}

@Composable
private fun CoverPage(state: MemoryState, s: BookScale) {
    val sum = state.summary ?: return
    val months = state.progress?.total ?: state.progress?.current ?: 0
    At(s, 0, 0, 800, 28) { AirmailStripe(Modifier.fillMaxSize()) }
    At(s, 0, 772, 800, 28) { AirmailStripe(Modifier.fillMaxSize()) }
    At(s, 110, 210, 580, 380) {
        Box(Modifier.fillMaxSize().background(PAPER)) {
            AirmailStripe(Modifier.fillMaxWidth().height(s.u(14)))
        }
    }
    At(s, 548, 250, 104, 124) {
        Box(Modifier.fillMaxSize().background(Color.White).border(s.u(3), RED), contentAlignment = Alignment.Center) {
            StickerPreview("img:heart/12", Modifier.size(s.u(80)))
        }
    }
    At(s, 486, 292, 120, 120) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(NAVY.copy(alpha = 0.55f), style = Stroke(width = s.u(3).toPx())) }
    }
    At(s, 486, 342, 120) { Text(sum.to?.dot() ?: "", style = s.style(15, NAVY.copy(alpha = 0.7f), bold = true), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    At(s, 150, 262) { Text("군 사 우 편", style = s.style(18, RED, bold = true, spacing = 0.1f)) }
    At(s, 150, 312) { Text("우리의 ${months}개월", style = s.style(50, INK, bold = true)) }
    At(s, 150, 412, 500, 1) { Box(Modifier.fillMaxSize().background(RULE)) }
    At(s, 150, 432) { Text("받는 사람  ${sum.partner}", style = s.style(20, INK)) }
    At(s, 150, 470) { Text("보내는 사람  ${sum.my}", style = s.style(20, INK)) }
    At(s, 150, 530) { Text("${sum.from?.dot() ?: ""} — ${sum.to?.dot() ?: ""}", style = s.style(16, MUTED, spacing = 0.04f)) }
    At(s, 0, 700, 800) { Text("꽃신", style = s.style(20, Color.White, bold = true), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun SummaryPage(state: MemoryState, s: BookScale) {
    val sum = state.summary ?: return
    At(s, 64, 60) { Text("주고받은 기록", style = s.style(36, INK, bold = true)) }
    At(s, 64, 112) { Text(listOfNotNull("${sum.from?.dot() ?: ""} — ${sum.to?.dot() ?: ""}", sum.days?.let { "${it}일" }).joinToString(" · "), style = s.style(17, MUTED)) }
    val rows = listOf(
        Triple("보낸 편지", "${sum.my} ${sum.myLetters}통 · ${sum.partner} ${sum.partnerLetters}통", "${sum.letters}"),
        Triple("그림", "", "${sum.drawings}"),
        Triple("만남", "면회 ${sum.visits} · 외박 ${sum.overnights} · 휴가 ${sum.vacations}", "${sum.meets}"),
        Triple("가장 바빴던 달", sum.busiest?.first?.let { "${it.year}년 ${it.month}월" } ?: "", sum.busiest?.second?.let { "${it}통" } ?: "—"),
    )
    At(s, 64, 160, 672, 300) {
        Column(Modifier.fillMaxSize().background(PAPER).border(1.dp, RULE)) {
            rows.forEachIndexed { i, (label, detail, value) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(RULE))
                Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = s.u(28)), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = s.style(20, INK, bold = true), modifier = Modifier.width(s.u(190)))
                    Text(detail, style = s.style(16, MUTED), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(value, style = s.style(30, RED, bold = true))
                }
            }
        }
    }
    At(s, 64, 500) { Text("지나온 길", style = s.style(22, INK, bold = true)) }
    sum.milestones.take(6).forEachIndexed { i, (date, name) ->
        val last = i == sum.milestones.take(6).lastIndex
        At(s, 64 + i * 114, 550, 100, 100) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(if (last) RED else Color.Transparent).border(s.u(3), if (last) RED else NAVY.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.shortDot(), style = s.style(14, if (last) Color.White else NAVY, bold = true))
                    Text(name, style = s.style(20, if (last) Color.White else NAVY, bold = true))
                }
            }
        }
    }
    At(s, 0, 752, 800) { Text("1", style = s.style(14, MUTED), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun LeftPage(m: MonthDetail, photos: Map<String, ByteArray?>, s: BookScale, number: Int) {
    At(s, 64, 48) { Text("${ordinalKo(m.number)} 달", style = s.style(16, RED, bold = true, spacing = 0.06f)) }
    At(s, 64, 72) { Text("${m.ym.year}년 ${m.ym.month}월", style = s.style(42, INK, bold = true)) }
    At(s, 436, 92, 300) {
        Text("편지 ${m.letters.size}통 · 그림 ${m.drawings}장 · 만남 ${m.meets.size}번", style = s.style(17, MUTED), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
    At(s, 64, 162, 672) {
        Column(Modifier.fillMaxWidth().background(PAPER).border(1.dp, RULE).padding(s.u(10))) {
            MonthCalendar(m, today = null, ink = INK, muted = MUTED, sunday = RED, font = s.font, scale = s.u(62).value / 44f)
        }
    }
    val shots = m.meets.flatMap { it.photos }.take(2)
    shots.forEachIndexed { i, id ->
        At(s, 64 + i * 262, 590 + i * 4, 250, 160) {
            Box(Modifier.fillMaxSize().rotate(if (i == 0) -2f else 2f).background(Color.White).padding(s.u(8))) {
                PhotoImage(photos[id], Modifier.fillMaxSize())
            }
        }
    }
    val captions = m.meets.map { "${it.date.md()} ${it.title}" } + m.bars.filter { it.kind == BarKind.RANK }.map { "${m.ym.month}.${it.from} ${it.label}" }
    if (captions.isNotEmpty()) At(s, if (shots.isEmpty()) 64 else 600, 616, if (shots.isEmpty()) 672 else 180) {
        Text(captions.take(4).joinToString("\n"), style = s.style(15, MUTED))
    }
    At(s, 0, 768, 800) { Text("$number", style = s.style(14, MUTED), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

/** 오른쪽 쪽: 그 달 편지 세 통까지(글 사이 그림 · 그림만 · 글만을 먼저 하나씩 고른다) — 봉투 카드. */
@Composable
private fun RightPage(m: MonthDetail, photos: Map<String, ByteArray?>, s: BookScale, number: Int) {
    val picked = buildList {
        LetterKind.entries.forEach { k -> m.letters.firstOrNull { it.kind == k }?.let(::add) }
        m.letters.filter { it !in this }.forEach { if (size < 3) add(it) }
    }.take(3).sortedBy { it.date }
    At(s, 64, 48, 672, 700) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(s.u(20))) {
            if (picked.isEmpty()) Text("이달에는 편지 대신 만남이 있었어요", style = s.style(18, MUTED))
            picked.forEach { l ->
                Column(Modifier.fillMaxWidth().weight(if (l.kind == LetterKind.TEXT) 0.8f else 1.2f).background(PAPER)) {
                    AirmailStripe(Modifier.fillMaxWidth().height(s.u(10)))
                    Column(Modifier.fillMaxSize().padding(horizontal = s.u(24), vertical = s.u(16)), verticalArrangement = Arrangement.spacedBy(s.u(8))) {
                        Text(l.meta, style = s.style(15, RED, bold = true))
                        when (l.kind) {
                            LetterKind.TEXT -> Text(l.text, style = s.style(18, INK), maxLines = 5, overflow = TextOverflow.Ellipsis)
                            LetterKind.INLINE -> {
                                if (l.before.isNotBlank()) Text(l.before, style = s.style(18, INK), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Art(l, photos, Modifier.height(s.u(110)))
                                if (l.after.isNotBlank()) Text(l.after, style = s.style(18, INK), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            LetterKind.DRAWING_ONLY -> {
                                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) { Art(l, photos, Modifier.fillMaxHeight()) }
                                Text("그림 편지", style = s.style(13, NAVY, bold = true), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
    At(s, 64, 768, 672) {
        Text("${m.ym.month}월 편지 ${m.letters.size}통 중 ${picked.size}통 · $number", style = s.style(14, MUTED), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
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

private fun kotlinx.datetime.LocalDate.shortDot() = "${(year % 100).toString().padStart(2, '0')}.${(month.ordinal + 1).toString().padStart(2, '0')}"
