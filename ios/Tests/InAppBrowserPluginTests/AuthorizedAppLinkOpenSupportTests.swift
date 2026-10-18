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
