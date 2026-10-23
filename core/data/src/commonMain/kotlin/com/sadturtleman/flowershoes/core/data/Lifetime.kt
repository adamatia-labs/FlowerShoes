package com.sadturtleman.flowershoes.core.data

import kotlinx.coroutines.flow.StateFlow

/** 결제 결과(설정 › 꽃신 평생). */
sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    /** 구매 복원했는데 산 기록이 없다. */
    data object NothingToRestore : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * 꽃신 평생(한 번 결제). 스토어 결제는 플랫폼(Android: RevenueCat)이 맡는다 — 없는 빌드에서는 등록되지 않는다.
 * [owned] 면 광고를 모두 숨기고 꽃신 평생 혜택을 연다.
 */
interface LifetimeStore {
    val owned: StateFlow<Boolean>

    /** 스토어 표시 가격("₩7,900"). 상품을 못 읽으면 null. */
    suspend fun price(): String?

    suspend fun buy(): PurchaseOutcome

    suspend fun restore(): PurchaseOutcome

    /** 앱이 앞으로 나올 때 · 로그인 뒤: 계정을 맞추고 이용권을 다시 읽는다. */
    suspend fun refresh()
}
