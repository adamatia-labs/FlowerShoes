package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangePrompt
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeRequest
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeState
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeStatus
import com.sadturtleman.flowershoes.core.data.sync.TransferCounts
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Figma 7 · 기기 바꾸기(366:3961)의 새 폰 · 연인 폰 · 옛 폰 화면을 Figma 예시 값으로 그려 build/render 에 남긴다.
 * 받는 중 · 막음 완료처럼 실기기에서 잠깐 스치거나 만들기 어려운 상태도 여기서 본다.
 */
class DeviceChangeRenderTest {
    private val now = System.currentTimeMillis()
    private val request = DeviceChangeRequest("r2", "jisu", "jisu-new", "jisu-old", "Galaxy S25", "KAKAO", now - 60_000, now + 7L * 86_400_000)
    private val linked = DeviceChangeState.Linked("c", TransferCounts(letters = 48, events = 23, photos = 31, drawings = 11), epoch = 2)

    @Test
    fun `새 폰 화면을 그린다`() {
        val ui = DeviceChangeUi(available = true)
        render("change_new_intro") { DeviceChangeScreen(ui, {}, {}, {}, {}) }
        render("change_new_waiting") { DeviceChangeScreen(ui.copy(state = DeviceChangeState.Waiting(request)), {}, {}, {}, {}) }
        render("change_new_receiving") {
            DeviceChangeScreen(ui.copy(state = linked, expected = linked.expected, received = TransferCounts(48, 23, 19, 0)), {}, {}, {}, {})
        }
        render("change_new_done") { DeviceChangeScreen(ui.copy(state = linked, received = linked.expected, done = true), {}, {}, {}, {}) }
        render("change_new_rejected") { DeviceChangeScreen(ui.copy(state = DeviceChangeState.Rejected), {}, {}, {}, {}, partnerName = "민준") }
    }

    @Test
    fun `연인 폰 · 옛 폰 화면을 그린다`() {
        val first = request.copy(id = "r1", deviceName = "Galaxy S25", status = DeviceChangeStatus.APPROVED, createdAtMs = now - 20 * 3_600_000L)
        val second = request.copy(deviceName = "iPhone 15", second = true)
        render("change_partner_ask") { prompt(DeviceChangePrompt.Approve(request, listOf(request))) }
        render("change_partner_approved") { prompt(DeviceChangePrompt.Approve(request, listOf(request)), PromptResult.Approved(0)) }
        render("change_partner_second") { prompt(DeviceChangePrompt.Approve(second, listOf(first, second))) }
        render("change_old_signed_in") { prompt(DeviceChangePrompt.SignedInElsewhere(request)) }
        render("change_old_approved") { prompt(DeviceChangePrompt.SignedInElsewhere(request.copy(status = DeviceChangeStatus.APPROVED, answeredAtMs = now))) }
        render("change_old_blocked") { prompt(DeviceChangePrompt.SignedInElsewhere(request), PromptResult.Blocked) }
    }

    @Test
    fun `요청 시각은 오늘 · 어제 · 날짜와 오전 · 오후로 읽는다`() {
        val nowAt = Instant.parse("2026-10-29T12:30:00Z") // 서울 21:30
        assertEquals("오늘 오후 9:30", whenText(nowAt.toEpochMilliseconds(), nowAt))
        assertEquals("어제 오후 3:10", whenText(Instant.parse("2026-10-28T06:10:00Z").toEpochMilliseconds(), nowAt))
        assertEquals("10.21 오전 12:05", whenText(Instant.parse("2026-10-20T15:05:00Z").toEpochMilliseconds(), nowAt))
        assertEquals("민준이", "민준".withSubject())
        assertEquals("지수와", "지수".withAnd())
    }

    @Composable
    private fun prompt(p: DeviceChangePrompt, result: PromptResult? = null) =
        DeviceChangePromptScreen(p, "지수", "카카오", result, busy = false, failed = false, onApprove = {}, onReject = {}, onBlock = {}, onClose = {})

    private fun render(name: String, content: @Composable () -> Unit) {
        for ((theme, dark) in listOf(AppTheme.GOMSIN to false, AppTheme.SOLDIER to true)) {
            val png = ImageComposeScene(width = 360 * 2, height = 780 * 2, density = Density(2f)) {
                KkotsinTheme(theme = theme, dark = dark) { content() }
            }.use { scene ->
                scene.render()
                scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            val out = File("build/render/${name}_${theme.name.lowercase()}_${if (dark) "dark" else "light"}.png").apply { parentFile.mkdirs(); writeBytes(png) }
            assertTrue(out.length() > 0)
        }
    }
}
