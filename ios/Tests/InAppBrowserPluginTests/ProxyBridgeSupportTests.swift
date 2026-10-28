import XCTest
@testable import InappbrowserPlugin

final class ProxyBridgeSupportTests: XCTestCase {
    func testIsBridgeMarkerRequestURL() {
        XCTAssertTrue(
            ProxyBridgeSupport.isBridgeMarkerRequestURL(
                "https://example.com/_capgo_proxy_?u=https%3A%2F%2Fapi.example.com%2Fupload&rid=pr_1"
            )
        )
        XCTAssertFalse(ProxyBridgeSupport.isBridgeMarkerRequestURL("https://example.com/api/upload"))
        XCTAssertFalse(ProxyBridgeSupport.isBridgeMarkerRequestURL("https://example.com/_capgo_proxy_"))
    }

    func testParseBridgeMarkerRequest() {
        let parsed = ProxyBridgeSupport.parseBridgeMarkerRequest(
            "https://example.com/_capgo_proxy_?u=https%3A%2F%2Fapi.example.com%2Fupload&rid=pr_1"
        )

        XCTAssertEqual(parsed?.originalURL, "https://api.example.com/upload")
        XCTAssertEqual(parsed?.requestId, "pr_1")
    }

    func testProxyBridgeStoresAndRetrievesRequestPayload() {
        let bridge = ProxyBridge(accessToken: "token-1")
        bridge.storeRequest(
            token: "token-1",
            requestId: "rid-1",
            method: "PUT",
            headersJson: "{\"content-type\":\"application/octet-stream\"}",
            base64Body: "ZmlsZQ==",
            credentialsMode: "same-origin"
        )

        let stored = bridge.getAndRemove(requestId: "rid-1")
        XCTAssertEqual(stored?.method, "PUT")
        XCTAssertEqual(stored?.base64Body, "ZmlsZQ==")
        XCTAssertEqual(
            ProxyBridgeSupport.decodeStoredHeaders(stored?.headersJson ?? "")["content-type"],
            "application/octet-stream"
        )
        XCTAssertNil(bridge.getAndRemove(requestId: "rid-1"))
    }

    func testFailedHttpSchemeRegistrationDropsProxyHandler() {
        XCTAssertNil(
            ProxyBridgeSupport.resolvedProxySchemeHandler(
                "handler",
                registrationSucceeded: false
            )
        )
        XCTAssertEqual(
            ProxyBridgeSupport.resolvedProxySchemeHandler(
                "handler",
                registrationSucceeded: true
            ),
            "handler"
        )
        XCTAssertNil(
            ProxyBridgeSupport.resolvedProxySchemeHandler(
                nil as String?,
                registrationSucceeded: true
            )
        )
    }

    func testMobileAppWindowCloseOverrideFallsBackWhenOriginDisallowed() {
        let script = ProxyBridgeSupport.mobileAppWindowCloseOverrideScript(
            bridgeOriginMode: "allowlist",
            bridgeOriginRulesJson: "[\"https://login.example.com\"]"
        )
        XCTAssertTrue(script.contains("__capgoInAppBrowserBridgeDocumentOriginAllowed"))
        XCTAssertTrue(script.contains("__capgoInAppBrowserOriginalClose"))
        XCTAssertTrue(script.contains("window.webkit.messageHandlers.close.postMessage(null)"))
        XCTAssertTrue(script.contains("__capgoInAppBrowserOriginalClose.apply(window, arguments)"))
    }
}
