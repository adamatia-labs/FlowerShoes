package com.sadturtleman.flowershoes.core.data

/** 결제 퍼널의 한 걸음. */
enum class PurchaseStep { STARTED, COMPLETED }

/**
 * 결제 퍼널을 받아 적는 곳. 앱 조립이 BizLogger(BizEvent.PurchaseStarted · PurchaseCompleted)로 잇는다.
 *
 * 이 모듈이 core:logging 을 직접 보지 않는 이유: core:logging 의 iOS 쪽은 Firebase 를 링크하므로,
 * core:data 를 쓰는 모든 모듈의 iOS 테스트 바이너리가 FirebaseCore 없이는 링크되지 않는다.
 */
fun interface PurchaseRecorder {
    fun record(step: PurchaseStep)
}

/**
 * 결제 시작 · 완료를 남기는 스토어. 플랫폼 스토어를 Koin 에 올릴 때 감싼다.
 *
 * 화면이 아니라 스토어 자리에서 남기는 이유는, 사는 곳(설정 · 추억 책 · 권유 팝업)이 늘어도
 * 결제는 언제나 [LifetimeStore.buy] · [OneTimeProduct.buy] 하나를 거치기 때문이다.
 * 구매 복원은 새 결제가 아니라 퍼널에 넣지 않는다.
 */
fun LifetimeStore.logged(recorder: PurchaseRecorder): LifetimeStore = object : LifetimeStore by this {
    override suspend fun buy(): PurchaseOutcome = recorder.around { this@logged.buy() }
}

/** 추억 책 PDF. [LifetimeStore.logged] 와 같다. */
fun BookStore.logged(recorder: PurchaseRecorder): BookStore = object : BookStore by this {
    override suspend fun buy(): PurchaseOutcome = recorder.around { this@logged.buy() }
}

private suspend fun PurchaseRecorder.around(buy: suspend () -> PurchaseOutcome): PurchaseOutcome {
    record(PurchaseStep.STARTED)
    return buy().also { if (it == PurchaseOutcome.Purchased) record(PurchaseStep.COMPLETED) }
}
