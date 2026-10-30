package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.LIFETIME_PAPERS
import kotlin.test.Test
import kotlin.test.assertEquals

class LifetimePapersTest {

    @Test
    fun `설정 꽃신 평생의 편지지 수는 실제 평생 편지지 수와 같다`() {
        assertEquals(LIFETIME_PAPERS, Paper.entries.count { it.lifetime })
    }
}
