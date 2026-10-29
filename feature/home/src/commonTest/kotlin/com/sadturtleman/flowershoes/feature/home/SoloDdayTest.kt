package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoloDdayTest {
    private val today = LocalDate(2026, 10, 28)

    private fun timeline(branch: Branch, enlist: LocalDate) =
        ServiceCalculator().timeline(Service("pending:soldier", branch, enlist, ServiceRulesTable.BUNDLED.version))

    @Test fun beforeEnlistmentCountsDownToEnlistment() {
        val card = ddayCard(timeline(Branch.AIR_FORCE, LocalDate(2026, 11, 9)), Branch.AIR_FORCE, today)
        assertEquals("입대까지", card.caption)
        assertEquals("D-12", card.days)
        assertEquals("공군", card.rank)
        assertEquals("입대 2026.11.09 (월)", card.date)
        assertEquals(0f, card.progress)
    }

    @Test fun enlistmentDayCountsDownToDischarge() {
        val card = ddayCard(timeline(Branch.ARMY, today), Branch.ARMY, today)
        assertEquals("전역까지", card.caption)
        assertEquals("2028.04.27 전역", card.date)
        assertEquals("D-547", card.days)
    }

    @Test fun nudgeShowsOnlyWhileUnlinkedBeforeEnlistment() {
        val future = timeline(Branch.ARMY, LocalDate(2026, 11, 9))
        assertTrue(needsEnlistNudge(linked = false, timeline = future, today = today))
        assertFalse(needsEnlistNudge(linked = true, timeline = future, today = today))
        assertFalse(needsEnlistNudge(linked = false, timeline = timeline(Branch.ARMY, today), today = today))
        assertFalse(needsEnlistNudge(linked = false, timeline = null, today = today))
    }
}
