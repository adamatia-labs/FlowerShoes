package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.designsystem.res.Res
import com.sadturtleman.flowershoes.core.designsystem.res.nanum_myeongjo
import com.sadturtleman.flowershoes.core.designsystem.res.nanum_pen
import org.jetbrains.compose.resources.Font

/**
 * 편지 글꼴(편지 쓰기 › 글꼴). id 는 편지에 실려 상대 폰도 같은 글꼴로 보인다. 모르는 id 는 기본.
 * 손글씨는 글자가 작게 보여 같은 줄 높이 안에서 크기를 키운다([scale]).
 */
enum class KkLetterFont(val id: String, val label: String, val scale: Float) {
    SANS("sans", "기본", 1f),
    PEN("pen", "손글씨", 1.3f),
    MYEONGJO("myeongjo", "명조", 1f),
    ;

    companion object {
        fun of(id: String?): KkLetterFont = entries.firstOrNull { it.id == id } ?: SANS
    }
}

/** 글꼴의 FontFamily. 기본은 테마 본문 글꼴(Noto Sans KR). */
@Composable
fun KkLetterFont.family(): FontFamily = when (this) {
    KkLetterFont.SANS -> KkTheme.type.bodyL.fontFamily ?: FontFamily.Default
    KkLetterFont.PEN -> Font(Res.font.nanum_pen).let { f -> remember(f) { FontFamily(f) } }
    KkLetterFont.MYEONGJO -> Font(Res.font.nanum_myeongjo).let { f -> remember(f) { FontFamily(f) } }
}

/** 편지 본문 글자 크기(기본 [base]sp 에 글꼴 배율). */
fun KkLetterFont.size(base: Float): TextUnit = (base * scale).sp
