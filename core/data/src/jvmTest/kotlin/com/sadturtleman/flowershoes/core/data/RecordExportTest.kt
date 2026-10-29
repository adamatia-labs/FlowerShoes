package com.sadturtleman.flowershoes.core.data

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

}
