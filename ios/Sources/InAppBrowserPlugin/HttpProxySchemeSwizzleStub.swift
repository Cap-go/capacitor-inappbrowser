import Foundation

/// Compiled only when `plugins.CapgoInAppBrowser.httpProxySwizzle` is false.
/// This file must not reference `method_exchangeImplementations`.
enum HttpProxySchemeSwizzle {
    static let isCompiled = false

    static func install(for schemes: [String]) -> Bool {
        _ = schemes
        HttpProxySchemeSwizzleNotice.logOnce()
        return false
    }
}

private enum HttpProxySchemeSwizzleNotice {
    private static let once: Void = {
        print(
            "[InAppBrowser][Proxy] WARNING: HTTP/HTTPS proxy scheme handling is omitted from this build. " +
                "Set plugins.CapgoInAppBrowser.httpProxySwizzle to true in capacitor.config.ts and run cap sync " +
                "to include it. Proxy rules will not intercept requests."
        )
    }()

    static func logOnce() {
        _ = once
    }
}
