package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
            LetterReadScreen(read.copy(letter = read.letter.copy(paper = "rose"), title = "지수가 보낸 편지", senderRole = Role.GOMSIN), {}, {})
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

    /** Figma 10 · 다크 확인(386:5350) "받은 편지 · 그림 포함": 군인이 보낸 군사우편 편지. */
    private val readDrawing = read.copy(
        letter = Letter(
            "7", "partner", null,
            listOf(
                LetterBlock.Text("지수에게\n쉬는 시간에 너 그려 봤어."), LetterBlock.DrawingRef("d-7"),
                LetterBlock.Text("닮았지? 다음엔 더 잘 그릴게.\n— 민준이가"),
            ),
            Paper.MAIL.id, Instant.parse("2026-10-27T11:40:00Z"),
        ),
        subtitle = "10월 27일 (화) 오후 8:40", postmark = "2026.10.27", captions = mapOf("d-7" to "민준이 그려준 나"),
    )

    @Test
    fun `편지함과 받은 편지를 4개 모드로 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val mode = "${theme.name.lowercase()}_${if (dark) "dark" else "light"}"
            render("inbox_all_$mode", theme, dark) { LetterInboxScreen(inbox, {}, {}, {}, {}) }
            render("read_drawing_$mode", theme, dark) { LetterReadScreen(readDrawing, {}, {}) }
        }
        render("inbox_all_font200", fontScale = 2f) { LetterInboxScreen(inbox, {}, {}, {}, {}) }
        render("read_drawing_font200", fontScale = 2f) { LetterReadScreen(readDrawing, {}, {}) }
    }

    @Test
    fun `편지지는 읽는 사람의 테마 · 다크 모드와 상관없이 보낸 편지지 색 그대로다`() {
        val paper = Paper.MAIL.look().background.toArgb()
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val image = image(theme, dark) { LetterReadScreen(readDrawing, {}, {}) }
            // 화면 가운데에서 가장 많은 색 = 편지지 바탕.
            val counts = HashMap<Int, Int>()
            for (y in image.height / 4 until image.height * 3 / 4 step 3) for (x in image.width / 10 until image.width * 9 / 10 step 3) {
                counts.merge(image.getColor(x, y), 1, Int::plus)
            }
            val dominant = counts.maxBy { it.value }.key
            assertEquals("%08X".format(paper), "%08X".format(dominant), "$theme dark=$dark")
        }
    }

    private val solo = LetterState(
        loading = false, today = today, solo = true,
        inbox = listOf(
            card("q1", "입대하는 날 못 한 말", "지수 · 10.27 씀", Envelope.Waiting, LocalDate(2026, 10, 27), mine = true),
            card("q2", "훈련소 밥은 먹을 만해?", "지수 · 10.25 씀", Envelope.Waiting, LocalDate(2026, 10, 25), mine = true),
            card("q3", "벌써 보고 싶어", "지수 · 10.21 씀", Envelope.Waiting, LocalDate(2026, 10, 21), mine = true),
        ),
    )

    @Test
    fun `혼자 모드 편지함은 연결 안내와 보낼 편지 N통`() {
        render("inbox_solo") { LetterInboxScreen(solo.copy(partnerName = "민준"), {}, {}, {}, {}) }
    }

    private val writing = LetterState(
        loading = false, partnerName = "민준",
        draft = ComposeDraft(
            title = "보고 싶은 밤",
            text = "민준아, 오늘은 유난히 네 생각이 많이 났어. 퇴근길에 우리 자주 가던 떡볶이집 앞을 지나는데 괜히 혼자 웃었어.\n\n이번 휴가 때 꼭 같이 가자.\n훈련 다치지 말고, 밥 잘 챙겨 먹어!",
            paper = Paper.ROSE.id,
        ),
    )

    @Test
    fun `편지 쓰기 상태를 그린다`() {
        fun compose(name: String, s: LetterState, theme: AppTheme = AppTheme.GOMSIN, dark: Boolean = false) =
            render(name, theme, dark) { LetterComposeScreen(s, lifetimeOn = false, canPhoto = true, onIntent = {}, onClose = {}, onSchedule = {}) }
        compose("compose_blank", LetterState(loading = false, partnerName = "민준"))
        compose("compose_writing", writing)
        compose("compose_scheduled", writing.copy(draft = writing.draft.copy(revealAt = LocalDateTime(2026, 11, 1, 9, 0))))
        compose("compose_sending", writing.copy(draft = writing.draft.copy(sending = true)))
        // 편지지는 콘텐츠: 군인 · 다크에서도 같은 종이 색.
        compose("compose_writing_soldier_dark", writing, AppTheme.SOLDIER, dark = true)
        render("compose_sent") { LetterSentScreen(ComposeDone.Sent("보고 싶은 밤", "지수 · 오늘 오후 10:03", "D-479", "민준"), {}) }
        render("compose_sent_solo") { LetterSentScreen(ComposeDone.Sent("보고 싶은 밤", "지수 · 오늘 오후 10:03", "대기", "연인", queued = true, queuedCount = 4), {}) }
    }

    private fun image(theme: AppTheme, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit): Bitmap =
        ImageComposeScene(width = 360 * 2, height = 760 * 2, density = Density(2f, fontScale)) {
            KkotsinTheme(theme = theme, dark = dark) { content() }
        }.use { scene ->
            scene.render()
            Bitmap.makeFromImage(scene.render(1_000_000_000L))
        }

    private fun render(name: String, theme: AppTheme = AppTheme.GOMSIN, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val png = Image.makeFromBitmap(image(theme, dark, fontScale, content)).encodeToData(EncodedImageFormat.PNG)!!.bytes
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
