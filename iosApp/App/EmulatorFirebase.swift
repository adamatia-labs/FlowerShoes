import Foundation
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import FirebaseStorage
import FirebaseMessaging

/// 출시 빌드에서는 항상 false. 실제 프로젝트 설정과 섞이지 않도록 localhost + demo 프로젝트로 고정한다.
enum EmulatorFirebase {
    static func configureIfRequested() -> Bool {
        #if DEBUG
        guard ProcessInfo.processInfo.environment["KKOTSIN_FIREBASE_EMULATOR"] == "1" else { return false }
        let options = FirebaseOptions(googleAppID: "1:000000000000:ios:0000000000000000", gcmSenderID: "000000000000")
        options.projectID = "demo-kkotsin"
        // 실제 프로젝트 키가 아닌, SDK 형식 검사만 통과하는 로컬 전용 값.
        options.apiKey = "AIzaSy" + String(repeating: "0", count: 33)
        options.storageBucket = "demo-kkotsin.appspot.com"
        FirebaseApp.configure(options: options)
        Auth.auth().useEmulator(withHost: "127.0.0.1", port: 9099)
        let firestore = Firestore.firestore()
        firestore.useEmulator(withHost: "127.0.0.1", port: 8080)
        let settings = firestore.settings
        settings.isSSLEnabled = false
        firestore.settings = settings
        Functions.functions(region: "asia-northeast3").useEmulator(withHost: "127.0.0.1", port: 5001)
        Storage.storage().useEmulator(withHost: "127.0.0.1", port: 9199)
        // FCM에는 로컬 에뮬레이터가 없다. 가짜 프로젝트로 외부 토큰 등록을 시도하지 않는다.
        Messaging.messaging().isAutoInitEnabled = false
        return true
        #else
        return false
        #endif
    }
}
