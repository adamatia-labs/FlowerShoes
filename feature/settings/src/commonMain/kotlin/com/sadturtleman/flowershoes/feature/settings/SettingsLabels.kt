package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.data.LIFETIME_PAPERS
import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.designsystem.KkLetterFont
import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.military.expectedName
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus

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

/** 기념일 › 자동으로 챙겨요 한 줄: "100일" · "1주년"과 그날. */
internal data class Milestone(val label: String, val date: LocalDate)

/**
 * 기념일 › 자동으로 챙겨요(Figma 8 · 기념일 · 목록): 사귄 날을 1일로 센 100일 · 500일 · 1000일 단위와 해마다 주년 가운데
 * 지난 날 둘과 다가올 날 둘(오늘은 다가올 날).
 */
internal fun anniversaryMilestones(anniversary: LocalDate, today: LocalDate): List<Milestone> {
    val passed = anniversary.daysUntil(today) + 1
    val days = (listOf(100, 500) + (1000..maxOf(1000, passed + 2000) step 1000)).map { n ->
        Milestone("${n}일", anniversary.plus(DatePeriod(days = n - 1)))
    }
    val years = (1..(today.year - anniversary.year + 3)).map { n -> Milestone("${n}주년", anniversary.plus(DatePeriod(years = n))) }
    val (past, upcoming) = (days + years).sortedBy { it.date }.partition { it.date < today }
    return past.takeLast(2) + upcoming.take(2)
}

/** 우리 › 기념일: 사귄 날 + 직접 추가한 날 개수. 하나도 없으면 "정하기". */
internal fun anniversaryCountLabel(coupleSet: Boolean, custom: Int): String {
    val n = custom + if (coupleSet) 1 else 0
    return if (n == 0) "정하기" else "${n}개"
}

/** 기념일 › 직접 추가한 날 값: 매년이면 "매년 1월 7일", 한 번이면 "2025.08.30". */
internal fun customAnniversaryLabel(date: LocalDate, repeats: Boolean): String =
    if (repeats) "매년 ${date.month.number}월 ${date.day}일" else date.dotted()

/** 군종 · 입대일로 계산한 전역일(직접 고친 값 없이). */
internal fun computedDischarge(branch: Branch, enlist: LocalDate): LocalDate =
    ServiceCalculator().timeline(Service("", branch, enlist, ServiceRulesTable.BUNDLED.version)).dischargeDate

/** 군 정보 › 수정 › 저장: 고른 전역일이 계산한 날과 같으면(또는 고르지 않았으면) 직접 고친 값이 없다. */
internal fun dischargeOverrideFor(branch: Branch, enlist: LocalDate, picked: LocalDate?): LocalDate? =
    picked?.takeIf { it != computedDischarge(branch, enlist) }

/** 꽃신 평생 혜택(planning §6). 글꼴 · 편지지 수는 실제 고르는 목록의 평생 항목 수다. */
internal fun lifetimeBenefits(partner: String): List<String> = listOf(
    "광고 제거",
    "손글씨 글꼴 ${KkLetterFont.listed.count { it.lifetime }}종",
    "편지지 ${LIFETIME_PAPERS}종",
    "펜 · 색 · 위젯 꾸미기",
    "연결된 ${partner}도 함께",
)

internal fun LocalDate.dotted() = "$year.${month.number.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
