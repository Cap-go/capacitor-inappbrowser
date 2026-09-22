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
        default:
            return nil
        }
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
