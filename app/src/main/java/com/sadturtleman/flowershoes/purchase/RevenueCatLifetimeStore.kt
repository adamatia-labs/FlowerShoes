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
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.LifetimeStore
import com.sadturtleman.flowershoes.core.data.PurchaseOutcome
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 꽃신 평생 = RevenueCat 의 지금 Offering 의 평생(lifetime) 상품(없으면 첫 상품).
 * 이용권은 "활성 entitlement 가 하나라도 있으면" 산 것으로 본다 — 대시보드의 entitlement 이름에 묶이지 않게.
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

    private fun update(info: CustomerInfo) {
        _owned.value = info.entitlements.active.isNotEmpty()
    }

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

    override suspend fun buy(): PurchaseOutcome {
        val host = activity.get() ?: return PurchaseOutcome.Failed("결제 화면을 열 수 없어요")
        return try {
            ensureUser()
            val pkg = lifetimePackage() ?: return PurchaseOutcome.Failed("상품 정보를 불러오지 못했어요")
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(host, pkg).build())
            update(result.customerInfo)
            if (_owned.value) PurchaseOutcome.Purchased else PurchaseOutcome.Failed("결제는 됐지만 이용권을 확인하지 못했어요. 구매 복원을 눌러 주세요")
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseOutcome.Cancelled else PurchaseOutcome.Failed(message(e.code))
        } catch (e: PurchasesException) {
            PurchaseOutcome.Failed(message(e.code))
        }
    }

    override suspend fun restore(): PurchaseOutcome = try {
        ensureUser()
        update(Purchases.sharedInstance.awaitRestore())
        if (_owned.value) PurchaseOutcome.Purchased else PurchaseOutcome.NothingToRestore
    } catch (e: PurchasesException) {
        PurchaseOutcome.Failed(message(e.code))
    }

    override suspend fun refresh() {
        ensureUser()
        runCatching { update(Purchases.sharedInstance.awaitCustomerInfo()) }
    }

    private fun message(code: PurchasesErrorCode): String = when (code) {
        PurchasesErrorCode.NetworkError -> "인터넷 연결을 확인해 주세요"
        PurchasesErrorCode.ProductAlreadyPurchasedError -> "이미 산 상품이에요. 구매 복원을 눌러 주세요"
        PurchasesErrorCode.PaymentPendingError -> "결제를 확인하는 중이에요. 끝나면 바로 열려요"
        PurchasesErrorCode.StoreProblemError -> "스토어에 문제가 있어요. 잠시 뒤 다시 해 주세요"
        PurchasesErrorCode.PurchaseNotAllowedError -> "이 기기에서는 결제할 수 없어요"
        else -> "결제하지 못했어요. 잠시 뒤 다시 해 주세요"
    }
}
