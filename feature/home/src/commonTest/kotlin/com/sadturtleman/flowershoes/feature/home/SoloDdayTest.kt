package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoloDdayTest {
    private val today = LocalDate(2026, 10, 28)

    private fun timeline(branch: Branch, enlist: LocalDate) =
        ServiceCalculator().timeline(Service("pending:soldier", branch, enlist, ServiceRulesTable.BUNDLED.version))

    @Test fun nudgeShowsOnlyWhileUnlinkedBeforeEnlistment() {
        val future = timeline(Branch.ARMY, LocalDate(2026, 11, 9))
        assertTrue(needsEnlistNudge(linked = false, timeline = future, today = today))
        assertFalse(needsEnlistNudge(linked = true, timeline = future, today = today))
        assertFalse(needsEnlistNudge(linked = false, timeline = timeline(Branch.ARMY, today), today = today))
        assertFalse(needsEnlistNudge(linked = false, timeline = null, today = today))
    }
}
