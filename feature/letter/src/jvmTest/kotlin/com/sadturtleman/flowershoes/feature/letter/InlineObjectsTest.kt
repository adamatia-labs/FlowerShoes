package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineObjectsTest {

    private fun meta(id: String) = DrawingMeta(id, 0, "SQUARE", null, "png-$id", null, true, 0)

    private fun draftWith(text: String, cursor: Int) = ComposeDraft(text = text, cursor = cursor)

    @Test
    fun `그림 · 사진은 커서 자리에 한 글자로 들어가고 보낼 때 그 순서 그대로 블록이 된다`() {
        var d = draftWith("가자 훈련", cursor = 3)
        d = d.copy(drawings = d.drawings + meta("a")).insertObject(drawingKey("a"))
        d = d.copy(cursor = 1).let { it.copy(photos = it.photos + "p1").insertObject(photoKey("p1")) }
        assertEquals("가${OBJ}자 ${OBJ}훈련", d.text)
        assertEquals(listOf("p:p1", "d:a"), d.objects)
        assertEquals(
            listOf(LetterBlock.Text("가"), LetterBlock.Photo("p1"), LetterBlock.Text("자"), LetterBlock.DrawingRef("a"), LetterBlock.Text("훈련")),
            d.toBlocks(),
        )
    }

    @Test
    fun `자리 글자를 지우면 그 그림도 빠진다`() {
        var d = draftWith("앞뒤", cursor = 1)
        d = d.copy(drawings = listOf(meta("a"), meta("b")))
        d = d.insertObject(drawingKey("a")).insertObject(drawingKey("b")) // 앞 a b 뒤
        val backspace = d.text.removeRange(1, 2) // a 자리 지움
        val after = d.editText(backspace, 1)
        assertEquals(listOf("d:b"), after.objects)
        assertEquals(listOf("b"), after.drawings.map { it.id })
        assertEquals(0, after.drawings.single().orderInLetter)
        assertEquals("앞${OBJ}뒤", after.text)
    }

    @Test
    fun `붙여 넣은 글의 자리 글자는 버린다`() {
        val d = draftWith("안녕", cursor = 2)
        val after = d.editText("안녕$OBJ!", 4)
        assertEquals("안녕!", after.text)
        assertTrue(after.objects.isEmpty())
        assertEquals(3, after.cursor)
    }

    @Test
    fun `예전 임시저장(자리 없이 그림만)은 글 끝에 붙여 맞춘다`() {
        val old = ComposeDraft(text = "보고 싶어", drawings = listOf(meta("a")), photos = listOf("p1"))
        val d = old.normalized()
        assertEquals("보고 싶어$OBJ$OBJ", d.text)
        assertEquals(listOf("d:a", "p:p1"), d.objects)
    }

    @Test
    fun `k 번째 그림 빼기`() {
        val d = ComposeDraft(text = "a${OBJ}b${OBJ}c", drawings = listOf(meta("x")), photos = listOf("y"), objects = listOf("d:x", "p:y"))
        val after = d.removeObjectAt(1)
        assertEquals("a${OBJ}bc", after.text)
        assertEquals(listOf("d:x"), after.objects)
        assertTrue(after.photos.isEmpty())
    }
}
