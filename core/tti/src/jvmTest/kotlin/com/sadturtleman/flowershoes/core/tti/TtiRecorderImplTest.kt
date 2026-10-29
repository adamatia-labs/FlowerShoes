package com.sadturtleman.flowershoes.core.tti

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기록 순서 회귀 테스트.
 *
 * 측정 지점은 값을 기다리지 않고 던지기만 하므로, 순서가 어긋나면 오류 없이 조용히 값이 사라진다.
 * 실제로 두 번 겪었다 — end 가 start 를 추월해 구간이 안 닫혔고, 쏘기가 겹쳐 같은 기록이 두 번 나갔다.
 * 그래서 가짜 저장소는 모든 함수에서 [yield] 해 중간에 다른 작업이 끼어들 틈을 일부러 만든다.
 *
 * 기록기는 아무것도 돌려주지 않으므로 "언제 다 끝났는가" 를 밖에서 알 방법이 없다.
 * 그래서 기록기에 테스트 스케줄러를 물리고 [advanceUntilIdle] 로 큐를 비운 다음에 본다 —
 * 실제 시간을 두고 기다리면 느린 기계에서 아직 처리되지 않은 상태를 결과로 읽는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TtiRecorderImplTest {

    @Test
    fun `start 직후 end 가 와도 구간이 닫힌다`() = runTest {
        val store = FakeTtiRecordStore()
        val shooter = RecordingShooter()
        val recorder = recorder(store, shooter)
        recorder.init()

        val tti = Tti("record")
        TtiTimeline.entries.forEach { timeline ->
            recorder.startRecord(timeline, tti, PAGE)
            recorder.endRecord(timeline, tti, PAGE)
        }
        recorder.shot(tti, PAGE)
        advanceUntilIdle()

        val shot = shooter.received
        assertEquals(1, shot.size)
        assertTrue(shot.single().isComplete)
        assertEquals(PAGE, shot.single().pageName)
    }

    @Test
    fun `쏘기가 겹쳐도 한 번만 나간다`() = runTest {
        val store = FakeTtiRecordStore()
        val shooter = RecordingShooter()
        val recorder = recorder(store, shooter)
        recorder.init()

        val tti = Tti("record")
        TtiTimeline.entries.forEach { timeline ->
            recorder.startRecord(timeline, tti, PAGE)
            recorder.endRecord(timeline, tti, PAGE)
        }
        // 화면이 다 그려져서 한 번, 마지막 구간이 닫혀서 또 한 번 — 실제로 겹치는 조합이다.
        repeat(5) { recorder.shot(tti, PAGE) }
        recorder.destroy()
        advanceUntilIdle()

        assertEquals(1, shooter.shotCount)
        // 쏜 기록은 지워져 다음에 다시 나가지 않는다.
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun `미완성 기록은 쏘지 않고 남는다`() = runTest {
        val store = FakeTtiRecordStore()
        val shooter = RecordingShooter()
        val recorder = recorder(store, shooter)
        recorder.init()

        val tti = Tti("record")
        // 큰 덩어리가 아직 내려오는 중.
        listOf(TtiTimeline.VIEW_CREATE, TtiTimeline.BACKEND, TtiTimeline.VIEW_BINDING).forEach {
            recorder.startRecord(it, tti, PAGE)
            recorder.endRecord(it, tti, PAGE)
        }
        recorder.startRecord(TtiTimeline.BIG_PART_LOADING, tti, PAGE)
        recorder.shot(tti, PAGE)
        recorder.destroy()
        advanceUntilIdle()

        assertEquals(0, shooter.shotCount)
        assertEquals(1, store.records.size)
    }

    /** 구간 길이가 실제로 재지는지도 함께 본다 — 시계가 포트가 된 뒤로는 값을 꾸며 넣을 수 있다. */
    @Test
    fun `구간 길이는 시계가 흐른 만큼이다`() = runTest {
        val store = FakeTtiRecordStore()
        val shooter = RecordingShooter()
        val clock = FakeTtiClock()
        val recorder = recorder(store, shooter, clock)
        recorder.init()

        val tti = Tti("record")
        TtiTimeline.entries.forEachIndexed { index, timeline ->
            recorder.startRecord(timeline, tti, PAGE)
            clock.elapse((index + 1) * 10L)
            recorder.endRecord(timeline, tti, PAGE)
        }
        recorder.shot(tti, PAGE)
        advanceUntilIdle()

        val record = shooter.received.single()
        assertEquals(10L, record.spans.getValue(TtiTimeline.VIEW_CREATE).durationMillis)
        assertEquals(40L, record.spans.getValue(TtiTimeline.BIG_PART_LOADING).durationMillis)
        assertEquals(10L + 20L + 30L + 40L, record.totalTimeMillis)
    }

    private fun kotlinx.coroutines.test.TestScope.recorder(
        store: TtiRecordStore,
        shooter: TtiShooter,
        clock: TtiClock = FakeTtiClock(),
    ): TtiRecorder = TtiRecorderImpl(store, shooter, clock, StandardTestDispatcher(testScheduler))

    private companion object {
        const val PAGE = "/detail"
    }
}

/** 손으로 감는 시계. 흐르게 하지 않으면 멈춰 있다. */
private class FakeTtiClock : TtiClock {
    private var elapsed = 0L

    fun elapse(millis: Long) {
        elapsed += millis
    }

    override fun elapsedMillis(): Long = elapsed

    /** 오래된 기록 청소에만 쓰이므로 지금은 흐르지 않아도 된다. */
    override fun wallTimeMillis(): Long = WALL_TIME_ORIGIN + elapsed

    private companion object {
        /** 청소 기준(24시간)보다 뒤여야 갓 만든 기록이 오래된 것으로 잡히지 않는다. */
        const val WALL_TIME_ORIGIN = 48 * 60 * 60 * 1000L
    }
}

/** 모든 호출에서 한 번 양보해, 순서가 지켜지지 않으면 곧바로 어긋나게 만든다. */
private class FakeTtiRecordStore : TtiRecordStore {
    private val lock = Mutex()
    val records = mutableMapOf<String, TtiRecord>()

    override suspend fun openSpan(
        tti: Tti,
        pageName: String,
        createdAt: Long,
        timeline: TtiTimeline,
        startedAt: Long,
    ) = edit {
        val existing = records[tti.id]
            ?: TtiRecord(tti = tti, pageName = pageName, createdAt = createdAt, spans = emptyMap())
        // 이미 열린 구간은 처음 시각을 지킨다.
        if (timeline in existing.spans) {
            records[tti.id] = existing
            return@edit
        }
        records[tti.id] = existing.copy(spans = existing.spans + (timeline to TtiSpan(startedAt)))
    }

    override suspend fun closeSpan(tti: Tti, timeline: TtiTimeline, endedAt: Long) = edit {
        val record = records[tti.id] ?: return@edit
        val span = record.spans[timeline] ?: return@edit
        // 이미 닫힌 구간은 덮어쓰지 않는다.
        if (span.endedAt != null) return@edit
        records[tti.id] = record.copy(spans = record.spans + (timeline to span.copy(endedAt = endedAt)))
    }

    override suspend fun find(tti: Tti): TtiRecord? = edit { records[tti.id] }

    override suspend fun findAll(): List<TtiRecord> = edit { records.values.sortedBy { it.createdAt } }

    override suspend fun delete(ttis: List<Tti>) = edit {
        ttis.forEach { records.remove(it.id) }
    }

    override suspend fun deleteCreatedBefore(threshold: Long) = edit {
        records.values.removeAll { it.createdAt < threshold }
        Unit
    }

    private suspend fun <T> edit(block: () -> T): T {
        yield()
        return lock.withLock { block() }
    }
}

private class RecordingShooter : TtiShooter {
    private val _received = mutableListOf<TtiRecord>()

    /** [advanceUntilIdle] 로 큐를 비운 뒤에 본다 — 그 시점에는 더 들어올 것이 없다. */
    val received: List<TtiRecord> get() = _received
    val shotCount: Int get() = _received.size

    override suspend fun shoot(records: List<TtiRecord>) {
        yield()
        _received += records
    }
}
