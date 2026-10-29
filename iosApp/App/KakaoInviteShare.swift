import ComposeApp
import KakaoSDKCommon
import KakaoSDKShare
import UIKit

/// :composeApp 의 IosInviteShare. 초대 링크를 카카오 사용자 정의 피드 템플릿으로 보낸다(인자 name · token).
/// 카카오 키가 없거나(SocialSignIn 이 SDK 를 초기화하지 않았다) 카카오톡이 없으면 공유 시트로 [fallbackText].
final class KakaoInviteShare: NSObject, IosInviteShare {
    private let kakaoKey = Bundle.main.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String ?? ""
    private var kakaoConfigured: Bool { !kakaoKey.isEmpty && !kakaoKey.hasPrefix("$(") }

    func share(templateId: Int64, args: [String: String], fallbackText: String) {
        DispatchQueue.main.async {
            guard self.kakaoConfigured, ShareApi.isKakaoTalkSharingAvailable() else {
                Self.sheet(fallbackText)
                return
            }
            ShareApi.shared.shareCustom(templateId: templateId, templateArgs: args) { result, error in
                if let result, error == nil {
                    UIApplication.shared.open(result.url)
                } else {
                    Self.sheet(fallbackText)
                }
            }
        }
    }

    private static func sheet(_ text: String) {
        let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        top?.present(UIActivityViewController(activityItems: [text], applicationActivities: nil), animated: true)
    }
}
