import UIKit
import XCTest
@testable import InappbrowserPlugin

final class InAppBrowserPluginTests: XCTestCase {
    func testCustomWebViewFrameUsesExplicitDimensions() {
        XCTAssertEqual(
            CustomWebViewFrameSupport.resolvedFrame(
                width: 320,
                height: 480,
                x: 12,
                y: 24,
                fallbackSize: CGSize(width: 390, height: 844)
            ),
            CGRect(x: 12, y: 24, width: 320, height: 480)
        )
    }

    func testCustomWebViewFrameUsesFallbackWidthForHeightOnly() {
        XCTAssertEqual(
            CustomWebViewFrameSupport.resolvedFrame(
                width: nil,
                height: 480,
                x: nil,
                y: 32,
                fallbackSize: CGSize(width: 390, height: 844)
            ),
            CGRect(x: 0, y: 32, width: 390, height: 480)
        )
    }

    func testCustomWebViewFrameIgnoresMissingDimensions() {
        XCTAssertNil(
            CustomWebViewFrameSupport.resolvedFrame(
                width: nil,
                height: nil,
                x: 12,
                y: 24,
                fallbackSize: CGSize(width: 390, height: 844)
            )
        )
    }

    func testCustomWebViewFrameIgnoresWidthWithoutHeight() {
        XCTAssertNil(
            CustomWebViewFrameSupport.resolvedFrame(
                width: 320,
                height: nil,
                x: 12,
                y: 24,
                fallbackSize: CGSize(width: 390, height: 844)
            )
        )
    }

    func testCustomWebViewFrameInHostPreservesScreenSpaceSize() {
        let frame = CustomWebViewFrameSupport.frameInHost(
            width: 320,
            height: 480,
            x: 12,
            y: 100,
            screenSize: CGSize(width: 390, height: 844),
            hostOriginInScreen: CGPoint(x: 0, y: 47)
        )

        XCTAssertEqual(frame, CGRect(x: 12, y: 53, width: 320, height: 480))
    }

    func testCustomWebViewFrameInHostIdentityWhenHostFillsScreen() {
        let frame = CustomWebViewFrameSupport.frameInHost(
            width: nil,
            height: 600,
            x: 0,
            y: 80,
            screenSize: CGSize(width: 390, height: 844),
            hostOriginInScreen: .zero
        )

        XCTAssertEqual(frame, CGRect(x: 0, y: 80, width: 390, height: 600))
    }

    func testApplyCustomDimensionsKeepsExactSizeOnOffsetHost() {
        let controller = WKWebViewController()
        controller.customWidth = 320
        controller.customHeight = 480
        controller.customX = 12
        controller.customY = 100

        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.loadViewIfNeeded()
        controller.loadViewIfNeeded()

        let host = UIView(frame: CGRect(x: 0, y: 47, width: 390, height: 797))
        host.addSubview(navigationController.view)

        controller.applyCustomDimensions()

        XCTAssertEqual(navigationController.view.frame, CGRect(x: 12, y: 53, width: 320, height: 480))
        XCTAssertEqual(navigationController.view.bounds.size, CGSize(width: 320, height: 480))
        XCTAssertTrue(navigationController.view.clipsToBounds)
    }

    func testApplyCustomDimensionsUsesScreenFallbackWidthNotHostWidth() {
        let controller = WKWebViewController()
        controller.customHeight = 500
        controller.customY = 64

        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.loadViewIfNeeded()
        controller.loadViewIfNeeded()

        // Narrower-than-screen host must not shrink height-only custom width.
        let host = UIView(frame: CGRect(x: 0, y: 0, width: 300, height: 700))
        host.addSubview(navigationController.view)

        controller.applyCustomDimensions()

        XCTAssertEqual(navigationController.view.frame.width, UIScreen.main.bounds.width)
        XCTAssertEqual(navigationController.view.frame.height, 500)
        XCTAssertEqual(navigationController.view.frame.origin, CGPoint(x: 0, y: 64))
    }

