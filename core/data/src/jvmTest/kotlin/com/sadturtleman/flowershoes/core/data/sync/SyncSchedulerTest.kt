package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import java.util.concurrent.atomic.AtomicInteger

class SyncSchedulerTest {
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "scheduler-${Uuid.random()}"
    private val cloud = InMemoryCloud()
    private val vault = InMemoryKeyVault()
    private val identity = object : Identity {
        override val userId = "me"
        override val deviceId = "device"
        override val keyEpoch = 1
    }
    private val writer = SyncWriter(db, identity)

    @BeforeTest fun setup() = runBlocking {
        Sodium.init()
        val keys = DeviceKeys.generate()
        vault.putDeviceKeys(keys)
        vault.putCoupleKey(CoupleKey.generate(1))
        db.coupleDao().upsert(CoupleEntity("couple", "me", "partner", 1, 0))
        db.deviceDao().upsert(DeviceEntity("partner-device", "partner", "partner", keys.signPub, keys.boxPub, "ACTIVE", 0, null))
    }
    @AfterTest fun cleanup() { db.close(); FileSystem.SYSTEM.deleteRecursively(root, mustExist = false) }
    private fun client(mailbox: Mailbox) = SyncClient(db, identity, vault, mailbox, cloud, AttachmentStore(db, root), InboxApplier(db), writer)

    @Test fun `new database write wakes an already idle scheduler`() = runBlocking {
        val scheduler = SyncScheduler(db, client(cloud), period = 1.hours)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scheduler.start(scope)
        try {
            withTimeout(5000) { while (scheduler.lastReport == null) delay(10) }
            delay(100)
            MissYouRepository(db, writer, identity).send()
            withTimeout(5000) { while (cloud.inboxSize("couple", "partner-device") != 1) delay(10) }
        } finally { job.cancelAndJoin(); scope.cancel() }
    }

