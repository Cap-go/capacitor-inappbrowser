import Foundation
import ObjectiveC
import WebKit

extension WKWebView {
    private enum HandlesURLSchemePrivateAPI {
        static let originalSelector = #selector(WKWebView.handlesURLScheme(_:))
        static let swizzledSelector = #selector(WKWebView._capgo_handlesURLScheme(_:))
    }

    private static var _overriddenSchemes = Set<String>()
    private static let _overriddenSchemesLock = NSLock()
    private static var _originalHandlesURLSchemeIMP: IMP?

    // Original +[WKWebView handlesURLScheme:] IMP (saved before swizzle).
    private typealias HandlesURLSchemeIMP = @convention(c) (AnyClass, Selector, NSString) -> Bool

    private static let _swizzleOnce: Void = {
        guard WKWebView.responds(to: HandlesURLSchemePrivateAPI.originalSelector) else {
            print("[InAppBrowser][Proxy] WARNING: WKWebView does not respond to handlesURLScheme; swizzle skipped")
            return
        }

        // appstore-2.5.2-allow: resolve WKWebView class methods for proxy scheme registration
        let original = class_getClassMethod(WKWebView.self, HandlesURLSchemePrivateAPI.originalSelector)
        // appstore-2.5.2-allow: resolve swizzled class method for proxy scheme registration
        let swizzled = class_getClassMethod(WKWebView.self, HandlesURLSchemePrivateAPI.swizzledSelector)

        guard let original, let swizzled else {
            print("[InAppBrowser][Proxy] WARNING: Could not get methods for swizzle")
            return
        }

        // appstore-2.5.2-allow: preserve original handlesURLScheme IMP before exchange
        _originalHandlesURLSchemeIMP = method_getImplementation(original)
        // appstore-2.5.2-allow: route handlesURLScheme through proxy override for http/https
        method_exchangeImplementations(original, swizzled)
    }()

    @objc(capgo_handlesURLScheme:)
    private static func _capgo_handlesURLScheme(_ urlScheme: String) -> Bool {
        _overriddenSchemesLock.lock()
        let isOverridden = _overriddenSchemes.contains(urlScheme.lowercased())
        _overriddenSchemesLock.unlock()

        if isOverridden { return false }

        guard let imp = _originalHandlesURLSchemeIMP else {
            print("[InAppBrowser][Proxy] WARNING: Original handlesURLScheme IMP missing; assuming not handled")
            return false
        }

        // appstore-2.5.2-allow: call saved handlesURLScheme IMP without re-entering swizzled Swift entry
        let original: HandlesURLSchemeIMP = unsafeBitCast(imp, to: HandlesURLSchemeIMP.self)
        return original(WKWebView.self, HandlesURLSchemePrivateAPI.originalSelector, urlScheme as NSString)
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
