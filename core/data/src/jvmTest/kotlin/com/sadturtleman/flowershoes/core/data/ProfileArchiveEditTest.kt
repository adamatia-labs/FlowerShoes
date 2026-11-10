package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.DayLine
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProfileArchiveEditTest {
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val identity = object : Identity {
        override val userId = "owner"
        override val deviceId = "device"
        override val keyEpoch = 1
    }
    private val writer = SyncWriter(db, identity)
    private val profile = ProfileRepository(db, writer, identity)
    private val events = EventRepository(db, writer, identity)
    private val oldCouple = CoupleEntity("old-couple", "owner", "old-partner", 1, 100)
    private val date = LocalDate(2025, 1, 1)
    private val nextDate = LocalDate(2026, 2, 2)

    @AfterTest fun close() = db.close()

    private fun anniversary() = Event(
        Event.COUPLE_ANNIVERSARY_ID, EventType.ANNIVERSARY, "둘만의 이름",
        date.atTime(0, 0), date.atTime(23, 59), false, Instant.fromEpochMilliseconds(0), 1, "device",
        memo = "이전 관계의 메모", dayLines = mapOf("old-partner" to DayLine("이전 관계의 한 줄", 10)),
    )

    @Test fun `현재 기념일 날짜를 바꾸면 제목과 메모와 한 줄은 보존한다`() = runTest {
        events.save(anniversary())
        val original = assertNotNull(events.get(Event.COUPLE_ANNIVERSARY_ID))

        assertEquals(original, profile.observeAnniversaryRecord().first())
        profile.setAnniversary(nextDate, expectedOriginal = original)

        val edited = assertNotNull(events.get(original.id))
        assertEquals(nextDate, edited.start.date)
        assertEquals(original.title, edited.title)
        assertEquals(original.memo, edited.memo)
        assertEquals(original.dayLines, edited.dayLines)
    }

    @Test fun `첫 이관 중 기념일 날짜 변경은 보관할 행의 내용을 복제하지 않는다`() = runTest {
        // 구버전 active DB: 보관 표식 없이 이전 관계 정보가 고정 ID에 남아 있다.
        db.coupleDao().upsert(oldCouple)
        db.eventDao().upsert(anniversary().toEntity())

        profile.setAnniversary(nextDate)

        val current = assertNotNull(events.get(Event.COUPLE_ANNIVERSARY_ID))
        assertEquals("사귄 날", current.title)
        assertEquals(nextDate, current.start.date)
        assertEquals("", current.memo)
        assertTrue(current.dayLines.isEmpty())
        val archived = assertNotNull(events.get("archived:${oldCouple.id}:${current.id}"))
        assertEquals(anniversary().memo, archived.memo)
        assertEquals(anniversary().dayLines, archived.dayLines)
        val payload = assertIs<OpPayload.EventPut>(OpCodec.decode(db.syncDao().log("device:app").single().payload))
        assertEquals("", payload.event.memo)
        assertTrue(payload.event.dayLines.isEmpty())
    }

    @Test fun `첫 이관 중 기념일 지우기는 보관 내용을 새 삭제 행으로 복제하지 않는다`() = runTest {
        db.coupleDao().upsert(oldCouple)
        db.eventDao().upsert(anniversary().toEntity())

        profile.setAnniversary(null)

        assertNull(events.get(Event.COUPLE_ANNIVERSARY_ID))
        assertNotNull(events.get("archived:${oldCouple.id}:${Event.COUPLE_ANNIVERSARY_ID}"))
        assertTrue(db.syncDao().log("device:app").isEmpty())
    }

    @Test fun `기념일 선택기가 들고 있던 원본은 해제 뒤 새 날짜로 복제하거나 덮어쓰지 않는다`() = runTest {
        events.save(anniversary())
        val original = assertNotNull(profile.observeAnniversaryRecord().first())
        db.coupleDao().upsert(oldCouple)
        RelationshipArchive(db).archive(oldCouple)
        val before = db.syncDao().log("device:app").map { it.opId }
        assertFailsWith<StaleRecordException> { profile.setAnniversary(nextDate, expectedOriginal = original) }
        assertNull(events.get(original.id))
        assertEquals(before, db.syncDao().log("device:app").map { it.opId })

        db.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
        profile.setAnniversary(nextDate)
        val replacement = assertNotNull(profile.observeAnniversaryRecord().first())
        val afterNew = db.syncDao().log("device:app").map { it.opId }
        assertFailsWith<StaleRecordException> { profile.setAnniversary(date, expectedOriginal = original) }
        assertEquals(replacement, profile.observeAnniversaryRecord().first())
        assertEquals(afterNew, db.syncDao().log("device:app").map { it.opId })
    }

    private suspend fun archivedService(): Service {
        profile.saveService(Service(PENDING_SOLDIER, Branch.ARMY, date, 1))
        val original = assertNotNull(db.serviceDao().get(PENDING_SOLDIER)).toModel()
        db.coupleDao().upsert(oldCouple)
        RelationshipArchive(db).archive(oldCouple)
        return original
    }

    private suspend fun rejectServiceEdit(original: Service) {
        val current = db.serviceDao().get(PENDING_SOLDIER)
        val log = db.syncDao().log("device:app").map { it.opId to it.payload.toList() }
        val seq = db.syncDao().nextSeq("device:app")
        val lamport = db.syncDao().meta(SyncWriter.LAMPORT)

        assertFailsWith<StaleRecordException> {
            profile.saveService(original.copy(enlistDate = nextDate), expectedOriginal = original)
        }

        assertEquals(current, db.serviceDao().get(PENDING_SOLDIER))
        assertEquals(log, db.syncDao().log("device:app").map { it.opId to it.payload.toList() })
        assertEquals(seq, db.syncDao().nextSeq("device:app"))
        assertEquals(lamport, db.syncDao().meta(SyncWriter.LAMPORT))
    }

    @Test fun `보관용 ID로 이동한 복무 정보는 오래된 편집으로 되살리지 않는다`() = runTest {
        val original = archivedService()

        rejectServiceEdit(original)

        assertNull(db.serviceDao().get(PENDING_SOLDIER))
        assertEquals(original.copy(userId = "archived:${oldCouple.id}:$PENDING_SOLDIER"),
            db.serviceDao().get("archived:${oldCouple.id}:$PENDING_SOLDIER")?.toModel())
    }

    @Test fun `새 관계의 복무 정보는 이전 관계의 오래된 편집으로 덮어쓰지 않는다`() = runTest {
        val original = archivedService()
        db.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
        profile.saveService(Service(PENDING_SOLDIER, Branch.NAVY, nextDate, 2))

        rejectServiceEdit(original)

        assertEquals(Branch.NAVY, db.serviceDao().get(PENDING_SOLDIER)?.toModel()?.branch)
    }

    @Test fun `복무 정보 새 작성과 원본이 그대로인 편집은 정상 저장한다`() = runTest {
        profile.saveService(Service(PENDING_SOLDIER, Branch.ARMY, date, 1))
        val original = assertNotNull(db.serviceDao().get(PENDING_SOLDIER)).toModel()

        profile.saveService(original.copy(branch = Branch.NAVY), expectedOriginal = original)

        assertEquals(Branch.NAVY, db.serviceDao().get(PENDING_SOLDIER)?.toModel()?.branch)
        assertEquals(2, db.syncDao().log("device:app").size)
    }
}
