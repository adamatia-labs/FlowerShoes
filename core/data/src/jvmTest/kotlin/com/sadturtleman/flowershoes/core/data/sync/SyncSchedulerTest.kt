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
        db.coupleDao().upsert(CoupleEntity("couple", "me", "partner", 1, 0, null, 0))
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
