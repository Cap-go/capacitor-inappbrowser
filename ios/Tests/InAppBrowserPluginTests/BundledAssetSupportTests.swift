import XCTest
@testable import InappbrowserPlugin

final class BundledAssetSupportTests: XCTestCase {
    deinit {}

    func testResolveRelativePathForCapacitorLocalURL() {
        let localURL = URL(string: "capacitor://localhost")!
        let resolution = BundledAssetSupport.resolve("/page.html", localURL: localURL)

        XCTAssertEqual(resolution?.url, "capacitor://localhost/page.html")
    }

    func testUsesCapacitorSchemeWhenLocalConfigUsesHttp() {
        let localURL = URL(string: "http://localhost")!
        let resolution = BundledAssetSupport.resolve("/page.html", localURL: localURL)

        XCTAssertEqual(resolution?.url, "capacitor://localhost/page.html")
        XCTAssertEqual(BundledAssetSupport.handlerScheme(for: BundledAssetSupport.parseLocalConfig(from: localURL)!), "capacitor")
    }

    func testRewritesBundledLocalUrlToHandlerScheme() {
        let localURL = URL(string: "https://localhost")!
        let resolution = BundledAssetSupport.resolve("https://localhost/index.html", localURL: localURL)

        XCTAssertEqual(resolution?.url, "capacitor://localhost/index.html")
    }

    func testRejectsPathTraversal() {
        XCTAssertNil(BundledAssetSupport.resolve("/../secret.txt", localURL: URL(string: "capacitor://localhost")))
        XCTAssertNil(BundledAssetSupport.normalizeBundledPath("/../secret.txt"))
        XCTAssertNil(BundledAssetSupport.routeAssetPath(for: "/../../secret.txt", basePath: "/tmp/public"))
    }

    func testKeepsRemoteUrlsUnchanged() {
        let resolution = BundledAssetSupport.resolve("https://example.com/page.html", localURL: URL(string: "capacitor://localhost"))

        XCTAssertEqual(resolution?.url, "https://example.com/page.html")
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
