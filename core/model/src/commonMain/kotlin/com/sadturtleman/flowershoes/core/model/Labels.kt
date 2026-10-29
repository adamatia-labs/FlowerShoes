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
        EventType.ANNIVERSARY -> "기념일"
        EventType.OTHER -> "기타"
    }

val AppTheme.koreanName: String
    get() = when (this) {
        AppTheme.GOMSIN -> "곰신"
        AppTheme.SOLDIER -> "군인"
    }

/** 테마 카드 설명(온보딩 · 설정 테마). */
val AppTheme.mood: String
    get() = when (this) {
        AppTheme.GOMSIN -> "편지지 · 말린 꽃 · 크라프트"
        AppTheme.SOLDIER -> "군사우편 · 카키 · 스텐실"
    }
