import XCTest
@testable import InappbrowserPlugin

final class ProxyRequestRoutingSupportTests: XCTestCase {
    private final class FakeProxyRequestLocator: ProxyRequestLocating {
        private let pending: Bool

        init(pending: Bool) {
            self.pending = pending
        }

        func hasPendingProxyRequest(_ requestId: String) -> Bool {
            pending
        }
    }

    func testProxySchemeNormalizedRequestMethodCanonicalizesOverrides() {
        XCTAssertEqual(ProxySchemeRequestSupport.normalizedRequestMethod(" post "), "POST")
        XCTAssertEqual(ProxySchemeRequestSupport.normalizedRequestMethod("head"), "HEAD")
    }

    func testProxySchemeNormalizedRequestMethodDefaultsBlankValuesToGet() {
        XCTAssertEqual(ProxySchemeRequestSupport.normalizedRequestMethod(nil), "GET")
        XCTAssertEqual(ProxySchemeRequestSupport.normalizedRequestMethod("   "), "GET")
    }

    func testProxySchemePrepareOverrideHeadersDropsOriginBoundHeadersAcrossOrigins() {
        let overrideHeaders = ProxySchemeRequestSupport.prepareOverrideHeaders(
            originalHeaders: [
                "Authorization": "Bearer abc",
                "Cookie": "session=123",
                "Origin": "https://example.com",
                "Referer": "https://example.com/login",
                "Accept": "application/json"
            ],
            requestURL: "https://example.com/login",
            overrideURL: "https://accounts.example.net/oauth"
        )

        XCTAssertNil(overrideHeaders["Authorization"])
        XCTAssertNil(overrideHeaders["Cookie"])
        XCTAssertNil(overrideHeaders["Origin"])
        XCTAssertNil(overrideHeaders["Referer"])
        XCTAssertEqual(overrideHeaders["Accept"], "application/json")
    }

    func testProxySchemeResolvedRedirectHeadersDoNotReuseFallbackHeadersWhenRedirectOmitsThem() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedRedirectHeaders(
                nil,
                fallback: ["Authorization": "Bearer abc", "Accept": "application/json"]
            ),
            [:]
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedRedirectHeaders(
                ["Accept": "application/json"],
                fallback: ["Authorization": "Bearer abc"]
            ),
            ["Accept": "application/json"]
        )
    }

    func testProxySchemeResolvedRedirectBodyPreservesFallbackForBodyMethodsOnly() {
        let fallbackBody = Data("payload".utf8).base64EncodedString()

        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedRedirectBody(nil, method: "POST", fallback: fallbackBody),
            fallbackBody
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedRedirectBody("bmV4dA==", method: "PUT", fallback: fallbackBody),
            "bmV4dA=="
        )
        XCTAssertNil(
            ProxySchemeRequestSupport.resolvedRedirectBody(fallbackBody, method: "GET", fallback: fallbackBody)
        )
    }

    func testProxyResponseRoutingFindsUniquePendingHandlerWithoutWebviewId() {
        let handlers = [
            "a": FakeProxyRequestLocator(pending: false),
            "b": FakeProxyRequestLocator(pending: true)
        ]

        switch ProxyResponseRoutingSupport.resolveTargetHandler(
            webviewId: nil,
            requestId: "req-1",
            handlers: handlers
        ) {
        case .matched(let handler):
            XCTAssertTrue(handler === handlers["b"])
        default:
            XCTFail("Expected unique matched handler")
        }
    }

    func testProxyResponseRoutingRejectsAmbiguousMatchesWithoutWebviewId() {
        let handlers = [
            "a": FakeProxyRequestLocator(pending: true),
            "b": FakeProxyRequestLocator(pending: true)
        ]

        switch ProxyResponseRoutingSupport.resolveTargetHandler(
            webviewId: nil,
            requestId: "req-1",
            handlers: handlers
        ) {
        case .ambiguous:
            XCTAssertTrue(true)
        default:
            XCTFail("Expected ambiguous routing result")
        }
    }

    func testActiveWebViewSupportPrefersOriginatingVisibilityTarget() {
        XCTAssertEqual(
            ActiveWebViewSupport.resolveVisibilityTarget(
                originatingWebViewId: "popup-webview",
                activeWebViewId: "parent-webview"
            ),
            "popup-webview"
        )
        XCTAssertEqual(
            ActiveWebViewSupport.resolveVisibilityTarget(
                originatingWebViewId: nil,
                activeWebViewId: "parent-webview"
            ),
            "parent-webview"
        )
    }
}
