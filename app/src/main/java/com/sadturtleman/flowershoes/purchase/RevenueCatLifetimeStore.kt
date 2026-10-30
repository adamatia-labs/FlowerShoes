package com.sadturtleman.flowershoes.purchase

import android.app.Activity
import android.app.Application
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.sadturtleman.flowershoes.core.data.BookStore
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.LifetimeStore
import com.sadturtleman.flowershoes.core.data.PurchaseOutcome
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 꽃신 평생 = RevenueCat 의 지금 Offering 의 평생(lifetime) 상품(없으면 첫 상품).
 * 이용권은 "추억 책([BOOK_ENTITLEMENT]) 말고 활성 entitlement 가 하나라도 있으면" 산 것으로 본다 — 대시보드의 이름에 묶이지 않게.
 * 추억 책 PDF 는 Offering [BOOK_OFFERING] 의 첫 상품, entitlement [BOOK_ENTITLEMENT] 이다([book]).
 * RevenueCat 사용자 id 는 꽃신 계정 id 와 같게 맞춰, 폰을 바꿔도(같은 계정이면) 이용권이 따라온다.
 */
class RevenueCatLifetimeStore(app: Application, apiKey: String, private val identity: Identity, debug: Boolean) : LifetimeStore {

    private val _owned = MutableStateFlow(false)
    override val owned: StateFlow<Boolean> = _owned

    /** 결제 시트를 띄울 화면. MainActivity 가 만들어질 때마다 바꿔 끼운다. */
    var activity: WeakReference<Activity> = WeakReference(null)

    init {
        if (debug) Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { update(it) }
    }

    private val _bookOwned = MutableStateFlow(false)

    private fun update(info: CustomerInfo) {
        val active = info.entitlements.active.keys
        _owned.value = active.any { it != BOOK_ENTITLEMENT }
        _bookOwned.value = BOOK_ENTITLEMENT in active
    }

    /** 따로 파는 한 번 결제 상품: Offering [offering] 의 첫 상품, 산 표시는 [flow]. */
    private open inner class Product(private val offering: String, private val flow: MutableStateFlow<Boolean>) {
        val owned: StateFlow<Boolean> = flow

        private suspend fun pkg(): Package? = Purchases.sharedInstance.awaitOfferings().all[offering]?.availablePackages?.firstOrNull()

        suspend fun price(): String? = runCatching { pkg()?.product?.price?.formatted }.getOrNull()

        suspend fun buy(): PurchaseOutcome = purchase({ pkg() }) { flow.value }

        suspend fun restore(): PurchaseOutcome = this@RevenueCatLifetimeStore.restore { flow.value }
    }

    /** 추억 책 PDF(같은 RevenueCat 사용자 · 같은 결제 흐름). */
    val book: BookStore = object : Product(BOOK_OFFERING, _bookOwned), BookStore {}

    /** 꽃신 계정으로 로그인해 둔다(아직 신원이 없으면 익명 그대로). */
    private suspend fun ensureUser() {
        val uid = runCatching { identity.userId }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (Purchases.sharedInstance.appUserID != uid) runCatching { update(Purchases.sharedInstance.awaitLogIn(uid).customerInfo) }
    }

    private suspend fun lifetimePackage(): Package? {
        val current = Purchases.sharedInstance.awaitOfferings().current ?: return null
        return current.lifetime ?: current.availablePackages.firstOrNull { it.packageType == PackageType.LIFETIME } ?: current.availablePackages.firstOrNull()
    }

    override suspend fun price(): String? = runCatching { lifetimePackage()?.product?.price?.formatted }.getOrNull()

    override suspend fun buy(): PurchaseOutcome = purchase({ lifetimePackage() }) { _owned.value }

    override suspend fun restore(): PurchaseOutcome = restore { _owned.value }

