package com.sadturtleman.flowershoes.common.presentation.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.common.presentation.R

/**
 * Google Fonts 다운로더블 폰트 제공자.
 *
 * 한글 폰트는 글리프가 많아 파일이 크다 (Noto Sans KR 가변폰트 10.4MB, Noto Serif KR 22.7MB).
 * APK 에 넣는 대신 Play 서비스가 기기 단위로 캐시하는 다운로더블 폰트를 쓴다.
 *
 * 인증서 배열은 `res/values/font_certs.xml` 에 있으며, Google Play 서비스 폰트 제공자의
 * 서명 인증서라 값이 정해져 있다. AOSP 공식 샘플(Apache-2.0)에서 그대로 가져왔다.
 */
private val FlowerFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val NotoSansKr = GoogleFont(name = "Noto Sans KR")
private val NotoSerifKr = GoogleFont(name = "Noto Serif KR")

/**
 * 디자인 시스템이 쓰는 두 서체.
 *
 * Figma 원본대로 제목에는 **Noto Serif KR**, 조작 UI·본문에는 **Noto Sans KR** 을 쓴다.
 * 타입 스케일이 실제로 사용하는 웨이트만 선언한다.
 *
 * 다운로드는 비동기이며, 첫 실행 중 폰트가 아직 없으면 Compose 가 플랫폼 기본 서체로 그렸다가
 * 도착하는 대로 교체한다. Play 서비스가 없는 기기(일부 중국 롬 · 커스텀 롬)에서는
 * 플랫폼 기본 서체로 계속 렌더링되며, 한글 표시 자체에는 문제가 없다.
 */
object FlowerFontFamily {

    /** Noto Sans KR — Regular 400 / Medium 500 / Bold 700 */
    val Sans: FontFamily = FontFamily(
        Font(googleFont = NotoSansKr, fontProvider = FlowerFontProvider, weight = FontWeight.Normal),
        Font(googleFont = NotoSansKr, fontProvider = FlowerFontProvider, weight = FontWeight.Medium),
        Font(googleFont = NotoSansKr, fontProvider = FlowerFontProvider, weight = FontWeight.Bold),
    )

    /** Noto Serif KR — Regular 400 / SemiBold 600 / Bold 700 */
    val Serif: FontFamily = FontFamily(
        Font(googleFont = NotoSerifKr, fontProvider = FlowerFontProvider, weight = FontWeight.Normal),
        Font(googleFont = NotoSerifKr, fontProvider = FlowerFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = NotoSerifKr, fontProvider = FlowerFontProvider, weight = FontWeight.Bold),
    )
}

/**
 * Figma 의 lineHeight 는 배수(140%~180%), letterSpacing 은 폰트 크기 대비 퍼센트(-2%~+8%)로 정의되어 있다.
 * Compose 에서는 lineHeight 를 `fontSize * 배수` 의 sp 로, letterSpacing 을 동일 비율의 `em` 으로 옮긴다.
 * (예: Title/M 18sp · -1.5% → 18 * -0.015 = -0.27sp, Figma dev mode 의 `-0.27px` 과 일치)
 */
private val FlowerLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun flowerTextStyle(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeightRatio: Float,
    letterSpacingPercent: Float,
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = (fontSize * lineHeightRatio).sp,
    letterSpacing = (letterSpacingPercent / 100f).em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = FlowerLineHeightStyle,
)

/**
 * FlowerShoes 타입 스케일 (Figma: 01 · Foundations → 02 · TYPOGRAPHY).
 *
 * 이름은 Figma 스타일명을 그대로 따른다. `Title/Serif L` → [titleSerifL].
 */
@Immutable
data class FlowerTypography(
    /** 전시 타이틀. Noto Serif KR Bold · 32 / 140% / -2% */
    val displaySerif: TextStyle,
    /** 큰 제목 (상세 히어로). Noto Serif KR SemiBold · 24 / 145% / -1.5% */
    val titleSerifL: TextStyle,
    /** 제목 (카드 · 리스트). Noto Serif KR SemiBold · 20 / 150% / -1% */
    val titleSerifM: TextStyle,
    /** 화면 제목. Noto Sans KR Bold · 22 / 140% / -2% */
    val titleL: TextStyle,
    /** 섹션 제목 · 앱바 타이틀. Noto Sans KR Bold · 18 / 145% / -1.5% */
    val titleM: TextStyle,
    /** 소제목. Noto Sans KR Bold · 16 / 150% / -1% */
    val headline: TextStyle,
    /** 본문 L. Noto Sans KR Regular · 16 / 165% / -0.5% */
    val bodyL: TextStyle,
    /** 본문 M (기본). Noto Sans KR Regular · 15 / 165% / -0.5% */
    val bodyM: TextStyle,
    /** 본문 S (스펙 값). Noto Sans KR Regular · 14 / 160% / -0.5% */
    val bodyS: TextStyle,
    /** 긴 설명문. Noto Serif KR Regular · 15 / 180% / -0.5% */
    val bodySerif: TextStyle,
    /** 버튼 L · 연관 카드 제목. Noto Sans KR Medium · 15 / 140% / -0.5% */
    val labelL: TextStyle,
    /** 버튼 M · 칩 · 메타 라벨. Noto Sans KR Medium · 13 / 140% / 0 */
    val labelM: TextStyle,
    /** 뱃지 · 탭 라벨. Noto Sans KR Medium · 11 / 140% / 0 */
    val labelS: TextStyle,
    /** 캡션 · 소장처. Noto Sans KR Regular · 12 / 150% / 0 */
    val caption: TextStyle,
    /** 영문 대문자 오버라인. Noto Sans KR Medium · 11 / 140% / +8% */
    val overline: TextStyle,
)

fun flowerTypography(): FlowerTypography = FlowerTypography(
    displaySerif = flowerTextStyle(FlowerFontFamily.Serif, FontWeight.Bold, 32, 1.40f, -2.0f),
    titleSerifL = flowerTextStyle(FlowerFontFamily.Serif, FontWeight.SemiBold, 24, 1.45f, -1.5f),
    titleSerifM = flowerTextStyle(FlowerFontFamily.Serif, FontWeight.SemiBold, 20, 1.50f, -1.0f),
    titleL = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Bold, 22, 1.40f, -2.0f),
    titleM = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Bold, 18, 1.45f, -1.5f),
    headline = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Bold, 16, 1.50f, -1.0f),
    bodyL = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Normal, 16, 1.65f, -0.5f),
    bodyM = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Normal, 15, 1.65f, -0.5f),
    bodyS = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Normal, 14, 1.60f, -0.5f),
    bodySerif = flowerTextStyle(FlowerFontFamily.Serif, FontWeight.Normal, 15, 1.80f, -0.5f),
    labelL = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Medium, 15, 1.40f, -0.5f),
    labelM = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Medium, 13, 1.40f, 0f),
    labelS = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Medium, 11, 1.40f, 0f),
    caption = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Normal, 12, 1.50f, 0f),
    overline = flowerTextStyle(FlowerFontFamily.Sans, FontWeight.Medium, 11, 1.40f, 8.0f),
)
