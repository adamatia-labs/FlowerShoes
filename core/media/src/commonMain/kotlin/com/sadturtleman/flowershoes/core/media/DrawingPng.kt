package com.sadturtleman.flowershoes.core.media

import com.sadturtleman.flowershoes.core.drawing.DrawingDocument

/**
 * 그림 문서 → 완성 PNG. 위젯 크기의 2배로 그린다(SQUARE 680×680, WIDE 1456×680).
 * 배경은 문서의 고정 ARGB, 지우개는 실제로 지운다 — 리플레이 마지막 장면과 같다.
 */
expect object DrawingPng {
    fun render(doc: DrawingDocument, widthPx: Int = doc.canvas.shape.pngWidth): ByteArray
}
