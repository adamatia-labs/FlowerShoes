package com.sadturtleman.flowershoes.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 셸이 여는 화면. 알림 · 위젯 · 푸시의 딥링크(kkotsin://…)를 이것으로 바꾼다. */
sealed interface DeepLink {
    data object Home : DeepLink
    data class Letter(val id: String) : DeepLink
    data class Calendar(val date: String?) : DeepLink
    data object Pairing : DeepLink

    companion object {
        fun parse(uri: String): DeepLink? {
            if (!uri.startsWith("$SCHEME://")) return null
            val parts = uri.removePrefix("$SCHEME://").trimEnd('/').split('/')
            return when (parts.firstOrNull()) {
                "home" -> Home
                "letter" -> parts.getOrNull(1)?.let(::Letter)
                "calendar" -> Calendar(parts.getOrNull(1))
                "pairing" -> Pairing
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
