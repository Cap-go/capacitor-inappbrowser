import XCTest
@testable import InappbrowserPlugin

final class DownloadPreviewSupportTests: XCTestCase {
    deinit {
        // Exists only to satisfy SwiftLint `required_deinit` (Sonar/CodeRabbit flag empty deinit).
    }

    func testNormalizesSupportedModes() {
        XCTAssertEqual(DownloadPreviewSupport.normalizedMode("inAppBrowser"), "inAppBrowser")
        XCTAssertEqual(DownloadPreviewSupport.normalizedMode("SYSTEMPREVIEW"), "systemPreview")
        XCTAssertEqual(DownloadPreviewSupport.normalizedMode("NONE"), "none")
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
        XCTAssertFalse(DownloadPreviewSupport.usesInAppBrowserPreview("none"))
    }

    func testSkipsPreviewOnlyForNone() {
        XCTAssertTrue(DownloadPreviewSupport.skipsPreview("none"))
        XCTAssertFalse(DownloadPreviewSupport.skipsPreview("inAppBrowser"))
        XCTAssertFalse(DownloadPreviewSupport.skipsPreview("systemPreview"))
    }
}
