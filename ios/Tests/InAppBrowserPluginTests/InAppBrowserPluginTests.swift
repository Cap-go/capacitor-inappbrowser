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

    func testLegacyProxyRequestsConfigurationSupportsBooleanValues() throws {
        let enabled = try ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: true)
        XCTAssertTrue(enabled.isEnabled)
        XCTAssertNil(enabled.urlRegex)

        let disabled = try ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: false)
        XCTAssertFalse(disabled.isEnabled)
        XCTAssertNil(disabled.urlRegex)
    }

    func testLegacyProxyRequestsConfigurationCompilesRegexStrings() throws {
        let configuration = try ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: "api\\.example\\.com")

        XCTAssertTrue(configuration.isEnabled)
        XCTAssertNotNil(configuration.urlRegex)
        XCTAssertNotNil(configuration.urlRegex?.firstMatch(
            in: "https://api.example.com/login",
            options: [],
            range: NSRange(location: 0, length: "https://api.example.com/login".utf16.count)
        ))
    }

    func testLegacyProxyRequestsConfigurationTreatsBlankStringsAsDisabled() throws {
        let configuration = try ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: "   ")

        XCTAssertFalse(configuration.isEnabled)
        XCTAssertNil(configuration.urlRegex)
    }

    func testLegacyProxyRequestsConfigurationRejectsInvalidRegex() {
        XCTAssertThrowsError(try ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: "["))
    }

    func testLegacyCatchAllRuleOnlyAppliesToOutboundPhase() {
        XCTAssertTrue(
            ProxySchemeRequestSupport.shouldUseLegacyCatchAllRule(
                legacyProxyRequests: true,
                hasOutboundRules: false,
                hasInboundRules: false,
                phase: "outbound"
            )
        )
        XCTAssertFalse(
            ProxySchemeRequestSupport.shouldUseLegacyCatchAllRule(
                legacyProxyRequests: true,
                hasOutboundRules: false,
                hasInboundRules: false,
                phase: "inbound"
            )
        )
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

    func testProxySchemeRelativeOverrideURLResolvesAgainstOriginalRequest() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.sanitizedOverrideURL("/api/v2", fallback: "https://example.com/users/sign_in"),
            "https://example.com/api/v2"
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.sanitizedOverrideURL("oauth/start", fallback: "https://example.com/users/sign_in"),
            "https://example.com/users/oauth/start"
        )
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

    func testProxySchemeDecodedRequestBodyAcceptsValidBase64() throws {
        let decodedBody = try ProxySchemeRequestSupport.decodedRequestBody(
            from: Data("hello".utf8).base64EncodedString()
        )

        XCTAssertEqual(decodedBody, Data("hello".utf8))
    }

    func testProxySchemeDecodedRequestBodyRejectsInvalidBase64() {
        XCTAssertThrowsError(try ProxySchemeRequestSupport.decodedRequestBody(from: "%%%")) { error in
            XCTAssertEqual(error as? ProxySchemeRequestSupport.RequestBuildError, .invalidBase64Body)
        }
    }

    func testProxySchemeResolvedOverrideBodyClearsNullAndBodylessGetOverrides() {
        let existingBody = Data("secret".utf8).base64EncodedString()

        XCTAssertNil(
            ProxySchemeRequestSupport.resolvedOverrideBody(
                from: ["body": NSNull()],
                method: "POST",
                fallback: existingBody
            )
        )
        XCTAssertNil(
            ProxySchemeRequestSupport.resolvedOverrideBody(
                from: [:],
                method: "GET",
                fallback: existingBody
            )
        )
        XCTAssertNil(
            ProxySchemeRequestSupport.resolvedOverrideBody(
                from: ["body": existingBody],
                method: "HEAD",
                fallback: existingBody
            )
        )
    }

    func testProxySchemeResolvedOverrideBodyPreservesFallbackForBodyMethodsWithoutOverride() {
        let existingBody = Data("kept".utf8).base64EncodedString()

        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedOverrideBody(
                from: [:],
                method: "POST",
                fallback: existingBody
            ),
            existingBody
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedOverrideBody(
                from: ["body": Data("next".utf8).base64EncodedString()],
                method: "PUT",
                fallback: existingBody
            ),
            Data("next".utf8).base64EncodedString()
        )
    }

    func testProxySchemeResolvedResponseURLPrefersFinalURL() throws {
        let response = try XCTUnwrap(
            HTTPURLResponse(
                url: XCTUnwrap(URL(string: "https://example.com/final")),
                statusCode: 302,
                httpVersion: nil,
                headerFields: nil
            )
        )

        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedResponseURL(response, fallback: "https://example.com/original"),
            "https://example.com/final"
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.resolvedResponseURL(nil, fallback: "https://example.com/original"),
            "https://example.com/original"
        )
    }

    func testProxySchemeResponseCookieURLFallsBackToOriginalURL() throws {
        let response = try XCTUnwrap(
            HTTPURLResponse(
                url: XCTUnwrap(URL(string: "https://example.com/final")),
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )
        )

        XCTAssertEqual(
            ProxySchemeRequestSupport.responseCookieURL(response, fallback: "https://example.com/original")?.absoluteString,
            "https://example.com/final"
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.responseCookieURL(nil, fallback: "https://example.com/original")?.absoluteString,
            "https://example.com/original"
        )
    }

    func testProxySchemeResponseCookiesCollectsAllCookiesForResolvedURL() throws {
        let cookieURL = try XCTUnwrap(URL(string: "https://proxy-cookie-tests.example/path"))
        let response = try XCTUnwrap(
            HTTPURLResponse(
                url: cookieURL,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )
        )

        let cookieOne = try XCTUnwrap(
            HTTPCookie(properties: [
                .domain: cookieURL.host as Any,
                .path: "/",
                .name: "session_cookie",
                .value: "abc123",
                .secure: "TRUE"
            ])
        )
        let cookieTwo = try XCTUnwrap(
            HTTPCookie(properties: [
                .domain: cookieURL.host as Any,
                .path: "/",
                .name: "csrf_cookie",
                .value: "def456",
                .secure: "TRUE"
            ])
        )

        HTTPCookieStorage.shared.setCookie(cookieOne)
        HTTPCookieStorage.shared.setCookie(cookieTwo)
        defer {
            HTTPCookieStorage.shared.deleteCookie(cookieOne)
            HTTPCookieStorage.shared.deleteCookie(cookieTwo)
        }

        let cookies = ProxySchemeRequestSupport.responseCookies(response: response, fallback: "https://example.com/original")
        let cookieNames = Set(cookies.map(\.name))

        XCTAssertTrue(cookieNames.contains("session_cookie"))
        XCTAssertTrue(cookieNames.contains("csrf_cookie"))
    }

    func testProxySchemeNormalizedResponseHeadersCombinesRepeatedValues() {
        let headers = ProxySchemeRequestSupport.normalizedResponseHeaders(
            from: [
                "WWW-Authenticate": ["Bearer realm=one", "Basic realm=two"],
                "Cache-Control": "no-cache"
            ]
        )

        XCTAssertEqual(headers["WWW-Authenticate"], "Bearer realm=one, Basic realm=two")
        XCTAssertEqual(headers["Cache-Control"], "no-cache")
    }

    func testProxySchemeTimeoutResolutionActionPrefersNativeFallbackForOutbound() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(phase: "outbound", hasCachedResponse: false),
            .fallbackToNative
        )
    }

    func testProxySchemeTimeoutResolutionActionFinishesCachedInboundResponses() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(phase: "inbound", hasCachedResponse: true),
            .finishCachedResponse
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(phase: "inbound", hasCachedResponse: false),
            .failRequest
        )
    }

    func testProxySchemeJsResponseResolutionActionFinishesCachedResponsesBeforeReentry() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "inbound", hasCachedResponse: true),
            .finishCachedResponse
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "outbound", hasCachedResponse: true),
            .finishCachedResponse
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "outbound", hasCachedResponse: false),
            .executeNativePipeline
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.jsResponseResolutionAction(phase: "inbound", hasCachedResponse: false),
            .executeInboundDecision
        )
    }

    func testProxySchemeTimeoutTokenMustMatchLatestGeneration() {
        let activeToken = UUID()

        XCTAssertTrue(
            ProxySchemeRequestSupport.timeoutTokenMatches(
                scheduledToken: activeToken,
                currentToken: activeToken
            )
        )
        XCTAssertFalse(
            ProxySchemeRequestSupport.timeoutTokenMatches(
                scheduledToken: activeToken,
                currentToken: UUID()
            )
        )
        XCTAssertFalse(
            ProxySchemeRequestSupport.timeoutTokenMatches(
                scheduledToken: activeToken,
                currentToken: nil
            )
        )
    }
}