    private suspend fun purchase(pkgOf: suspend () -> Package?, has: () -> Boolean): PurchaseOutcome {
        val host = activity.get() ?: return PurchaseOutcome.Failed("결제 화면을 열 수 없어요")
        return try {
            ensureUser()
            val pkg = pkgOf() ?: return PurchaseOutcome.Failed("상품 정보를 불러오지 못했어요")
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(host, pkg).build())
            update(result.customerInfo)
            if (has()) PurchaseOutcome.Purchased else PurchaseOutcome.Failed("결제는 됐지만 이용권을 확인하지 못했어요. 구매 복원을 눌러 주세요")
        } catch (e: PurchasesTransactionException) {
            when {
                e.userCancelled -> PurchaseOutcome.Cancelled
                e.code in NOT_CHARGED -> PurchaseOutcome.Failed(NOT_CHARGED_MESSAGE)
                // 결제 뒤 확인이 끊겼을 수도 있는 오류(네트워크 · 스토어 · 모름)엔 "돈이 안 나갔다"고 하지 않는다.
                e.code in SPECIFIC -> PurchaseOutcome.Failed(message(e.code))
                else -> PurchaseOutcome.Failed("결제를 확인하지 못했어요. 돈이 나갔다면 구매 복원을 눌러 주세요")
            }
        } catch (e: PurchasesException) {
            // 결제 창을 열기 전(계정 맞추기 · 상품 읽기)이라 돈은 나가지 않았다.
            if (e.code in SPECIFIC) PurchaseOutcome.Failed(message(e.code)) else PurchaseOutcome.Failed(NOT_CHARGED_MESSAGE)
        }
    }

    private suspend fun restore(has: () -> Boolean): PurchaseOutcome = try {
        ensureUser()
        update(Purchases.sharedInstance.awaitRestore())
        if (has()) PurchaseOutcome.Purchased else PurchaseOutcome.NothingToRestore
    } catch (e: PurchasesException) {
        PurchaseOutcome.Failed(message(e.code))
    }

    override suspend fun refresh() {
        ensureUser()
        runCatching { update(Purchases.sharedInstance.awaitCustomerInfo()) }
    }

    private companion object {
        /** 구매 실패 토스트(Figma 8 · 꽃신 평생 · 실패). 결제가 시작되지 않았거나 스토어가 거절한 것이 확실할 때만. */
        const val NOT_CHARGED_MESSAGE = "결제가 안 됐어요. 돈은 나가지 않았어요"

        /** 스토어가 결제를 받지 않은 오류(상품 · 요청이 잘못됨). */
        val NOT_CHARGED = setOf(
            PurchasesErrorCode.PurchaseInvalidError,
            PurchasesErrorCode.ProductNotAvailableForPurchaseError,
        )

        /** 따로 알릴 문구가 있는 오류([message]). */
        val SPECIFIC = setOf(
            PurchasesErrorCode.NetworkError,
            PurchasesErrorCode.ProductAlreadyPurchasedError,
            PurchasesErrorCode.PaymentPendingError,
            PurchasesErrorCode.StoreProblemError,
            PurchasesErrorCode.PurchaseNotAllowedError,
        )

        /** RevenueCat 대시보드: 추억 책 PDF 의 Offering · entitlement id. */
        const val BOOK_OFFERING = "memory_book"
        const val BOOK_ENTITLEMENT = "memory_book"
    }

    /** 오류마다 정해진 문구(구매 · 복원 공통). */
    private fun message(code: PurchasesErrorCode): String = when (code) {
        PurchasesErrorCode.NetworkError -> "인터넷 연결을 확인해 주세요"
        PurchasesErrorCode.ProductAlreadyPurchasedError -> "이미 산 상품이에요. 구매 복원을 눌러 주세요"
        PurchasesErrorCode.PaymentPendingError -> "결제를 확인하는 중이에요. 끝나면 바로 열려요"
        PurchasesErrorCode.StoreProblemError -> "스토어에 문제가 있어요. 잠시 뒤 다시 해 주세요"
        PurchasesErrorCode.PurchaseNotAllowedError -> "이 기기에서는 결제할 수 없어요"
        else -> "결제하지 못했어요. 잠시 뒤 다시 해 주세요"
    }
}
