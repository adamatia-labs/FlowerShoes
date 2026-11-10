package com.sadturtleman.flowershoes.core.data.sync

import kotlin.test.Test
import kotlin.test.assertFalse

class PairingPrivacyTest {
    @Test
    fun `초대 상태와 저장 항목 문자열에 토큰이 노출되지 않는다`() {
        val token = "a".repeat(22)
        val values = listOf(
            PendingInvite(token, 123),
            PairingState.Waiting(token, 123),
            PairingState.Invited(token, "이름"),
            PairingState.OwnLink(token),
        )
        values.forEach { assertFalse(it.toString().contains(token)) }
    }
}
