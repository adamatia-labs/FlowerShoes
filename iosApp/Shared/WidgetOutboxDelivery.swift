import Foundation

/// A widget-only restart must deliver recovered envelopes too; opening the app is not required.
enum WidgetOutboxDelivery {
    struct Destination {
        let coupleId: String
        let deviceId: String
        let partnerDeviceId: String
    }

    static func send(root: URL, destination: Destination, post: (Data) async throws -> Void) async throws {
        let directory = root.appendingPathComponent("widget-outbox")
        guard FileManager.default.fileExists(atPath: directory.path) else { return }
        let files = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        let pending: [(seq: Int64, body: Data)] = files.compactMap { file in
            guard file.pathExtension == "json", let body = try? Data(contentsOf: file),
                  let object = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
                  object["coupleId"] as? String == destination.coupleId,
                  object["fromDeviceId"] as? String == destination.deviceId,
                  object["toDeviceId"] as? String == destination.partnerDeviceId,
                  let encoded = object["header"] as? String, let bytes = Data(base64Encoded: encoded),
                  let header = OpEnvelope.Header.parse(Array(bytes)), header.seq > 0,
                  header.stream == "\(destination.deviceId):widget" else { return nil }
            return (header.seq, body)
        }
        for envelope in pending.sorted(by: { $0.seq < $1.seq }) {
            try await post(envelope.body)
            // Keep the original until the app has committed it into the retransmission log.
        }
    }
}
