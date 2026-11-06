package com.sadturtleman.flowershoes.feature.letter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.KkWindowSizeProvider
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.drawing.BrushSize
import com.sadturtleman.flowershoes.core.drawing.CanvasSpec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.PointBuffer
import com.sadturtleman.flowershoes.core.drawing.Stroke
import com.sadturtleman.flowershoes.core.drawing.Tool
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class DrawingReceiptSessionUiTest {
    @Test
    fun `같은 편지를 재생 중 다시 열면 새 화면의 재생 완료가 기록된다`() {
        for (drawingOnly in listOf(false, true)) for (width in listOf(360, 840)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                val points = PointBuffer(intArrayOf(10, 100), intArrayOf(10, 100), intArrayOf(0, 1000))
                val doc = DrawingDocument(canvas = CanvasSpec(WidgetShape.SQUARE), elements = listOf(Stroke("stroke", 0, Tool.PEN, 0xff000000, BrushSize.M, points)), totalDurationMs = 1000)
                val blocks = if (drawingOnly) listOf(LetterBlock.DrawingRef("drawing")) else listOf(LetterBlock.Text("안녕"), LetterBlock.DrawingRef("drawing"))
                val letter = Letter("letter", "partner", blocks = blocks, paper = "rose", visibleAt = Instant.parse("2026-01-01T00:00:00Z"))
                val first = OpenLetter(letter, mine = false, drawingOnly = drawingOnly, meta = "", drawings = mapOf("drawing" to doc), firstView = setOf("drawing"), viewSessionId = "first")
                val current = mutableStateOf(first)
                val seen = mutableListOf<LetterIntent.DrawingSeen>()
                setContent {
                    KkotsinTheme(AppTheme.GOMSIN, dark = false) {
                        Box(Modifier.size(width.dp, 760.dp)) {
                            KkWindowSizeProvider {
                                LetterInboxPanes(LetterState(loading = false, open = current.value), { if (it is LetterIntent.DrawingSeen) seen.add(it) }, {}, {}, {}, {}, {})
                            }
                        }
                    }
                }
                mainClock.advanceTimeBy(200)
                runOnIdle { current.value = first.copy(viewSessionId = "second") }
                mainClock.advanceTimeBy(3_000)
                runOnIdle { assertEquals(listOf("second"), seen.map { it.viewSessionId }, "drawingOnly=$drawingOnly width=$width") }
            }
        }
    }
}
