import UIKit
import ComposeApp
import GoogleMobileAds
import UserMessagingPlatform

/// AdMob 배너(Figma 9 · 광고 지면: 캘린더 · 휴가 · 설정에 1개씩).
///
/// 동의(UMP) → SDK 시작은 앱이 앞으로 나올 때 한 번. 광고를 요청할 수 없는 동안 만든 배너는
/// 빈 뷰로 두었다가 SDK 가 켜지면 그때 요청한다.
final class AdBanners: NSObject, IosAdBanners {
    #if DEBUG
    /// Google 테스트 단위 — 개발 중 자기 광고를 보거나 누르는 것은 정책 위반이다.
    private let unitId = "ca-app-pub-3940256099942544/2435281174"
    #else
    private let unitId = "ca-app-pub-9179621242550991/4592697065"
    #endif

    private var started = false
    private var waiting: [() -> Void] = []

    /// 동의가 필요한 지역(EEA · 영국 등)이면 양식을 띄우고, 광고를 요청해도 될 때 SDK 를 켠다.
    func gatherConsent() {
        let info = ConsentInformation.shared
        info.requestConsentInfoUpdate(with: RequestParameters()) { [weak self] error in
            guard let self else { return }
            if error != nil { self.startIfAllowed(); return }
            Task { @MainActor in
                try? await ConsentForm.loadAndPresentIfRequired(from: Self.topViewController())
                self.startIfAllowed()
            }
        }
        // 지난 실행에서 이미 동의를 받았으면 갱신을 기다리지 않는다.
        startIfAllowed()
    }

    private func startIfAllowed() {
        guard ConsentInformation.shared.canRequestAds, !started else { return }
        started = true
        MobileAds.shared.start { [weak self] _ in
            DispatchQueue.main.async {
                let pending = self?.waiting ?? []
                self?.waiting.removeAll()
                pending.forEach { $0() }
            }
        }
    }

    func makeBanner(widthPoints: Double, onHeight: AdBannerHeight) -> UIView {
        let banner = BannerView(adSize: currentOrientationAnchoredAdaptiveBanner(width: CGFloat(widthPoints)))
        banner.adUnitID = unitId
        let delegate = Delegate(onHeight: onHeight)
        banner.delegate = delegate
        // 배너가 살아 있는 동안 delegate 도 붙잡아 둔다(delegate 는 weak).
        objc_setAssociatedObject(banner, &Delegate.key, delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        let load = { [weak banner] in
            guard let banner else { return }
            banner.rootViewController = Self.topViewController()
            banner.load(Request())
        }
        if started { load() } else { waiting.append(load) }
        return banner
    }

    private final class Delegate: NSObject, BannerViewDelegate {
        static var key: UInt8 = 0
        let onHeight: AdBannerHeight
        init(onHeight: AdBannerHeight) { self.onHeight = onHeight }

        func bannerViewDidReceiveAd(_ bannerView: BannerView) {
            onHeight.changed(points: Double(bannerView.adSize.size.height))
        }

        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
            onHeight.changed(points: 0)
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
