import XCTest
@testable import InappbrowserPlugin

final class InAppBrowserPluginTests: XCTestCase {
    func testConsoleMessagePayloadNormalizesFields() {
        let payload = ConsoleMessageSupport.normalizePayload(
            from: [
                "level": "WARN",
                "message": "popup blocked",
                "source": "https://example.com/app.js",
                "line": NSNumber(value: 42),
                "column": "7",
                "kind": "console"
            ]
        )

        XCTAssertEqual(payload["level"] as? String, "warn")
        XCTAssertEqual(payload["message"] as? String, "popup blocked")
        XCTAssertEqual(payload["source"] as? String, "https://example.com/app.js")
        XCTAssertEqual(payload["line"] as? Int, 42)
        XCTAssertEqual(payload["column"] as? Int, 7)
        XCTAssertEqual(payload["kind"] as? String, "console")
    }

    func testConsoleMessagePayloadDefaultsInvalidValues() {
        let payload = ConsoleMessageSupport.normalizePayload(
            from: [
                "level": "unexpected",
                "message": NSNumber(value: 15)
            ]
        )

        XCTAssertEqual(payload["level"] as? String, "log")
        XCTAssertEqual(payload["message"] as? String, "15")
        XCTAssertNil(payload["source"])
        XCTAssertNil(payload["line"])
    }

    func testConsoleCaptureScriptContainsExpectedHooks() {
        let script = ConsoleMessageSupport.captureScriptSource()

        XCTAssertTrue(script.contains("consoleMessageHandler"))
        XCTAssertTrue(script.contains("unhandledrejection"))
        XCTAssertTrue(script.contains("console.assert"))
    }

    func testProxySchemeMainFrameDetectionTracksMainDocumentURL() throws {
        let pageURL = try XCTUnwrap(URL(string: "https://example.com/page"))
        let scriptURL = try XCTUnwrap(URL(string: "https://example.com/app.js"))

        var mainFrameRequest = URLRequest(url: pageURL)
        mainFrameRequest.mainDocumentURL = pageURL
        XCTAssertTrue(ProxySchemeRequestSupport.isMainFrameRequest(mainFrameRequest))

        var subresourceRequest = URLRequest(url: scriptURL)
        subresourceRequest.mainDocumentURL = pageURL
        XCTAssertFalse(ProxySchemeRequestSupport.isMainFrameRequest(subresourceRequest))
    }

    func testProxySchemeInvalidOverrideURLFallsBackToOriginalRequest() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.sanitizedOverrideURL("://bad-url", fallback: "https://example.com/original"),
            "https://example.com/original"
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.sanitizedOverrideURL("https://example.com/override", fallback: "https://example.com/original"),
            "https://example.com/override"
        )
    }
}
