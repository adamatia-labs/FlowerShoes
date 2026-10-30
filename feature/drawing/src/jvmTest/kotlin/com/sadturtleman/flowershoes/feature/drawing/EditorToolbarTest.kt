package com.sadturtleman.flowershoes.feature.drawing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorToolbarTest {

    @Test
    fun `펜 시트에는 지금 있는 펜만 나온다`() {
        assertEquals(listOf("펜", "형광펜", "크레용", "수채", "지우개"), PEN_CHOICES.map { it.label })
    }

    @Test
    fun `붓을 고른 채 붓을 한 번 더 누르면 펜 시트가 열린다`() {
        val s = DrawingEditorState().tap(ToolbarItem.BRUSH)
        assertEquals(EditorSheet.PEN, s.sheet)
        assertEquals(EditorTool.PEN, s.tool)
    }

    @Test
    fun `지우개에서 붓을 누르면 그리던 펜으로 돌아가고 시트는 열지 않는다`() {
        val s = DrawingEditorState().pickTool(EditorTool.HIGHLIGHTER).tap(ToolbarItem.ERASER).let {
            assertEquals(EditorTool.ERASER, it.tool)
            assertEquals(ToolbarItem.ERASER, it.toolbar)
            it.tap(ToolbarItem.BRUSH)
        }
        assertEquals(EditorTool.HIGHLIGHTER, s.tool)
        assertEquals(ToolbarItem.BRUSH, s.toolbar)
        assertNull(s.sheet)
    }

    @Test
    fun `스티커를 누르면 스티커 시트가 열리고 고르면 닫혀 붙일 수 있다`() {
        val opened = DrawingEditorState().tap(ToolbarItem.STICKER)
        assertEquals(EditorSheet.STICKER, opened.sheet)
        val picked = opened.pickSticker("img:flower/01")
        assertNull(picked.sheet)
        assertEquals(EditorTool.STICKER, picked.tool)
        assertEquals(ToolbarItem.STICKER, picked.toolbar)
        assertEquals("img:flower/01", picked.sticker)
    }

    @Test
    fun `사진과 틀은 시트만 열고 그리던 도구는 그대로다`() {
        val base = DrawingEditorState().pickTool(EditorTool.CRAYON)
        assertEquals(EditorSheet.PHOTO, base.tap(ToolbarItem.PHOTO).sheet)
        val framed = base.tap(ToolbarItem.FRAME)
        assertEquals(EditorSheet.FRAME, framed.sheet)
        assertEquals(EditorTool.CRAYON, framed.tool)
    }

    @Test
    fun `스티커를 붙이다 붓을 누르면 마지막 펜으로 돌아간다`() {
        val s = DrawingEditorState().pickTool(EditorTool.WATERCOLOR).tap(ToolbarItem.STICKER).pickSticker("img:heart/00").tap(ToolbarItem.BRUSH)
        assertEquals(EditorTool.WATERCOLOR, s.tool)
        assertNull(s.sheet)
    }

    @Test
    fun `펜 시트에서 지우개를 고르면 툴바도 지우개가 된다`() {
        val s = DrawingEditorState().tap(ToolbarItem.BRUSH).pickTool(EditorTool.ERASER)
        assertEquals(ToolbarItem.ERASER, s.toolbar)
    }

    @Test
    fun `사진 시트가 열려 있으면 사진 칸에만 판이 깔린다`() {
        val s = DrawingEditorState().tap(ToolbarItem.PHOTO)
        assertEquals(listOf(ToolbarItem.PHOTO), ToolbarItem.entries.filter { s.highlighted(it) })
        val closed = s.copy(sheet = null)
        assertEquals(listOf(ToolbarItem.BRUSH), ToolbarItem.entries.filter { closed.highlighted(it) })
    }
}
