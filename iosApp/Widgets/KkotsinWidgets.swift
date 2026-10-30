import WidgetKit
import SwiftUI
import AppIntents

/// iOS 위젯 v2(Figma 9 · 위젯 383:5321, 설계 8장). 앱 DB 를 읽지 않고 App Group 의 스냅샷만 읽는다.
/// 글자는 스냅샷에 만들어져 있고(Android 위젯과 같은 문구), 색은 WidgetPalette 한 곳에서 테마 · 라이트/다크로 고른다.
struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: SharedStore.WidgetSnapshot?

    /// 보고싶어를 보낸 지 1분 안이면 "보냈어요" 상태.
    var justSent: Bool {
        guard let at = snapshot?.missYouJustSentAtMs else { return false }
        return date.timeIntervalSince1970 * 1000 < Double(at) + 60_000
    }
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: .now, snapshot: nil) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: .now, snapshot: SharedStore.readSnapshot()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        // D-day 는 자정에 바뀐다. 앱 · NSE 가 스냅샷을 바꾸면 WidgetCenter.reloadAllTimelines() 로 앞당긴다.
        let snapshot = SharedStore.readSnapshot()
        var entries = [SnapshotEntry(date: .now, snapshot: snapshot)]
        // 방금 보낸 보고싶어: 1분 뒤 기본 상태로 돌아간다.
        if let at = snapshot?.missYouJustSentAtMs {
            let back = Date(timeIntervalSince1970: Double(at) / 1000 + 60)
            if back > .now { entries.append(SnapshotEntry(date: back, snapshot: snapshot)) }
        }
        let midnight = Calendar.current.startOfDay(for: .now.addingTimeInterval(86_400))
        completion(Timeline(entries: entries, policy: .after(midnight)))
    }
}

/// 위젯마다 같은 틀: 종이 표면 + 테마 · 다크 팔레트. 여백은 시스템 위젯 여백(16)을 그대로 쓴다.
private struct PaperCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    let entry: SnapshotEntry
    @ViewBuilder let content: (WidgetPalette) -> Content

    var body: some View {
        let p = WidgetPalette(theme: entry.snapshot?.theme, dark: scheme == .dark)
        content(p)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .containerBackground(p.surface, for: .widget)
    }
}

/// 종이 태그(보여 주기만 하는 물건이라 살짝 기울인다): tag 바탕 위 ink 글자만.
private struct PaperTagView: View {
    let tag: SharedStore.WidgetTag
    let p: WidgetPalette
    let angle: Double
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(tag.label).font(WidgetPalette.caption)
            Text(tag.value).font(WidgetPalette.label)
        }
        .lineLimit(1)
        .foregroundStyle(p.ink)
        .padding(.horizontal, 8).padding(.vertical, 5)
        .background(p.tag)
        .rotationEffect(.degrees(angle))
    }
}

private struct PaperTags: View {
    let tags: [SharedStore.WidgetTag]
    let p: WidgetPalette
    private let angles: [Double] = [2, -1.5, 1]
    var body: some View {
        HStack(spacing: 8) {
            ForEach(Array(tags.enumerated()), id: \.offset) { i, tag in PaperTagView(tag: tag, p: p, angle: angles[i % angles.count]) }
        }
    }
}

/// 복무 진행(Service Progress): 지난 길(accent) · 남은 길(점선) + 입대 · 진행률 · 전역.
private struct ServiceProgressView: View {
    let s: SharedStore.WidgetSnapshot
    let p: WidgetPalette
    var body: some View {
        VStack(spacing: 8) {
            GeometryReader { geo in
                let done = geo.size.width * CGFloat(s.progress ?? 0)
                ZStack(alignment: .leading) {
                    Path { path in
                        path.move(to: CGPoint(x: done, y: 6))
                        path.addLine(to: CGPoint(x: geo.size.width, y: 6))
                    }
                    .stroke(p.sub, style: StrokeStyle(lineWidth: 1.2, dash: [2, 3]))
                    Capsule().fill(p.accent).frame(width: done, height: 3)
                    Circle().fill(p.accent).frame(width: 12, height: 12).offset(x: max(0, done - 6))
                }
            }
            .frame(height: 12)
            HStack {
                Text(s.progressStart ?? "").foregroundStyle(p.sub)
                Spacer()
                Text(s.progressPercent ?? "").foregroundStyle(p.accent)
                Spacer()
                Text(s.progressEnd ?? "").foregroundStyle(p.sub)
            }
            .font(WidgetPalette.caption)
            .lineLimit(1)
        }
    }
}

/// 보고싶어 버튼(Miss You FAB). 보낸 직후엔 체크 + "보냈어요".
private struct MissYouFab: View {
    let p: WidgetPalette
    let sent: Bool
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: sent ? "checkmark" : "heart.fill").font(.system(size: 14, weight: .semibold))
            Text(sent ? "보냈어요" : "보고싶어").font(WidgetPalette.label)
        }
        .foregroundStyle(p.loveOn)
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Capsule().fill(p.love))
    }
}