    func testPassThroughViewKeepsContentOnTargetFrameAfterLayout() {
        let targetFrame = CGRect(x: 12, y: 24, width: 320, height: 480)
        let container = PassThroughView(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let contentView = UIView(frame: .zero)

        container.addSubview(contentView)
        container.framedContentView = contentView
        container.targetFrame = targetFrame
        container.layoutIfNeeded()

        XCTAssertEqual(contentView.frame, targetFrame)

        container.frame = CGRect(x: 0, y: 0, width: 430, height: 932)
        container.layoutIfNeeded()

        XCTAssertEqual(contentView.frame, targetFrame)
    }

    func testPassThroughViewDeclinesHitsOutsideTargetFrame() {
        let container = PassThroughView(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let contentView = UIView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
        container.addSubview(contentView)
        container.framedContentView = contentView
        container.targetFrame = contentView.frame

        let outsideHit = container.hitTest(CGPoint(x: 24, y: 712), with: nil)
        let insideHit = container.hitTest(CGPoint(x: 24, y: 120), with: nil)

        XCTAssertNil(outsideHit)
        XCTAssertTrue(insideHit === contentView)
    }

    func testFramedFrontOverlayRequiresCustomHeightAndFrontLayer() {
        let controller = WKWebViewController()
        XCTAssertFalse(controller.shouldPresentAsFramedOverlay)

        controller.customHeight = 600
        XCTAssertTrue(controller.shouldPresentAsFramedOverlay)

        controller.isLayeredBehind = true
        XCTAssertFalse(controller.shouldPresentAsFramedOverlay)
    }

    func testViewportRefreshIgnoresZeroSizeBeforeLayout() {
        XCTAssertFalse(
            WebViewViewportLayoutSupport.shouldRefreshViewport(
                previousSize: nil,
                currentSize: .zero
            )
        )
    }

    func testViewportRefreshRunsWhenWebViewSizeChanges() {
        XCTAssertTrue(
            WebViewViewportLayoutSupport.shouldRefreshViewport(
                previousSize: CGSize(width: 390, height: 500),
                currentSize: CGSize(width: 390, height: 844)
            )
        )
    }

    func testViewportRefreshCanBeForcedAfterKeyboardHide() {
        XCTAssertTrue(
            WebViewViewportLayoutSupport.shouldRefreshViewport(
                previousSize: CGSize(width: 390, height: 844),
                currentSize: CGSize(width: 390, height: 844),
                force: true
            )
        )
    }

    func testSafeAreaLayoutDisablesAutomaticBottomInsetByDefault() {
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.contentInsetAdjustmentBehavior(enabledSafeBottomMargin: false),
            .never
        )
        XCTAssertFalse(
            WebViewSafeAreaLayoutSupport.shouldInsetLayoutMarginsFromSafeArea(enabledSafeBottomMargin: false)
        )
    }

    func testSafeAreaLayoutKeepsAutomaticBottomInsetWhenEnabled() {
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.contentInsetAdjustmentBehavior(enabledSafeBottomMargin: true),
            .automatic
        )
        XCTAssertTrue(
            WebViewSafeAreaLayoutSupport.shouldInsetLayoutMarginsFromSafeArea(enabledSafeBottomMargin: true)
        )
    }

