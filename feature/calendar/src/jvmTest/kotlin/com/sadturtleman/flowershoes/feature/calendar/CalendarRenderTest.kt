package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
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

/**
 * Figma 10 · 다크 확인(386:5350) "캘린더 · 펼침"과 3 · 캘린더(354:1267) "캘린더 · 접힘"을
 * 4개 모드 · 글자 200% 로 build/render 에 그린다(머리 "2026년 10월 +" · 색 채운 일정 줄 · 오늘 테두리 원).
 */
class CalendarRenderTest {
    private val t0 = Instant.fromEpochMilliseconds(0)
    private val today = LocalDate(2026, 10, 28)

    private fun event(id: String, type: EventType, title: String, from: String, to: String) = Event(
        id, type, title, LocalDateTime.parse(from), LocalDateTime.parse(to), false, t0, 1, "dev",
    )

    private val training = event("t", EventType.TRAINING, "공군 전투 훈련", "2026-10-20T00:00", "2026-10-24T23:59")
    private val anniversary = event("a", EventType.ANNIVERSARY, "사귄 지 400일", "2026-10-17T00:00", "2026-10-17T23:59")
    private val duty = event("d", EventType.DUTY, "민준 당직", "2026-10-28T18:00", "2026-10-29T09:00")
    private val visit = event("v", EventType.VISIT, "면회", "2026-11-08T00:00", "2026-11-08T23:59")

    private val state = CalendarState(
        month = LocalDate(2026, 10, 1), selected = today, today = today, expanded = true,
        monthEvents = listOf(anniversary, training, duty), upcoming = listOf(duty, visit),
        pool = LeavePool.of(Branch.AIR_FORCE, emptyList(), emptyList()),
    )

    @Test
    fun `캘린더를 4개 모드와 글자 200%로 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val mode = "${theme.name.lowercase()}_${if (dark) "dark" else "light"}"
            render("calendar_expanded_$mode", theme, dark)
            render("calendar_week_$mode", theme, dark, s = state.copy(expanded = false))
        }
        render("calendar_expanded_font200", AppTheme.GOMSIN, dark = false, fontScale = 2f)
        render("calendar_week_font200", AppTheme.GOMSIN, dark = false, fontScale = 2f, s = state.copy(expanded = false))
    }

    private fun render(name: String, theme: AppTheme, dark: Boolean, fontScale: Float = 1f, s: CalendarState = state) {
        val png = ImageComposeScene(width = 360 * 2, height = 760 * 2, density = Density(2f, fontScale)) {
            KkotsinTheme(theme = theme, dark = dark) { CalendarScreen(s, {}) }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
