package com.sadturtleman.flowershoes.core.backup

import kotlin.test.*

class AutoBackupPolicyTest {
    @Test fun `disabled offline and disallowed cellular never upload`() {
        assertFalse(AutoBackupPolicy.shouldRun(false, true, true, true, null, 10_000))
        assertFalse(AutoBackupPolicy.shouldRun(true, false, true, true, null, 10_000))
        assertFalse(AutoBackupPolicy.shouldRun(true, true, false, false, null, 10_000))
    }
    @Test fun `wifi and opted in cellular upload after thirty minutes`() {
        assertTrue(AutoBackupPolicy.shouldRun(true, true, true, false, null, 10_000))
        assertTrue(AutoBackupPolicy.shouldRun(true, true, false, true, 1, 1_800_001))
        assertFalse(AutoBackupPolicy.shouldRun(true, true, true, true, 1, 1_800_000))
    }
}