private struct Dot: View {
    let color: Color
    var body: some View { Circle().fill(color).frame(width: 8, height: 8) }
}

// MARK: 전역 D-day(작게 · 중간 · 잠금 화면)

struct DdayWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SnapshotEntry

    var body: some View {
        let s = entry.snapshot
        switch family {
        case .accessoryCircular:
            Gauge(value: s?.progress ?? 0) {
                Text("전역")
            } currentValueLabel: {
                Text(s?.ddayValue ?? "–").minimumScaleFactor(0.6)
            }
            .gaugeStyle(.accessoryCircular)
            .containerBackground(.clear, for: .widget)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Text(s?.ddayCaption ?? "전역까지").font(.caption2)
                Text(s?.ddayValue ?? "–").font(.headline)
                if let foot = s?.ddayFoot { Text(foot).font(.caption2).lineLimit(1) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .containerBackground(.clear, for: .widget)
        case .accessoryInline:
            Text("전역 \(s?.ddayValue ?? "–")")
                .containerBackground(.clear, for: .widget)
        default:
            PaperCard(entry: entry) { p in
                if let s, let value = s.ddayValue {
                    if family == .systemMedium { medium(s, value, p) } else { small(s, value, p) }
                } else {
                    Text("전역까지").font(WidgetPalette.caption).foregroundStyle(p.sub)
                }
            }
        }
    }

    private func small(_ s: SharedStore.WidgetSnapshot, _ value: String, _ p: WidgetPalette) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(s.ddayCaption ?? "").font(WidgetPalette.caption).foregroundStyle(p.sub)
            Text(value).font(p.number()).foregroundStyle(p.ink)
            Spacer()
            if let progress = s.progress {
                ProgressView(value: progress).tint(p.accent).background(p.line)
            }
            if let foot = s.ddayFoot { Text(foot).font(WidgetPalette.caption).foregroundStyle(p.sub).lineLimit(1) }
        }
    }

    private func medium(_ s: SharedStore.WidgetSnapshot, _ value: String, _ p: WidgetPalette) -> some View {
        VStack(alignment: .leading) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(s.ddayCaption ?? "").font(WidgetPalette.caption).foregroundStyle(p.sub)
                    Text(value).font(p.number()).foregroundStyle(p.ink)
                }
                Spacer()
                PaperTags(tags: [s.tagLeave, s.tagRank].compactMap { $0 }, p: p)
            }
            Spacer()
            if s.progress != nil {
                ServiceProgressView(s: s, p: p)
            } else if let foot = s.ddayFoot {
                Text(foot).font(WidgetPalette.caption).foregroundStyle(p.sub)
            }
        }
    }
}

struct DdayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dday", provider: SnapshotProvider()) { DdayWidgetView(entry: $0) }
            .configurationDisplayName("전역 D-day")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

// MARK: 보고싶어

/// 위젯에서 바로 보고싶어(iOS 17+ App Intent). 위젯 프로세스에는 Firebase 가 없으므로
/// Op 을 swift-sodium 으로 암호화 · 서명해 Cloud Function `sendFromWidget` 에 보낸다.
/// 스트림은 "{deviceId}:widget", seq 는 App Group 카운터 파일(파일 잠금 안에서 1 올림).
/// 보낸 Op 의 평문 사본을 widget-outbox 에 남겨 앱이 SyncOp 로그로 가져간다(재동기화용).
struct SendMissYouIntent: AppIntent {
    static var title: LocalizedStringResource = "보고싶어 보내기"

    func perform() async throws -> some IntentResult {
        try await WidgetSender.sendMissYou()
        // "보냈어요" 상태 · 오늘 보낸 횟수는 앱이 다음에 스냅샷을 다시 쓸 때 DB 기준으로 맞춘다.
        SharedStore.updateSnapshot {
            $0.missYouSentToday = ($0.missYouSentToday ?? 0) + 1
            $0.missYouJustSentAtMs = Int64(Date().timeIntervalSince1970 * 1000)
        }
        WidgetCenter.shared.reloadTimelines(ofKind: "missyou")
        WidgetCenter.shared.reloadTimelines(ofKind: "overview")
        return .result()
    }
}

