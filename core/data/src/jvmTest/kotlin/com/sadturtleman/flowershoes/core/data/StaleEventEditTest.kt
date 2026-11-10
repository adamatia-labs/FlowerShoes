package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class StaleEventEditTest {
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val identity = object : Identity {
        override val userId = "owner"
        override val deviceId = "device"
        override val keyEpoch = 1
    }
    private val events = EventRepository(db, SyncWriter(db, identity), identity)

    @AfterTest fun close() = db.close()

    private fun event(id: String) = Event(
        id, EventType.ANNIVERSARY, "이전 관계의 날",
        LocalDateTime(2025, 1, 1, 0, 0), LocalDateTime(2025, 1, 1, 23, 59),
        false, Instant.fromEpochMilliseconds(0), 0, "",
    )

    private suspend fun archiveAnniversary(): Event {
        events.save(event(Event.COUPLE_ANNIVERSARY_ID))
        val original = assertNotNull(events.get(Event.COUPLE_ANNIVERSARY_ID))
        val oldCouple = CoupleEntity("old-couple", "owner", "old-partner", 1, 100)
        db.coupleDao().upsert(oldCouple)
        RelationshipArchive(db).archive(oldCouple)
        return original
    }

    private suspend fun assertRejected(original: Event) {
        val log = db.syncDao().log("device:app").map { it.opId to it.payload.toList() }
        val seq = db.syncDao().nextSeq("device:app")
        val lamport = db.syncDao().meta(SyncWriter.LAMPORT)
        val current = db.eventDao().get(original.id)

        assertFailsWith<StaleRecordException> {
            events.save(original.copy(title = "오래된 편집 내용"), expectedOriginal = original)
        }

        assertEquals(current, db.eventDao().get(original.id))
        assertEquals(log, db.syncDao().log("device:app").map { it.opId to it.payload.toList() })
        assertEquals(seq, db.syncDao().nextSeq("device:app"))
        assertEquals(lamport, db.syncDao().meta(SyncWriter.LAMPORT))
    }

    @Test fun `보관용 ID로 이동한 기념일의 오래된 편집은 새 행을 만들지 않는다`() = runTest {
        val original = archiveAnniversary()
        val archiveId = "archived:old-couple:${original.id}"
        val archived = assertNotNull(db.eventDao().get(archiveId))

        assertRejected(original)

        assertNull(events.get(original.id))
        assertEquals(archived, db.eventDao().get(archiveId))
    }

    @Test fun `삭제한 일정의 오래된 편집은 삭제를 되돌리지 않는다`() = runTest {
        events.save(event("custom-anniversary"))
        val original = assertNotNull(events.get("custom-anniversary"))
        events.delete(original.id)
        assertNotNull(events.get(original.id)?.deletedAt)

        assertRejected(original)
    }

    @Test fun `새 관계가 같은 기념일 ID를 재사용해도 이전 관계의 편집은 거부한다`() = runTest {
        val original = archiveAnniversary()
        db.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
        events.save(event(original.id).copy(title = "새 관계의 날"))

        assertRejected(original)

        assertEquals("새 관계의 날", events.get(original.id)?.title)
    }

    @Test fun `원본이 그대로인 편집과 새 작성은 저장하고 동기화한다`() = runTest {
        events.save(event("new-anniversary"))
        val original = assertNotNull(events.get("new-anniversary"))
        val before = db.syncDao().log("device:app").size

        events.save(original.copy(title = "바꾼 제목"), expectedOriginal = original)

        assertEquals("바꾼 제목", events.get(original.id)?.title)
        assertEquals(before + 1, db.syncDao().log("device:app").size)
    }
}
