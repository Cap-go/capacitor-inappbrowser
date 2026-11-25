import Capacitor
import UIKit

#if canImport(WebViewNativeUIEngine)
import WebViewNativeUIEngine

enum ToolbarNativeUIEngineBridge {
    private static var projections: [String: NativeUIProjection] = [:]

    static var hasActiveProjections: Bool {
        !projections.isEmpty
    }

    static func hasProjection(webViewId: String) -> Bool {
        projections[webViewId] != nil
    }

    @discardableResult
    static func start(
        webViewId: String,
        pluginJsName: String,
        webView: WKWebView?,
        navigationBar: UINavigationBar,
        selector: String,
        notify: (String, [String: Any]?) -> Void,
        initiallyVisible: Bool = true
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
            let payload: [String: Any] = ["id": webViewId]
            if initiallyVisible {
                notify(ToolbarNativeUIEngineSupport.nativeShownEvent, payload)
            } else {
                notify(ToolbarNativeUIEngineSupport.hiddenEvent, payload)
            }
            notify(ToolbarNativeUIEngineSupport.appearanceChangedEvent, payload)
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
        guard let webViewId = data?["id"] as? String, let projection = projections[webViewId] else {
            return
        }
        projection.receive(eventName, data: data)
    }
}
#else
enum ToolbarNativeUIEngineBridge {
    static var hasActiveProjections: Bool {
        false
    }

    static func hasProjection(webViewId: String) -> Bool {
        false
    }

  @discardableResult
    static func start(
        webViewId: String,
        pluginJsName: String,
        webView: WKWebView?,
        navigationBar: UINavigationBar,
        selector: String,
        notify: (String, [String: Any]?) -> Void,
        initiallyVisible: Bool = true
    ) -> Bool {
        false
    }

    static func destroy(webViewId: String) {}

    static func forward(eventName: String, data: [String: Any]?) {}
}
#endif