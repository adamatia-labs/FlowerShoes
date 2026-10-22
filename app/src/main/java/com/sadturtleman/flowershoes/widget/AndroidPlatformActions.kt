package com.sadturtleman.flowershoes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.sadturtleman.flowershoes.feature.settings.PlatformActions

/** 설정의 "위젯 추가": 런처에 위젯 고정을 요청한다(시스템 확인창이 뜬다). */
class AndroidPlatformActions(private val context: Context) : PlatformActions {

    private val receivers = linkedMapOf(
        "dday" to ("전역 D-day" to DdayWidgetReceiver::class.java),
        "missyou" to ("보고싶어" to MissYouWidgetReceiver::class.java),
        "next" to ("다음 만남" to NextMeetingWidgetReceiver::class.java),
        "drawing" to ("받은 그림" to DrawingWidgetReceiver::class.java),
    )

    override val pinnableWidgets: List<Pair<String, String>>
        get() = if (AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) receivers.map { (k, v) -> k to v.first } else emptyList()

    override fun requestPinWidget(kind: String): Boolean {
        val target = receivers[kind]?.second ?: return false
        return AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, target), null, null)
    }
}
