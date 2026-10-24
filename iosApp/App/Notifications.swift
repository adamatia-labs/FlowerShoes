import UIKit
import UserNotifications
import FirebaseCore
import FirebaseAuth
import FirebaseMessaging
import ComposeApp

/// 시스템 알림 권한 · APNs/FCM · 알림 탭을 앱의 동기화와 내비게이션에 잇는다.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    private var authListener: AuthStateDidChangeListenerHandle?
    private var token: String?
    private var requesting = false

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        MainViewControllerKt.setNotificationRegistration { [weak self] in self?.registerNotifications() }
        return true
    }

    private var remoteEnabled: Bool {
        FirebaseApp.app() != nil && ProcessInfo.processInfo.environment["KKOTSIN_FIREBASE_EMULATOR"] != "1"
    }

    private func registerNotifications() {
        guard !requesting else { return }
        requesting = true
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { [weak self] settings in
            guard let self else { return }
            if settings.authorizationStatus == .notDetermined {
                center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
                    DispatchQueue.main.async { self.finishRegistration(granted) }
                }
            } else {
                DispatchQueue.main.async { self.finishRegistration(settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional) }
            }
        }
    }

    private func finishRegistration(_ granted: Bool) {
        requesting = false
        guard granted, remoteEnabled else { return }
        Messaging.messaging().isAutoInitEnabled = true
        Messaging.messaging().delegate = self
        if authListener == nil {
            authListener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
                if user != nil, let token = self?.token { MainViewControllerKt.onPushToken(token: token) }
            }
        }
        UIApplication.shared.registerForRemoteNotifications()
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        guard remoteEnabled else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        token = fcmToken
        if let fcmToken { MainViewControllerKt.onPushToken(token: fcmToken) }
    }

    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        MainViewControllerKt.onRemoteNotification { completionHandler(.newData) }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        MainViewControllerKt.onRemoteNotification {}
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if let link = info["link"] as? String {
            DeepLinks.shared.open(uri: link)
        } else if let letter = info["letterId"] as? String {
            DeepLinks.shared.open(uri: "kkotsin://letter/\(letter)")
        } else if ["letter", "letter-drawing"].contains(info["kind"] as? String ?? "") {
            // 서버는 암호화된 편지 본문의 ID를 알 수 없어 opId만 보낸다.
            DeepLinks.shared.open(uri: "kkotsin://inbox")
        } else {
            DeepLinks.shared.open(uri: "kkotsin://home")
        }
        MainViewControllerKt.onRemoteNotification { completionHandler() }
    }
}