    func testSafeAreaLayoutNegatesBottomInsetWhenDisabled() {
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.additionalBottomSafeAreaOffset(
                enabledSafeBottomMargin: false,
                safeAreaBottomInset: 34
            ),
            -34
        )
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.cssBottomInset(
                enabledSafeBottomMargin: false,
                safeAreaBottomInset: 34
            ),
            0
        )
    }

    func testSafeAreaLayoutPreservesBottomInsetWhenEnabled() {
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.additionalBottomSafeAreaOffset(
                enabledSafeBottomMargin: true,
                safeAreaBottomInset: 34
            ),
            0
        )
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.cssBottomInset(
                enabledSafeBottomMargin: true,
                safeAreaBottomInset: 34
            ),
            34
        )
    }

    func testSafeAreaLayoutRecoversRawSystemBottomInset() {
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.rawSystemBottomInset(
                effectiveBottomInset: 0,
                additionalBottomInset: -34
            ),
            34
        )
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.rawSystemBottomInset(
                effectiveBottomInset: 10,
                additionalBottomInset: 4
            ),
            6
        )
        XCTAssertEqual(
            WebViewSafeAreaLayoutSupport.rawSystemBottomInset(
                effectiveBottomInset: 10,
                additionalBottomInset: 20
            ),
            0
        )
    }

    func testSafeAreaCssVariablesScriptUsesRoundedPixelValues() {
        let script = WebViewSafeAreaLayoutSupport.safeAreaCssVariablesScript(
            top: 59.4,
            bottom: 0,
            left: 0,
            right: 0
        )

        XCTAssertTrue(script.contains("--safe-area-inset-top','59px'"))
        XCTAssertTrue(script.contains("--safe-area-inset-bottom','0px'"))
    }

    func testLegacyProxyRequestsConfigurationSupportsBooleanValues() {
        let enabled = ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: true)
        XCTAssertTrue(enabled.isEnabled)
        XCTAssertNil(enabled.urlRegex)

        let disabled = ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: false)
        XCTAssertFalse(disabled.isEnabled)
        XCTAssertNil(disabled.urlRegex)
    }

    func testLegacyProxyRequestsConfigurationIgnoresAndroidOnlyRegexStrings() {
        let configuration = ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: "api\\.example\\.com")

        XCTAssertFalse(configuration.isEnabled)
        XCTAssertNil(configuration.urlRegex)
    }

    func testLegacyProxyRequestsConfigurationTreatsBlankStringsAsDisabled() {
        let configuration = ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: "   ")

        XCTAssertFalse(configuration.isEnabled)
        XCTAssertNil(configuration.urlRegex)
    }

    func testLegacyProxyRequestsConfigurationIgnoresInvalidRegexStrings() {
        let configuration = ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: "[")

        XCTAssertFalse(configuration.isEnabled)
        XCTAssertNil(configuration.urlRegex)
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

    func testDelegatedRedirectFollowSkipsCanceledInboundResponses() {
        XCTAssertTrue(
            ProxySchemeRequestSupport.shouldFollowDelegatedRedirect(
                phase: "inbound",
                hasPendingRedirect: true,
                hasDirectResponse: false,
                isCanceled: false
            )
        )
        XCTAssertFalse(
            ProxySchemeRequestSupport.shouldFollowDelegatedRedirect(
                phase: "inbound",
                hasPendingRedirect: true,
                hasDirectResponse: false,
                isCanceled: true
            )
        )
        XCTAssertFalse(
            ProxySchemeRequestSupport.shouldFollowDelegatedRedirect(
                phase: "inbound",
                hasPendingRedirect: true,
                hasDirectResponse: true,
                isCanceled: false
            )
        )
        XCTAssertFalse(
            ProxySchemeRequestSupport.shouldFollowDelegatedRedirect(
                phase: "outbound",
                hasPendingRedirect: true,
                hasDirectResponse: false,
                isCanceled: false
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

    func testProxySchemeMainFrameDetectionDoesNotPromoteRequestsWithoutMainDocumentURL() throws {
        let scriptURL = try XCTUnwrap(URL(string: "https://example.com/app.js"))
        let request = URLRequest(url: scriptURL)

        XCTAssertFalse(ProxySchemeRequestSupport.isMainFrameRequest(request))
        XCTAssertTrue(ProxySchemeRequestSupport.isMainFrameRequest(request, fallback: true))
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

    func testProxySchemeDecodedResponseBodyRejectsInvalidBase64() {
        XCTAssertThrowsError(try ProxySchemeRequestSupport.decodedResponseBody(from: "%%%")) { error in
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

    func testProxySchemeResponseCookiesParsesSetCookieHeadersFromSyntheticResponse() {
        let cookies = ProxySchemeRequestSupport.responseCookies(
            from: [
                "Set-Cookie": "session_cookie=abc123; Path=/; Secure",
                "Content-Type": "application/json"
            ],
            fallback: "https://proxy-cookie-tests.example/path"
        )

        XCTAssertEqual(cookies.map(\.name), ["session_cookie"])
        XCTAssertEqual(cookies.first?.value, "abc123")
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

}

final class ProxySchemeTimeoutSupportTests: XCTestCase {
    func testProxySchemeTimeoutResolutionActionPrefersNativeFallbackForOutbound() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(
                phase: "outbound",
                hasCachedResponse: false,
                hasPendingRedirect: false
            ),
            .fallbackToNative
        )
    }

    func testProxySchemeTimeoutResolutionActionFinishesCachedInboundResponses() {
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(
                phase: "inbound",
                hasCachedResponse: true,
                hasPendingRedirect: false
            ),
            .finishCachedResponse
        )
        XCTAssertEqual(
            ProxySchemeRequestSupport.timeoutResolutionAction(
                phase: "inbound",
                hasCachedResponse: false,
                hasPendingRedirect: false
            ),
            .failRequest
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
