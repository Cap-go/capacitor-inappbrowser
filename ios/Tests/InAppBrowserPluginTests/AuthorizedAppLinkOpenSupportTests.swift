import XCTest
@testable import InappbrowserPlugin

private enum CustomSchemeTestURLs {
    static let handoffSuccess = URL(string: "myapp://callback/success")
    static let handoffFailure = URL(string: "myapp://callback/fail")
    static let schemelessRelative = URL(string: "relative/path")
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
    func testCancelsNavigationBeforeOpenInvoked() throws {
        let url = try XCTUnwrap(CustomSchemeTestURLs.handoffSuccess)
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
        let url = try XCTUnwrap(CustomSchemeTestURLs.handoffSuccess)
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
        let url = try XCTUnwrap(CustomSchemeTestURLs.handoffFailure)
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
        let url = try XCTUnwrap(CustomSchemeTestURLs.schemelessRelative)

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: false,
            shouldAttemptOpen: false,
            open: { _, _ in openCalled = true },
            onNavigationCancelled: { navigationCancelled = true },
            onIntercepted: { _, _ in XCTFail("Intercept event should not fire") },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertTrue(navigationCancelled)
        XCTAssertFalse(openCalled)
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
