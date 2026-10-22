package com.sadturtleman.flowershoes.core.model

// 화면 · 알림 · 위젯이 함께 쓰는 한글 이름(Figma 문구와 같다).

val Branch.koreanName: String
    get() = when (this) {
        Branch.ARMY -> "육군"
        Branch.NAVY -> "해군"
        Branch.AIR_FORCE -> "공군"
        Branch.MARINE -> "해병대"
        Branch.SOCIAL_SERVICE -> "사회복무요원"
    }

val EventType.koreanName: String
    get() = when (this) {
        EventType.VACATION -> "휴가"
        EventType.OVERNIGHT -> "외박"
        EventType.VISIT -> "면회"
        EventType.TRAINING -> "훈련"
        EventType.DUTY -> "당직"
    }

val ThemeAccent.koreanName: String
    get() = when (this) {
        ThemeAccent.ROSE -> "로즈"
        ThemeAccent.OLIVE -> "올리브"
        ThemeAccent.NAVY -> "네이비"
        ThemeAccent.MONO -> "모노"
    }

/** 테마 카드 설명(온보딩 · 설정 테마). */
val ThemeAccent.mood: String
    get() = when (this) {
        ThemeAccent.ROSE -> "따뜻하고 다정한"
        ThemeAccent.OLIVE -> "차분한 군복 색"
        ThemeAccent.NAVY -> "깔끔하고 무난한"
        ThemeAccent.MONO -> "가장 조용한"
    }
