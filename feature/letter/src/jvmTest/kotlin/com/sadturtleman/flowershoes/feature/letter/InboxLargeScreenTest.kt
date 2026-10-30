package com.sadturtleman.flowershoes.feature.letter

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
import com.sadturtleman.flowershoes.core.designsystem.KkWindowSizeProvider
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 큰 화면 편지함(Figma 11 · 편지함 2단 `429:10732`): 840dp 부터 목록 | 읽기 2단, 폰은 열면 읽기로 바뀐다. */
@OptIn(ExperimentalTestApi::class)
class InboxLargeScreenTest {
    private val today = LocalDate(2026, 10, 28)

    private fun letter(id: String, text: String) = Letter(
        id, "partner", null, listOf(LetterBlock.Text(text)), "mail", Instant.parse("2026-10-27T11:40:00Z"),
    )

    private val cards = listOf(
        LetterCard(letter("1", "오늘 당직인데 네 생각나서 몰래 써"), false, "오늘 당직인데 네 생각나서 몰래 써", false, true, "민준 · 오늘 오후 9:12", null, Envelope.New("D-479"), today),
        LetterCard(letter("2", "훈련 끝나고 쓰는 편지"), false, "훈련 끝나고 쓰는 편지", false, false, "민준 · 10.24", null, Envelope.Received("D-483"), LocalDate(2026, 10, 24)),
    )

    private val open = OpenLetter(
        letter("1", "지수에게\n쉬는 시간에 너 그려 봤어.\n닮았지? 다음엔 더 잘 그릴게.\n— 민준이가"),
        mine = false, meta = "", drawings = emptyMap(), firstView = emptySet(),
        title = "민준이 보낸 편지", subtitle = "10월 27일 (화) 오후 8:40", senderRole = Role.SOLDIER, postmark = "2026.10.27",
    )

    private val state = LetterState(loading = false, today = today, inbox = cards, open = open)

    @Composable
    private fun Panes(width: Int, height: Int, s: LetterState = state) {
        KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
            Box(Modifier.size(width.dp, height.dp)) {
                KkWindowSizeProvider { LetterInboxPanes(s, {}, {}, {}, {}, {}, {}) }
            }
        }
    }

    @Test
    fun `폰에서 편지를 열면 목록 대신 읽기만 보인다`() = runComposeUiTest {
        setContent { Panes(360, 760) }
        waitForIdle()
        onAllNodesWithText("편지함").assertCountEquals(0)
        onAllNodesWithText("민준이 보낸 편지").assertCountEquals(1)
    }

    @Test
    fun `큰 화면에서는 목록과 읽던 편지가 나란히 보인다`() = runComposeUiTest {
        setContent { Panes(840, 600) }
        waitForIdle()
        onAllNodesWithText("편지함").assertCountEquals(1)
        onAllNodesWithText("민준이 보낸 편지").assertCountEquals(1)
        // 2단 읽기 칸에는 뒤로 가기 대신 위 오른쪽 답장 쓰기(Figma).
        onAllNodesWithText("답장 쓰기").assertCountEquals(1)
    }

    @Test
    fun `큰 화면에서 아무 편지도 안 열었으면 목록만`() = runComposeUiTest {
        setContent { Panes(840, 600, state.copy(open = null)) }
        waitForIdle()
        onAllNodesWithText("편지함").assertCountEquals(1)
        onAllNodesWithText("민준이 보낸 편지").assertCountEquals(0)
    }

    @Test
    fun `편지함 2단을 폰 · 큰 화면 폭으로 그린다`() {
        render("inbox_phone_open", 360, 760)
        render("inbox_two_pane", 840, 600)
        render("inbox_two_pane_empty", 840, 600, state.copy(open = null))
        render("inbox_two_pane_1280", 1200, 800)
    }

    private fun render(name: String, width: Int, height: Int, s: LetterState = state) {
        val png = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) { Panes(width, height, s) }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
