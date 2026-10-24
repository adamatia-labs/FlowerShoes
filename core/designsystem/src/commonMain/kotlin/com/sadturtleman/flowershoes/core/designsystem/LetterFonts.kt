package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.designsystem.res.Res
import com.sadturtleman.flowershoes.core.designsystem.res.dongle
import com.sadturtleman.flowershoes.core.designsystem.res.gaegu
import com.sadturtleman.flowershoes.core.designsystem.res.gamja_flower
import com.sadturtleman.flowershoes.core.designsystem.res.gowun_batang
import com.sadturtleman.flowershoes.core.designsystem.res.gowun_dodum
import com.sadturtleman.flowershoes.core.designsystem.res.hi_melody
import com.sadturtleman.flowershoes.core.designsystem.res.jua
import com.sadturtleman.flowershoes.core.designsystem.res.nanum_brush
import com.sadturtleman.flowershoes.core.designsystem.res.nanum_myeongjo
import com.sadturtleman.flowershoes.core.designsystem.res.nanum_pen
import com.sadturtleman.flowershoes.core.designsystem.res.single_day
import com.sadturtleman.flowershoes.core.designsystem.res.yeon_sung
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * 편지 글꼴(편지 쓰기 › 글꼴, 화면 기능명세 2-10): 무료 4 + 꽃신 평생 7, 모두 Google Fonts(OFL).
 * 글꼴 파일은 앱에 묶었다(한글 11,172자 · 영문 · 기호만 남기고 힌팅을 뺐다).
 * id 는 편지에 실려 상대 폰도 같은 글꼴로 보인다. 모르는 id 는 기본. 손글씨는 글자가 작게 보여 크기를 키운다([scale]).
 * [listed] 가 false 인 것은 예전 편지를 그대로 보이려고 남긴 글꼴(고르는 목록에는 없다).
 */
enum class KkLetterFont(
    val id: String,
    val label: String,
    val scale: Float,
    val lifetime: Boolean = false,
    val listed: Boolean = true,
    internal val file: FontResource? = null,
) {
    SANS("sans", "기본", 1f),
    GOWUN_DODUM("gowun_dodum", "고운돋움", 1f, file = Res.font.gowun_dodum),
    GOWUN_BATANG("gowun_batang", "고운바탕", 1f, file = Res.font.gowun_batang),
    GAEGU("gaegu", "개구", 1.2f, file = Res.font.gaegu),
    HI_MELODY("hi_melody", "하이멜로디", 1.3f, lifetime = true, file = Res.font.hi_melody),
    GAMJA("gamja_flower", "감자꽃", 1.25f, lifetime = true, file = Res.font.gamja_flower),
    SINGLE_DAY("single_day", "싱글데이", 1.1f, lifetime = true, file = Res.font.single_day),
    YEON_SUNG("yeon_sung", "연성", 1.1f, lifetime = true, file = Res.font.yeon_sung),
    JUA("jua", "주아", 1f, lifetime = true, file = Res.font.jua),
    DONGLE("dongle", "동글", 1.6f, lifetime = true, file = Res.font.dongle),
    NANUM_BRUSH("nanum_brush", "나눔손글씨 붓", 1.35f, lifetime = true, file = Res.font.nanum_brush),

    // 예전 편지 글꼴(목록에서 뺐다).
    PEN("pen", "손글씨", 1.3f, listed = false, file = Res.font.nanum_pen),
    MYEONGJO("myeongjo", "명조", 1f, listed = false, file = Res.font.nanum_myeongjo),
    ;

    companion object {
        /** "gaegu" · "gaegu@L"(글자 크기를 함께 싣는다). */
        fun of(id: String?): KkLetterFont = entries.firstOrNull { it.id == id?.substringBefore('@') } ?: SANS

        val listed: List<KkLetterFont> get() = entries.filter { it.listed }
    }
}

/** 편지 글자 크기(편지 쓰기 › 글꼴 시트, 무료). 편지의 글꼴 id 뒤에 "@S" · "@L" 로 싣는다(보통은 붙이지 않는다). */
enum class KkLetterSize(val code: String, val label: String, val factor: Float) {
    S("S", "작게", 0.88f),
    M("M", "보통", 1f),
    L("L", "크게", 1.18f),
    ;

    companion object {
        fun of(fontId: String?): KkLetterSize = entries.firstOrNull { it.code == fontId?.substringAfter('@', "M") } ?: M

        /** 글꼴 + 크기 → 편지에 싣는 id. 기본 글꼴 · 보통 크기는 null(필드를 비운다). */
        fun encode(font: KkLetterFont, size: KkLetterSize): String? = when {
            font == KkLetterFont.SANS && size == M -> null
            size == M -> font.id
            else -> "${font.id}@${size.code}"
        }
    }
}

/** 글꼴의 FontFamily. 기본은 테마 본문 글꼴(Noto Sans KR). */
@Composable
fun KkLetterFont.family(): FontFamily {
    val res = file ?: return KkTheme.type.bodyL.fontFamily ?: FontFamily.Default
    val f = Font(res)
    return remember(f) { FontFamily(f) }
}

/** 편지 본문 글자 크기(기본 [base]sp 에 글꼴 배율 · 크기 단계). */
fun KkLetterFont.size(base: Float, size: KkLetterSize = KkLetterSize.M): TextUnit = (base * scale * size.factor).sp
