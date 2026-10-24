import WebKit
#if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE
import Foundation
import ObjectiveC
#endif

extension WKWebView {
    /// True only when the app compiled the HTTP/HTTPS scheme override in.
    /// The override calls `method_exchangeImplementations`, so it stays out of
    /// binaries that never set `CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE`.
    static var isHttpProxySwizzleCompiled: Bool {
        #if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE
        return true
        #else
        return false
        #endif
    }

    /// Installs the HTTP/HTTPS scheme override used by native proxy rules.
    /// Returns true when those schemes can be registered as custom scheme handlers.
    @discardableResult
    static func enableCustomSchemeHandling(for schemes: [String]) -> Bool {
        #if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE
        return installHttpProxySchemeSwizzle(for: schemes)
        #else
        _ = schemes
        HttpProxySchemeSwizzleNotice.logOnce()
        return false
        #endif
    }
}

#if !CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE
private enum HttpProxySchemeSwizzleNotice {
    private static let once: Void = {
        print(
            "[InAppBrowser][Proxy] WARNING: HTTP/HTTPS proxy scheme handling is omitted from this build. " +
                "Export CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE=1 before pod install or Swift package " +
                "resolution to opt in. Proxy rules will not intercept requests."
        )
    }()

    static func logOnce() {
        _ = once
    }
}
#endif

#if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE
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
            print("[InAppBrowser][Proxy] WARNING: WKWebView does not respond to handlesURLScheme; scheme override skipped")
            return
        }

        // appstore-2.5.2-allow: resolve WKWebView class methods for proxy scheme registration
        let original = class_getClassMethod(WKWebView.self, HandlesURLSchemePrivateAPI.originalSelector)
        // appstore-2.5.2-allow: resolve swizzled class method for proxy scheme registration
        let swizzled = class_getClassMethod(WKWebView.self, HandlesURLSchemePrivateAPI.swizzledSelector)

        guard let original, let swizzled else {
            print("[InAppBrowser][Proxy] WARNING: Could not get methods for scheme override")
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

    fileprivate static func installHttpProxySchemeSwizzle(for schemes: [String]) -> Bool {
        _overriddenSchemesLock.lock()
        for scheme in schemes {
            _overriddenSchemes.insert(scheme.lowercased())
        }
        _overriddenSchemesLock.unlock()

        _ = _swizzleOnce

        let lowered = Set(schemes.map { $0.lowercased() })
        let httpReady = !lowered.contains("http") || !WKWebView.handlesURLScheme("http")
        let httpsReady = !lowered.contains("https") || !WKWebView.handlesURLScheme("https")
        return httpReady && httpsReady
    }
}
#endif
