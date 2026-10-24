import WebKit

extension WKWebView {
    /// True when this build compiled `HttpProxySchemeSwizzle.swift`.
    /// The stub file reports false and does not call `method_exchangeImplementations`.
    static var isHttpProxySwizzleCompiled: Bool {
        HttpProxySchemeSwizzle.isCompiled
    }

    /// Installs the HTTP/HTTPS scheme override used by native proxy rules.
    /// Returns true when those schemes can be registered as custom scheme handlers.
    @discardableResult
    static func enableCustomSchemeHandling(for schemes: [String]) -> Bool {
        HttpProxySchemeSwizzle.install(for: schemes)
    }
}
