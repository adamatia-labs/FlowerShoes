package com.sadturtleman.flowershoes.core.logging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기록 회귀 테스트.
 *
 * 기록기는 아무것도 돌려주지 않으므로 "언제 나갔는가" 를 밖에서 알 방법이 없다.
 * 그래서 기록기에 테스트 스케줄러를 물리고 [advanceUntilIdle] 로 큐를 비운 다음에 본다 —
 * 실제 시간을 두고 기다리면 느린 기계에서 아직 처리되지 않은 상태를 결과로 읽는다.
 *
 * 가짜 전송기는 매번 [yield] 해, 전송이 왕복하는 사이에 다음 건이 끼어들 틈을 일부러 만든다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BizLoggerImplTest {

    @Test
    fun `기록한 건이 그 자리에서 나간다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        logger.record(VIEW, open(layout = "1"))
        advanceUntilIdle()

        val log = shooter.received.single()
        assertEquals(VIEW, log.viewName)
        assertEquals(BizEvent.LayoutToggle("1"), log.event)
        assertEquals("1", log.event.parameters["layout"])
    }

    /** 전송이 중간에 멈추므로, 순서를 묶어 두지 않으면 나중 건이 앞질러 나간다. */
    @Test
    fun `여러 건을 기록하면 일어난 순서대로 나간다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        listOf("1", "2", "3").forEach { logger.record(VIEW, open(layout = it)) }
        logger.record(VIEW, BizEvent.AbExposed(experiment = "exp", variant = "B"))
        advanceUntilIdle()

        assertEquals(listOf("1", "2", "3"), shooter.received.take(3).map { it.event.parameters["layout"] })
        assertEquals(BizEvent.AbExposed("exp", variant = "B"), shooter.received.last().event)
    }

    /** 같은 행동이라도 어느 화면에서 일어났는지로 갈린다 — 같은 보기 전환이 여러 화면에서 일어날 수 있다. */
    @Test
    fun `같은 이벤트라도 화면 이름이 함께 나간다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        logger.record("/home", open())
        logger.record("/settings", open())
        advanceUntilIdle()

        assertEquals(listOf("/home", "/settings"), shooter.received.map { it.viewName })
        assertEquals(listOf(open(), open()), shooter.received.map { it.event })
    }

    @Test
    fun `부른 쪽이 값을 바꿔도 나간 것은 그대로다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        val assignments = mutableMapOf("new_onboarding" to "true")
        logger.record(VIEW, BizEvent.FlagsResolved(environment = "dev", assignments = assignments))
        // 전송이 IO 로 넘어가 나중에 도는 사이 부른 쪽이 같은 맵을 다시 쓴다.
        assignments["home_page_size"] = "5"
        advanceUntilIdle()

        assertEquals(
            mapOf("environment" to "dev", "flag_new_onboarding" to "true"),
            shooter.received.single().event.parameters,
        )
    }

    @Test
    fun `한 실행 동안은 같은 UUID 를 달고 makeUUID 뒤에는 새 UUID 다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        logger.record(VIEW, open())
        logger.record(VIEW, open())
        val newUuid = logger.makeUUID()
        logger.record(VIEW, open())
        advanceUntilIdle()

        val uuids = shooter.received.map { it.userUuid }
        assertEquals(uuids[0], uuids[1])
        assertNotEquals(uuids[0], uuids[2])
        assertEquals(newUuid, uuids[2])
    }

    /** UUID 는 기록 시점에 붙는다 — 전송이 도는 사이에 사용자가 바뀌어도 원래 주인을 단다. */
    @Test
    fun `전송 중에 사용자가 바뀌어도 앞의 기록은 원래 주인을 단다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        logger.record(VIEW, open(layout = "먼저"))
        val newUuid = logger.makeUUID()
        advanceUntilIdle()

        assertNotEquals(newUuid, shooter.received.single().userUuid)
    }

    @Test
    fun `init 전에 기록한 것은 버린다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)

        logger.record(VIEW, open())
        advanceUntilIdle()

        assertTrue(shooter.received.isEmpty())
    }

    @Test
    fun `destroy 는 얹혀 있던 전송을 끝내고 접는다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        listOf("1", "2", "3").forEach { logger.record(VIEW, open(layout = it)) }
        // 셋 다 아직 전송 중이다. 여기서 스코프를 먼저 접으면 남은 것이 취소된다.
        logger.destroy()
        advanceUntilIdle()

        assertEquals(3, shooter.received.size)
    }

    @Test
    fun `destroy 뒤의 기록은 버리고 다시 init 하면 나간다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()
        logger.destroy()
        advanceUntilIdle()

        logger.record(VIEW, open(layout = "버려짐"))
        advanceUntilIdle()
        assertTrue(shooter.received.isEmpty())

        // 앱이 다시 앞으로 나왔다.
        logger.init()
        logger.record(VIEW, open(layout = "1"))
        advanceUntilIdle()

        assertEquals("1", shooter.received.single().event.parameters["layout"])
    }

    @Test
    fun `전송이 실패해도 다음 건은 나간다`() = runTest {
        val shooter = RecordingShooter()
        val logger = logger(shooter)
        logger.init()

        shooter.failing = true
        logger.record(VIEW, open(layout = "1"))
        advanceUntilIdle()
        assertTrue(shooter.received.isEmpty())

        shooter.failing = false
        logger.record(VIEW, open(layout = "2"))
        advanceUntilIdle()

        assertEquals("2", shooter.received.single().event.parameters["layout"])
    }

    @Test
    fun `기록 시각은 시계가 가리키는 때다`() = runTest {
        val shooter = RecordingShooter()
        val clock = FakeBizLogClock()
        val logger = logger(shooter, clock)
        logger.init()

        logger.record(VIEW, open())
        clock.elapse(50L)
        logger.record(VIEW, open())
        advanceUntilIdle()

        val recordedAt = shooter.received.map { it.recordedAt }
        assertEquals(50L, recordedAt[1] - recordedAt[0])
    }

    private fun TestScope.logger(
        shooter: BizLogShooter,
        clock: BizLogClock = FakeBizLogClock(),
    ): BizLogger = BizLoggerImpl(shooter, clock, StandardTestDispatcher(testScheduler))

    private companion object {
        const val VIEW = "/detail"

        fun open(layout: String = "1") = BizEvent.LayoutToggle(layout)
    }
}

/** 손으로 감는 시계. 흐르게 하지 않으면 멈춰 있다. */
private class FakeBizLogClock : BizLogClock {
    private var now = 0L

    fun elapse(millis: Long) {
        now += millis
    }

    override fun nowMillis(): Long = now
}

/** 매번 한 번 양보해, 전송이 왕복하는 사이에 다음 건이 끼어들 틈을 만든다. */
private class RecordingShooter : BizLogShooter {
    private val _received = mutableListOf<BizLog>()

    /** [advanceUntilIdle] 로 큐를 비운 뒤에 본다 — 그 시점에는 더 들어올 것이 없다. */
    val received: List<BizLog> get() = _received
    var failing = false

    override suspend fun shoot(log: BizLog) {
        yield()
        if (failing) throw IllegalStateException("network down")
        _received += log
    }
}