    @Test fun `failed sync retries and sends pending operation without app reentry`() = runBlocking {
        val attempts = AtomicInteger()
        val mailbox = object : Mailbox by cloud {
            override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
                if (attempts.incrementAndGet() <= 2) error("offline")
                return cloud.fetch(coupleId, myDevice)
            }
        }
        MissYouRepository(db, writer, identity).send()
        val scheduler = SyncScheduler(db, client(mailbox), period = 1.hours, retryBase = 20.milliseconds)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scheduler.start(scope)
        try {
            withTimeout(5000) { while (scheduler.lastReport?.sent != 1) delay(10) }
            assertTrue(attempts.get() >= 3)
            assertEquals(1, cloud.inboxSize("couple", "partner-device"))
        } finally { job.cancelAndJoin(); scope.cancel() }
    }

    @Test fun `successful upload keeps retrying until partner acknowledges it`() = runBlocking {
        val polls = AtomicInteger()
        val mailbox = object : Mailbox by cloud {
            override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
                polls.incrementAndGet()
                return emptyList()
            }
        }
        MissYouRepository(db, writer, identity).send()
        val scheduler = SyncScheduler(db, client(mailbox), period = 1.hours, retryBase = 20.milliseconds)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scheduler.start(scope)
        try {
            withTimeout(5000) { while (polls.get() < 3) delay(10) }
            assertEquals(1, cloud.inboxSize("couple", "partner-device"))
            assertTrue(db.syncDao().streamsWithLog().isNotEmpty())
        } finally { job.cancelAndJoin(); scope.cancel() }
    }

    @Test fun `stalled transport is reconnected after timeout and pending data is sent`() = runBlocking {
        val resets = AtomicInteger()
        val mailbox = object : Mailbox by cloud {
            override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
                if (resets.get() == 0) awaitCancellation()
                return emptyList()
            }
            override suspend fun reconnect() { resets.incrementAndGet() }
        }
        MissYouRepository(db, writer, identity).send()
        val scheduler = SyncScheduler(db, client(mailbox), period = 1.hours, retryBase = 20.milliseconds, syncTimeout = 100.milliseconds)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scheduler.start(scope)
        try {
            withTimeout(5000) { while (cloud.inboxSize("couple", "partner-device") != 1) delay(10) }
            assertTrue(resets.get() >= 1)
        } finally { job.cancelAndJoin(); scope.cancel() }
    }

    // ---- 서버 오류 화면(#72) ----

    /** 서버 쪽 실패라고 우편함이 코드를 준 것만 화면에 오른다. */
    private fun serverDown(failing: () -> Boolean = { true }) = object : Mailbox by cloud {
        override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
            if (failing()) throw IllegalStateException("UNAVAILABLE")
            return cloud.fetch(coupleId, myDevice)
        }
        override fun errorCode(error: Throwable): String? = error.message
    }

    private class MovableClock(var now: kotlin.time.Instant) : kotlin.time.Clock {
        override fun now() = now
    }

    @Test fun `failed sync is surfaced with the transport error code and the time it started`() = runBlocking {
        val clock = MovableClock(kotlin.time.Instant.fromEpochMilliseconds(1_000))
        val scheduler = SyncScheduler(db, client(serverDown()), period = 1.hours, clock = clock)

        scheduler.runOnce()

        assertEquals(SyncFailure(code = "UNAVAILABLE", since = clock.now, last = clock.now), scheduler.failure.value)
    }

    /** 권한 창 · 복호화 같은 실패는 서버가 답하지 않는 것이 아니다 — 다음 차례에 조용히 다시 돈다. */
    @Test fun `failures the transport does not call a server fault are not surfaced`() = runBlocking {
        val mailbox = object : Mailbox by cloud {
            override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> = error("PERMISSION_DENIED")
        }
        val scheduler = SyncScheduler(db, client(mailbox), period = 1.hours)

        scheduler.runOnce()

        assertNull(scheduler.failure.value)
    }

    /** 화면은 한 번 닫으면 같은 실패가 이어지는 동안 다시 뜨지 않는다 — 그러려면 시작 시각이 그대로여야 한다. */
    @Test fun `repeated failures keep the time the failure started and move the latest`() = runBlocking {
        val clock = MovableClock(kotlin.time.Instant.fromEpochMilliseconds(1_000))
        val scheduler = SyncScheduler(db, client(serverDown()), period = 1.hours, clock = clock)

        scheduler.runOnce()
        clock.now = kotlin.time.Instant.fromEpochMilliseconds(9_000)
        scheduler.runOnce()

        assertEquals(kotlin.time.Instant.fromEpochMilliseconds(1_000), scheduler.failure.value?.since)
        assertEquals(kotlin.time.Instant.fromEpochMilliseconds(9_000), scheduler.failure.value?.last)
    }

    @Test fun `a successful sync clears the failure`() = runBlocking {
        val failing = java.util.concurrent.atomic.AtomicBoolean(true)
        val scheduler = SyncScheduler(db, client(serverDown { failing.get() }), period = 1.hours)
        scheduler.runOnce()
        assertNotNull(scheduler.failure.value)

        failing.set(false)
        scheduler.runOnce()

        assertNull(scheduler.failure.value)
    }

    @Test fun `terminated listener is resubscribed and still wakes synchronization`() = runBlocking {
        val subscriptions = AtomicInteger()
        val received = AtomicInteger()
        val mailbox = object : Mailbox by cloud {
            override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
                received.incrementAndGet()
                return emptyList()
            }
        }
        val bell = flow {
            if (subscriptions.incrementAndGet() == 1) error("temporary permission window")
            emit(Unit)
            awaitCancellation()
        }
        val scheduler = SyncScheduler(db, client(mailbox), doorbell = bell, period = 1.hours, retryBase = 20.milliseconds)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scheduler.start(scope)
        try {
            withTimeout(5000) { while (received.get() < 2) delay(10) }
            assertEquals(2, subscriptions.get())
        } finally { job.cancelAndJoin(); scope.cancel() }
    }
}
