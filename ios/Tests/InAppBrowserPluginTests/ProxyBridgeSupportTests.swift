import JavaScriptCore
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
        let counts = evaluateWindowCloseOverride(
            locationHref: "https://evil.example/",
            bridgeOriginMode: "allowlist",
            bridgeOriginRulesJson: "[\"https://login.example.com\"]"
        )
        XCTAssertEqual(counts.nativeCalls, 0)
        XCTAssertEqual(counts.originalCalls, 1)
    }

    func testMobileAppWindowCloseOverrideUsesNativeHandlerWhenOriginAllowed() {
        let counts = evaluateWindowCloseOverride(
            locationHref: "https://login.example.com/",
            bridgeOriginMode: "allowlist",
            bridgeOriginRulesJson: "[\"https://login.example.com\"]"
        )
        XCTAssertEqual(counts.nativeCalls, 1)
        XCTAssertEqual(counts.originalCalls, 0)
    }

    private func evaluateWindowCloseOverride(
        locationHref: String,
        bridgeOriginMode: String,
        bridgeOriginRulesJson: String
    ) -> (nativeCalls: Int32, originalCalls: Int32) {
        let script = ProxyBridgeSupport.mobileAppWindowCloseOverrideScript(
            bridgeOriginMode: bridgeOriginMode,
            bridgeOriginRulesJson: bridgeOriginRulesJson
        )
        let context = JSContext()!
        context.exceptionHandler = { _, exception in
            XCTFail("JavaScript error: \(exception?.toString() ?? "unknown")")
        }
        context.evaluateScript(
            """
            var URL = function(value) {
                var match = /^([a-z][a-z0-9+.-]*):\\/\\/([^\\/?#:]+)(?::(\\d+))?/i.exec(String(value));
                if (!match) { throw new TypeError('Invalid URL'); }
                this.protocol = match[1].toLowerCase() + ':';
                this.hostname = match[2].toLowerCase();
                this.port = match[3] || '';
            };
            var nativeCalls = 0;
            var originalCalls = 0;
            var window = {
                location: { href: '\(locationHref)' },
                close: function() { originalCalls++; },
                webkit: {
                    messageHandlers: {
                        close: { postMessage: function() { nativeCalls++; } }
                    }
                }
            };
            """
        )
        context.evaluateScript(script)
        context.evaluateScript("window.close();")
        let nativeCalls = context.evaluateScript("nativeCalls")?.toInt32() ?? -1
        let originalCalls = context.evaluateScript("originalCalls")?.toInt32() ?? -1
        return (nativeCalls, originalCalls)
    }
}
