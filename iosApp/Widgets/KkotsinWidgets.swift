import WidgetKit
import SwiftUI
import AppIntents

/// iOS 위젯(설계 8장). 앱 DB 를 읽지 않고 App Group 의 스냅샷만 읽는다.
struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: SharedStore.WidgetSnapshot?
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: .now, snapshot: nil) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: .now, snapshot: SharedStore.readSnapshot()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        // D-day 는 자정에 바뀐다. 앱 · NSE 가 스냅샷을 바꾸면 WidgetCenter.reloadAllTimelines() 로 앞당긴다.
        let midnight = Calendar.current.startOfDay(for: .now.addingTimeInterval(86_400))
        completion(Timeline(entries: [SnapshotEntry(date: .now, snapshot: SharedStore.readSnapshot())], policy: .after(midnight)))
    }
}

private let rose = Color(red: 0xC7 / 255, green: 0x2C / 255, blue: 0x4D / 255)

struct DdayWidgetView: View {
    let entry: SnapshotEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("전역까지").font(.caption).foregroundStyle(.secondary)
            if let d = entry.snapshot?.daysLeft {
                Text(d > 0 ? "D-\(d)" : d == 0 ? "D-DAY" : "전역 +\(-d)").font(.system(size: 34, weight: .bold))
                if let p = entry.snapshot?.progress { Text("\(Int(p * 1000) / 10)%").font(.caption2) }
            } else {
                Text("입대일을 넣어 주세요").font(.footnote)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(.background, for: .widget)
    }
}

struct DdayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dday", provider: SnapshotProvider()) { DdayWidgetView(entry: $0) }
            .configurationDisplayName("전역 D-day")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

/// 위젯에서 바로 보고싶어(iOS 17+ App Intent). 위젯 프로세스에는 Firebase 가 없으므로
/// Op 을 swift-sodium 으로 암호화 · 서명해 Cloud Function `sendFromWidget` 에 보낸다.
/// 스트림은 "{deviceId}:widget", seq 는 App Group 카운터 파일(파일 잠금 안에서 1 올림).
/// 보낸 Op 의 평문 사본을 widget-outbox 에 남겨 앱이 SyncOp 로그로 가져간다(재동기화용).
struct SendMissYouIntent: AppIntent {
    static var title: LocalizedStringResource = "보고싶어 보내기"

    func perform() async throws -> some IntentResult {
        try await WidgetSender.sendMissYou()
        WidgetCenter.shared.reloadTimelines(ofKind: "missyou")
        return .result()
    }
}

struct MissYouWidgetView: View {
    let entry: SnapshotEntry
    var body: some View {
        Button(intent: SendMissYouIntent()) {
            VStack(spacing: 6) {
                Image(systemName: "heart.fill").font(.system(size: 34)).foregroundStyle(.white)
                Text("보고싶어").font(.headline).foregroundStyle(.white)
                if let n = entry.snapshot?.missYouFromPartnerToday, n > 0 {
                    Text("오늘 받은 \(n)번").font(.caption2).foregroundStyle(.white.opacity(0.9))
                }
            }
        }
        .buttonStyle(.plain)
        .containerBackground(rose, for: .widget) // "마음" 요소는 테마와 무관하게 로즈
    }
}

struct MissYouWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "missyou", provider: SnapshotProvider()) { MissYouWidgetView(entry: $0) }
            .configurationDisplayName("보고싶어")
            .supportedFamilies([.systemSmall])
    }
}

@main
struct KkotsinWidgetBundle: WidgetBundle {
    var body: some Widget {
        DdayWidget()
        MissYouWidget()
    }
}
