package com.sadturtleman.flowershoes.app

import com.sadturtleman.flowershoes.core.data.PurchaseRecorder
import com.sadturtleman.flowershoes.core.data.PurchaseStep
import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.core.logging.BizView
import com.sadturtleman.flowershoes.core.logging.PurchaseProduct

/**
 * 결제 퍼널을 BizEvent 로 남긴다. 플랫폼 스토어를 Koin 에 올릴 때 `store.logged(bizLogger.purchaseRecorder(...))` 로 감싼다.
 * 화면 이름은 상품을 파는 페이지로 고정한다(권유 팝업에서 열어도 결제는 그 페이지의 흐름이다).
 */
fun BizLogger.purchaseRecorder(product: PurchaseProduct): PurchaseRecorder {
    val view = when (product) {
        PurchaseProduct.LIFETIME -> BizView.LIFETIME
        PurchaseProduct.MEMORY_BOOK -> BizView.MEMORY_BOOK
    }
    return PurchaseRecorder { step ->
        record(
            view,
            when (step) {
                PurchaseStep.STARTED -> BizEvent.PurchaseStarted(product)
                PurchaseStep.COMPLETED -> BizEvent.PurchaseCompleted(product)
            },
        )
    }
}
