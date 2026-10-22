package com.sadturtleman.flowershoes.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.sadturtleman.flowershoes.MainActivity
import com.sadturtleman.flowershoes.R
import com.sadturtleman.flowershoes.core.notification.LocalNotification
import com.sadturtleman.flowershoes.core.notification.NotificationScheduler

/**
 * Android 로컬 알림 예약. 정확한 시각 알람 권한 없이 setAndAllowWhileIdle 로 맞춘다
 * (몇 분 늦을 수 있다 — 예약 편지 공개 · 전날 알림에는 충분하다).
 * 예약한 id 를 기억해 두었다가 계획에서 빠진 것은 취소한다.
 */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("kkotsin_alarms", Context.MODE_PRIVATE)

    init {
        ensureChannel(context)
    }

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        val keep = notifications.map { it.id }.toSet()
        prefs.getStringSet(KEY, emptySet()).orEmpty().filterNot { it in keep }.forEach { alarms.cancel(pending(it, null)) }
        notifications.forEach { n ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.at.toEpochMilliseconds(), pending(n.id, n))
        }
        prefs.edit { putStringSet(KEY, keep) }
    }

    private fun pending(id: String, n: LocalNotification?): PendingIntent {
        val intent = Intent(context, LocalNotificationReceiver::class.java).setAction("kkotsin.alarm.$id").apply {
            n?.let {
                putExtra(EXTRA_ID, it.id)
                putExtra(EXTRA_TITLE, it.title)
                putExtra(EXTRA_BODY, it.body)
                putExtra(EXTRA_LINK, it.deepLink)
            }
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL = "kkotsin_reminders"
        private const val KEY = "scheduled"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_LINK = "link"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "예약 편지 · 일정 · 기념일", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }
}

/** 예약 시각이 되면 알림을 띄운다. 누르면 앱이 열린다(딥링크 화면 이동은 셸 라우팅이 붙으면). */
class LocalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_ID) ?: return
        AndroidNotificationScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java).setData(intent.getStringExtra(AndroidNotificationScheduler.EXTRA_LINK)?.let(Uri::parse)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AndroidNotificationScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_heart_white)
            .setContentTitle(intent.getStringExtra(AndroidNotificationScheduler.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(AndroidNotificationScheduler.EXTRA_BODY))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) runCatching { manager.notify(id.hashCode(), notification) }
    }
}
