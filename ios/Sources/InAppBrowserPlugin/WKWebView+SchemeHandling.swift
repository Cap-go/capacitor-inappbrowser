import Foundation
import ObjectiveC
import WebKit

extension WKWebView {
    private static var _overriddenSchemes = Set<String>()
    private static let _overriddenSchemesLock = NSLock()

    private static let _swizzleOnce: Void = {
        let original = class_getClassMethod(WKWebView.self, #selector(WKWebView.handlesURLScheme(_:)))
        let swizzled = class_getClassMethod(
            WKWebView.self,
            #selector(WKWebView._capgo_handlesURLScheme(_:))
        )

        guard let original, let swizzled else {
            print("[InAppBrowser][Proxy] WARNING: Could not get methods for swizzle")
            return
        }

        method_exchangeImplementations(original, swizzled)
    }()

    @objc(capgo_handlesURLScheme:)
    private static func _capgo_handlesURLScheme(_ urlScheme: String) -> Bool {
        _overriddenSchemesLock.lock()
        let isOverridden = _overriddenSchemes.contains(urlScheme.lowercased())
        _overriddenSchemesLock.unlock()

        if isOverridden { return false }
        return _capgo_handlesURLScheme(urlScheme)
    }

    static func enableCustomSchemeHandling(for schemes: [String]) {
        _overriddenSchemesLock.lock()
        for scheme in schemes {
            _overriddenSchemes.insert(scheme.lowercased())
        }
        _overriddenSchemesLock.unlock()

        _ = _swizzleOnce
    }
}