struct MissYouWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SnapshotEntry

    var body: some View {
        let s = entry.snapshot
        let sent = entry.justSent
        if family == .accessoryCircular {
            sendButton(s) {
                ZStack {
                    AccessoryWidgetBackground()
                    VStack(spacing: 2) {
                        Image(systemName: sent ? "checkmark" : "heart.fill").font(.system(size: 18))
                        Text(sent ? "보냈어요" : "보고싶어").font(.system(size: 10))
                    }
                }
            }
            .containerBackground(.clear, for: .widget)
        } else {
            PaperCard(entry: entry) { p in
                sendButton(s) {
                    VStack(alignment: .leading, spacing: 6) {
                        if let name = s?.partnerName {
                            Text("\(name)에게").font(WidgetPalette.caption).foregroundStyle(p.sub)
                            Text(sent ? "\(name) 폰이 울렸어요" : "지금 말해 줄까요?").font(p.hand()).foregroundStyle(p.ink).lineLimit(2)
                        }
                        Spacer(minLength: 0)
                        MissYouFab(p: p, sent: sent).frame(maxWidth: .infinity)
                        if let foot = footText(s, sent: sent) {
                            Text(foot).font(WidgetPalette.caption).foregroundStyle(p.sub).frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }
}

extension MissYouWidgetView {
    /// 연결 전에는 보낼 사람이 없다 — 누르면 앱이 열린다.
    @ViewBuilder
    fileprivate func sendButton<Label: View>(_ s: SharedStore.WidgetSnapshot?, @ViewBuilder label: () -> Label) -> some View {
        if s?.linked == true {
            Button(intent: SendMissYouIntent()) { label() }.buttonStyle(.plain)
        } else {
            label()
        }
    }

    fileprivate func footText(_ s: SharedStore.WidgetSnapshot?, sent: Bool) -> String? {
        if sent { return "방금 보냈어요" }
        guard let n = s?.missYouSentToday, n > 0 else { return nil }
        return "오늘 \(n)번 보냈어요"
    }
}

struct MissYouWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "missyou", provider: SnapshotProvider()) { MissYouWidgetView(entry: $0) }
            .configurationDisplayName("보고싶어")
            .supportedFamilies([.systemSmall, .accessoryCircular])
    }
}

// MARK: 받은 그림

/// 받은 그림(Android DrawingWidget 과 같다): 테이프로 붙인 완성 그림 + "▶", 누르면 그 편지를 열고 리플레이. 프레임은 설정 › 위젯(꽃신 평생).
struct DrawingWidgetView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    let entry: SnapshotEntry

    var body: some View {
        let s = entry.snapshot
        let p = WidgetPalette(theme: s?.theme, dark: scheme == .dark)
        let image = s?.drawingPngPath.flatMap { UIImage(contentsOfFile: $0) }
        Group {
            if let image {
                if family == .systemMedium {
                    HStack(spacing: 16) {
                        taped(image, p)
                        VStack(alignment: .leading, spacing: 6) {
                            if let caption = s?.drawingCaption { Text(caption).font(p.hand()).foregroundStyle(p.ink).lineLimit(2) }
                            Text(s?.drawingMeta ?? "").font(WidgetPalette.caption).foregroundStyle(p.sub)
                            Spacer()
                            HStack(spacing: 6) {
                                Image(systemName: "play.fill").font(.system(size: 9))
                                Text("그리는 과정 보기").font(WidgetPalette.labelMedium)
                            }
                            .foregroundStyle(p.ink)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(Capsule().fill(p.tag))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        taped(image, p)
                        HStack(spacing: 4) {
                            Image(systemName: "play.fill").font(.system(size: 8))
                            Text(s?.drawingMeta ?? "").font(WidgetPalette.caption)
                        }
                        .foregroundStyle(p.sub)
                    }
                }
            } else {
                Text("받은 그림").font(WidgetPalette.caption).foregroundStyle(p.sub)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .padding(inset(s?.widgetFrame))
        .widgetURL(s?.drawingLetterId.flatMap { URL(string: "kkotsin://letter/\($0)") })
        .containerBackground(WidgetPalette.frame(s?.widgetFrame) ?? p.surface, for: .widget)
    }

    /// 프레임 여백(폴라로이드는 아래를 넓게). 기본은 시스템 위젯 여백.
    private func inset(_ frame: String?) -> EdgeInsets {
        switch frame {
        case "POLAROID": return EdgeInsets(top: 8, leading: 8, bottom: 26, trailing: 8)
        case "BLOSSOM", "KRAFT", "NIGHT": return EdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)
        default: return EdgeInsets()
        }
    }

    /// 테이프로 붙여 살짝 기울인 그림(보여 주기만 하는 물건).
    private func taped(_ image: UIImage, _ p: WidgetPalette) -> some View {
        Image(uiImage: image).resizable().scaledToFit()
            .rotationEffect(.degrees(3))
            .overlay(alignment: .top) {
                Rectangle().fill(p.tape).frame(width: 40, height: 14).rotationEffect(.degrees(-4)).offset(y: -6)
            }
    }
}

struct DrawingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "drawing", provider: SnapshotProvider()) { DrawingWidgetView(entry: $0) }
            .configurationDisplayName("받은 그림")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: 다음 만남

/// 다음 만남(Android NextMeetingWidget 과 같다): 가장 가까운 면회 · 외박 · 휴가 일정까지 D-n.
struct NextMeetingWidgetView: View {
    let entry: SnapshotEntry
    var body: some View {
        PaperCard(entry: entry) { p in
            VStack(alignment: .leading, spacing: 8) {
                Text("다음 만남").font(WidgetPalette.caption).foregroundStyle(p.sub)
                if let s = entry.snapshot, let value = s.nextEventValue {
                    Text(value).font(p.number()).foregroundStyle(p.ink)
                    Spacer()
                    HStack(spacing: 6) {
                        Dot(color: p.event(s.nextEventType))
                        Text(s.nextEventLine ?? "").font(p.hand()).foregroundStyle(p.ink).lineLimit(1)
                    }
                    if let when = s.nextEventWhen { Text(when).font(WidgetPalette.caption).foregroundStyle(p.sub) }
                }
            }
        }
        .widgetURL(URL(string: "kkotsin://calendar"))
    }
}

struct NextMeetingWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "nextmeet", provider: SnapshotProvider()) { NextMeetingWidgetView(entry: $0) }
            .configurationDisplayName("다음 만남")
            .supportedFamilies([.systemSmall])
    }
}

