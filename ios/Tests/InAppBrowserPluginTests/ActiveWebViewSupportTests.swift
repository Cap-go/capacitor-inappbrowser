import XCTest
@testable import InappbrowserPlugin

final class ActiveWebViewSupportTests: XCTestCase {
    func testHiddenPopupDoesNotReplaceExistingActiveWebView() {
        XCTAssertFalse(
            ActiveWebViewSupport.shouldActivateNewWebView(isHidden: true, hasActiveWebView: true)
        )
    }

    func testVisibleOrFirstWebViewBecomesActive() {
        XCTAssertTrue(
            ActiveWebViewSupport.shouldActivateNewWebView(isHidden: false, hasActiveWebView: true)
        )
        XCTAssertTrue(
            ActiveWebViewSupport.shouldActivateNewWebView(isHidden: true, hasActiveWebView: false)
        )
    }
}
