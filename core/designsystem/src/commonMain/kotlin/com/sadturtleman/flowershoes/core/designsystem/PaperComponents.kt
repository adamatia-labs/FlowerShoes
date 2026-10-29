package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.res.Res
import com.sadturtleman.flowershoes.core.designsystem.res.do_hyeon
import com.sadturtleman.flowershoes.core.model.Role as SenderRole
import org.jetbrains.compose.resources.Font
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// Figma "🎨 꽃신 v2 디자인 시스템"의 종이 물건(조합 341:82 · 기본 340:89).
// 기울기 · 테이프는 보여 주기만 하는 물건(홈 태그 · 폴라로이드 · 추억 페이지 · 책 표지)에만 쓴다(design.md).
// 편지함 봉투 · 목록 · 버튼처럼 읽고 누르는 것은 반듯하게 두고, 장식(우표 · 소인 · 미니 폴라로이드)만 기운다(Figma Envelope Row 설명).

// ── Tape ──────────────────────────────────────────────────────────────────

/**
 * 마스킹 테이프(Figma Tape `340:247`, 반투명 paper/tape). 종이 위 가운데에 -8 걸쳐 놓고 ±5° 돌려 쓴다.
 * 위치 · 회전은 [modifier] 로 준다(예: `Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).rotate(4f)`).
 *
 * @param width 기본 56(Figma). 작은 폴라로이드는 35.
 * @param height 기본 16(Figma). 작은 폴라로이드는 10.
 */
@Composable
fun KkTape(modifier: Modifier = Modifier, width: Dp = 56.dp, height: Dp = 16.dp) {
    Box(modifier.size(width, height).background(KkTheme.colors.tape))
}

// ── Paper Tag ─────────────────────────────────────────────────────────────

/** 종이 태그 값 글꼴: 숫자(D-day/S) · 손글씨(Hand/L). */
enum class KkPaperTagStyle { Number, Hand }

/**
 * 종이 태그(Figma Paper Tag `341:212`): paper/tag 바탕 140×66 + 위 가운데 테이프. 다음 휴가 · 만남 · 진급 같은 작은 정보.
 * 태그 위 글자는 ink 만 쓴다. 보여 주기만 하는 물건이라 [tilt] 로 기울일 수 있다.
 *
 * @param label 위 라벨(Label), 예: "다음 휴가".
 * @param value 아래 값, 예: "D-23".
 * @param valueStyle [KkPaperTagStyle.Number] 면 D-day/S, [KkPaperTagStyle.Hand] 면 Hand/L.
 * @param tilt 태그 전체 회전(도). 기본 0. 보여 주기만 하는 물건이라 누르는 동작은 두지 않는다.
 */
@Composable
fun KkPaperTag(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: KkPaperTagStyle = KkPaperTagStyle.Number,
    tilt: Float = 0f,
) {
    val c = KkTheme.colors
    val t = KkTheme.type
    Box(modifier.rotate(tilt)) {
        Box(
            Modifier.widthIn(min = 140.dp).height(66.dp).kkElevation(KkElevation.Low, RectangleShape).background(c.tag),
        ) {
            Text(label, style = t.label, color = c.ink, maxLines = 1, modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp))
            Text(
                value,
                style = if (valueStyle == KkPaperTagStyle.Number) t.ddayS else t.handL,
                color = c.ink, maxLines = 1,
                modifier = Modifier.padding(start = 14.dp, top = 28.dp, end = 14.dp),
            )
        }
        KkTape(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).rotate(4f))
    }
}

// ── Polaroid ──────────────────────────────────────────────────────────────

/**
 * 폴라로이드 틀 크기. [Regular] 는 Figma Polaroid(틀 8 · 아래 40 · 테이프 56×16),
 * [Compact] 는 추억 · 그날의 한 줄의 작은 사진(틀 5 · 아래 24 · 테이프 35×10).
 */
enum class KkPolaroidSize(internal val frame: Dp, internal val bottom: Dp, internal val tapeWidth: Dp, internal val tapeHeight: Dp) {
    Regular(8.dp, 40.dp, 56.dp, 16.dp),
    Compact(5.dp, 24.dp, 35.dp, 10.dp),
}

