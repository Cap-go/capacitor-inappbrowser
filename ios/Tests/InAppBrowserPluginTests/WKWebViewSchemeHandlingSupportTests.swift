import WebKit
import XCTest
@testable import InappbrowserPlugin

final class WKWebViewSchemeHandlingSupportTests: XCTestCase {
    func testHttpProxySchemeHandlingMatchesTheLinkedImplementation() {
        if WKWebView.isHttpProxySwizzleCompiled {
            XCTAssertTrue(WKWebView.enableCustomSchemeHandling(for: ["https", "http"]))
            XCTAssertFalse(WKWebView.handlesURLScheme("https"))
            XCTAssertFalse(WKWebView.handlesURLScheme("http"))
            XCTAssertFalse(WKWebView.handlesURLScheme("HTTPS"))
        } else {
            XCTAssertFalse(WKWebView.enableCustomSchemeHandling(for: ["https", "http"]))
            XCTAssertTrue(WKWebView.handlesURLScheme("https"))
            XCTAssertTrue(WKWebView.handlesURLScheme("http"))
        }
    }

    func testNonOverriddenSchemesCallThroughToOriginalImplementation() {
        guard WKWebView.isHttpProxySwizzleCompiled else {
            XCTAssertFalse(WKWebView.enableCustomSchemeHandling(for: ["https", "http"]))
            return
        }

        XCTAssertTrue(WKWebView.enableCustomSchemeHandling(for: ["https", "http"]))

        // Regression for #681: calling a non-overridden scheme must not recurse through the
        // swizzled Swift method (stack overflow / EXC_BAD_ACCESS). These calls should return
        // immediately with WebKit's original answer.
        _ = WKWebView.handlesURLScheme("capacitor")
        _ = WKWebView.handlesURLScheme("file")
        _ = WKWebView.handlesURLScheme("capgo-regression-scheme")
    }
}
