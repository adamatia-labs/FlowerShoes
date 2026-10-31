package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 글줄 무늬: 밑줄 · 원고지 칸 · 체크 모눈 · 타자 점선. */
enum class PaperRule { LINE, CELLS, GRID, DASH }

/** 종이 전체 무늬: 없음 · 수채 번짐(오른쪽 위) · 벚꽃 · 별 · 바랜 가장자리. */
enum class PaperDeco { NONE, WASH, BLOSSOM, STARS, AGED }

/**
 * 편지지 색(design.md "편지지는 콘텐츠다"): 보낸 사람이 고른 편지지 그대로 — 읽는 사람의 테마 · 다크 모드 토큰을 쓰지 않는다.
 *
 * @param background 종이 바탕.
 * @param edge 종이 테두리.
 * @param rule 괘선 · 칸.
 * @param ink 글자.
 * @param mark 종이에 인쇄된 표시("군사우편"). null 이면 없다.
 * @param ruleStyle 글줄 무늬([drawPaperRow]).
 * @param deco 종이 전체 무늬([paperDeco])와 그 색([decoColor]).
 */
data class PaperLook(
    val background: Color,
    val edge: Color,
    val rule: Color,
    val ink: Color,
    val mark: Color? = null,
    val ruleStyle: PaperRule = PaperRule.LINE,
    val deco: PaperDeco = PaperDeco.NONE,
    val decoColor: Color = rule,
)

private val INK = Color(0xFF3A2E28)

/**
 * 이 편지지의 고정 색 · 무늬. 무료 다섯은 Figma Letter Paper(`341:830`) 그대로.
 * 평생 다섯은 Figma 에 이름 · 잠긴 견본만 있어 이름에 맞춰 같은 결로 만들었다(벚꽃 엽서 · 밤하늘 · 체크 노트 · 빈티지 타자 · 라벤더 수채).
 */
fun Paper.look(): PaperLook = when (this) {
    Paper.ROSE -> PaperLook(Color(0xFFFFFDF8), Color(0xFFE4D8C8), Color(0xFFEBCFD3), INK)
    Paper.MAIL -> PaperLook(Color(0xFFF3EEDB), Color(0xFFD8CFB0), Color(0xFFCFC6A2), INK, mark = Palette.Stamp600)
    Paper.MANUSCRIPT -> PaperLook(Color(0xFFFFFFFF), Color(0xFFE6DADA), Color(0xFFE7C9C9), INK, ruleStyle = PaperRule.CELLS)
    Paper.KRAFT -> PaperLook(Color(0xFFD9C4A3), Color(0xFFC7B08A), Color(0xFFBFA884), INK)
    Paper.SKY -> PaperLook(Color(0xFFEEF4F7), Color(0xFFD5E2EA), Color(0xFFC9DBE5), INK, deco = PaperDeco.WASH, decoColor = Color(0x80C9DBE5))
    Paper.BLOSSOM -> PaperLook(Color(0xFFFFF4F5), Color(0xFFF0D2D8), Color(0xFFF0CDD4), INK, deco = PaperDeco.BLOSSOM, decoColor = Color(0xFFF4B9C6))
    Paper.NIGHT -> PaperLook(Color(0xFF1B2235), Color(0xFF2C3654), Color(0x2EFFFFFF), Color(0xFFF1EADB), deco = PaperDeco.STARS, decoColor = Color(0xFFF6E7B8))
    Paper.CHECK -> PaperLook(Color(0xFFFFFFFF), Color(0xFFDDE5F0), Color(0xFFD6E2F1), INK, ruleStyle = PaperRule.GRID)
    Paper.TYPEWRITER -> PaperLook(Color(0xFFF4ECDC), Color(0xFFD9CBB0), Color(0xFFCDBB9A), Color(0xFF2B2622), ruleStyle = PaperRule.DASH, deco = PaperDeco.AGED, decoColor = Color(0x339A7B4F))
    Paper.LAVENDER -> PaperLook(Color(0xFFF4F1F8), Color(0xFFE1D8EC), Color(0xFFDCD0EA), INK, deco = PaperDeco.WASH, decoColor = Color(0x80CDBFE3))
}

/**
 * 글줄 하나([top]..[bottom])의 무늬를 폭 끝까지 긋는다. 글자 크기가 바뀌어도 글줄에 맞는다.
 * 원고지는 Figma 칸(24×28) 비율로 칸을 나누고, 체크 노트는 줄 높이만큼의 정사각 모눈이다.
 */