/**
 * 폴라로이드(Figma Polaroid `341:282`): 받은 그림 · 사진. 액자는 늘 흰색이다(콘텐츠라 테마 · 다크와 무관).
 * 정사각 사진 칸 + 아래 손글씨 캡션 + 위 가운데 테이프. 너비는 [modifier] 로 정하고(Figma 150) 높이는 따라온다.
 *
 * @param tilt 회전(도). 보여 주기만 하는 자리에서만 준다.
 * @param caption 아래 손글씨(Hand/Letter) 한 줄. null 이면 빈 여백.
 * @param size 틀 크기([KkPolaroidSize]).
 * @param onReplay 주면 사진 칸 오른쪽 아래에 "다시 보기"(그림 그리는 과정 재생)를 띄운다.
 * @param photo 사진 칸 내용. 칸을 꽉 채워 그린다(비었을 때 자리 표시도 여기서).
 */
@Composable
fun KkPolaroid(
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    caption: String? = null,
    size: KkPolaroidSize = KkPolaroidSize.Regular,
    onReplay: (() -> Unit)? = null,
    photo: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.rotate(tilt)) {
        Column(
            Modifier.fillMaxWidth().kkElevation(KkElevation.Low, RectangleShape).background(Palette.PaperWhite)
                .padding(start = size.frame, end = size.frame, top = size.frame),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                photo()
                if (onReplay != null) ReplayChip(onReplay, Modifier.align(Alignment.BottomEnd).padding(bottom = KkSpacing.sm))
            }
            Box(Modifier.fillMaxWidth().height(size.bottom), contentAlignment = Alignment.CenterStart) {
                if (caption != null) {
                    // 액자가 흰색 고정이라 캡션도 라이트 ink 로 고정한다.
                    Text(caption, style = KkTheme.type.handLetter, color = Palette.InkBrown900, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        KkTape(Modifier.align(Alignment.TopCenter).offset(y = -size.frame).rotate(4f), size.tapeWidth, size.tapeHeight)
    }
}

/** "▶ 다시 보기"(사진 위 60% 검정 알약, 흰 글자). 사진 위라 테마와 무관하다. */
@Composable
private fun ReplayChip(onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clickable(role = Role.Button, onClick = onClick).minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(KkRadius.full)).background(Color.Black.copy(alpha = 0.6f)).padding(start = KkSpacing.sm, end = 10.dp, top = KkSpacing.xs, bottom = KkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkIcon(KkIcons.Play, tint = Palette.PaperWhite, size = 10.dp)
        Text("다시 보기", style = KkTheme.type.caption.copy(fontWeight = FontWeight.Medium), color = Palette.PaperWhite, maxLines = 1)
    }
}

// ── Postmark ──────────────────────────────────────────────────────────────

/**
 * 편지지 위 소인의 잉크(Figma Postmark Ink=Military · Flower). 보낸 사람 쪽 색 · 문구라 받는 사람 테마와 무관하다.
 * 화면 UI 위 소인(Ink=Theme)은 [KkPostmark] 의 기본 색(stamp)을 쓴다.
 */
enum class KkPostmarkInk(val label: String, val color: Color) {
    /** 군인이 보낸 편지: "군사우편". */
    Military("군사우편", Palette.Stamp600),

    /** 곰신이 보낸 편지: "꽃신 우편". */
    Flower("꽃신 우편", Palette.Rosewood600),
    ;

    companion object {
        /** 보낸 사람 역할 → 소인(design.md: 군인은 "군사우편", 곰신은 "꽃신 우편"). */
        fun of(sender: SenderRole): KkPostmarkInk = when (sender) {
            SenderRole.SOLDIER -> Military
            SenderRole.GOMSIN -> Flower
        }
    }
}

/**
 * 소인(Figma Postmark `340:248`): 85% 동그라미 테두리 + 문구 + 날짜. 크기는 [modifier] 로 정하고(기본 64) 글자 · 선은 크기에 비례한다.
 * 글자는 장식이라 글자 크기 설정을 따르지 않는다.
 *
 * @param text 가운데 문구, 예: "보냄" · "군사우편".
 * @param date 아래 날짜(예: "2026.10.28"). 빈 문자열이면 그리지 않는다.
 * @param color 잉크. 기본은 화면 UI 위 소인(stamp/ink). 편지지 위는 [KkPostmarkInk.color].
 * @param textFont 문구 글꼴. 기본 도현(Figma).
 * @param textWeight 문구 굵기. 한 굵기뿐인 도현은 Normal 로 둔다.
 * @param dateFont 날짜 글꼴. 기본 body.
 */
@Composable
fun KkPostmark(
    text: String,
    date: String,
    modifier: Modifier = Modifier,
    color: Color = KkTheme.colors.stamp,
    textFont: FontFamily = doHyeon(),
    textWeight: FontWeight = FontWeight.Normal,
    dateFont: FontFamily = KkTheme.fonts.body,
) {
    BoxWithConstraints(modifier.size(64.dp).clearAndSetSemantics { contentDescription = listOf(text, date).filter { it.isNotEmpty() }.joinToString(" ") }) {
        val side = minOf(maxWidth, maxHeight)
        val ring = side / 32f
        val density = LocalDensity.current
        fun style(family: FontFamily, ratio: Float, weight: FontWeight) = with(density) {
            val size = (side * ratio).toSp()
            TextStyle(fontFamily = family, fontSize = size, fontWeight = weight, lineHeight = size * 1.35f, textAlign = TextAlign.Center)
        }
        Box(Modifier.fillMaxSize().padding(ring), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(color.copy(alpha = 0.85f), style = Stroke(width = ring.toPx())) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = style(textFont, 0.19f, textWeight), color = color, maxLines = 1)
                if (date.isNotEmpty()) Text(date, style = style(dateFont, 0.11f, FontWeight.Normal), color = color, maxLines = 1)
            }
        }
    }
}

