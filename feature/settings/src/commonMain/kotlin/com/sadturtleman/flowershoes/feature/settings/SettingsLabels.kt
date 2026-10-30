package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

// 설정 줄의 값 문구와 알림 큰 스위치. 화면과 떼어 두어 테스트한다.

/** 설정 › 알림 "편지 · 보고싶어 · 일정 알림": 받을 종류 중 하나라도 켜져 있으면 켜짐. 3개월 기록 알림은 따로다. */
internal val NotificationPrefs.notifyMasterOn: Boolean
    get() = letters || missYou || status || eventEve || anniversary

/** 받을 종류를 한꺼번에 켜고 끈다. 3개월 기록 알림 · 조용한 시간 · 묶음은 건드리지 않는다. */
internal fun NotificationPrefs.withNotifyMaster(on: Boolean): NotificationPrefs =
    copy(letters = on, missYou = on, status = on, eventEve = on, anniversary = on)

/** 프로필 › 함께한 날: 커플 기념일이 1일째. */
internal fun daysTogetherLabel(days: Int?): String = days?.let { "${it}일째" } ?: "정하기"

/** 우리 › 군 정보: "공군 · 일병(예정)". 계급은 계산값이라 늘 "예정"(#32). */
internal fun militaryLabel(service: Service?, timeline: ServiceTimeline?, today: LocalDate): String {
    if (service == null || timeline == null) return "입력 전"
    val rank = timeline.rankAt(today)?.rank ?: return service.branch.koreanName
    return "${service.branch.koreanName} · ${rank.expectedName}"
}

/** 군 정보의 진급일 · 전역일 뒤: 지난 날은 "지남", 오늘은 "D-day", 남은 날은 "D-N". */
internal fun ddayLabel(today: LocalDate, date: LocalDate): String {
    val left = today.daysUntil(date)
    return when {
        left < 0 -> "지남"
        left == 0 -> "D-day"
        else -> "D-$left"
    }
}

/** 꾸미기 › 꽃신 평생. 내가 산 것이 연인과 나눠 쓰는 것보다 앞선다. */
internal fun lifetimeLabel(owned: Boolean, shared: Boolean, price: String): String = when {
    owned -> "이용 중"
    shared -> "함께 이용 중"
    else -> price
}

/** 이름 입력: 앞뒤 공백을 빼고 10자까지. */
internal fun nicknameInput(raw: String): String = raw.trim().take(NICKNAME_MAX)

internal const val NICKNAME_MAX = 10
