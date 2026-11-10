package com.sadturtleman.flowershoes.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.*
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CurrentStatusUiTest {
    @Test fun `상태 입력은 연결된 홈에서만 연다`() = runComposeUiTest {
        var opens = 0
        setContent { KkotsinTheme(AppTheme.GOMSIN) { HomeScreen(HomeState(loading = false, linked = true), {}, {}, onOpenStatus = { opens++ }) } }
        onNodeWithText("내 상태").performClick()
        assertEquals(1, opens)
    }

    @Test fun `혼자 모드에는 상대에게 보여 줄 상태 입력을 노출하지 않는다`() = runComposeUiTest {
        setContent { KkotsinTheme(AppTheme.GOMSIN) { HomeScreen(HomeState(loading = false, linked = false), {}, {}) } }
        onNodeWithText("내 상태").assertDoesNotExist()
    }

    @Test fun `저장 중에는 입력과 기간 변경 및 중복 저장을 막는다`() = runComposeUiTest {
        val s = CurrentStatusEditState(label = "당직 중", ready = true, saving = true, hadStatus = true)
        setContent { KkotsinTheme(AppTheme.GOMSIN) { Column(Modifier.verticalScroll(rememberScrollState())) { CurrentStatusSheetContent(s, {}, {}, {}, {}, {}) } } }
        onNodeWithText("당직 중").assertIsNotEnabled()
        onNodeWithText("1시간").assertIsNotEnabled()
        onNodeWithText("상태 지우기").performScrollTo().assertIsNotEnabled()
        onNodeWithText("저장 중…").performScrollTo().assertIsNotEnabled()
    }

    @Test fun `표시 기간은 서울 날짜 경계로 계산하고 기존 만료를 유지한다`() {
        val now = Instant.parse("2026-11-11T14:30:00Z")
        assertEquals(Instant.parse("2026-11-11T15:30:00Z"), StatusDuration.HOUR.expiresAt(now, null))
        assertEquals(Instant.parse("2026-11-11T15:00:00Z"), StatusDuration.TODAY.expiresAt(now, null))
        assertEquals(Instant.parse("2026-11-11T23:00:00Z"), StatusDuration.TOMORROW_MORNING.expiresAt(now, null))
        assertEquals(now, StatusDuration.KEEP.expiresAt(now, now))
        assertNull(StatusDuration.UNTIL_CLEARED.expiresAt(now, null))
    }

    @Test fun `상태 입력 시트의 네 가지 테마를 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val s = CurrentStatusEditState(label = "당직 중", duration = StatusDuration.TOMORROW_MORNING, ready = true, hadStatus = true)
            val png = ImageComposeScene(720, 1500, Density(2f)) {
                KkotsinTheme(theme, dark) {
                    KkSheetSurface(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { CurrentStatusSheetContent(s, {}, {}, {}, {}, {}) }
                }
            }.use { scene -> scene.render(); scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes }
            val out = File("build/render/status_${theme.name.lowercase()}_${if (dark) "dark" else "light"}.png")
            out.parentFile.mkdirs(); out.writeBytes(png)
            assertTrue(out.length() > 0)
        }
    }
}
