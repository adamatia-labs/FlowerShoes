package com.sadturtleman.flowershoes.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 초대 링크 두 입력(웹 링크 · 카카오 앱 실행)이 하나의 토큰으로 모인다(#15 결정 7). */
class InviteLinksTest {
    private val token = "AbCdEfGhIjKlMnOpQrSt_-"

    @Test
    fun `웹 링크에서 토큰을 꺼낸다`() {
        assertEquals(token, InviteLinks.tokenFrom("https://kkotsin-dev.web.app/i/$token"))
        assertEquals(token, InviteLinks.tokenFrom("https://kkotsin.app/i/$token/?utm=x"))
    }

    @Test
    fun `카카오 피드 템플릿 앱 실행 링크에서 토큰을 꺼낸다`() {
        assertEquals(token, InviteLinks.tokenFrom("kakao0123abcd://kakaolink?invite=$token"))
        assertEquals(token, InviteLinks.tokenFrom("kakao0123abcd://kakaolink?foo=1&invite=$token"))
    }

    @Test
    fun `만든 링크를 다시 읽으면 같은 토큰이다`() {
        assertEquals(token, InviteLinks.tokenFrom(InviteLinks.url(token, "example.web.app")))
    }

    @Test
    fun `초대가 아닌 링크는 무시한다`() {
        assertNull(InviteLinks.tokenFrom("kkotsin://pairing"))
        assertNull(InviteLinks.tokenFrom("http://kkotsin-dev.web.app/i/$token"))
        assertNull(InviteLinks.tokenFrom("https://kkotsin-dev.web.app/x/$token"))
        assertNull(InviteLinks.tokenFrom("kakao0123abcd://oauth?code=abc"))
        assertNull(InviteLinks.tokenFrom("https://kkotsin-dev.web.app/i/short"))
    }
}
