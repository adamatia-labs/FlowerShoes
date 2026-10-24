package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.model.MeetNote
import com.sadturtleman.flowershoes.core.model.mergeMeetNotes
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordExportTest {

    @Test
    fun zipIsReadableWithKoreanNames() {
        val zip = ZipWriter()
        zip.add("편지.txt", "안녕 ♥".encodeToByteArray())
        zip.add("그림/2026-10-21_a.png", ByteArray(3000) { (it % 251).toByte() })
        val read = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip.finish())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                read[e.name] = z.readBytes()
            }
        }
        assertEquals(setOf("편지.txt", "그림/2026-10-21_a.png"), read.keys)
        assertEquals("안녕 ♥", read.getValue("편지.txt").decodeToString())
        assertEquals(3000, read.getValue("그림/2026-10-21_a.png").size)
    }

    @Test
    fun meetNotesMergePerLine() {
        // 일정은 한쪽이 이겨도, 줄마다 늦게 쓴 메모가 남는다.
        val mine = mapOf("me" to MeetNote("드디어 얼굴 봤다", 200), "you" to MeetNote("옛 줄", 100))
        val theirs = mapOf("you" to MeetNote("면회실 짜장면 최고", 300))
        val merged = mergeMeetNotes(mine, theirs)
        assertEquals("드디어 얼굴 봤다", merged.getValue("me").text)
        assertEquals("면회실 짜장면 최고", merged.getValue("you").text)
    }
}
