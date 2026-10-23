package com.sadturtleman.flowershoes.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseTest {

    private val db = inMemoryDatabaseBuilder().buildKkotsin()

    @AfterTest
    fun close() = db.close()

    private fun letter(id: String, author: String, visibleAt: Long) =
        LetterEntity(id, author, null, "[]", "plain", visibleAt, null, 0)

    private fun drawing(id: String, letterId: String, author: String) = DrawingEntity(
        id, "c", letterId, 0, author, "SQUARE", null, "png-$id", "thumb", null,
        replayEnabled = true, replayDurationMs = 6000, showOnWidget = true, sentAtMs = 0, receivedAtMs = null, deletedAtMs = null,
    )

    @Test
    fun `예약 편지는 공개 시각 전에는 편지함과 위젯에 보이지 않는다`() = runTest {
        db.letterDao().upsert(letter("open", "partner", visibleAt = 1_000))
        db.letterDao().upsert(letter("later", "partner", visibleAt = 9_000))
        db.drawingDao().upsert(drawing("d1", "open", "partner"))
        db.drawingDao().upsert(drawing("d2", "later", "partner"))

        assertEquals(listOf("open"), db.letterDao().observeVisible(nowMs = 5_000).first().map { it.id })
        assertEquals("d1", db.drawingDao().observeForWidget("me", nowMs = 5_000).first()?.id)
        assertEquals("d2", db.drawingDao().observeForWidget("me", nowMs = 9_000).first()?.id)
        assertEquals(listOf("later"), db.letterDao().upcomingReveals("me", 5_000, 50).map { it.id })
    }

    @Test
    fun `읽음은 먼저 온 값을 남긴다`() = runTest {
        val dao = db.receiptDao()
        dao.insertFirst(ReceiptEntity("LETTER", "l1", "me", atMs = 100))
        dao.insertFirst(ReceiptEntity("LETTER", "l1", "me", atMs = 999))
        assertEquals(100, dao.get("LETTER", "l1", "me")?.atMs)
    }

    @Test
    fun `보낸 Op 로그는 확인받은 번호까지만 지운다`() = runTest {
        val dao = db.syncDao()
        (1L..5L).forEach { dao.appendOp(SyncOpEntity("a:app", it, "op$it", "missyou", "missyou", "m$it", ByteArray(0), it, 1, 0)) }
        dao.deleteThrough("a:app", 3)
        assertEquals(listOf(4L, 5L), dao.log("a:app").map { it.seq })
        assertEquals(listOf(5L), dao.opsAfter("a:app", 4).map { it.seq })
    }

    @Test
    fun `지운 일정은 목록에서 빠지고 오래된 tombstone 은 정리된다`() = runTest {
        val dao = db.eventDao()
        fun event(id: String, deletedAt: Long?) = EventEntity(
            id, "VISIT", "면회", "2026-11-01T10:00", "2026-11-01T15:00", true, 0, 1, "dev", deletedAt,
        )
        dao.upsert(event("keep", null))
        dao.upsert(event("gone", deletedAt = 10))
        assertEquals(listOf("keep"), dao.observeRange("2026-11-01T00:00", "2026-11-30T23:59").first().map { it.id })
        dao.purgeTombstones(beforeMs = 100)
        assertNull(dao.get("gone"))
    }
}
