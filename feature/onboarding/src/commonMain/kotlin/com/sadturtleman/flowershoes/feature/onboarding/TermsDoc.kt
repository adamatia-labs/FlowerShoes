package com.sadturtleman.flowershoes.feature.onboarding

/**
 * 약관 보기(온보딩 0-2). 개인정보 문서는 Figma 문구 그대로, 나머지는 같은 형식으로 쓴 요약이다.
 * 정식 약관 전문은 출시 전에 법률 검토를 거쳐 웹 페이지로 두고 여기에는 요약만 둔다.
 */
enum class TermsDoc(val title: String, val sections: List<Pair<String, List<String>>>) {
    SERVICE(
        "서비스 이용약관",
        listOf(
            "서비스" to listOf("꽃신은 연인 두 사람이 D-day · 편지 · 그림 · 일정을 함께 쓰는 앱이에요"),
            "내 콘텐츠" to listOf("편지 · 그림 · 일정 · 사진은 쓴 사람의 것이에요", "기기에서 암호화돼 꽃신은 내용을 볼 수 없어요"),
            "하지 말아 주세요" to listOf("다른 사람의 계정 · 기기로 접속하기", "서비스를 방해하거나 법을 어기는 내용 보내기"),
            "탈퇴 · 해제" to listOf("언제든 설정에서 연결을 해제하거나 탈퇴할 수 있어요", "탈퇴하면 서버의 계정 · 백업이 바로 삭제돼요"),
        ),
    ),
    PRIVACY(
        "개인정보 수집 · 이용 동의",
        listOf(
            "수집 항목" to listOf("소셜 로그인 식별자(카카오 · Apple · Google)", "역할(곰신 · 군인), 알림 토큰, 기기 종류"),
            "수집하지 않는 것" to listOf("편지 · 그림 · 일정 · 사진 내용 — 모두 기기에서 암호화돼 꽃신이 볼 수 없어요"),
            "이용 목적" to listOf("로그인, 연인과의 연결, 알림 발송, 암호화 백업 보관"),
            "보유 기간" to listOf("탈퇴 시 바로 삭제", "암호화 백업: 마지막 로그인 후 1년"),
        ),
    ),
    MARKETING(
        "이벤트 · 혜택 알림 받기",
        listOf(
            "받는 내용" to listOf("새 기능 · 이벤트 · 혜택 소식"),
            "보내는 방법" to listOf("앱 푸시 알림"),
            "바꾸기" to listOf("설정 › 알림에서 언제든 끌 수 있어요", "동의하지 않아도 꽃신을 쓰는 데 지장은 없어요"),
        ),
    ),
}
