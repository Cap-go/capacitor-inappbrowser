import XCTest
@testable import InappbrowserPlugin

final class DownloadPreviewSupportTests: XCTestCase {
    func testNormalizesSupportedModes() {
        XCTAssertEqual(DownloadPreviewSupport.normalizedMode("inAppBrowser"), "inAppBrowser")
        XCTAssertEqual(DownloadPreviewSupport.normalizedMode("SYSTEMPREVIEW"), "systemPreview")
    }

    func testRejectsUnknownModes() {
        XCTAssertNil(DownloadPreviewSupport.normalizedMode("external"))
    }

    func testResolveDefaultsToInAppBrowser() {
        XCTAssertEqual(DownloadPreviewSupport.resolve(perOpenValue: nil), "inAppBrowser")
        XCTAssertEqual(DownloadPreviewSupport.resolve(perOpenValue: "invalid"), "inAppBrowser")
    }

    func testResolveUsesPerOpenValue() {
        XCTAssertEqual(DownloadPreviewSupport.resolve(perOpenValue: "systemPreview"), "systemPreview")
    }

    func testUsesInAppBrowserPreview() {
        XCTAssertTrue(DownloadPreviewSupport.usesInAppBrowserPreview("inAppBrowser"))
        XCTAssertFalse(DownloadPreviewSupport.usesInAppBrowserPreview("systemPreview"))
    }
}