/** 편지지 위 소인: 보낸 사람 쪽 문구 · 색([KkPostmarkInk]). */
@Composable
fun KkPostmark(ink: KkPostmarkInk, date: String, modifier: Modifier = Modifier) =
    KkPostmark(ink.label, date, modifier, color = ink.color)

@Composable
private fun doHyeon(): FontFamily {
    val font = Font(Res.font.do_hyeon, FontWeight.Normal)
    return remember(font) { FontFamily(font) }
}

// ── Envelope Row ──────────────────────────────────────────────────────────

/**
 * 편지함 봉투 표시(design.md): 하트 대신 봉인 · 폴라로이드 · 소인 · 우표로 구분한다.
 */
sealed interface KkEnvelopeMark {
    /** 봉인 "새" = 안 읽은 편지. 우표([stamp], 예: "D-479")도 함께 붙는다. */
    data class Seal(val stamp: String) : KkEnvelopeMark

    /** 미니 폴라로이드 = 그림이 든 편지. [drawing] 은 실제 그림(없으면 빈 자리). */
    data class Polaroid(val drawing: ImageBitmap?) : KkEnvelopeMark

    /** 소인 "보냄" = 내가 보낸 편지. [date] 는 소인 날짜(예: "2026.10.28"). */
    data class Postmark(val date: String) : KkEnvelopeMark

    /** 우표 = 그 밖의 받은 편지. [text] 는 우표 글자(예: "D-479"). */
    data class Stamp(val text: String) : KkEnvelopeMark
}

/**
 * 편지함 한 줄(Figma Envelope Row `341:254`): surface 봉투(높이 100, line 테두리 · 덮개 선) + 실제 편지 첫 줄(Hand/Letter) + 보낸 사람 · 날짜(Caption).
 * 행은 늘 반듯하고, 오른쪽 장식([mark])만 기운다. 너비는 부모를 꽉 채운다.
 *
 * @param line 편지 첫 줄(자리표시 대신 실제 내용). 한 줄로 자른다.
 * @param meta 보낸 사람 · 날짜, 예: "민준 · 10월 28일".
 * @param mark 오른쪽 장식 = 편지 상태([KkEnvelopeMark]).
 * @param onClick 봉투를 눌렀을 때(편지 열기).
 */
