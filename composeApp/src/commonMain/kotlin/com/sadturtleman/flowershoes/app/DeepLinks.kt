package com.sadturtleman.flowershoes.app

import com.sadturtleman.flowershoes.core.data.sync.InviteLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 셸이 여는 화면. 알림 · 위젯 · 푸시의 딥링크(kkotsin://…, 앱 안에서만 쓴다)와
 * 초대 링크(카카오 앱 실행 `kakao{앱키}://kakaolink?invite=` · 웹 `https://{host}/i/{token}`)를 이것으로 바꾼다.
 */
sealed interface DeepLink {
    data object Home : DeepLink
    data object Inbox : DeepLink
    data class Letter(val id: String) : DeepLink
    data class Calendar(val date: String?) : DeepLink
    /** 그날의 한 줄 쓰기(일정이 끝난 다음 날 알림). */
    data class DayLine(val eventId: String) : DeepLink
    data object Pairing : DeepLink
    /** 설정 › 기기 바꾸기 · 복구(3개월마다 기록 받아 두기 알림). */
    data object DeviceRecovery : DeepLink

    /** 초대 링크를 열었다 → 받는 쪽 수락 흐름. */
    data class Invite(val token: String) : DeepLink

    companion object {
        fun parse(uri: String): DeepLink? {
            InviteLinks.tokenFrom(uri)?.let { return Invite(it) }
            if (!uri.startsWith("$SCHEME://")) return null
            val parts = uri.removePrefix("$SCHEME://").trimEnd('/').split('/')
            return when (parts.firstOrNull()) {
                "home" -> Home
                "inbox" -> Inbox
                "letter" -> parts.getOrNull(1)?.let(::Letter)
                "calendar" -> Calendar(parts.getOrNull(1))
                "dayline" -> parts.getOrNull(1)?.let(::DayLine)
                "pairing" -> Pairing
                "settings" -> if (parts.getOrNull(1) == "device") DeviceRecovery else null
                else -> null
            }
        }

        const val SCHEME = "kkotsin"
    }
}

/**
 * 플랫폼 진입점(Activity · iOS 앱)이 딥링크를 넣고, 셸이 꺼내 처리한다.
 * 화면이 아직 없을 때(앱이 막 켜질 때) 들어온 것도 놓치지 않게 마지막 하나를 들고 있다.
 */
object DeepLinks {
    private val pending = MutableStateFlow<DeepLink?>(null)
    val next: StateFlow<DeepLink?> = pending.asStateFlow()

    fun open(uri: String) {
        DeepLink.parse(uri)?.let { pending.value = it }
    }

    fun consumed() {
        pending.value = null
    }
}
