package com.sadturtleman.flowershoes.core.logging

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** BizLog 가 GA4 이벤트 한 건으로 옮겨지는 모양. */
class AnalyticsBizLogShooterTest {

    @Test
    fun `이벤트 이름과 값에 화면 이름을 붙여 보낸다`() = runTest {
        val sink = RecordingSink()

        AnalyticsBizLogShooter(sink).shoot(log(BizEvent.LayoutToggle(layout = "grid")))

        assertEquals("layout_toggle" to mapOf("layout" to "grid", "view_name" to "/home"), sink.received.single())
    }

    @Test
    fun `값이 없는 파라미터는 빼고 보낸다`() = runTest {
        val sink = RecordingSink()

        AnalyticsBizLogShooter(sink).shoot(log(BizEvent.ViewEnter(from = null)))

        assertEquals("view_enter" to mapOf("view_name" to "/home"), sink.received.single())
    }

    @Test
    fun `GA4 한도인 100자를 넘는 값은 잘라 보낸다`() = runTest {
        val sink = RecordingSink()

        AnalyticsBizLogShooter(sink).shoot(log(BizEvent.ViewEnter(from = "a".repeat(150))))

        assertEquals("a".repeat(100), sink.received.single().second["from"])
    }

    private fun log(event: BizEvent) = BizLog(viewName = "/home", event = event, userUuid = "u-1", recordedAt = 1L)
}

private class RecordingSink : AnalyticsSink {
    val received = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun logEvent(name: String, parameters: Map<String, Any>) {
        received += name to parameters
    }
}
