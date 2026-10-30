package com.sadturtleman.flowershoes.feature.settings

import com.sadturtleman.flowershoes.core.model.Branch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class MilitaryEditTest {

    private val enlist = LocalDate(2026, 4, 20)

    @Test
    fun `전역일을 고치지 않으면 규칙으로 계산한 날`() {
        // 공군 21개월(병무청 공지, #18): 2026.04.20 입대 → 2028.01.19 전역.
        assertEquals(LocalDate(2028, 1, 19), computedDischarge(Branch.AIR_FORCE, enlist))
        assertNull(dischargeOverrideFor(Branch.AIR_FORCE, enlist, picked = null))
    }

    @Test
    fun `계산한 날과 같은 날을 고르면 직접 고친 값이 아니다`() {
        assertNull(dischargeOverrideFor(Branch.AIR_FORCE, enlist, picked = LocalDate(2028, 1, 19)))
    }

    @Test
    fun `다른 날을 고르면 그 날로 고친다`() {
        assertEquals(LocalDate(2028, 2, 19), dischargeOverrideFor(Branch.AIR_FORCE, enlist, picked = LocalDate(2028, 2, 19)))
    }
}
