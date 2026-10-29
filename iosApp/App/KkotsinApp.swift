import SwiftUI
import ComposeApp
import FirebaseCore
import libzstd
import WidgetKit

/// iOS 진입점. 화면은 전부 Compose Multiplatform 셸(:composeApp 의 MainViewController)이 그린다.
@main
struct KkotsinApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var phase
    private let signIn = SocialSignIn()
    private let ads = AdBanners()
    private let purchases = LifetimePurchases()
    private let emulatorAuth: Bool

    init() {
        emulatorAuth = EmulatorFirebase.configureIfRequested()
        // 그림 문서 · 백업의 zstd. Android(zstd-jni)와 같은 프레임 형식.
        Zstd.shared.codec = SwiftZstd()
        // 위젯 스냅샷이 바뀌면 위젯을 새로 그린다(Kotlin 에서는 WidgetCenter 를 부를 수 없다).
        MainViewControllerKt.setWidgetReloader { WidgetCenter.shared.reloadAllTimelines() }
        // GoogleService-Info.plist 가 들어간 빌드에서만 Firebase 를 켠다. 없으면 혼자 모드.
        if !emulatorAuth && Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
        let project = FirebaseApp.app()?.options.projectID
        let endpoint = emulatorAuth
            ? "http://127.0.0.1:5001/demo-kkotsin/asia-northeast3/sendFromWidget"
            : project.map { "https://asia-northeast3-\($0).cloudfunctions.net/sendFromWidget" }
        MainViewControllerKt.setWidgetEndpoint(endpoint: endpoint)
    }

    var body: some Scene {
        WindowGroup {
            ComposeView(signIn: signIn, ads: ads, purchases: purchases, emulatorAuth: emulatorAuth).ignoresSafeArea()
                .onOpenURL { url in
                    // 카카오톡 · Google 로그인에서 돌아온 것이면 그쪽이, 아니면 앱 딥링크(kkotsin://)
                    if url.scheme == "kkotsin" {
                        DeepLinks.shared.open(uri: url.absoluteString)
                    } else {
                        _ = signIn.handle(url: url)
                    }
                }
        }
        .onChange(of: phase) { _, now in
            // 앞으로 나오면 기록기 다시 열기 · 프레임 계측 켜기, NSE 대기열 합치기 · 동기화 · 위젯 연결 정보 갱신
            if now == .active {
                MainViewControllerKt.onForeground()
            } else if now == .background {
                // 종료 직전의 마지막 신호 — 남은 이벤트를 보내고 기록기를 접는다. 프레임 계측도 멈춘다.
                MainViewControllerKt.onBackground()
            }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let signIn: SocialSignIn
    let ads: AdBanners
    let purchases: LifetimePurchases
    let emulatorAuth: Bool
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(signInUi: signIn, ads: ads, purchases: purchases, emulatorAuth: emulatorAuth)
        // scenePhase가 먼저 active가 되면 Koin 생성 전 호출은 아무 일도 하지 않는다.
        // 컨트롤러 초기화 뒤에도 한 번 호출해 첫 실행의 동기화/알림/위젯을 보장한다.
        MainViewControllerKt.onForeground()
        return controller
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// :core:compression 의 Compressor 를 libzstd 로 구현한다. 프레임에 원래 크기를 적는다(ZSTD_compress 기본).
final class SwiftZstd: Compressor {
    func compress(bytes: KotlinByteArray, level: Int32) -> KotlinByteArray {
        let src = bytes.toData()
        var out = Data(count: ZSTD_compressBound(src.count))
        let n = out.withUnsafeMutableBytes { dst in
            src.withUnsafeBytes { s in ZSTD_compress(dst.baseAddress, dst.count, s.baseAddress, s.count, level) }
        }
        precondition(ZSTD_isError(n) == 0, "zstd 압축 실패")
        return out.prefix(n).toKotlin()
    }

    func decompress(frame: KotlinByteArray) -> KotlinByteArray {
        let src = frame.toData()
        let size = src.withUnsafeBytes { ZSTD_getFrameContentSize($0.baseAddress, $0.count) }
        precondition(size != ZSTD_CONTENTSIZE_ERROR && size != ZSTD_CONTENTSIZE_UNKNOWN, "프레임에 원래 크기가 없다")
        var out = Data(count: Int(size))
        let n = out.withUnsafeMutableBytes { dst in
            src.withUnsafeBytes { s in ZSTD_decompress(dst.baseAddress, dst.count, s.baseAddress, s.count) }
        }
        precondition(ZSTD_isError(n) == 0, "zstd 풀기 실패")
        return out.prefix(n).toKotlin()
    }
}

private extension KotlinByteArray {
    func toData() -> Data {
        var d = Data(count: Int(size))
        for i in 0..<Int(size) { d[i] = UInt8(bitPattern: get(index: Int32(i))) }
        return d
    }
}

private extension Data {
    func toKotlin() -> KotlinByteArray {
        let a = KotlinByteArray(size: Int32(count))
        for (i, b) in enumerated() { a.set(index: Int32(i), value: Int8(bitPattern: b)) }
        return a
    }
}
