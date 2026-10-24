/// Default iOS implementation. Compiled unless `plugins.CapgoInAppBrowser.httpProxySwizzle` is true.
/// This file must not call the Objective-C method-exchange API.
/// `WKWebView.enableCustomSchemeHandling` logs why interception is off and how to turn it back on.
enum HttpProxySchemeSwizzle {
    static let isCompiled = false

    static func install(for schemes: [String]) -> Bool {
        _ = schemes
        return false
    }
}
