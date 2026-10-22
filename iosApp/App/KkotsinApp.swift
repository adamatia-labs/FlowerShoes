import SwiftUI
import ComposeApp
import FirebaseCore

/// iOS 진입점. 화면은 전부 Compose Multiplatform 셸(:composeApp 의 MainViewController)이 그린다.
@main
struct KkotsinApp: App {
    @Environment(\.scenePhase) private var phase

    init() {
        // GoogleService-Info.plist 가 들어간 빌드에서만 Firebase 를 켠다. 없으면 혼자 모드.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
                .onOpenURL { url in DeepLinks.shared.open(uri: url.absoluteString) }
        }
        .onChange(of: phase) { _, now in
            // 앞으로 나오면 NSE 대기열 합치기 · 동기화 · 위젯 연결 정보 갱신
            if now == .active { MainViewControllerKt.onForeground() }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
