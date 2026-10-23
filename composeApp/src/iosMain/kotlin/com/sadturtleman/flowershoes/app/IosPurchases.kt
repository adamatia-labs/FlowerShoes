package com.sadturtleman.flowershoes.app

import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.LifetimeStore
import com.sadturtleman.flowershoes.core.data.PurchaseOutcome
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface PurchasePriceCallback {
    fun result(price: String?)
}

/** [code]: 0 산 것 · 1 취소 · 2 복원할 것 없음 · 3 실패([message]). */
fun interface PurchaseOutcomeCallback {
    fun result(code: Int, message: String?)
}

fun interface LifetimeOwnedCallback {
    fun changed(owned: Boolean)
}

/**
 * Swift(RevenueCat iOS SDK)가 맡는 꽃신 평생 결제. Kotlin 은 SDK 를 모른다 — 결과만 콜백으로 받는다.
 * 사는 것은 지금 Offering 의 lifetime 패키지, 이용 중은 "활성 entitlement 가 하나라도 있음"(Android 와 같다).
 */
interface IosPurchases {
    /** 꽃신 계정으로 맞추고(아직 신원이 없으면 null) 이용권을 다시 읽는다. */
    fun refresh(userId: String?, done: LifetimeOwnedCallback)
    fun price(done: PurchasePriceCallback)
    fun buy(done: PurchaseOutcomeCallback)
    fun restore(done: PurchaseOutcomeCallback)
    /** 이용권이 바뀔 때마다(다른 기기에서 복원 · 환불 등). */
    fun observe(listener: LifetimeOwnedCallback)
}

/** [IosPurchases] → 공통 [LifetimeStore]. */
internal class IosLifetimeStore(private val purchases: IosPurchases, private val identity: Identity) : LifetimeStore {
    private val _owned = MutableStateFlow(false)
    override val owned: StateFlow<Boolean> = _owned

    init {
        purchases.observe { _owned.value = it }
    }

    private fun userId(): String? = runCatching { identity.userId }.getOrNull()?.takeIf { it.isNotBlank() }

    override suspend fun refresh() {
        _owned.value = suspendCancellableCoroutine { c -> purchases.refresh(userId()) { if (c.isActive) c.resume(it) } }
    }

    override suspend fun price(): String? = suspendCancellableCoroutine { c -> purchases.price { if (c.isActive) c.resume(it) } }

    override suspend fun buy(): PurchaseOutcome {
        refresh()
        return outcome { purchases.buy(it) }
    }

    override suspend fun restore(): PurchaseOutcome {
        refresh()
        return outcome { purchases.restore(it) }
    }

    private suspend fun outcome(call: (PurchaseOutcomeCallback) -> Unit): PurchaseOutcome = suspendCancellableCoroutine { c ->
        call { code, message ->
            if (!c.isActive) return@call
            val r = when (code) {
                0 -> PurchaseOutcome.Purchased.also { _owned.value = true }
                1 -> PurchaseOutcome.Cancelled
                2 -> PurchaseOutcome.NothingToRestore
                else -> PurchaseOutcome.Failed(message ?: "결제하지 못했어요. 잠시 뒤 다시 해 주세요")
            }
            c.resume(r)
        }
    }
}
