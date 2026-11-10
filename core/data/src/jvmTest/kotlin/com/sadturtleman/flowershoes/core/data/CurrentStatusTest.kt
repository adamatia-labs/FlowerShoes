package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import com.sadturtleman.flowershoes.core.model.Status
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class CurrentStatusTest {
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val identity = object : Identity {
        override val userId = "me"
        override val deviceId = "device"
        override val keyEpoch = 0
    }
    private class TestClock(var time: Long) : Clock {
        override fun now() = Instant.fromEpochMilliseconds(time)
    }
    private val clock = TestClock(4_000)
    private val statuses = StatusRepository(db, SyncWriter(db, identity), identity, clock)

    @AfterTest fun close() = db.close()

    @Test fun `최신 상태가 만료되면 이전의 무기한 상태를 다시 보여 주지 않는다`() = runBlocking {
        db.statusDao().upsert(StatusEntity("old", "me", "공부 중", 0, 1_000, null, 1))
        db.statusDao().upsert(StatusEntity("new", "me", "당직 중", 0, 2_000, 3_000, 2))
        assertNull(statuses.observeLatest("me").first())
    }

    @Test fun `같은 시각에 바꾼 상태도 나중의 변경이 만료되면 이전 값을 살리지 않는다`() = runBlocking {
        db.statusDao().upsert(StatusEntity("old", "me", "공부 중", 0, 1_000, null, 1))
        db.statusDao().upsert(StatusEntity("new", "me", "당직 중", 0, 1_000, 3_000, 2))
        assertNull(statuses.observeLatest("me").first())
    }

    @Test fun `상태 지우기도 새 변경으로 남겨 이전 상태를 숨긴다`() = runBlocking {
        statuses.set("공부 중", 0)
        assertEquals("공부 중", statuses.observeLatest("me").first()?.label)
        statuses.clear()
        assertNull(statuses.observeLatest("me").first())
        val latest = db.statusDao().all().maxBy { it.lamport }
        assertEquals("", latest.label)
        assertEquals(clock.time, latest.expiresAtMs)
        assertEquals(2, db.statusDao().all().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `DB 변경 없이 시간이 지나도 구독 중인 상태가 만료된다`() = runTest {
        db.statusDao().upsert(StatusEntity("current", "me", "당직 중", 0, 1_000, 5_000, 1))
        val changes = Channel<Status?>(Channel.UNLIMITED)
        backgroundScope.launch { statuses.observeLatest("me").collect { changes.send(it) } }
        runCurrent()
        assertEquals("당직 중", withContext(Dispatchers.IO) { withTimeout(5_000) { changes.receive() } }?.label)
        clock.time = 6_000
        advanceTimeBy(30_000); runCurrent()
        assertNull(withContext(Dispatchers.IO) { withTimeout(5_000) { changes.receive() } })
    }

    @Test fun `기기 시각이 뒤로 바뀌어도 나중에 저장하고 지운 상태가 우선한다`() = runBlocking {
        statuses.set("공부 중", 0)
        clock.time = 3_000
        statuses.set("당직 중", 0)
        assertEquals("당직 중", statuses.observeLatest("me").first()?.label)
        clock.time = 2_000
        statuses.clear()
        assertNull(statuses.observeLatest("me").first())
        clock.time = 0 // 수신 기기의 시각이 더 느려도 삭제는 즉시 적용한다.
        assertNull(statuses.observeLatest("me").first())
    }
}
