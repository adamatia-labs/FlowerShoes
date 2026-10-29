package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.BookCover
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/** 추억 책 표지: 산 사람이 고르고 두 사람이 같은 표지를 본다(#23 결정 1). */
class BookCoverSyncTest {

    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-20T01:00:00Z")
    }

    private inner class Phone(user: String, device: String) {
        val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()
        val identity = object : Identity {
            override val userId = user
            override val deviceId = device
            override val keyEpoch = 1
        }
        val covers = BookCoverRepository(db, SyncWriter(db, identity, clock), identity)
        val inbox = InboxApplier(db, clock)
        val stream = StreamId(device, StreamId.Channel.APP)
        var delivered = 0L
    }

    private val jisu = Phone("jisu", "dev-a")
    private val minjun = Phone("minjun", "dev-b")

    @AfterTest
    fun close() {
        jisu.db.close()
        minjun.db.close()
    }

    /** 아직 안 보낸 Op 만 보낸다. */
    private suspend fun deliver(from: Phone, to: Phone) =
        from.db.syncDao().log(from.stream.wire).filter { it.seq > from.delivered }.forEach {
            to.inbox.apply(ReceivedOp(from.stream, it.seq, it.opId, it.lamport, it.payload))
            from.delivered = it.seq
        }

    @Test
    fun `고르기 전에는 군사우편`() = runTest {
        assertEquals(BookCover.MILITARY, jisu.covers.observe().first())
    }

    @Test
    fun `한 사람이 고른 표지가 상대 폰에도 간다`() = runTest {
        jisu.covers.choose(BookCover.FLOWER)
        deliver(jisu, minjun)
        assertEquals(BookCover.FLOWER, jisu.covers.observe().first())
        assertEquals(BookCover.FLOWER, minjun.covers.observe().first())
    }

    @Test
    fun `나중에 바꾼 쪽이 이긴다`() = runTest {
        jisu.covers.choose(BookCover.FLOWER)
        deliver(jisu, minjun)
        minjun.covers.choose(BookCover.MILITARY)
        deliver(minjun, jisu)
        // 늦게 도착한 옛 Op 은 지지 않는다.
        jisu.covers.choose(BookCover.FLOWER)
        minjun.covers.choose(BookCover.MILITARY)
        deliver(jisu, minjun)
        deliver(minjun, jisu)
        assertEquals(jisu.covers.observe().first(), minjun.covers.observe().first())
    }

    @Test
    fun `Op 본문은 bookcover 종류로 표지를 싣는다`() = runTest {
        jisu.covers.choose(BookCover.FLOWER)
        val payload = jisu.db.syncDao().log(jisu.stream.wire).last().payload.decodeToString()
        assertEquals(true, "\"t\":\"bookcover\"" in payload && "\"FLOWER\"" in payload, payload)
    }
}
