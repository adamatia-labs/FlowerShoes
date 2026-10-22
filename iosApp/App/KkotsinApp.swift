import SwiftUI
import ComposeApp
import FirebaseCore
import libzstd

/// iOS 진입점. 화면은 전부 Compose Multiplatform 셸(:composeApp 의 MainViewController)이 그린다.
@main
struct KkotsinApp: App {
    @Environment(\.scenePhase) private var phase
    private let signIn = SocialSignIn()

    init() {
        // 그림 문서 · 백업의 zstd. Android(zstd-jni)와 같은 프레임 형식.
        Zstd.shared.codec = SwiftZstd()
        // GoogleService-Info.plist 가 들어간 빌드에서만 Firebase 를 켠다. 없으면 혼자 모드.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView(signIn: signIn).ignoresSafeArea()
                .onOpenURL { url in
                    // 카카오톡 · Google 로그인에서 돌아온 것이면 그쪽이, 아니면 앱 딥링크(kkotsin://)
                    if !signIn.handle(url: url) { DeepLinks.shared.open(uri: url.absoluteString) }
                }
        }
        .onChange(of: phase) { _, now in
            // 앞으로 나오면 NSE 대기열 합치기 · 동기화 · 위젯 연결 정보 갱신
            if now == .active { MainViewControllerKt.onForeground() }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let signIn: SocialSignIn
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController(signInUi: signIn) }
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
