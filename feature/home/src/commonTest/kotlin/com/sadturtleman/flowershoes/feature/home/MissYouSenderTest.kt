package com.sadturtleman.flowershoes.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MissYouSenderTest {
    @Test
    fun `저장 중에는 성공을 표시하지 않고 중복 입력을 한 번만 저장한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val sender = MissYouSender(this, { calls++; gate.await() })
        sender.send()
        sender.send()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(MissYouSendStatus.SAVING, sender.status.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(MissYouSendStatus.SAVED, sender.status.value)
        sender.send()
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(MissYouSendStatus.IDLE, sender.status.value)
        sender.send()
        runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(2_000)
        runCurrent()
    }

    @Test
    fun `저장 실패는 성공이 아닌 오류를 표시하고 다시 시도할 수 있다`() = runTest {
        var calls = 0
        val sender = MissYouSender(this, { if (++calls == 1) error("disk unavailable") })
        sender.send()
        runCurrent()
        assertEquals(MissYouSendStatus.FAILED, sender.status.value)
        sender.send()
        assertEquals(1, calls)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(MissYouSendStatus.IDLE, sender.status.value)
        sender.send()
        runCurrent()
        assertEquals(MissYouSendStatus.SAVED, sender.status.value)
        advanceTimeBy(2_000)
        runCurrent()
    }

    @Test
    fun `저장 작업 취소는 실패나 성공으로 표시하지 않는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val child = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val sender = MissYouSender(child, { gate.await() })
        sender.send()
        runCurrent()
        assertEquals(MissYouSendStatus.SAVING, sender.status.value)
        child.cancel()
        runCurrent()
        assertEquals(MissYouSendStatus.IDLE, sender.status.value)
        assertFalse(gate.isCompleted)
    }
}
