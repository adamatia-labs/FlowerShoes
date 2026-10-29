package com.sadturtleman.flowershoes.feature.drawing

/** 아래 툴바(Figma 그림 그리기 · toolbar): 붓 · 지우개 · 스티커 · 사진 · 틀 (+ 되돌리기 · 다시). */
enum class ToolbarItem { BRUSH, ERASER, STICKER, PHOTO, FRAME }

/** 툴바에서 여는 시트. */
enum class EditorSheet { PEN, STICKER, PHOTO, FRAME }

/** 틀(액자). 출시에는 지금 있는 것만 — 우표 · 둥근 액자 · 필름은 출시 뒤(#69 결정). */
enum class DrawingFrame(val label: String) { NONE("없음"), POLAROID("폴라로이드") }

/** 펜 시트 한 칸. [lifetime] 이면 꽃신 평생. */
data class PenChoice(val tool: EditorTool, val label: String, val lifetime: Boolean)

/** 펜 시트(Figma 펜 고르기): 지금 펜 세트만. 연필 · 사인펜 · 마커 · 스포이드는 출시 뒤(#69 결정). */
val PEN_CHOICES: List<PenChoice> = listOf(
    PenChoice(EditorTool.PEN, "펜", false),
    PenChoice(EditorTool.HIGHLIGHTER, "형광펜", false),
    PenChoice(EditorTool.CRAYON, "크레용", true),
    PenChoice(EditorTool.WATERCOLOR, "수채", true),
    PenChoice(EditorTool.ERASER, "지우개", false),
)

/** 툴바에서 고른 칸(밑에 판이 깔린다). */
val DrawingEditorState.toolbar: ToolbarItem
    get() = when (tool) {
        EditorTool.ERASER -> ToolbarItem.ERASER
        EditorTool.STICKER -> ToolbarItem.STICKER
        else -> ToolbarItem.BRUSH
    }

/**
 * 툴바 한 칸 누르기. 붓은 고른 채 한 번 더 누르면 펜 시트, 아니면 마지막 펜으로 돌아간다.
 * 스티커는 시트를 열고 붙이기 도구로, 사진 · 틀은 시트만 연다(그리던 도구는 그대로).
 */
fun DrawingEditorState.tap(item: ToolbarItem): DrawingEditorState = when (item) {
    ToolbarItem.BRUSH -> if (toolbar == ToolbarItem.BRUSH) copy(sheet = EditorSheet.PEN) else copy(tool = pen, sheet = null)
    ToolbarItem.ERASER -> copy(tool = EditorTool.ERASER, sheet = null)
    ToolbarItem.STICKER -> copy(tool = EditorTool.STICKER, sheet = EditorSheet.STICKER)
    ToolbarItem.PHOTO -> copy(sheet = EditorSheet.PHOTO)
    ToolbarItem.FRAME -> copy(sheet = EditorSheet.FRAME)
}

/** 펜 시트에서 도구 고르기. 지우개가 아닌 펜은 "마지막 펜"으로 기억한다. */
fun DrawingEditorState.pickTool(t: EditorTool): DrawingEditorState =
    copy(tool = t, pen = if (t == EditorTool.ERASER || t == EditorTool.STICKER) pen else t)

/** 스티커 고르기: 시트를 닫아 캔버스를 눌러 붙일 수 있게. */
fun DrawingEditorState.pickSticker(id: String): DrawingEditorState =
    copy(sticker = id, tool = EditorTool.STICKER, sheet = null)
