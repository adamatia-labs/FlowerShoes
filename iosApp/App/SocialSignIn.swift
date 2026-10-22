import AuthenticationServices
import ComposeApp
import CryptoKit
import GoogleSignIn
import KakaoSDKAuth
import KakaoSDKCommon
import KakaoSDKUser
import UIKit

/// :core:firebase 의 SocialSignInUi. 제공자 화면만 띄우고 토큰을 돌려준다 — Firebase 계정 연결은 Kotlin(FirebaseAuthGateway)이 한다.
/// 키는 Info.plist(KakaoNativeAppKey, GIDClientID)에서. 비어 있으면 그 제공자는 "설정 전".
final class SocialSignIn: NSObject, SocialSignInUi {
    private let kakaoKey = Bundle.main.object(forInfoDictionaryKey: "KakaoNativeAppKey") as? String ?? ""
    private let googleClientId = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String ?? ""

    var kakaoConfigured: Bool { !kakaoKey.isEmpty && !kakaoKey.hasPrefix("$(") }
    var googleConfigured: Bool { !googleClientId.isEmpty && !googleClientId.hasPrefix("$(") }

    override init() {
        super.init()
        if kakaoConfigured { KakaoSDK.initSDK(appKey: kakaoKey) }
    }

    /// 카카오톡 · Google 에서 앱으로 돌아오는 URL. 처리했으면 true.
    @MainActor func handle(url: URL) -> Bool {
        if AuthApi.isKakaoTalkLoginUrl(url) { return AuthController.handleOpenUrl(url: url) }
        return GIDSignIn.sharedInstance.handle(url)
    }

    // MARK: 카카오

    func kakao(done: @escaping (ProviderToken) -> Void) {
        let callback: (OAuthToken?, Error?) -> Void = { token, error in
            if let error = error as? SdkError, case .ClientFailed(let reason, _) = error, reason == .Cancelled {
                done(ProviderToken(token: nil, rawNonce: nil, error: nil, authCode: nil))
            } else if let error {
                done(ProviderToken(token: nil, rawNonce: nil, error: error.localizedDescription, authCode: nil))
            } else {
                done(ProviderToken(token: token?.accessToken, rawNonce: nil, error: nil, authCode: nil))
            }
        }
        DispatchQueue.main.async {
            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(completion: callback)
            } else {
                UserApi.shared.loginWithKakaoAccount(completion: callback)
            }
        }
    }

    // MARK: Apple

    private var appleDone: ((ProviderToken) -> Void)?
    private var appleNonce: String?

    func apple(done: @escaping (ProviderToken) -> Void) {
        let nonce = Self.randomNonce()
        appleNonce = nonce
        appleDone = done
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        DispatchQueue.main.async { controller.performRequests() }
    }

    private static func randomNonce(length: Int = 32) -> String {
        let chars = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        precondition(SecRandomCopyBytes(kSecRandomDefault, length, &bytes) == errSecSuccess)
        return String(bytes.map { chars[Int($0) % chars.count] })
    }

    // MARK: Google

    func google(done: @escaping (ProviderToken) -> Void) {
        DispatchQueue.main.async {
            guard let presenter = Self.topViewController() else {
                done(ProviderToken(token: nil, rawNonce: nil, error: "화면을 찾지 못했어요", authCode: nil))
                return
            }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: self.googleClientId)
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                if let error = error as? NSError, error.code == GIDSignInError.canceled.rawValue {
                    done(ProviderToken(token: nil, rawNonce: nil, error: nil, authCode: nil))
                } else if let error {
                    done(ProviderToken(token: nil, rawNonce: nil, error: error.localizedDescription, authCode: nil))
                } else {
                    done(ProviderToken(token: result?.user.idToken?.tokenString, rawNonce: nil, error: nil, authCode: nil))
                }
            }
        }
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

extension SocialSignIn: ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let credential = authorization.credential as? ASAuthorizationAppleIDCredential
        let token = credential?.identityToken.flatMap { String(data: $0, encoding: .utf8) }
        let code = credential?.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        appleDone?(ProviderToken(token: token, rawNonce: appleNonce, error: token == nil ? "Apple 토큰이 없어요" : nil, authCode: code))
        appleDone = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        appleDone?(ProviderToken(token: nil, rawNonce: nil, error: cancelled ? nil : error.localizedDescription, authCode: nil))
        appleDone = nil
    }
}
