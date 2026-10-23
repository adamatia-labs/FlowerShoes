import Foundation
import ComposeApp
import RevenueCat

/// 꽃신 평생 결제(RevenueCat). 사는 것은 지금 Offering 의 lifetime 패키지(없으면 첫 패키지),
/// 이용 중은 활성 entitlement 가 하나라도 있으면 — Android(RevenueCatLifetimeStore)와 같은 규칙.
/// RevenueCat 사용자 id 는 꽃신 계정 id 로 맞춘다(같은 계정이면 폰을 바꿔도 따라온다).
final class LifetimePurchases: NSObject, IosPurchases, PurchasesDelegate {
    /// 공개 SDK 키(앱에 실려 나가는 값). 실제 키는 Config.xcconfig 의 REVENUECAT_API_KEY 로 — 비어 있으면 테스트 스토어 키.
    private static let testKey = "test_chjLRvvSdzZezYKFbIWfRxJMRjg"
    private var listener: LifetimeOwnedCallback?

    override init() {
        super.init()
        let configured = (Bundle.main.object(forInfoDictionaryKey: "RevenueCatAPIKey") as? String) ?? ""
        let key = configured.isEmpty || configured.hasPrefix("$(") ? Self.testKey : configured
        #if DEBUG
        RevenueCat.Purchases.logLevel = .debug
        #endif
        RevenueCat.Purchases.configure(withAPIKey: key)
        RevenueCat.Purchases.shared.delegate = self
    }

    private static func owned(_ info: CustomerInfo) -> Bool { !info.entitlements.active.isEmpty }

    func purchases(_ purchases: RevenueCat.Purchases, receivedUpdated customerInfo: CustomerInfo) {
        let owned = Self.owned(customerInfo)
        DispatchQueue.main.async { self.listener?.changed(owned: owned) }
    }

    func observe(listener: LifetimeOwnedCallback) { self.listener = listener }

    func refresh(userId: String?, done: LifetimeOwnedCallback) {
        Task {
            var owned = false
            if let userId, !userId.isEmpty, RevenueCat.Purchases.shared.appUserID != userId,
               let result = try? await RevenueCat.Purchases.shared.logIn(userId) {
                owned = Self.owned(result.customerInfo)
            } else if let info = try? await RevenueCat.Purchases.shared.customerInfo() {
                owned = Self.owned(info)
            }
            await MainActor.run { done.changed(owned: owned) }
        }
    }

    private func lifetimePackage() async -> Package? {
        guard let current = try? await RevenueCat.Purchases.shared.offerings().current else { return nil }
        return current.lifetime ?? current.availablePackages.first { $0.packageType == .lifetime } ?? current.availablePackages.first
    }

    func price(done: PurchasePriceCallback) {
        Task {
            let price = await lifetimePackage()?.storeProduct.localizedPriceString
            await MainActor.run { done.result(price: price) }
        }
    }

    func buy(done: PurchaseOutcomeCallback) {
        Task {
            let (code, message) = await purchase()
            await MainActor.run { done.result(code: code, message: message) }
        }
    }

    private func purchase() async -> (Int32, String?) {
        guard let package = await lifetimePackage() else { return (3, "상품 정보를 불러오지 못했어요") }
        do {
            let result = try await RevenueCat.Purchases.shared.purchase(package: package)
            if result.userCancelled { return (1, nil) }
            return Self.owned(result.customerInfo) ? (0, nil) : (3, "결제는 됐지만 이용권을 확인하지 못했어요. 구매 복원을 눌러 주세요")
        } catch {
            return Self.failure(error)
        }
    }

    func restore(done: PurchaseOutcomeCallback) {
        Task {
            let outcome: (Int32, String?)
            do {
                let info = try await RevenueCat.Purchases.shared.restorePurchases()
                outcome = Self.owned(info) ? (0, nil) : (2, nil)
            } catch {
                outcome = Self.failure(error)
            }
            await MainActor.run { done.result(code: outcome.0, message: outcome.1) }
        }
    }

    private static func failure(_ error: Error) -> (Int32, String?) {
        switch (error as? RevenueCat.ErrorCode) ?? .unknownError {
        case .purchaseCancelledError: return (1, nil)
        case .networkError, .offlineConnectionError: return (3, "인터넷 연결을 확인해 주세요")
        case .productAlreadyPurchasedError: return (3, "이미 산 상품이에요. 구매 복원을 눌러 주세요")
        case .paymentPendingError: return (3, "결제를 확인하는 중이에요. 끝나면 바로 열려요")
        case .storeProblemError: return (3, "스토어에 문제가 있어요. 잠시 뒤 다시 해 주세요")
        case .purchaseNotAllowedError: return (3, "이 기기에서는 결제할 수 없어요")
        default: return (3, "결제하지 못했어요. 잠시 뒤 다시 해 주세요")
        }
    }
}
