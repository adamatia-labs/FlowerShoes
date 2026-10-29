package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import com.sadturtleman.flowershoes.core.logging.PurchaseProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 결제 퍼널: 어느 화면에서 사든 스토어를 거치므로 스토어 자리에서 시작 · 완료를 남긴다. */
class PurchaseLoggingTest {

    @Test
    fun `꽃신 평생을 사면 시작과 완료가 남는다`() = runTest {
        val logger = RecordingBizLogger()

        val outcome = FakeLifetime(PurchaseOutcome.Purchased).logged(logger).buy()

        assertEquals(PurchaseOutcome.Purchased, outcome)
        assertEquals(
            listOf(
                BizView.LIFETIME to BizEvent.PurchaseStarted(PurchaseProduct.LIFETIME),
                BizView.LIFETIME to BizEvent.PurchaseCompleted(PurchaseProduct.LIFETIME),
            ),
            logger.recorded,
        )
    }

    @Test
    fun `추억 책 결제를 취소하면 시작만 남는다`() = runTest {
        val logger = RecordingBizLogger()

        FakeBook(PurchaseOutcome.Cancelled).logged(logger).buy()

        assertEquals(listOf<Pair<String, BizEvent>>(BizView.MEMORY_BOOK to BizEvent.PurchaseStarted(PurchaseProduct.MEMORY_BOOK)), logger.recorded)
    }

    @Test
    fun `구매 복원은 결제 퍼널에 남기지 않는다`() = runTest {
        val logger = RecordingBizLogger()

        FakeLifetime(PurchaseOutcome.Purchased).logged(logger).restore()
        FakeBook(PurchaseOutcome.Purchased).logged(logger).restore()

        assertEquals(emptyList<Pair<String, BizEvent>>(), logger.recorded)
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

private class RecordingBizLogger : BizLogger {
    val recorded = mutableListOf<Pair<String, BizEvent>>()
    override fun init() = Unit
    override fun makeUUID() = "u"
    override fun record(viewName: String, event: BizEvent) {
        recorded += viewName to event
    }
    override fun destroy() = Unit
}
