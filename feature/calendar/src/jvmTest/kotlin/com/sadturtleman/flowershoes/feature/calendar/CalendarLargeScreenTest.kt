package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkWindowWidth
import com.sadturtleman.flowershoes.core.designsystem.LocalKkWindowWidth
import androidx.compose.runtime.CompositionLocalProvider
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 큰 화면 캘린더(Figma 11 · 캘린더 + 일정 `429:10850`): 840dp 부터 달력 옆에 고른 날 · 다가오는 일정. */
@OptIn(ExperimentalTestApi::class)
class CalendarLargeScreenTest {
    private val t0 = Instant.fromEpochMilliseconds(0)
    private val today = LocalDate(2026, 10, 28)

    private fun event(id: String, type: EventType, title: String, from: String, to: String) = Event(
        id, type, title, LocalDateTime.parse(from), LocalDateTime.parse(to), false, t0, 1, "dev",
    )

    private val duty = event("d", EventType.DUTY, "민준 당직", "2026-10-28T18:00", "2026-10-29T08:00")
    private val visit = event("v", EventType.VISIT, "면회", "2026-11-08T00:00", "2026-11-08T23:59")
    private val vacation = event("h", EventType.VACATION, "정기휴가", "2026-11-14T00:00", "2026-11-18T23:59")

    private val state = CalendarState(
        month = LocalDate(2026, 10, 1), selected = today, today = today,
        monthEvents = listOf(duty), upcoming = listOf(duty, visit, vacation),
        pool = LeavePool.of(Branch.AIR_FORCE, emptyList(), emptyList()),
    )

    /** 셸 안의 캘린더: 창 폭 구분은 창 전체로 정하고(Nav Rail 80 을 뺀 [width] 가 화면에 온다). */
    @Composable
    private fun Screen(width: Int, height: Int) {
        val window = if (width < 600) KkWindowWidth.Compact else KkWindowWidth.of((width + 80).dp)
        KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
            CompositionLocalProvider(LocalKkWindowWidth provides window) {
                Box(Modifier.size(width.dp, height.dp)) { CalendarScreen(state, {}) }
            }
        }
    }

    @Test
    fun `폰은 한 줄로 쌓고 다가오는 일정 칸이 없다`() = runComposeUiTest {
        setContent { Screen(360, 760) }
        waitForIdle()
        onAllNodesWithText("캘린더").assertCountEquals(1)
        onAllNodesWithText("다가오는 일정").assertCountEquals(0)
    }

    @Test
    fun `큰 화면은 달력 옆에 고른 날과 다가오는 일정`() = runComposeUiTest {
        setContent { Screen(760, 600) }
        waitForIdle()
        // 오른쪽 칸: 고른 날 머리 + 그날 일정, 다가오는 일정(고른 날 것은 빼고).
        onAllNodesWithText("2026년 10월").assertCountEquals(1)
        onAllNodesWithText("10월 28일 (수)").assertCountEquals(1)
        onAllNodesWithText("다가오는 일정").assertCountEquals(1)
        onAllNodesWithText("정기휴가", substring = true).assertCountEquals(1)
        // 달력은 늘 펼쳐 둔다 — 접기 토글이 없다.
        onAllNodesWithText("달력 펼치기").assertCountEquals(0)
    }

    @Test
    fun `캘린더를 폰 · 큰 화면 폭으로 그린다`() {
        render("calendar_phone", 360, 760)
        render("calendar_two_pane", 760, 600)
        render("calendar_two_pane_1280", 1200, 800)
    }

    private fun render(name: String, width: Int, height: Int) {
        val png = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) { Screen(width, height) }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
