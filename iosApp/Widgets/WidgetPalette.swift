import SwiftUI

/// 위젯 색은 여기서만 정한다. 위젯 확장은 Compose 토큰을 쓸 수 없어 :core:designsystem kkColors(테마 v2)의 값을 옮겨 둔다
/// — Colors.kt 를 바꾸면 여기도 함께 고친다. 모드는 테마(곰신 · 군인) × 라이트 · 다크.
struct WidgetPalette {
    let surface: Color
    let tag: Color
    let tape: Color
    let ink: Color
    let sub: Color
    let line: Color
    let accent: Color
    let love: Color
    let loveOn: Color
    let soldier: Bool
    private let dark: Bool

    init(theme: String?, dark: Bool) {
        let soldier = theme == "SOLDIER"
        func pick(_ gomsinLight: UInt32, _ gomsinDark: UInt32, _ soldierLight: UInt32, _ soldierDark: UInt32, alpha: Double = 1) -> Color {
            Color(hex: soldier ? (dark ? soldierDark : soldierLight) : (dark ? gomsinDark : gomsinLight), alpha: alpha)
        }
        self.soldier = soldier
        self.dark = dark
        surface = pick(0xFFFDF8, 0x2A2420, 0xF8F6EE, 0x23271E)
        tag = pick(0xD8C1A0, 0x4A3D30, 0xE3D3A8, 0x4A4430)
        tape = pick(0xEFE3B8, 0x6B6142, 0xEFE3B8, 0x5E5A3E, alpha: 0.85)
        ink = pick(0x3B302B, 0xEFE6DA, 0x262B20, 0xE6E8DC)
        sub = pick(0x74665C, 0xB8AB9E, 0x62665A, 0xB8BCAA)
        line = pick(0xE4D8C8, 0x3A322C, 0xCFCDBE, 0x343A2C)
        accent = pick(0xB9475E, 0xE07A8F, 0x55623A, 0x9AAE6B)
        love = pick(0xB9475E, 0xB9475E, 0xA8402F, 0xB5503D)
        loveOn = Color(hex: 0xFFFFFF)
    }

    /// 일정 색(다음 만남 점).
    func event(_ type: String?) -> Color {
        func mode(_ light: UInt32, _ darkColor: UInt32) -> Color { Color(hex: dark ? darkColor : light) }
        switch type {
        case "VACATION": return mode(0xC2703D, 0xE39A6A)
        case "OVERNIGHT": return mode(0x6B5BA8, 0xA397D8)
        case "VISIT": return mode(0x2F7F86, 0x6CC0C6)
        case "TRAINING": return mode(0x55623A, 0x9AAE6B)
        case "DUTY": return mode(0x6E7262, 0xA9AD9B)
        case "ANNIVERSARY": return mode(0xB9475E, 0xE07A8F)
        default: return sub
        }
    }

    /// 상대가 고른 상태 색(ARGB, 콘텐츠). 없으면 보조 글자 색.
    func status(_ argb: Int64?) -> Color {
        guard let argb, argb != 0 else { return sub }
        return Color(hex: UInt32(truncatingIfNeeded: argb & 0xFFFFFF), alpha: Double((argb >> 24) & 0xFF) / 255)
    }

    /// 받은 그림 프레임(설정 › 위젯, 꽃신 평생). 편지지처럼 콘텐츠라 테마 · 다크와 무관하다. 기본은 nil — 테마 표면.
    static func frame(_ frame: String?) -> Color? {
        switch frame {
        case "BLOSSOM": return Color(hex: 0xFFE0E7)
        case "KRAFT": return Color(hex: 0xE9DCC4)
        case "POLAROID": return Color(hex: 0xFFFFFF)
        case "NIGHT": return Color(hex: 0x1B2235)
        default: return nil
        }
    }

    /// 테마 v2 텍스트 스타일: 숫자(D-day)는 곰신 명조 · 군인 굵은 고딕, 손글씨는 위젯에 글꼴이 없어 둥근 글꼴로 대신한다.
    func number(_ size: CGFloat = 24) -> Font { .system(size: size, weight: .bold, design: soldier ? .default : .serif) }
    func hand(_ size: CGFloat = 21) -> Font { .system(size: size, design: .rounded) }
    static let caption = Font.system(size: 11)
    static let label = Font.system(size: 13, weight: .bold)
}

private extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
}
