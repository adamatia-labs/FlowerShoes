package com.sadturtleman.flowershoes.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AppleTokenRegistrationTest {
    @Test fun `저장과 준비 확인이 모두 성공해야 안내를 생략한다`() = runBlocking {
        assertTrue(AppleTokenRegistration({ true }, { true }).register("fresh-code"))
        assertFalse(AppleTokenRegistration({ true }, { false }).register("fresh-code"))
        assertFalse(AppleTokenRegistration({ false }, { error("저장 실패 뒤 조회하면 안 된다") }).register("fresh-code"))
    }

    @Test fun `코드 부재와 서버 오류는 성공으로 숨기지 않는다`() = runBlocking {
        assertFalse(AppleTokenRegistration({ error("코드가 없다") }, { true }).register(null))
        assertFalse(AppleTokenRegistration({ throw IllegalStateException("offline") }, { true }).register("code"))
        assertFalse(AppleTokenRegistration({ true }, { throw IllegalStateException("offline") }).ready())
    }

    @Test fun `화면 취소를 저장 실패로 삼켜 다음 계정 작업을 계속하지 않는다`(): Unit = runBlocking {
        assertFailsWith<CancellationException> {
            AppleTokenRegistration({ throw CancellationException("closed") }, { true }).register("code")
        }
        assertFailsWith<CancellationException> {
            AppleTokenRegistration({ true }, { throw CancellationException("closed") }).ready()
        }
    }
}
