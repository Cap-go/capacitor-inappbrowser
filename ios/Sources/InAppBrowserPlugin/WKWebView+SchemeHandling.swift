import Foundation
import ObjectiveC
import WebKit

/// Enables registering a `WKURLSchemeHandler` for built-in schemes like `http` and `https`.
///
/// Apple's public `setURLSchemeHandler(_:forURLScheme:)` API throws for http/https because
/// `WKWebView.handlesURLScheme(_:)` returns `true` for them. This extension swizzles that
/// class method to return `false` for specified schemes, allowing the public API to work.
///
/// This is the industry-standard approach used by DuckDuckGo Browser, TON Proxy, and others.
extension WKWebView {

    /// Tracks which schemes have been overridden to allow custom handling
    private static var _overriddenSchemes = Set<String>()

    /// One-time swizzle — matches the pattern used by DuckDuckGo and ton-proxy-swift
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

    /// Swizzled replacement — returns false for overridden schemes, calls original for all others
    @objc(capgo_handlesURLScheme:)
    private static func _capgo_handlesURLScheme(_ urlScheme: String) -> Bool {
        if _overriddenSchemes.contains(urlScheme.lowercased()) {
            return false
        }
        // After swizzle, this actually calls the ORIGINAL handlesURLScheme
        return _capgo_handlesURLScheme(urlScheme)
    }

    /// Call this before `setURLSchemeHandler(_:forURLScheme:)` to allow registering for http/https.
    static func enableCustomSchemeHandling(for schemes: [String]) {
        // Add schemes BEFORE triggering the swizzle
        for scheme in schemes {
            _overriddenSchemes.insert(scheme.lowercased())
        }

        // Trigger the one-time swizzle
        _ = _swizzleOnce
    }
}
