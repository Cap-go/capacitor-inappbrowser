import Capacitor
import UIKit

#if canImport(WebViewNativeUIEngine)
import WebViewNativeUIEngine

enum ToolbarNativeUIEngineBridge {
    private static var projections: [String: NativeUIProjection] = [:]

    static var hasActiveProjections: Bool {
        !projections.isEmpty
    }

    static func start(
        webViewId: String,
        pluginJsName: String,
        webView: WKWebView?,
        navigationBar: UINavigationBar,
        selector: String,
        notify: (String, [String: Any]?) -> Void
    ) {
        guard let webView else {
            return
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
        } catch {
            CAPLog.print("[InAppBrowser] Native UI toolbar projection unavailable: \(error)")
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

    static func start(
        webViewId: String,
        pluginJsName: String,
        webView: WKWebView?,
        navigationBar: UINavigationBar,
        selector: String,
        notify: (String, [String: Any]?) -> Void
    ) {}

    static func destroy(webViewId: String) {}

    static func forward(eventName: String, data: [String: Any]?) {}
}
#endif