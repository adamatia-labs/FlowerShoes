package com.sadturtleman.flowershoes.core.notification

import com.sadturtleman.flowershoes.core.data.ExportHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 내 기록 내보내기 권유(#29): 마지막으로 내보낸 날(없으면 처음 쓴 날)부터 3개월마다 서울 10시. */
class ExportReminderTest {

    // 서울 2026-10-22 12:00
    private val now = Instant.parse("2026-10-22T03:00:00Z")

    private fun plan(history: ExportHistory?) =
        NotificationPlanner().plan(now, "민준", emptyList(), emptyList(), null, exports = history)

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    @Test
    fun `내보낸 지 3개월이 되는 날 서울 10시에 알린다`() {
        val n = plan(ExportHistory(firstRunAtMs = ms("2026-01-01T00:00:00Z"), lastExportAtMs = ms("2026-08-01T05:00:00Z"))).single()
        assertEquals("export-reminder", n.id)
        assertEquals(Instant.parse("2026-11-01T01:00:00Z"), n.at)
        assertEquals("내 기록 받아 두기", n.title)
        assertEquals("마지막으로 내보낸 지 3개월이 지났어요", n.body)
        assertEquals("kkotsin://settings/device", n.deepLink)
    }

    @Test
    fun `알림을 지나쳤으면 다음 3개월 째에 다시 알린다`() {
        val n = plan(ExportHistory(firstRunAtMs = null, lastExportAtMs = ms("2026-01-10T05:00:00Z"))).single()
        assertEquals(Instant.parse("2027-01-10T01:00:00Z"), n.at)
        assertEquals("마지막으로 내보낸 지 12개월이 지났어요", n.body)
    }

    @Test
    fun `한 번도 내보내지 않았으면 처음 쓴 날부터 센다`() {
        val n = plan(ExportHistory(firstRunAtMs = ms("2026-09-05T05:00:00Z"), lastExportAtMs = null)).single()
        assertEquals(Instant.parse("2026-12-05T01:00:00Z"), n.at)
        assertEquals("아직 내 기록을 내보낸 적이 없어요", n.body)
    }

    @Test
    fun `알림을 끄면 예약하지 않는다`() {
        assertTrue(plan(null).isEmpty())
        assertTrue(plan(ExportHistory()).isEmpty())
    }
}
