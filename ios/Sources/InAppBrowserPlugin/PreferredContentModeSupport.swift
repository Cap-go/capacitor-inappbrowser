import Foundation
import WebKit

/// Maps Capacitor and plugin `preferredContentMode` values onto WKWebpagePreferences.
enum PreferredContentModeSupport {
    static func normalizedMode(_ value: String) -> String? {
        switch value.lowercased() {
        case "recommended", "mobile", "desktop":
            return value.lowercased()
        default:
            return nil
        }
    }

    static func resolve(
        perOpenValue: String?,
        pluginConfigValue: String?,
        legacyPluginConfigValue: String?,
        capacitorConfigValue: String?
    ) -> String? {
        if let perOpenValue, let normalized = normalizedMode(perOpenValue) {
            return normalized
        }
        if let pluginConfigValue, let normalized = normalizedMode(pluginConfigValue) {
            return normalized
        }
        if let legacyPluginConfigValue, let normalized = normalizedMode(legacyPluginConfigValue) {
            return normalized
        }
        if let capacitorConfigValue, let normalized = normalizedMode(capacitorConfigValue) {
            return normalized
        }
        return nil
    }

    static func webpageContentMode(for mode: String) -> WKWebpagePreferences.ContentMode {
        switch mode.lowercased() {
        case "mobile":
            return .mobile
        case "desktop":
            return .desktop
        default:
            return .recommended
        }
    }

    static func apply(to configuration: WKWebViewConfiguration, mode: String) {
        configuration.defaultWebpagePreferences.preferredContentMode = webpageContentMode(for: mode)
    }
}
