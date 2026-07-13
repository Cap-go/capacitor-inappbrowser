import XCTest
@testable import InappbrowserPlugin

final class BlankTargetNavigationSupportTests: XCTestCase {
    func testAuthorizedHttpLinkOpensExternalApp() {
        XCTAssertEqual(
            BlankTargetNavigationSupport.resolve(
                urlIsHttpOrHttps: true,
                openBlankTargetInWebView: false,
                preventDeeplink: false,
                isAuthorizedAppLink: true
            ),
            .openExternalApp
        )
    }

    func testAuthorizedLinkPrefersExternalAppOverBlankTargetOption() {
        XCTAssertEqual(
            BlankTargetNavigationSupport.resolve(
                urlIsHttpOrHttps: true,
                openBlankTargetInWebView: true,
                preventDeeplink: false,
                isAuthorizedAppLink: true
            ),
            .openExternalApp
        )
    }
    func testAuthorizedLinkLoadsInCurrentWhenDeeplinksPrevented() {
        XCTAssertEqual(
            BlankTargetNavigationSupport.resolve(
                urlIsHttpOrHttps: true,
                openBlankTargetInWebView: false,
                preventDeeplink: true,
                isAuthorizedAppLink: true
            ),
            .loadInCurrentWebView
        )
    }

    func testBlankTargetOptionLoadsInCurrentWebView() {
        XCTAssertEqual(
            BlankTargetNavigationSupport.resolve(
                urlIsHttpOrHttps: true,
                openBlankTargetInWebView: true,
                preventDeeplink: false,
                isAuthorizedAppLink: false
            ),
            .loadInCurrentWebView
        )
    }

    func testDefaultHttpBlankTargetCreatesPopup() {
        XCTAssertEqual(
            BlankTargetNavigationSupport.resolve(
                urlIsHttpOrHttps: true,
                openBlankTargetInWebView: false,
                preventDeeplink: false,
                isAuthorizedAppLink: false
            ),
            .createPopup
        )
    }

    func testNonHttpUrlCreatesPopup() {
        XCTAssertEqual(
            BlankTargetNavigationSupport.resolve(
                urlIsHttpOrHttps: false,
                openBlankTargetInWebView: true,
                preventDeeplink: false,
                isAuthorizedAppLink: false
            ),
            .createPopup
        )
    }
}
