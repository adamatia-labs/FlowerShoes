package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 편지함(Figma 360:2613) · 받은 편지(363:3666)를 build/render 에 그린다. 예시 데이터: 지수(곰신) · 민준(군인), 오늘 2026-10-28. */
class InboxRenderTest {
    private val today = LocalDate(2026, 10, 28)

    private fun letter(id: String, text: String, mine: Boolean = false, drawing: Boolean = false, paper: String = "mail") = Letter(
        id, if (mine) "me" else "partner", null,
        listOfNotNull(LetterBlock.Text(text), if (drawing) LetterBlock.DrawingRef("d-$id") else null), paper, Instant.parse("2026-10-28T12:12:00Z"),
    )

    private fun card(id: String, text: String, meta: String, envelope: Envelope, date: LocalDate, mine: Boolean = false, drawing: Boolean = false) =
        LetterCard(letter(id, text, mine, drawing), mine, text, false, envelope is Envelope.New, meta, null, envelope, date)

    private val inbox = LetterState(
        loading = false,
        today = today,
        inbox = listOf(
            card("1", "오늘 당직인데 네 생각나서 몰래 써", "민준 · 오늘 오후 9:12", Envelope.New("D-479"), today),
            card("2", "민준이 그려준 나", "민준 · 10.27", Envelope.Drawing, LocalDate(2026, 10, 27), drawing = true),
            card("3", "훈련 끝나고 쓰는 편지", "민준 · 10.24", Envelope.Received("D-483"), LocalDate(2026, 10, 24)),
            card("4", "첫 면회 다녀와서", "보낸 편지 · 10.19", Envelope.Sent("2026.10.19"), LocalDate(2026, 10, 19), mine = true),
            card("5", "일병 된 거 축하해!", "보낸 편지 · 09.20", Envelope.Sent("2026.09.20"), LocalDate(2026, 9, 20), mine = true),
        ),
    )

    private val read = OpenLetter(
        letter("1", "지수에게\n오늘 당직인데 네 생각나서 몰래 써.\n불침번 서면서 별 보니까\n한강에서 같이 본 하늘이 생각나더라.\n\n— 민준이가"),
        mine = false, meta = "", drawings = emptyMap(), firstView = emptySet(),
        title = "민준이 보낸 편지", subtitle = "10월 28일 (수) 오후 9:12", senderRole = Role.SOLDIER, postmark = "2026.10.28",
    )

    @Test
    fun `편지함과 받은 편지를 그린다`() {
        render("inbox_all") { LetterInboxScreen(inbox, {}, {}, {}, {}) }
        render("inbox_sent") { LetterInboxScreen(inbox.copy(filter = LetterFilter.SENT), {}, {}, {}, {}) }
        render("inbox_empty") { LetterInboxScreen(LetterState(loading = false), {}, {}, {}, {}) }
        render("inbox_loading") { LetterInboxScreen(LetterState(), {}, {}, {}, {}) }
        render("inbox_all_soldier_dark", AppTheme.SOLDIER, dark = true) { LetterInboxScreen(inbox, {}, {}, {}, {}) }
        render("read_text") { LetterReadScreen(read, {}, {}) }
        render("read_thanked") { LetterReadScreen(read.copy(thanked = true), {}, {}) }
        // 군인이 읽는 곰신 편지: 편지지는 보낸 사람 것 그대로, 소인은 "꽃신 우편".
        render("read_by_soldier_dark", AppTheme.SOLDIER, dark = true) {
            LetterReadScreen(read.copy(letter = read.letter.copy(paper = "plain"), title = "지수가 보낸 편지", senderRole = Role.GOMSIN), {}, {})
        }
        render("read_locked") {
            LetterReadScreen(
                read.copy(
                    locked = true, subtitle = "예약 편지", opensAt = LocalDateTime(2099, 11, 1, 9, 0),
                    envelopeLine = "열어 보면 알 거야", envelopeMeta = "민준 · 11월 1일 오전 9시 도착", stamp = "D-479",
                ),
                {}, {},
            )
        }
    }

    private fun render(name: String, theme: AppTheme = AppTheme.GOMSIN, dark: Boolean = false, content: @Composable () -> Unit) {
        val png = ImageComposeScene(width = 360 * 2, height = 760 * 2, density = Density(2f)) {
            KkotsinTheme(theme = theme, dark = dark) { content() }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
