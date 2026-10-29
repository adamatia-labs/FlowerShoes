package com.sadturtleman.flowershoes.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 결제 퍼널: 어느 화면에서 사든 스토어를 거치므로 스토어 자리에서 시작 · 완료를 남긴다. */
class PurchaseLoggingTest {

    @Test
    fun `꽃신 평생을 사면 시작과 완료가 남는다`() = runTest {
        val steps = mutableListOf<PurchaseStep>()

        val outcome = FakeLifetime(PurchaseOutcome.Purchased).logged { steps += it }.buy()

        assertEquals(PurchaseOutcome.Purchased, outcome)
        assertEquals(listOf(PurchaseStep.STARTED, PurchaseStep.COMPLETED), steps)
    }

    @Test
    fun `추억 책 결제를 취소하면 시작만 남는다`() = runTest {
        val steps = mutableListOf<PurchaseStep>()

        FakeBook(PurchaseOutcome.Cancelled).logged { steps += it }.buy()

        assertEquals(listOf(PurchaseStep.STARTED), steps)
    }

    @Test
    fun `구매 복원은 결제 퍼널에 남기지 않는다`() = runTest {
        val steps = mutableListOf<PurchaseStep>()

        FakeLifetime(PurchaseOutcome.Purchased).logged { steps += it }.restore()
        FakeBook(PurchaseOutcome.Purchased).logged { steps += it }.restore()

        assertEquals(emptyList<PurchaseStep>(), steps)
    }
}

private class FakeLifetime(private val outcome: PurchaseOutcome) : LifetimeStore {
    override val owned: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun price(): String? = null
    override suspend fun buy() = outcome
    override suspend fun restore() = outcome
    override suspend fun refresh() = Unit
}

private class FakeBook(private val outcome: PurchaseOutcome) : BookStore {
    override val owned: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun price(): String? = null
    override suspend fun buy() = outcome
    override suspend fun restore() = outcome
}
