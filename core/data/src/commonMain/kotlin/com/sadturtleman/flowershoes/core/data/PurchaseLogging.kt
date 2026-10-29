package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import com.sadturtleman.flowershoes.core.logging.PurchaseProduct

/**
 * 결제 퍼널(시작 · 완료)을 남기는 스토어. 플랫폼 스토어를 Koin 에 올릴 때 감싼다.
 *
 * 화면이 아니라 스토어 자리에서 남기는 이유는, 사는 곳(설정 · 추억 책 · 권유 팝업)이 늘어도
 * 결제는 언제나 [LifetimeStore.buy] · [OneTimeProduct.buy] 하나를 거치기 때문이다.
 * 구매 복원은 새 결제가 아니라 퍼널에 넣지 않는다.
 */
fun LifetimeStore.logged(logger: BizLogger): LifetimeStore = object : LifetimeStore by this {
    override suspend fun buy(): PurchaseOutcome = logPurchase(logger, PurchaseProduct.LIFETIME, BizView.LIFETIME) { this@logged.buy() }
}

/** 추억 책 PDF. [LifetimeStore.logged] 와 같다. */
fun BookStore.logged(logger: BizLogger): BookStore = object : BookStore by this {
    override suspend fun buy(): PurchaseOutcome = logPurchase(logger, PurchaseProduct.MEMORY_BOOK, BizView.MEMORY_BOOK) { this@logged.buy() }
}

private suspend fun logPurchase(logger: BizLogger, product: PurchaseProduct, view: String, buy: suspend () -> PurchaseOutcome): PurchaseOutcome {
    logger.record(view, BizEvent.PurchaseStarted(product))
    return buy().also { if (it == PurchaseOutcome.Purchased) logger.record(view, BizEvent.PurchaseCompleted(product)) }
}
