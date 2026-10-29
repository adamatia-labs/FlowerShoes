package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toLocalDateTime

/**
 * 내 기록 내보내기(설정 › 데이터, 무료 — 수익 모델 확정 "원본은 무료로").
 * 편지 글 · 그림(완성 PNG) · 사진 원본 · 일정 · 그날의 한 줄을 ZIP 한 개로 묶는다. 폰 안에서 복호화된 사본으로 만들고 서버를 거치지 않는다.
 * 꾸밈 없는 원본이라 디자인된 추억 책 PDF 와 겹치지 않는다.
 */
class RecordExporter(
    private val db: AppDatabase,
    private val attachments: AttachmentStore,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {

    /** 내보내기 화면의 줄들: 편지 수 · 그림 · 사진 수 · 일정 수 · ZIP 크기(어림). */
    data class Preview(val letters: Int, val drawings: Int, val photos: Int, val events: Int, val bytes: Long)

    private class Item(val path: String, val attachmentId: String)

    suspend fun preview(): Preview {
        val (letters, files, events) = collect()
        val sizes = db.attachmentDao().all().associate { it.id to it.sizeBytes }
        return Preview(
            letters = letters.size,
            drawings = files.count { it.path.startsWith("그림/") },
            photos = files.count { it.path.startsWith("사진/") },
            events = events,
            bytes = files.sumOf { sizes[it.attachmentId] ?: 0L } + letters.sumOf { it.length.toLong() },
        )
    }

    /** ZIP 바이트(파일 이름은 UTF-8). */
    suspend fun zip(): ByteArray {
        val (letters, files, _) = collect()
        val zip = ZipWriter()
        zip.add("편지.txt", letters.joinToString("\n\n") .encodeToByteArray())
        zip.add("일정 · 만남.txt", eventsText().encodeToByteArray())
        files.forEach { f -> attachments.read(f.attachmentId)?.let { zip.add(f.path, it) } }
        zip.add("읽어 주세요.txt", README.encodeToByteArray())
        return zip.finish()
    }

    /** 사용자 id → 이름. 내 이름이 비어 있으면 "나", 상대는 "연인". */
    private suspend fun names(): Map<String, String> {
        val me = runCatching { identity.userId }.getOrNull()
        return db.userDao().all().associate { it.id to it.nickname.ifBlank { if (it.id == me) "나" else "연인" } } +
            listOfNotNull(me?.let { id -> id to (db.userDao().get(id)?.nickname?.ifBlank { null } ?: "나") })
    }

    private suspend fun collect(): Triple<List<String>, List<Item>, Int> {
        val now = clock.now().toEpochMilliseconds()
        val who = names()
        val letters = db.letterDao().observeAll().first().filter { it.deletedAtMs == null && it.visibleAtMs <= now }.sortedBy { it.visibleAtMs }
        val files = mutableListOf<Item>()
        val texts = letters.map { e ->
            val l = e.toModel()
            val day = l.visibleAt.toLocalDateTime(SEOUL)
            val stamp = "${day.date}"
            val drawings = db.drawingDao().forLetter(l.id).associateBy { it.id }
            var n = 0
            val body = l.blocks.joinToString("\n") { b ->
                when (b) {
                    is LetterBlock.Text -> b.text
                    is LetterBlock.DrawingRef -> drawings[b.drawingId]?.let { d ->
                        n++
                        val path = "그림/${stamp}_${l.id.take(8)}_$n.png"
                        files += Item(path, d.pngAttachmentId)
                        "[$path]"
                    } ?: "[그림]"
                    is LetterBlock.Photo -> {
                        n++
                        val path = "사진/${stamp}_${l.id.take(8)}_$n.jpg"
                        files += Item(path, b.attachmentId)
                        "[$path]"
                    }
                }
            }
            listOfNotNull("$stamp ${day.time.toString().take(5)} · ${who[l.authorId] ?: "연인"}", l.title?.takeIf { it.isNotBlank() }, body).joinToString("\n")
        }
        val events = db.eventDao().all().filter { it.deletedAtMs == null }
        events.forEach { e -> e.toModel().photoIds.forEachIndexed { i, id -> files += Item("사진/만남_${e.start.take(10)}_${e.id.take(8)}_${i + 1}.jpg", id) } }
        return Triple(texts, files, events.size)
    }

    private suspend fun eventsText(): String {
        val who = names()
        return db.eventDao().all().filter { it.deletedAtMs == null }.map { it.toModel() }.sortedBy { it.start }.joinToString("\n") { e ->
            val range = if (e.end.date > e.start.date) "${e.start.date} – ${e.end.date}" else "${e.start.date}"
            listOfNotNull(
                "$range · ${e.type.name.lowercase()} · ${e.title}",
                e.memo.takeIf { it.isNotBlank() }?.let { "  메모: $it" },
                *e.dayLines.filterValues { it.text.isNotBlank() }.map { (id, n) -> "  ${who[id] ?: "연인"}: ${n.text}" }.toTypedArray(),
            ).joinToString("\n")
        }
    }

    private companion object {
        const val README = "꽃신에서 내보낸 우리 기록이에요.\n" +
            "편지.txt — 주고받은 편지(날짜 · 쓴 사람 · 제목 · 본문). 본문의 [그림/…] · [사진/…] 은 같은 폴더의 파일이에요.\n" +
            "일정 · 만남.txt — 캘린더 일정과 그날의 한 줄.\n" +
            "디자인된 책으로 남기려면 추억 › 책으로 보기 › PDF 받기를 써 주세요."
    }
}

/** 압축 없이(STORED) 담는 작은 ZIP 작성기. 사진 · PNG 는 이미 압축돼 있어 더 줄지 않는다. */
internal class ZipWriter {
    private val out = Bytes()
    private val central = Bytes()
    private var count = 0

    fun add(name: String, data: ByteArray) {
        val nameBytes = name.encodeToByteArray()
        val crc = crc32(data)
        val offset = out.size
        out.u32(0x04034b50); out.u16(20); out.u16(0x0800); out.u16(0); out.u16(0); out.u16(0x21)
        out.u32(crc); out.u32(data.size.toLong()); out.u32(data.size.toLong()); out.u16(nameBytes.size); out.u16(0)
        out.bytes(nameBytes); out.bytes(data)
        central.u32(0x02014b50); central.u16(20); central.u16(20); central.u16(0x0800); central.u16(0); central.u16(0); central.u16(0x21)
        central.u32(crc); central.u32(data.size.toLong()); central.u32(data.size.toLong()); central.u16(nameBytes.size)
        central.u16(0); central.u16(0); central.u16(0); central.u16(0); central.u32(0); central.u32(offset.toLong())
        central.bytes(nameBytes)
        count++
    }

    fun finish(): ByteArray {
        val start = out.size
        out.bytes(central.toByteArray())
        out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(count); out.u16(count)
        out.u32(central.size.toLong()); out.u32(start.toLong()); out.u16(0)
        return out.toByteArray()
    }

    private class Bytes {
        private var buf = ByteArray(1 shl 16)
        var size = 0
            private set

        private fun ensure(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }

        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun u16(v: Int) { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte() }
        fun u32(v: Long) { ensure(4); for (i in 0 until 4) buf[size++] = (v shr (8 * i)).toByte() }
        fun toByteArray() = buf.copyOf(size)
    }

    private companion object {
        val TABLE = LongArray(256) { n ->
            var c = n.toLong()
            repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
            c
        }

        fun crc32(data: ByteArray): Long {
            var c = 0xFFFFFFFFL
            for (b in data) c = TABLE[((c xor b.toLong()) and 0xFF).toInt()] xor (c ushr 8)
            return c xor 0xFFFFFFFFL
        }
    }
}
