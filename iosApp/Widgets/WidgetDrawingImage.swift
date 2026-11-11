import ImageIO
import UIKit

/// 원본 그림을 바꾸지 않고 위젯에 필요한 픽셀까지만 디코딩한다.
enum WidgetDrawingImage {
    static func load(at url: URL, displayWidth: CGFloat, scale: CGFloat) -> UIImage? {
        let pixels = (displayWidth * scale).rounded(.up)
        guard displayWidth > 0, scale > 0, pixels.isFinite,
              pixels >= 1, pixels < CGFloat(Int.max) else { return nil }
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithURL(url as CFURL, sourceOptions) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: Int(pixels),
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }
}
