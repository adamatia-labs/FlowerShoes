package com.sadturtleman.flowershoes.feature.pairing

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PairingFailureTest {
    @Test
    fun `서버 오류 원문에 포함된 초대 정보는 화면에 노출하지 않는다`() {
        val token = "a".repeat(22)
        val failure = pairingFailure(IllegalStateException("pairings/$token"))
        assertFalse(failure.reason.contains(token))
        assertEquals("연결하지 못했어요. 다시 시도해 주세요", failure.reason)
    }

    @Test
    fun `초대 공유 객체 문자열에 링크와 토큰이 노출되지 않는다`() {
        val token = "a".repeat(22)
        val url = "https://example.test/i/$token"
        val text = InviteShare(token, url, "이름").toString()
        assertFalse(text.contains(token))
        assertFalse(text.contains(url))
    }

    @Test
    fun `코루틴 취소는 오류 화면으로 바꾸지 않는다`() {
        assertFailsWith<CancellationException> { pairingFailure(CancellationException()) }
    }
}
