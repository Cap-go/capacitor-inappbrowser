import XCTest
@testable import InappbrowserPlugin

private enum CustomSchemeTestURLParts {
    static let scheme = "myapp"
    static let callbackHost = "callback"
    static let successSegment = "success"
    static let failureSegment = "fail"
    static let schemelessRelativePath = "relative/path"

    static var successPath: String {
        pathWithLeadingDelimiter(for: successSegment)
    }

    static var failurePath: String {
        pathWithLeadingDelimiter(for: failureSegment)
    }

    private static func pathWithLeadingDelimiter(for segment: String) -> String {
        segment.hasPrefix(pathDelimiter) ? segment : pathDelimiter + segment
    }

    private static var pathDelimiter: String {
        String(UnicodeScalar(47))
    }

    static func handoffSuccess() -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = callbackHost
        components.path = successPath
        return components.url
    }

    static func handoffFailure() -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = callbackHost
        components.path = failurePath
        return components.url
    }

    static func schemelessRelative() -> URL? {
        URL(string: schemelessRelativePath)
    }
}

final class AuthorizedAppLinkOpenSupportTests: XCTestCase {
    func testUniversalLinkSuccessOpensExternally() {
        XCTAssertEqual(
            AuthorizedAppLinkOpenSupport.resolve(universalLinkOpened: true, systemOpenSucceeded: false),
            .openedExternally
        )
    }

    func testSystemOpenFallbackOpensExternally() {
        XCTAssertEqual(
            AuthorizedAppLinkOpenSupport.resolve(universalLinkOpened: false, systemOpenSucceeded: true),
            .openedExternally
        )
    }

    func testBothExternalAttemptsFailedStayInWebView() {
        XCTAssertEqual(
            AuthorizedAppLinkOpenSupport.resolve(universalLinkOpened: false, systemOpenSucceeded: false),
            .loadInWebView
        )
    }
}

final class CustomSchemeHandoffSupportTests: XCTestCase {
    func testHandoffFixtureURLsUseLeadingSlashPaths() throws {
        let success = try XCTUnwrap(CustomSchemeTestURLParts.handoffSuccess())
        let failure = try XCTUnwrap(CustomSchemeTestURLParts.handoffFailure())

        XCTAssertEqual(success.scheme, CustomSchemeTestURLParts.scheme)
        XCTAssertEqual(success.host, CustomSchemeTestURLParts.callbackHost)
        XCTAssertEqual(success.path, CustomSchemeTestURLParts.successPath)

        XCTAssertEqual(failure.scheme, CustomSchemeTestURLParts.scheme)
        XCTAssertEqual(failure.host, CustomSchemeTestURLParts.callbackHost)
        XCTAssertEqual(failure.path, CustomSchemeTestURLParts.failurePath)
    }

    func testCancelsNavigationBeforeOpenInvoked() throws {
        let url = try XCTUnwrap(CustomSchemeTestURLParts.handoffSuccess())
        var navigationCancelled = false
        var openInvoked = false
        var interceptCalls = 0
        var pageLoadErrors = 0

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: true,
            shouldAttemptOpen: true,
            open: { _, _ in
                openInvoked = true
                XCTAssertTrue(navigationCancelled)
            },
            onNavigationCancelled: { navigationCancelled = true },
            onIntercepted: { _, _ in interceptCalls += 1 },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertTrue(openInvoked)
        XCTAssertEqual(interceptCalls, 0)
        XCTAssertEqual(pageLoadErrors, 0)
    }

    func testSuccessfulOpenEmitsInterceptedWithoutPageLoadError() throws {
        let url = try XCTUnwrap(CustomSchemeTestURLParts.handoffSuccess())
        var intercepted: (String, Bool)?
        var pageLoadErrors = 0
        var navigationCancelled = false

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: true,
            shouldAttemptOpen: true,
            open: { _, completion in completion(true) },
            onNavigationCancelled: { navigationCancelled = true },
            onIntercepted: { urlString, opened in
                intercepted = (urlString, opened)
            },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertTrue(navigationCancelled)
        XCTAssertEqual(intercepted?.0, url.absoluteString)
        XCTAssertEqual(intercepted?.1, true)
        XCTAssertEqual(pageLoadErrors, 0)
    }

    func testFailedOpenEmitsInterceptedAndPageLoadError() throws {
        let url = try XCTUnwrap(CustomSchemeTestURLParts.handoffFailure())
        var intercepted: (String, Bool)?
        var pageLoadErrors = 0
        var navigationCancelled = false

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: true,
            shouldAttemptOpen: true,
            open: { _, completion in completion(false) },
            onNavigationCancelled: { navigationCancelled = true },
            onIntercepted: { urlString, opened in
                intercepted = (urlString, opened)
            },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertTrue(navigationCancelled)
        XCTAssertEqual(intercepted?.0, url.absoluteString)
        XCTAssertEqual(intercepted?.1, false)
        XCTAssertEqual(pageLoadErrors, 1)
    }

    func testInvalidSchemeEmitsFailureWithoutCallingOpen() throws {
        var openCalled = false
        var pageLoadErrors = 0
        var navigationCancelled = false
        var interceptCalls = 0
        let url = try XCTUnwrap(CustomSchemeTestURLParts.schemelessRelative())

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: false,
            shouldAttemptOpen: false,
            open: { _, _ in openCalled = true },
            onNavigationCancelled: { navigationCancelled = true },
            onIntercepted: { _, _ in interceptCalls += 1 },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertTrue(navigationCancelled)
        XCTAssertFalse(openCalled)
        XCTAssertEqual(interceptCalls, 0)
        XCTAssertEqual(pageLoadErrors, 1)
    }
}

final class CustomSchemeOpenSupportTests: XCTestCase {
    func testLegacyGateRequiresCanOpenURLForCustomSchemes() {
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "instagram", canOpenURL: true))
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "instagram", canOpenURL: false))
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "myapp", canOpenURL: false))
    }

    func testLegacyGateAllowsMailtoTelSmsWithoutQueriesScheme() {
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "mailto", canOpenURL: false))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "tel", canOpenURL: false))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "sms", canOpenURL: false))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "MAILTO", canOpenURL: false))
    }

    func testOptInAllowsCustomSchemesWithoutCanOpenURL() {
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpenWithoutQuery(scheme: "instagram"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpenWithoutQuery(scheme: "myapp"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpenWithoutQuery(scheme: "mailto"))
    }

    func testDoesNotAttemptOpenWithoutScheme() {
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: nil, canOpenURL: false))
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "", canOpenURL: false))
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpenWithoutQuery(scheme: nil))
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpenWithoutQuery(scheme: ""))
    }
}
