package com.sadturtleman.flowershoes.core.tti

import androidx.room.Room
import com.sadturtleman.flowershoes.core.tti.room.TtiDatabase
import com.sadturtleman.flowershoes.core.tti.room.buildTti
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장소의 "처음 값을 지킨다" 규칙.
 *
 * 이 규칙은 SQL(IGNORE · endedAt IS NULL)이 맡는다. 가짜 저장소로는 잡히지 않으므로
 * Android · iOS 가 함께 쓰는 번들 SQLite 에 실제로 쿼리를 돌려 본다.
 */
class RoomTtiRecordStoreTest {

    private val database = Room.inMemoryDatabaseBuilder<TtiDatabase>().buildTti()
    private val store: TtiRecordStore = RoomTtiRecordStore(database.ttiDao())

    @After
    fun close() = database.close()

    @Test
    fun `연 구간을 닫으면 길이가 남는다`() = runTest {
        store.openSpan(TTI, PAGE, createdAt = 1_000, timeline = TtiTimeline.BACKEND, startedAt = 10)
        store.closeSpan(TTI, TtiTimeline.BACKEND, endedAt = 210)

        val record = store.find(TTI)!!
        assertEquals(PAGE, record.pageName)
        assertEquals(200L, record.spans.getValue(TtiTimeline.BACKEND).durationMillis)
    }

    /** 컴포지션이 다시 돌아 start 가 또 와도 처음 시각이 기준이다. */
    @Test
    fun `두 번째 start 는 처음 시각을 덮지 않는다`() = runTest {
        store.openSpan(TTI, PAGE, createdAt = 1_000, timeline = TtiTimeline.BACKEND, startedAt = 10)
        store.openSpan(TTI, PAGE, createdAt = 9_000, timeline = TtiTimeline.BACKEND, startedAt = 90)

        val record = store.find(TTI)!!
        assertEquals(1_000L, record.createdAt)
        assertEquals(10L, record.spans.getValue(TtiTimeline.BACKEND).startedAt)
    }

    /** 늦게 도착한 end 가 앞의 측정을 덮어쓰면 구간이 길어진 것처럼 보인다. */
    @Test
    fun `이미 닫힌 구간은 다시 닫히지 않는다`() = runTest {
        store.openSpan(TTI, PAGE, createdAt = 1_000, timeline = TtiTimeline.BACKEND, startedAt = 10)
        store.closeSpan(TTI, TtiTimeline.BACKEND, endedAt = 50)
        store.closeSpan(TTI, TtiTimeline.BACKEND, endedAt = 500)

        assertEquals(50L, store.find(TTI)!!.spans.getValue(TtiTimeline.BACKEND).endedAt)
    }

    @Test
    fun `지운 기록은 구간과 함께 사라진다`() = runTest {
        store.openSpan(TTI, PAGE, createdAt = 1_000, timeline = TtiTimeline.BACKEND, startedAt = 10)
        store.delete(listOf(TTI))

        assertNull(store.find(TTI))
        assertTrue(store.findAll().isEmpty())
    }

    @Test
    fun `오래된 기록만 정리된다`() = runTest {
        val old = Tti("old")
        store.openSpan(old, PAGE, createdAt = 1_000, timeline = TtiTimeline.BACKEND, startedAt = 0)
        store.openSpan(TTI, PAGE, createdAt = 5_000, timeline = TtiTimeline.BACKEND, startedAt = 0)

        store.deleteCreatedBefore(threshold = 2_000)

        assertEquals(listOf(TTI), store.findAll().map { it.tti })
    }

    private companion object {
        val TTI = Tti("record")
        const val PAGE = "/detail"
    }
}