fun DrawScope.drawPaperRow(look: PaperLook, top: Float, bottom: Float) {
    val stroke = 1.dp.toPx()
    val h = bottom - top
    when (look.ruleStyle) {
        PaperRule.LINE -> drawLine(look.rule, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = stroke)
        PaperRule.DASH -> drawLine(
            look.rule, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
        PaperRule.CELLS, PaperRule.GRID -> {
            if (h <= 0f) return
            val cell = if (look.ruleStyle == PaperRule.CELLS) h * 24f / 28f else h
            val count = (size.width / cell).toInt().coerceAtLeast(1)
            // 남는 폭은 양쪽에 나눠 칸을 가운데에 둔다.
            val left = (size.width - cell * count) / 2f
            val right = left + cell * count
            drawLine(look.rule, Offset(left, top), Offset(right, top), strokeWidth = stroke)
            drawLine(look.rule, Offset(left, bottom), Offset(right, bottom), strokeWidth = stroke)
            for (i in 0..count) drawLine(look.rule, Offset(left + cell * i, top), Offset(left + cell * i, bottom), strokeWidth = stroke)
        }
    }
}

/** 종이 전체 무늬([PaperLook.deco])를 바탕 위 · 글 아래에 그린다. 그림 파일 없이 코드로 그린다. */
fun Modifier.paperDeco(look: PaperLook): Modifier = if (look.deco == PaperDeco.NONE) this else drawBehind {
    clipRect { deco(look) }
}

private fun DrawScope.deco(look: PaperLook) {
    when (look.deco) {
        PaperDeco.NONE -> Unit
        // Figma 하늘 수채: 폭 324 종이의 (260, 30)에 220×140 타원, 15 흐림 → 가장자리가 사라지는 타원 번짐. 견본에서도 같도록 폭에 비례한다.
        PaperDeco.WASH -> {
            val center = Offset(size.width * 260f / 324f, size.width * 30f / 324f)
            val r = size.width * 140f / 324f
            scale(1f, 70f / 110f, pivot = center) {
                drawCircle(Brush.radialGradient(0f to look.decoColor, 0.55f to look.decoColor, 1f to look.decoColor.copy(alpha = 0f), center = center, radius = r), r, center)
            }
        }
        PaperDeco.BLOSSOM -> {
            blossom(look.decoColor, Offset(size.width - 34.dp.toPx(), 26.dp.toPx()), 13.dp.toPx())
            blossom(look.decoColor.copy(alpha = 0.7f), Offset(size.width - 70.dp.toPx(), 18.dp.toPx()), 8.dp.toPx())
            blossom(look.decoColor.copy(alpha = 0.7f), Offset(22.dp.toPx(), size.height - 26.dp.toPx()), 10.dp.toPx())
        }
        PaperDeco.STARS -> STARS.forEach { (x, y, r) ->
            drawCircle(look.decoColor.copy(alpha = 0.35f + r * 0.25f), (0.8f + r).dp.toPx(), Offset(size.width * x, size.height * y))
        }
        // 바랜 종이: 가운데는 그대로, 가장자리로 갈수록 누렇게.
        PaperDeco.AGED -> drawRect(
            Brush.radialGradient(0.55f to Color.Transparent, 1f to look.decoColor, center = center, radius = maxOf(size.width, size.height) * 0.75f),
        )
    }
}

/** 다섯 잎 벚꽃: 잎 다섯 + 가운데. */
private fun DrawScope.blossom(color: Color, center: Offset, size: Float) {
    for (i in 0 until 5) {
        val a = -PI / 2 + i * 2 * PI / 5
        drawCircle(color, size * 0.42f, Offset(center.x + (cos(a) * size * 0.5f).toFloat(), center.y + (sin(a) * size * 0.5f).toFloat()))
    }
    drawCircle(Color.White.copy(alpha = 0.8f), size * 0.18f, center)
}

/** 밤하늘 별 자리(폭 · 높이 비율, 크기 0..1). 매번 같은 자리에 찍는다. */
private val STARS = listOf(
    Triple(0.08f, 0.04f, 0.6f), Triple(0.22f, 0.11f, 0.2f), Triple(0.41f, 0.03f, 0.9f), Triple(0.57f, 0.09f, 0.3f),
    Triple(0.66f, 0.21f, 0.5f), Triple(0.83f, 0.14f, 0.1f), Triple(0.93f, 0.33f, 0.7f), Triple(0.12f, 0.36f, 0.3f),
    Triple(0.31f, 0.52f, 0.1f), Triple(0.74f, 0.48f, 0.4f), Triple(0.05f, 0.67f, 0.5f), Triple(0.48f, 0.71f, 0.2f),
    Triple(0.89f, 0.64f, 0.8f), Triple(0.24f, 0.86f, 0.4f), Triple(0.62f, 0.92f, 0.6f), Triple(0.95f, 0.88f, 0.2f),
)
