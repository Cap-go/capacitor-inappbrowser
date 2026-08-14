import XCTest
@testable import InappbrowserPlugin

final class BundledAssetSupportTests: XCTestCase {
    func testResolveRelativePathForCapacitorLocalURL() {
        let localURL = URL(string: "capacitor://localhost")!
        let resolution = BundledAssetSupport.resolve("/page.html", localURL: localURL)

        XCTAssertEqual(resolution.url, "capacitor://localhost/page.html")
        XCTAssertTrue(resolution.needsHandler)
    }

    func testKeepsRemoteUrlsUnchanged() {
        let resolution = BundledAssetSupport.resolve("https://example.com/page.html", localURL: URL(string: "capacitor://localhost"))

        XCTAssertEqual(resolution.url, "https://example.com/page.html")
        XCTAssertFalse(resolution.needsHandler)
    }

    func testRecognizesBundledLocalURL() {
        let localConfig = BundledAssetSupport.LocalConfig(scheme: "capacitor", host: "localhost")
        XCTAssertTrue(BundledAssetSupport.isBundledLocalURL("capacitor://localhost/assets/app.js", localConfig: localConfig))
        XCTAssertFalse(BundledAssetSupport.isBundledLocalURL("https://example.com/assets/app.js", localConfig: localConfig))
    }

    func testRoutesExtensionlessPathsToIndexHtml() {
        let basePath = "/tmp/public"
        XCTAssertEqual(BundledAssetSupport.routeAssetPath(for: "/dashboard", basePath: basePath), "/tmp/public/index.html")
        XCTAssertEqual(BundledAssetSupport.routeAssetPath(for: "/assets/app.js", basePath: basePath), "/tmp/public/assets/app.js")
    }
}
