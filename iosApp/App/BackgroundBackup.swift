import UIKit
import Network
import ComposeApp

/// Automatic backup gets a bounded background window and respects the cellular preference.
final class BackgroundBackup {
    static let shared = BackgroundBackup()
    private let monitor = NWPathMonitor()
    private var connected = false
    private var wifi = false
    private var task: UIBackgroundTaskIdentifier = .invalid

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.connected = path.status == .satisfied
                self?.wifi = path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet)
            }
        }
        monitor.start(queue: DispatchQueue(label: "kkotsin.backup.network"))
    }

    func start() {
        guard task == .invalid, connected else { return }
        task = UIApplication.shared.beginBackgroundTask(withName: "Encrypted backup") { [weak self] in
            IosAutoBackupKt.cancelBackgroundBackup()
            self?.finish()
        }
        guard task != .invalid else { return }
        IosAutoBackupKt.onBackgroundBackup(connected: connected, wifi: wifi) { [weak self] in
            DispatchQueue.main.async { self?.finish() }
        }
    }

    private func finish() {
        guard task != .invalid else { return }
        let ended = task
        task = .invalid
        UIApplication.shared.endBackgroundTask(ended)
    }
}
