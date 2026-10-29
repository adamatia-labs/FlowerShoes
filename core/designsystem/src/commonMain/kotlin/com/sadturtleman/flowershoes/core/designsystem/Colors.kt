package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.sadturtleman.flowershoes.core.model.AppTheme

/**
 * Figma "Primitives" 컬렉션 중 테마 v2 가 가리키는 값. 화면 코드는 이것을 직접 쓰지 않고 [KkColors] 를 쓴다.
 */
object Palette {
    val Cream50 = Color(0xFFFFFDF8)
    val Cream100 = Color(0xFFFBF7F1)
    val Cream200 = Color(0xFFF7F1E8)
    val Cream300 = Color(0xFFE4D8C8)
    val Kraft300 = Color(0xFFD8C1A0)
    val Kraft700 = Color(0xFF4A3D30)
    val Tape100 = Color(0xD9EFE3B8)
    val Tape600 = Color(0xD95E5A3E)
    val Tape700 = Color(0xD96B6142)
    val InkBrown100 = Color(0xFFEFE6DA)
    val InkBrown300 = Color(0xFFB8AB9E)
    val InkBrown500 = Color(0xFF74665C)
    val InkBrown900 = Color(0xFF3B302B)
    val Mocha800 = Color(0xFF3A322C)
    val Mocha900 = Color(0xFF2A2420)
    val Mocha950 = Color(0xFF1F1A17)
    val Rosewood400 = Color(0xFFE07A8F)
    val Rosewood600 = Color(0xFFB9475E)
    val Sage300 = Color(0xFFA3B07F)
    val Sage500 = Color(0xFF7C8B5A)

    val Field50 = Color(0xFFF8F6EE)
    val Field100 = Color(0xFFF3F2EA)
    val Field200 = Color(0xFFECEADF)
    val Field300 = Color(0xFFCFCDBE)
    val Manila300 = Color(0xFFE3D3A8)
    val Manila700 = Color(0xFF4A4430)
    val InkOlive100 = Color(0xFFE6E8DC)
    val InkOlive300 = Color(0xFFB8BCAA)
    val InkOlive500 = Color(0xFF62665A)
    val InkOlive900 = Color(0xFF262B20)
    val Night800 = Color(0xFF343A2C)
    val Night900 = Color(0xFF23271E)
    val Night950 = Color(0xFF191C15)
    val Moss300 = Color(0xFF9AAE6B)
    val Moss600 = Color(0xFF55623A)
    val Stamp400 = Color(0xFFD0705E)
    val Stamp500 = Color(0xFFB5503D)
    val Stamp600 = Color(0xFFA8402F)

    val PaperWhite = Color(0xFFFFFFFF)

    val EventVacationL = Color(0xFFC2703D)
    val EventVacationD = Color(0xFFE39A6A)
    val EventOvernightL = Color(0xFF6B5BA8)
    val EventOvernightD = Color(0xFFA397D8)
    val EventVisitL = Color(0xFF2F7F86)
    val EventVisitD = Color(0xFF6CC0C6)
    val EventTrainingL = Color(0xFF55623A)
    val EventTrainingD = Color(0xFF9AAE6B)
    val EventDutyL = Color(0xFF6E7262)
    val EventDutyD = Color(0xFFA9AD9B)
    val EventAnnivL = Color(0xFFB9475E)
    val EventAnnivD = Color(0xFFE07A8F)
    val DangerL = Color(0xFFB3412E)
    val DangerD = Color(0xFFE8826E)
    val SuccessL = Color(0xFF4F7A3A)
    val SuccessD = Color(0xFF93C27A)
}

/**
 * 테마 v2 의미 색. Figma 변수(paper/canvas → [canvas])와 1:1 이고 docs/product/design.md "테마 v2 토큰" 표의 이름을 따른다.
 * 모드는 [AppTheme](곰신 · 군인) × 라이트 · 다크 4개.
 */
@Immutable
data class KkColors(
    /** paper/canvas — 화면 바탕. */
    val canvas: Color,
    /** paper/surface — 시트 · 카드 · 입력란. */
    val surface: Color,
    /** paper/bar — 탭 바 · 위 막대. */
    val bar: Color,
    /** paper/tag — 종이 태그 바탕(위에는 ink 글자만). */
    val tag: Color,
    /** paper/tape — 테이프. */
    val tape: Color,
    /** ink/primary — 본문 글자. */
    val ink: Color,
    /** ink/secondary — 보조 글자. */
    val sub: Color,
    /** line/default — 구분선 · 테두리. */
    val line: Color,
    /** accent/default — 주 동작. */
    val accent: Color,
    /** love/bg · love/on — 보고싶어 버튼에만. */
    val love: Color,
    val loveOn: Color,
    /** stamp/ink — 소인 · 우표. */
    val stamp: Color,
    /** deco/plant — 장식 식물. */
    val plant: Color,
    /** status/danger · status/success — 오류 · 완료. */
    val danger: Color,
    val success: Color,
    /** event/… — 일정 색(휴가 · 외박 · 면회 · 훈련 · 당직 · 기념일). */
    val eventVacation: Color,
    val eventOvernight: Color,
    val eventVisit: Color,
    val eventTraining: Color,
    val eventDuty: Color,
    val eventAnniversary: Color,
    val isDark: Boolean,
)

fun kkColors(theme: AppTheme, dark: Boolean): KkColors = with(Palette) {
    val gomsin = theme == AppTheme.GOMSIN
    fun pick(gomsinLight: Color, gomsinDark: Color, soldierLight: Color, soldierDark: Color) =
        if (gomsin) (if (dark) gomsinDark else gomsinLight) else (if (dark) soldierDark else soldierLight)
    fun mode(light: Color, darkColor: Color) = if (dark) darkColor else light
    KkColors(
        canvas = pick(Cream200, Mocha950, Field200, Night950),
        surface = pick(Cream50, Mocha900, Field50, Night900),
        bar = pick(Cream100, Mocha900, Field100, Night900),
        tag = pick(Kraft300, Kraft700, Manila300, Manila700),
        tape = pick(Tape100, Tape700, Tape100, Tape600),
        ink = pick(InkBrown900, InkBrown100, InkOlive900, InkOlive100),
        sub = pick(InkBrown500, InkBrown300, InkOlive500, InkOlive300),
        line = pick(Cream300, Mocha800, Field300, Night800),
        accent = pick(Rosewood600, Rosewood400, Moss600, Moss300),
        love = pick(Rosewood600, Rosewood600, Stamp600, Stamp500),
        loveOn = PaperWhite,
        stamp = pick(Rosewood600, Rosewood400, Stamp600, Stamp400),
        plant = pick(Sage500, Sage300, Moss600, Moss300),
        danger = mode(DangerL, DangerD),
        success = mode(SuccessL, SuccessD),
        eventVacation = mode(EventVacationL, EventVacationD),
        eventOvernight = mode(EventOvernightL, EventOvernightD),
        eventVisit = mode(EventVisitL, EventVisitD),
        eventTraining = mode(EventTrainingL, EventTrainingD),
        eventDuty = mode(EventDutyL, EventDutyD),
        eventAnniversary = mode(EventAnnivL, EventAnnivD),
        isDark = dark,
    )
}
