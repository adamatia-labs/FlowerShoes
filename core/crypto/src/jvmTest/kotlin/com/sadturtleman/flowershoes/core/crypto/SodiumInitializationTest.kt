package com.sadturtleman.flowershoes.core.crypto

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class SodiumInitializationTest {
    @Test
    fun `동시 초기화와 대기 초기화를 반복해도 키 생성이 성공한다`() {
        val workers = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        try {
            val results = (1..16).map {
                workers.submit<Int> {
                    start.await()
                    repeat(20) {
                        Sodium.init()
                        // Both entry points must share the same synchronous initialization boundary.
                        var result: Result<Unit>? = null
                        suspend { Sodium.awaitInit() }.startCoroutine(object : Continuation<Unit> {
                            override val context = EmptyCoroutineContext
                            override fun resumeWith(value: Result<Unit>) { result = value }
                        })
                        checkNotNull(result).getOrThrow()
                        assertEquals(32, DeviceKeys.generate().signPub.size)
                    }
                    20
                }
            }
            start.countDown()
            assertEquals(320, results.sumOf { it.get(30, TimeUnit.SECONDS) })
        } finally { workers.shutdownNow() }
    }
}