@Composable
fun KkEnvelopeRow(
    line: String,
    meta: String,
    mark: KkEnvelopeMark,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KkTheme.colors
    val state = when (mark) {
        is KkEnvelopeMark.Seal -> "새 편지"
        is KkEnvelopeMark.Polaroid -> "그림 편지"
        is KkEnvelopeMark.Postmark -> "보낸 편지"
        is KkEnvelopeMark.Stamp -> "받은 편지"
    }
    // 봉인은 봉투 모서리 밖으로 걸쳐서, 테두리 위에 그려지도록 봉투 바깥 Box 에 둔다.
    Box(
        modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = state },
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp).kkElevation(KkElevation.Low, RectangleShape).background(c.surface).border(1.dp, c.line)) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val flap = Path().apply { moveTo(0f, 0f); lineTo(size.width / 2f, 34.dp.toPx()); lineTo(size.width, 0f) }
                drawPath(flap, c.line, style = Stroke(width = 1.dp.toPx()))
            }
            Text(
                line, style = KkTheme.type.handLetter, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 17.dp, top = 35.dp, end = 80.dp),
            )
            Text(meta, style = KkTheme.type.caption, color = c.sub, maxLines = 1, modifier = Modifier.padding(start = 17.dp, top = 73.dp, end = 80.dp))
            val deco = Modifier.align(Alignment.TopEnd).clearAndSetSemantics {}
            when (mark) {
                is KkEnvelopeMark.Seal -> PostageStamp(mark.stamp, deco.padding(top = 12.dp, end = 16.dp).rotate(-3f))
                is KkEnvelopeMark.Polaroid -> MiniPolaroid(mark.drawing, deco.padding(top = 7.dp, end = 13.dp).rotate(-4f))
                is KkEnvelopeMark.Postmark -> KkPostmark("보냄", mark.date, deco.padding(top = 20.dp, end = 19.dp).rotate(-9f))
                is KkEnvelopeMark.Stamp -> PostageStamp(mark.text, deco.padding(top = 12.dp, end = 16.dp).rotate(-3f))
            }
        }
        if (mark is KkEnvelopeMark.Seal) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-11).dp).size(28.dp).clip(CircleShape).background(c.accent).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) { Text("새", style = KkTheme.type.labelBold, color = c.canvas) }
        }
    }
}

/** 우표(44×54, paper/tag 바탕 · line 점선 테두리 · Caption 글자). */
@Composable
private fun PostageStamp(text: String, modifier: Modifier) {
    val c = KkTheme.colors
    Box(modifier.size(44.dp, 54.dp).background(c.tag).dashedBorder(c.line), contentAlignment = Alignment.Center) {
        Text(text, style = KkTheme.type.caption, color = c.ink, maxLines = 1)
    }
}

