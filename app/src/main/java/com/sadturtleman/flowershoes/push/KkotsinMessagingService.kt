package com.sadturtleman.flowershoes.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sadturtleman.flowershoes.core.data.sync.SyncScheduler
import com.sadturtleman.flowershoes.core.firebase.PushTokenRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

/**
 * 푸시는 초인종이다 — 내용은 우편함(Firestore)에 있다(설계 1장).
 * 받으면 우편함을 읽어 반영한다. 화면 알림은 서버가 notification 으로 보내면 시스템이 띄우고,
 * data 만 온 경우(조용한 시간 · 재전송 · 읽음 등)는 위젯 · DB 만 갱신한다.
 */
class KkotsinMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val scheduler = GlobalContext.getOrNull()?.getOrNull<SyncScheduler>() ?: return
        // 이 콜백은 백그라운드 스레드에서 돈다. 몇 초 안에 끝내야 하므로 시간 제한을 둔다.
        runBlocking { withTimeoutOrNull(SYNC_TIMEOUT_MS) { scheduler.runOnce() } }
    }

    override fun onNewToken(token: String) {
        runBlocking { withTimeoutOrNull(SYNC_TIMEOUT_MS) { runCatching { PushTokenRegistry().register(token) } } }
    }

    private companion object {
        const val SYNC_TIMEOUT_MS = 8_000L
    }
}
