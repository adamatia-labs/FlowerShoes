package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.sync.OpKind
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MissYouSenderIntegrationTest {
    private val identity = object : Identity {
        override val userId = "owner"
        override val deviceId = "device"
        override val keyEpoch = 1
    }

    @Test
    fun `실제 저장소에 보고싶어와 동기화 작업이 함께 기록된 뒤 성공을 표시한다`() = runTest {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val repository = MissYouRepository(db, SyncWriter(db, identity), identity)
            val sender = MissYouSender(this, repository::send)
            sender.send()
            assertEquals(MissYouSendStatus.SAVED, sender.status.first { it == MissYouSendStatus.SAVED || it == MissYouSendStatus.FAILED })
            assertEquals(1, repository.observeTodayFrom(identity.userId).first())
            val stream = StreamId(identity.deviceId, StreamId.Channel.APP)
            assertEquals(listOf(OpKind.MISS_YOU.wire), db.syncDao().log(stream.wire).map { it.kind })
            advanceTimeBy(2_000)
            runCurrent()
        } finally {
            db.close()
        }
    }

    @Test
    fun `트랜잭션이 실패하면 성공 대신 오류를 표시하고 일부 기록을 남기지 않는다`() = runTest {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val broken = object : Identity {
                override val userId = "owner"
                override val deviceId: String get() = error("device identity unavailable")
                override val keyEpoch = 1
            }
            val repository = MissYouRepository(db, SyncWriter(db, broken), broken)
            val sender = MissYouSender(this, repository::send)
            sender.send()
            assertEquals(MissYouSendStatus.FAILED, sender.status.first { it == MissYouSendStatus.SAVED || it == MissYouSendStatus.FAILED })
            assertEquals(0, repository.observeTodayFrom(broken.userId).first())
            advanceTimeBy(2_000)
            runCurrent()
        } finally {
            db.close()
        }
    }
}
