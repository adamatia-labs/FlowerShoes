package com.sadturtleman.flowershoes.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SyncEngineTest {

    private val stream = StreamId("dev-a", StreamId.Channel.APP)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private fun op(seq: Long, createdAt: Instant = now) = SyncOp(
        stream = stream, seq = seq, opId = "op-$seq", kind = OpKind.MISS_YOU,
        entity = "missyou", entityId = "m$seq", payload = ByteArray(0),
        lamport = seq, keyEpoch = 1, createdAt = createdAt,
    )

    private fun log(from: Long, to: Long, createdAt: Instant = now) =
        SendLog(stream, (from..to).map { op(it, createdAt) }, nextSeq = to + 1)

    @Test
    fun `스트림 이름은 기기와 채널로 이루어진다`() {
        assertEquals("dev-a:app", stream.wire)
        assertEquals(StreamId("x:y", StreamId.Channel.WIDGET), StreamId.parse("x:y:widget"))
    }

    @Test
    fun `순서대로 오면 커서가 바로 오른다`() {
        val s = ReceiveState().receive(1).receive(2).receive(3)
        assertEquals(ReceiveState(3), s)
    }

    @Test
    fun `앞서 온 번호는 pending 에 두었다가 빈 번호가 오면 한 번에 이어진다`() {
        val s = ReceiveState().receive(1).receive(3).receive(5)
        assertEquals(1, s.contiguousSeq)
        assertEquals(listOf(2L, 4L), s.missing())
        val filled = s.receive(2).receive(4)
        assertEquals(ReceiveState(5), filled)
    }

    @Test
    fun `같은 번호가 다시 오면 중복이다`() {
        val s = ReceiveState().receive(1).receive(3)
        assertTrue(s.isDuplicate(1))
        assertTrue(s.isDuplicate(3))
        assertFalse(s.isDuplicate(2))
        assertEquals(s, s.receive(3))
    }

    @Test
    fun `스냅샷은 커서를 한 번에 옮기고 그 아래 pending 을 버린다`() {
        val s = ReceiveState(contiguousSeq = 2, pending = setOf(5, 9, 11))
        val after = s.applySnapshot(coversThrough = 10)
        assertEquals(ReceiveState(11), after)
        assertEquals(after, after.applySnapshot(4))
    }

    @Test
    fun `확인받은 Op 만 로그에서 지운다`() {
        val l = log(1, 10).acknowledge(7)
        assertEquals(8, l.floor)
        assertEquals(10, l.lastSeq)
        val empty = l.acknowledge(10)
        assertEquals(11, empty.floor)
    }

    @Test
    fun `로그가 넘치면 오래된 것부터 지운다`() {
        val l = log(1, 10).cap(maxSize = 4)
        assertEquals(7, l.floor)
        assertEquals(4, l.ops.size)
    }

    @Test
    fun `missing 에 적힌 번호는 바로 다시 보낸다`() {
        val plan = ResendPlanner().plan(log(1, 10), peerCursor = 5, missing = listOf(7), now = now)
        assertEquals(listOf(7L), assertIs<ResendPlan.Resend>(plan).ops.map { it.seq })
    }

    @Test
    fun `커서 뒤라도 3일이 안 지난 Op 은 다시 보내지 않는다`() {
        val fresh = ResendPlanner().plan(log(1, 10, createdAt = now - 2.days), 5, emptyList(), now)
        assertEquals(ResendPlan.Nothing, fresh)
        val stale = ResendPlanner().plan(log(1, 10, createdAt = now - 3.days - 1.hours), 5, emptyList(), now)
        assertEquals((6L..10L).toList(), assertIs<ResendPlan.Resend>(stale).ops.map { it.seq })
    }

    @Test
    fun `floor 앞을 달라고 하면 스냅샷으로 답한다`() {
        val trimmed = log(1, 10).acknowledge(6) // floor = 7
        assertEquals(ResendPlan.Snapshot(10), ResendPlanner().plan(trimmed, peerCursor = 3, missing = emptyList(), now = now))
        assertEquals(ResendPlan.Nothing, ResendPlanner().plan(trimmed, peerCursor = 6, missing = emptyList(), now = now))
    }

    @Test
    fun `lamport 가 크면 이기고 같으면 삭제가 이긴다`() {
        val local = Versioned(5, "a")
        assertTrue(Lww.incomingWins(local, Versioned(6, "a")))
        assertFalse(Lww.incomingWins(local, Versioned(4, "b")))
        assertTrue(Lww.incomingWins(local, Versioned(5, "a", deleted = true)))
        assertFalse(Lww.incomingWins(Versioned(5, "a", deleted = true), Versioned(5, "z")))
        // 둘 다 같은 조건이면 기기 id 로 정해 양쪽이 같은 답을 낸다.
        assertTrue(Lww.incomingWins(Versioned(5, "a"), Versioned(5, "b")))
        assertFalse(Lww.incomingWins(Versioned(5, "b"), Versioned(5, "a")))
    }

    @Test
    fun `lamport 시계는 받은 값보다 크게 맞춘다`() {
        val clock = LamportClock()
        assertEquals(1, clock.tick())
        clock.observe(10)
        assertEquals(11, clock.tick())
        clock.observe(3)
        assertEquals(12, clock.tick())
    }

    @Test
    fun `폐기된 기기의 cutoff 를 넘는 Op 은 버린다`() {
        val filter = RevocationFilter(listOf(RevokedDevice("dev-a", now, mapOf(stream to 5))))
        assertTrue(filter.accepts(op(5)))
        assertFalse(filter.accepts(op(6)))
        // cutoff 가 적히지 않은 스트림(예: 폐기 뒤 생긴 위젯 스트림)은 전부 버린다.
        assertFalse(filter.accepts(op(1).copy(stream = StreamId("dev-a", StreamId.Channel.WIDGET))))
        assertTrue(filter.accepts(op(99).copy(stream = StreamId("dev-b", StreamId.Channel.APP))))
    }
}
