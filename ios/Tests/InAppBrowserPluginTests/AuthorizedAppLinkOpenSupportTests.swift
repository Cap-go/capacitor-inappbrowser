import XCTest
@testable import InappbrowserPlugin

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
        let url = try XCTUnwrap(URL(string: "myapp://callback/success"))
        var navigationCancelled = false

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: true,
            open: { _, _ in
                XCTAssertTrue(navigationCancelled)
            },
            onNavigationCancelled: { navigationCancelled = true },
            onIntercepted: { _, _ in },
            onPageLoadError: {}
        )
    }

    func testSuccessfulOpenEmitsInterceptedWithoutPageLoadError() throws {
        let url = try XCTUnwrap(URL(string: "myapp://callback/success"))
        var intercepted: (String, Bool)?
        var pageLoadErrors = 0

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: true,
            open: { _, completion in completion(true) },
            onNavigationCancelled: {},
            onIntercepted: { urlString, opened in
                intercepted = (urlString, opened)
            },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertEqual(intercepted?.0, url.absoluteString)
        XCTAssertEqual(intercepted?.1, true)
        XCTAssertEqual(pageLoadErrors, 0)
    }

    func testFailedOpenEmitsInterceptedAndPageLoadError() throws {
        let url = try XCTUnwrap(URL(string: "myapp://callback/fail"))
        var intercepted: (String, Bool)?
        var pageLoadErrors = 0

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: true,
            open: { _, completion in completion(false) },
            onNavigationCancelled: {},
            onIntercepted: { urlString, opened in
                intercepted = (urlString, opened)
            },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertEqual(intercepted?.0, url.absoluteString)
        XCTAssertEqual(intercepted?.1, false)
        XCTAssertEqual(pageLoadErrors, 1)
    }

    func testInvalidSchemeEmitsFailureWithoutCallingOpen() throws {
        var openCalled = false
        var pageLoadErrors = 0
        let url = try XCTUnwrap(URL(string: "relative/path"))

        CustomSchemeHandoffSupport.handoff(
            url: url,
            shouldEmitInterceptEvent: false,
            open: { _, _ in openCalled = true },
            onNavigationCancelled: {},
            onIntercepted: { _, _ in },
            onPageLoadError: { pageLoadErrors += 1 }
        )

        XCTAssertFalse(openCalled)
        XCTAssertEqual(pageLoadErrors, 1)
    }
}

final class CustomSchemeOpenSupportTests: XCTestCase {
    func testAttemptsOpenForCustomSchemes() {
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "instagram"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "myapp"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "mailto"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "tel"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "sms"))
        XCTAssertTrue(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: "MAILTO"))
    }

    func testDoesNotAttemptOpenWithoutScheme() {
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: nil))
        XCTAssertFalse(CustomSchemeOpenSupport.shouldAttemptOpen(scheme: ""))
    }
}
