import Capacitor
import UIKit

#if canImport(WebViewNativeUIEngine)
import WebViewNativeUIEngine

enum ToolbarNativeUIEngineBridge {
    private static var projections: [String: NativeUIProjection] = [:]

    static var hasActiveProjections: Bool {
        !projections.isEmpty
    }

    @discardableResult
    static func start(
        webViewId: String,
        pluginJsName: String,
        webView: WKWebView?,
        navigationBar: UINavigationBar,
        selector: String,
        notify: (String, [String: Any]?) -> Void
    ) -> Bool {
        guard let webView else {
            return false
        }
        do {
            let projection = try NativeUIProjection(
                webView: webView,
                plugin: pluginJsName,
                projectionSelector: selector,
                receiveListeners: ToolbarNativeUIEngineSupport.receiveListeners(),
                view: { [weak navigationBar] in navigationBar }
            )
            projections[webViewId] = projection
            notify(ToolbarNativeUIEngineSupport.nativeShownEvent, [:])
            notify(ToolbarNativeUIEngineSupport.appearanceChangedEvent, [:])
            return true
        } catch {
            CAPLog.print("[InAppBrowser] Native UI toolbar projection unavailable: \(error)")
            return false
        }
    }

    static func destroy(webViewId: String) {
        if let projection = projections.removeValue(forKey: webViewId) {
            projection.destroy()
        }
    }

    static func forward(eventName: String, data: [String: Any]?) {
        for projection in projections.values {
            projection.receive(eventName, data: data)
        }
    }
}
#else
enum ToolbarNativeUIEngineBridge {
    static var hasActiveProjections: Bool {
        false
    }

  @discardableResult
    static func start(
        webViewId: String,
        pluginJsName: String,
        webView: WKWebView?,
        navigationBar: UINavigationBar,
        selector: String,
        notify: (String, [String: Any]?) -> Void
    ) -> Bool {
        false
    }

    static func destroy(webViewId: String) {}

    static func forward(eventName: String, data: [String: Any]?) {}
}
#endif