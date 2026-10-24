import WebKit

extension WKWebView {
    /// True when this build compiled `HttpProxySchemeSwizzle.swift`.
    /// The default stub reports false and does not swap WebKit scheme handling.
    static var isHttpProxySwizzleCompiled: Bool {
        HttpProxySchemeSwizzle.isCompiled
    }

    /// Installs the HTTP/HTTPS scheme override used by native proxy rules.
    ///
    /// The default build leaves that override out. App Store guideline 2.5.2 can reject
    /// the private WebKit scheme swap even when proxy is unused. This function logs that
    /// once and returns false until `plugins.CapgoInAppBrowser.httpProxySwizzle` is `true`
    /// and the app runs cap sync.
    /// Returns true when `http` and `https` can be registered as custom scheme handlers.
    @discardableResult
    static func enableCustomSchemeHandling(for schemes: [String]) -> Bool {
        if !isHttpProxySwizzleCompiled {
            HttpProxySchemeHandlingNotice.logOmittedOnce()
        }
        return HttpProxySchemeSwizzle.install(for: schemes)
    }
}

private enum HttpProxySchemeHandlingNotice {
    private static let once: Void = {
        print(
            "[InAppBrowser][Proxy] WARNING: HTTP/HTTPS proxy interception is off after this update. " +
                "This plugin no longer overrides WebKit scheme handling by default because App Store " +
                "review guideline 2.5.2 can reject that private API usage even when the app never " +
                "enables proxy. To intercept http and https again, set plugins.CapgoInAppBrowser.httpProxySwizzle " +
                "to true in capacitor.config.ts and run cap sync. Proxy rules will not intercept requests until then."
        )
    }()

    static func logOmittedOnce() {
        _ = once
    }
}