// MARK: 상대 상태(홈 v2 에서 빠져 위젯에만 있다)

struct PartnerStatusWidgetView: View {
    let entry: SnapshotEntry
    var body: some View {
        PaperCard(entry: entry) { p in
            VStack(alignment: .leading, spacing: 6) {
                if let s = entry.snapshot, let caption = s.partnerStatusCaption {
                    Text(caption).font(WidgetPalette.caption).foregroundStyle(p.sub)
                    if let status = s.partnerStatus {
                        Text(status).font(p.hand(26)).foregroundStyle(p.ink).lineLimit(1)
                        Spacer()
                        if let until = s.partnerStatusUntil {
                            HStack(spacing: 6) {
                                Dot(color: p.status(s.partnerStatusColor))
                                Text(until).font(WidgetPalette.caption).foregroundStyle(p.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

struct PartnerStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "status", provider: SnapshotProvider()) { PartnerStatusWidgetView(entry: $0) }
            .configurationDisplayName("상대 상태")
            .supportedFamilies([.systemSmall])
    }
}

// MARK: 한눈에 보기(큰)

struct OverviewWidgetView: View {
    let entry: SnapshotEntry
    var body: some View {
        PaperCard(entry: entry) { p in
            VStack(alignment: .leading, spacing: 0) {
                if let s = entry.snapshot, let value = s.ddayValue {
                    Text(s.ddayCaption ?? "").font(WidgetPalette.caption).foregroundStyle(p.sub)
                    Text(value).font(p.number(52)).foregroundStyle(p.ink).padding(.top, 6)
                    if s.progress != nil {
                        ServiceProgressView(s: s, p: p).padding(.top, 12)
                    } else if let foot = s.ddayFoot {
                        Text(foot).font(WidgetPalette.caption).foregroundStyle(p.sub).padding(.top, 12)
                    }
                    let tags = [s.tagLeave, s.tagMeet, s.tagRank].compactMap { $0 }
                    if !tags.isEmpty { PaperTags(tags: tags, p: p).padding(.top, 18) }
                }
                Spacer(minLength: 12)
                Rectangle().fill(p.line).frame(height: 1)
                HStack(spacing: 10) {
                    if let s = entry.snapshot, let line = s.letterLine {
                        Link(destination: URL(string: "kkotsin://letter/\(s.letterId ?? "")")!) {
                            HStack(spacing: 10) {
                                Image(systemName: "envelope").font(.system(size: 18)).foregroundStyle(p.accent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(line).font(WidgetPalette.label).foregroundStyle(p.ink).lineLimit(1)
                                    Text(s.letterMeta ?? "").font(WidgetPalette.caption).foregroundStyle(p.sub)
                                }
                            }
                        }
                    }
                    Spacer()
                    if entry.snapshot?.linked == true {
                        Button(intent: SendMissYouIntent()) { MissYouFab(p: p, sent: entry.justSent) }.buttonStyle(.plain)
                    }
                }
                .padding(.top, 16)
            }
        }
    }
}

struct OverviewWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "overview", provider: SnapshotProvider()) { OverviewWidgetView(entry: $0) }
            .configurationDisplayName("한눈에 보기")
            .supportedFamilies([.systemLarge])
    }
}

@main
struct KkotsinWidgetBundle: WidgetBundle {
    var body: some Widget {
        DdayWidget()
        MissYouWidget()
        DrawingWidget()
        NextMeetingWidget()
        PartnerStatusWidget()
        OverviewWidget()
    }
}
