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

/** 활성 entitlement id 들(추억 책 memory_book · 백업 용량 추가 backup_plus 를 가려 보려고). */
fun interface EntitlementsCallback {
    fun changed(active: List<String>)
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
    /** 이용권이 바뀔 때마다(다른 기기에서 복원 · 환불 등). 꽃신 평생 = 추억 책 · 용량 추가 말고 활성 entitlement. */
    fun observe(listener: LifetimeOwnedCallback)

    /** 활성 entitlement 가 바뀔 때마다(따로 파는 상품의 산 표시). */
    fun observeEntitlements(listener: EntitlementsCallback)

    /** 따로 파는 한 번 결제 상품: Offering [offering] 의 첫 패키지. */
    fun productPrice(offering: String, done: PurchasePriceCallback)
    fun buyProduct(offering: String, entitlement: String, done: PurchaseOutcomeCallback)
}

/** 따로 파는 상품 하나(추억 책 PDF · 백업 용량 추가). Android RevenueCatLifetimeStore.Product 와 같은 Offering · entitlement id. */
internal class IosProduct(
    private val purchases: IosPurchases,
    private val offering: String,
    private val entitlement: String,
) : com.sadturtleman.flowershoes.core.data.OneTimeProduct {
    private val _owned = MutableStateFlow(false)
    override val owned: StateFlow<Boolean> = _owned

    init {
        purchases.observeEntitlements { _owned.value = entitlement in it }
    }

    override suspend fun price(): String? = suspendCancellableCoroutine { c -> purchases.productPrice(offering) { if (c.isActive) c.resume(it) } }

    override suspend fun buy(): PurchaseOutcome = outcomeOf { purchases.buyProduct(offering, entitlement, it) }.also { if (it == PurchaseOutcome.Purchased) _owned.value = true }

    override suspend fun restore(): PurchaseOutcome = outcomeOf { purchases.restore(it) }.let { if (it == PurchaseOutcome.Purchased && !_owned.value) PurchaseOutcome.NothingToRestore else it }
}

private suspend fun outcomeOf(call: (PurchaseOutcomeCallback) -> Unit): PurchaseOutcome = suspendCancellableCoroutine { c ->
    call { code, message ->
        if (!c.isActive) return@call
        c.resume(
            when (code) {
                0 -> PurchaseOutcome.Purchased
                1 -> PurchaseOutcome.Cancelled
                2 -> PurchaseOutcome.NothingToRestore
                else -> PurchaseOutcome.Failed(message ?: "결제하지 못했어요. 잠시 뒤 다시 해 주세요")
            },
        )
    }
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
