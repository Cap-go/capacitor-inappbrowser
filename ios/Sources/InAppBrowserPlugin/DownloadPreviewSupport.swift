import Foundation

/// Maps `downloadPreview` openWebView option values for managed download handling.
enum DownloadPreviewSupport {
    static let defaultMode = "inAppBrowser"

    static func normalizedMode(_ value: String) -> String? {
        switch value.lowercased() {
        case "inappbrowser":
            return "inAppBrowser"
        case "systempreview":
            return "systemPreview"
        case "none":
            return "none"
        default:
            return nil
        }
    }

    static func skipsPreview(_ mode: String) -> Bool {
        mode == "none"
    }

    static func resolve(perOpenValue: String?) -> String {
        if let perOpenValue, let normalized = normalizedMode(perOpenValue) {
            return normalized
        }
        return defaultMode
    }

    static func usesInAppBrowserPreview(_ mode: String) -> Bool {
        mode == "inAppBrowser"
    }
}
