package com.sadturtleman.flowershoes

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshot
import com.sadturtleman.flowershoes.core.widget.WidgetTag
import com.sadturtleman.flowershoes.widget.MissYouWidgetReceiver
import com.sadturtleman.flowershoes.widget.OverviewWidgetReceiver
import com.sadturtleman.flowershoes.widget.NextMeetingWidgetReceiver
import com.sadturtleman.flowershoes.widget.snapshotStore
import com.sadturtleman.flowershoes.widget.updateAllWidgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Glance session and Android RemoteViews host, without a launcher dependency. */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun activeMissYouWidgetReadsNewSnapshotAfterUpdate(): Unit = runBlocking {
        val initial = WidgetSnapshot(theme = "GOMSIN", partnerName = "민준", linked = true, missYouSentToday = 2, updatedAtMs = System.currentTimeMillis())
        withWidget(initial, MissYouWidgetReceiver::class.java, 180) { view ->
            awaitText(view, "오늘 2번 보냈어요")
            // Keep the Glance composition alive; updateAll must render the new disk contents.
            snapshotStore(context).write(initial.copy(missYouSentToday = 7, updatedAtMs = System.currentTimeMillis()))
            updateAllWidgets(context)
            assertEquals(7, snapshotStore(context).read()?.missYouSentToday)
            awaitText(view, "오늘 7번 보냈어요")
        }
    }

    @Test
    fun smallestOverviewKeepsUnreadLetterAndMissYouVisible(): Unit = runBlocking {
        val initial = WidgetSnapshot(
            theme = "GOMSIN", partnerName = "민준", linked = true,
            ddayCaption = "민준이 전역까지", ddayValue = "D-479", progress = .286f,
            progressStart = "입대 2026.04.20", progressPercent = "28.6%", progressEnd = "전역 2028.02.19",
            tagLeave = WidgetTag("다음 휴가", "D-17"), tagMeet = WidgetTag("다음 만남", "D-11"), tagRank = WidgetTag("진급 예정", "D-53"),
            unreadLetters = 1, letterLine = "새 편지 1통", letterMeta = "민준 · 오후 9:12", updatedAtMs = System.currentTimeMillis(),
        )
        withWidget(initial, OverviewWidgetReceiver::class.java, 250) { view ->
            awaitText(view, "새 편지 1통")
            assertVisible(view, "새 편지 1통", 250)
            assertVisible(view, "보고싶어", 250)
        }
    }

    @Test
    fun smallNextMeetingKeepsShortDateAndTitleReadable(): Unit = runBlocking {
        val initial = WidgetSnapshot(theme = "GOMSIN", nextEventValue = "D-11", nextEventLine = "11월 8일 면회", nextEventWhen = "토요일 10:00", nextEventType = "VISIT", updatedAtMs = System.currentTimeMillis())
        withWidget(initial, NextMeetingWidgetReceiver::class.java, 110) { view ->
            awaitText(view, "11월 8일 면회")
            assertVisible(view, "11월 8일 면회", 110)
        }
    }

    private suspend fun withWidget(initial: WidgetSnapshot, receiver: Class<*>, sizeDp: Int, block: suspend (AppWidgetHostView) -> Unit) {
        // Run this test with the isolated QA emulator build; no production account is needed.
        assumeTrue(BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
        val store = snapshotStore(context)
        withTimeout(15_000) { while (store.read() == null) delay(50) }
        delay(800) // Let the initial DB snapshot publication finish before installing the fixture.
        val original = store.read()
        var host: AppWidgetHost? = null
        lateinit var view: AppWidgetHostView
        var widgetId = 0
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try {
            store.write(initial)
            instrumentation.runOnMainSync {
                val activeHost = AppWidgetHost(context, 158001).also { host = it }
                activeHost.startListening()
                widgetId = activeHost.allocateAppWidgetId()
                val manager = AppWidgetManager.getInstance(context)
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, sizeDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, sizeDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, sizeDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, sizeDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }
                assertTrue("Could not bind QA provider ${receiver.simpleName}", manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(context, receiver), options))
                view = activeHost.createView(context, widgetId, manager.getAppWidgetInfo(widgetId))
            }
            updateAllWidgets(context)
            block(view)
        } finally {
            host?.let { activeHost ->
                instrumentation.runOnMainSync {
                    if (widgetId != 0) activeHost.deleteAppWidgetId(widgetId)
                    activeHost.stopListening()
                }
            }
            original?.let(store::write)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private suspend fun assertVisible(view: AppWidgetHostView, expected: String, sizeDp: Int) {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt()
        val spec = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
        // The first layout selects the responsive RemoteViews variant asynchronously.
        instrumentation.runOnMainSync {
            view.measure(spec, spec)
            view.layout(0, 0, px, px)
        }
        awaitText(view, expected)
        instrumentation.runOnMainSync {
            view.measure(spec, spec)
            view.layout(0, 0, px, px)
            val text = textViews(view).first { it.text.toString() == expected }
            val rect = android.graphics.Rect(0, 0, text.width, text.height)
            view.offsetDescendantRectToMyCoords(text, rect)
            assertTrue("'$expected' is clipped: $rect in ${px}x$px", text.height > 0 && text.width > 0 && rect.top >= 0 && rect.bottom <= px)
            assertTrue("'$expected' has no visible text line", text.lineCount > 0 && text.height >= text.lineHeight)
            for (line in 0 until text.lineCount) assertEquals("'$expected' is ellipsized", 0, text.layout.getEllipsisCount(line))
        }
    }

    private suspend fun awaitText(view: AppWidgetHostView, expected: String) {
        var last = emptyList<String>()
        val found = kotlinx.coroutines.withTimeoutOrNull(15_000) {
            while (true) {
                instrumentation.runOnMainSync { last = texts(view) }
                if (expected in last) return@withTimeoutOrNull true
                delay(50)
            }
        }
        assertTrue("Expected '$expected'; Android widget contains $last", found == true)
    }

    private fun texts(view: View) = textViews(view).map { it.text.toString() }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
