package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.ui.graphics.Color
import com.sadturtleman.flowershoes.core.designsystem.Palette

/**
 * 편지지 색(design.md "편지지는 콘텐츠다"): 보낸 사람이 고른 편지지 그대로 — 읽는 사람의 테마 · 다크 모드 토큰을 쓰지 않는다.
 *
 * @param background 종이 바탕.
 * @param edge 종이 테두리.
 * @param rule 괘선.
 * @param ink 글자.
 * @param mark 종이에 인쇄된 표시(군사우편 편지지의 "군사우편").
 */
data class PaperLook(val background: Color, val edge: Color, val rule: Color, val ink: Color, val mark: Color = ink)

/** 이 편지지의 고정 색. 기본 다섯은 곰신 · 라이트 종이 색, 평생 다섯은 각자의 종이 색(Figma Letter Paper). */
fun Paper.look(): PaperLook = when (this) {
    Paper.PLAIN -> PaperLook(Palette.Cream50, Palette.Cream300, Palette.Cream300, Palette.InkBrown900)
    Paper.BLOSSOM -> PaperLook(Palette.Cream100, Palette.Cream300, Palette.Rosewood600.copy(alpha = 0.35f), Palette.InkBrown900)
    Paper.OLIVE -> PaperLook(Palette.Field100, Palette.Field300, Palette.Sage500.copy(alpha = 0.3f), Palette.InkOlive900)
    Paper.GRID -> PaperLook(Palette.Cream200, Palette.Cream300, Palette.InkBrown300.copy(alpha = 0.5f), Palette.InkBrown900)
    Paper.NIGHT -> PaperLook(Palette.Night900, Palette.Night800, Palette.Cream200.copy(alpha = 0.18f), Palette.Cream200)
    Paper.MANUSCRIPT -> PaperLook(Color(0xFFFFF8EC), Color(0xFFEBDCC4), Color(0x66D9776B), Color(0xFF2E2A26))
    Paper.KRAFT -> PaperLook(Color(0xFFE9DCC4), Color(0xFFD6C6A8), Color(0xFFCFC3AE), Color(0xFF2E2A26))
    // Figma letter/군사우편(341:319).
    Paper.MAIL -> PaperLook(Color(0xFFF3EEDB), Color(0xFFD8CFB0), Color(0xFFCFC6A2), Color(0xFF3A2E28), mark = Palette.Stamp600)
    Paper.NOTE -> PaperLook(Color(0xFFFFFFFF), Color(0xFFDDE5F0), Color(0xFFA9C4E8), Color(0xFF2E2A26))
    Paper.SKY -> PaperLook(Color(0xFFDDEFFA), Color(0xFFC4DDEE), Color(0x99FFFFFF), Color(0xFF2E2A26))
}
