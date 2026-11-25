import Foundation

#if canImport(WebViewNativeUIEngine)
import WebViewNativeUIEngine
#endif

enum ToolbarNativeUIEngineSupport {
    static let appearanceChangedEvent = "inAppBrowserToolbarAppearanceChanged"
    static let nativeShownEvent = "inAppBrowserToolbarNativeShown"
    static let webProjectionEvent = "inAppBrowserToolbarWebProjectionStarted"
    static let hiddenEvent = "inAppBrowserToolbarHidden"

    static func shouldRegister(enabled: Bool, toolbarType: String) -> Bool {
        guard enabled else {
            return false
        }
        return toolbarType.lowercased() != "blank"
    }

    static func validateOptions(enabled: Bool, projectionSelector: String?) -> String? {
        guard enabled else {
            return nil
        }
        guard let projectionSelector, !projectionSelector.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "nativeUIProjectionSelector is required when enableNativeUIEngine is true"
        }
        let trimmed = projectionSelector.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("#") || trimmed.hasPrefix(".") || trimmed.hasPrefix("[") else {
            return "nativeUIProjectionSelector must be a CSS selector"
        }
        return nil
    }

    #if canImport(WebViewNativeUIEngine)
    static func receiveListeners() -> NativeUIProjection.ReceiveListeners {
        NativeUIProjection.ReceiveListeners(
            update: [appearanceChangedEvent],
            projection: .init(
                native: [nativeShownEvent],
                web: [webProjectionEvent],
                none: [hiddenEvent]
            )
        )
    }
    #endif
}