package com.sadturtleman.flowershoes.core.data

import kotlinx.serialization.Serializable

/**
 * 알림 설정(Figma 설정 / 알림). 받을 종류 · 조용한 시간 · 보고싶어 묶음은 서버가 보낼지 정하므로 서버에도 올리고,
 * 일정 전날 · 기념일은 이 폰이 예약하는 로컬 알림이라 여기서만 쓴다. 미리보기 숨기기는 이 폰(iOS 알림 확장)만 본다.
 */
@Serializable
data class NotificationPrefs(
    val letters: Boolean = true,
    val missYou: Boolean = true,
    val status: Boolean = true,
    val eventEve: Boolean = true,
    val anniversary: Boolean = true,
    /** 서울 기준 분. 시작 == 끝이면 조용한 시간 없음. */
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
    /** 보고싶어를 몇 번마다 한 번 알릴지(1 = 매번). */
    val missYouBundle: Int = 10,
    val hidePreview: Boolean = false,
) {
    val quietEnabled: Boolean get() = quietStart != quietEnd
}

/** 서버(users/{uid}/notifyPrefs/main)에 알림 정책을 올린다. 클라우드가 없으면 없다. */
fun interface NotifyPrefsTransport {
    suspend fun upload(prefs: NotificationPrefs)
}