/** 봉투 위 미니 폴라로이드(52×66, 흰 틀 · 42 그림). */
@Composable
private fun MiniPolaroid(drawing: ImageBitmap?, modifier: Modifier) {
    Box(modifier.size(52.dp, 66.dp).kkElevation(KkElevation.Low, RectangleShape).background(Palette.PaperWhite).padding(5.dp)) {
        Box(Modifier.size(42.dp).background(Palette.InkBrown100)) {
            drawing?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** 1 점선 테두리(우표 · 광고 지면). */
internal fun Modifier.dashedBorder(color: Color, radius: Dp = 0.dp): Modifier = drawWithContent {
    drawContent()
    val w = 1.dp.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

// ── Service Progress ──────────────────────────────────────────────────────

/** 복무 진행 모양: 곰신은 점선 길([Path]), 군인은 개월 칸([Months]). */
enum class KkServiceProgressStyle { Path, Months }

/**
 * 복무 진행(Figma Service Progress `341:998`): 위에 진행 표시, 아래 "입대 …" · 진행률(accent, 지금 위치 아래) · "전역 …"(Caption).
 *
 * @param progress 0..1 복무 진행률. 범위를 벗어나면 잘라 쓴다.
 * @param start 왼쪽 라벨, 예: "입대 2026.04.20".
 * @param end 오른쪽 라벨, 예: "전역 2028.02.19".
 * @param percent 진행률 라벨, 예: "42.3%".
 * @param style [KkServiceProgressStyle.Path](곰신) · [KkServiceProgressStyle.Months](군인).
 * @param months [KkServiceProgressStyle.Months] 의 전체 개월 수(칸 수). 지금 지나는 달까지 채운다.
 */
@Composable
fun KkServiceProgress(
    progress: Float,
    start: String,
    end: String,
    percent: String,
    modifier: Modifier = Modifier,
    style: KkServiceProgressStyle = KkServiceProgressStyle.Path,
    months: Int = 18,
) {
    val c = KkTheme.colors
    val p = progress.coerceIn(0f, 1f)
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (style) {
            KkServiceProgressStyle.Path -> Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val y = size.height / 2f
                val r = 6.dp.toPx()
                val x = (size.width * p).coerceIn(r, size.width - r)
                drawLine(c.sub, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                drawLine(c.accent, Offset(0f, y), Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(c.accent, radius = r, center = Offset(x, y))
            }
            KkServiceProgressStyle.Months -> {
                val filled = filledMonths(p, months)
                Row(Modifier.fillMaxWidth().padding(top = 2.dp).height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(months) { i ->
                        val cell = Modifier.weight(1f).fillMaxHeight()
                        Box(if (i < filled) cell.background(c.accent) else cell.border(1.dp, c.sub))
                    }
                }
            }
        }
        ProgressLabels(p, start, percent, end)
    }
}

/** 입대 · 진행률 · 전역 라벨. 진행률은 지금 위치 아래 가운데에 두되 양 끝 라벨과 겹치지 않게 민다. */
@Composable
private fun ProgressLabels(progress: Float, start: String, percent: String, end: String) {
    val c = KkTheme.colors
    val t = KkTheme.type.caption
    Layout(
        content = {
            Text(start, style = t, color = c.sub, maxLines = 1)
            Text(percent, style = t, color = c.accent, maxLines = 1)
            Text(end, style = t, color = c.sub, maxLines = 1)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val (s, p, e) = measurables.map { it.measure(loose) }
        val w = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val ideal = (progress * w - p.width / 2f).roundToInt()
        val lo = s.width + gap
        val hi = w - e.width - gap - p.width
        val x = if (hi >= lo) ideal.coerceIn(lo, hi) else ideal.coerceIn(0, max(0, w - p.width))
        layout(w, maxOf(s.height, p.height, e.height)) {
            s.place(0, 0)
            p.place(x, 0)
            e.place(w - e.width, 0)
        }
    }
}

/** 채울 개월 칸 수: 지금 지나고 있는 달까지(Figma 21개월 · 42.3% → 9칸). */
internal fun filledMonths(progress: Float, months: Int): Int {
    if (months <= 0) return 0
    // 소수 오차(9.0000001)로 칸이 하나 더 차지 않게 조금 깎는다.
    return ceil(progress.coerceIn(0f, 1f) * months - 1e-4f).toInt().coerceIn(0, months)
}

// ── Miss You FAB ──────────────────────────────────────────────────────────

/**
 * 보고싶어 FAB(Figma Miss You FAB `341:200`): 홈 오른쪽 아래 알약(love 바탕 · 하트 16 + "보고싶어" Label/Bold, loveOn).
 * 누르는 동안 옅어지고, 보낸 뒤 [sent] 면 체크 + "보냈어요" 로 바뀌었다가 화면이 돌려놓는다. love · loveOn 은 이 버튼과 [KkMissYouButton] 에만 쓴다.
 * 터치 영역은 48 이상(보이는 알약은 40).
 *
 * @param sent true 면 Sent 상태(체크 · "보냈어요").
 * @param onClick 보고싶어 보내기.
 */
@Composable
fun KkMissYouFab(sent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(KkRadius.full)
    Row(
        modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .minimumInteractiveComponentSize()
            .kkElevation(if (pressed) KkElevation.None else KkElevation.Mid, shape)
            .clip(shape)
            .background(if (pressed) c.love.copy(alpha = 0.8f) else c.love)
            .padding(horizontal = KkSpacing.lg, vertical = KkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sent) KkIcon(KkIcons.Check, tint = c.loveOn, size = 16.dp) else KkHeartIcon(c.loveOn, 16.dp)
        Text(if (sent) "보냈어요" else "보고싶어", style = KkTheme.type.labelBold, color = c.loveOn, maxLines = 1)
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────

/**
 * 목록 자리(Figma Skeleton `340:369`): line 색 알약, 높이 12. 너비는 [modifier] 로 준다(예: `Modifier.width(200.dp)` · `fillMaxWidth(0.6f)`).
 * 읽어 줄 내용이 없어 접근성 트리에서 뺀다.
 */
@Composable
fun KkSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.height(12.dp).clip(RoundedCornerShape(KkRadius.full)).background(KkTheme.colors.line).clearAndSetSemantics {})
}

// ── Underline Tab ─────────────────────────────────────────────────────────

/**
 * 밑줄 탭(Figma Underline Tab `340:228`): 필터 · 보기 전환. 칩 대신 밑줄.
 * 켜진 탭은 Label/Bold ink + accent 2 밑줄, 나머지는 Label sub. 탭 사이 16, 터치 높이 48.
 *
 * @param items 탭 이름.
 * @param selected 켜진 탭 번호(0부터).
 * @param onSelect 탭을 눌렀을 때 그 번호.
 */
@Composable
fun KkUnderlineTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg)) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.heightIn(min = 48.dp).width(IntrinsicSize.Max)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                verticalArrangement = Arrangement.spacedBy(KkSpacing.xs, Alignment.CenterVertically),
            ) {
                Text(label, style = if (on) KkTheme.type.labelBold else KkTheme.type.label, color = if (on) c.ink else c.sub, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(KkRadius.full)).background(if (on) c.accent else Color.Transparent))
            }
        }
    }
}
