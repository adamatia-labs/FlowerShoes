import SwiftUI
import ComposeApp

/// iOS 진입점. 화면은 전부 Compose Multiplatform 셸(:composeApp 의 MainViewController)이 그린다.
@main
struct KkotsinApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
