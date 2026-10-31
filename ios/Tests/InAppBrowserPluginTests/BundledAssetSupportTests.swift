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

    func testKeepsAbsoluteHttpsSameHostRemoteWithQueryString() {
        let localURL = URL(string: "https://portal.example.com/")!
        let remoteURL = "https://portal.example.com/auth/signin?state=example"
        let resolution = BundledAssetSupport.resolve(remoteURL, localURL: localURL)

        XCTAssertEqual(resolution?.url, remoteURL)
    }

    func testResolvesRelativePathForCustomHostnameLocalURL() {
        let localURL = URL(string: "https://portal.example.com/")!
        let resolution = BundledAssetSupport.resolve("/index.html", localURL: localURL)

        XCTAssertEqual(resolution?.url, "https://portal.example.com/index.html")
    }

    func testTreatsLoopbackAbsoluteHttpsAsBundledLocalURL() {
        let localConfig = BundledAssetSupport.LocalConfig(scheme: "https", host: "127.0.0.1")
        XCTAssertTrue(BundledAssetSupport.isBundledLocalURL("https://127.0.0.1/assets/app.js", localConfig: localConfig))
    }

    func testRoutesExtensionlessPathsToIndexHtml() {
        let basePath = "/tmp/public"
        XCTAssertEqual(BundledAssetSupport.routeAssetPath(for: "/dashboard", basePath: basePath), "/tmp/public/index.html")
        XCTAssertEqual(BundledAssetSupport.routeAssetPath(for: "/assets/app.js", basePath: basePath), "/tmp/public/assets/app.js")
    }

    func testDistinguishesBundledPathsFromBareHostnames() {
        XCTAssertTrue(BundledAssetSupport.isLikelyBundledRelativePath("/index.html"))
        XCTAssertTrue(BundledAssetSupport.isLikelyBundledRelativePath("assets/page.html"))
        XCTAssertTrue(BundledAssetSupport.isLikelyBundledRelativePath("index.html?v=2"))
        XCTAssertFalse(BundledAssetSupport.isLikelyBundledRelativePath("www.example.com"))
        XCTAssertFalse(BundledAssetSupport.isLikelyBundledRelativePath(".env"))
        XCTAssertFalse(BundledAssetSupport.isLikelyBundledRelativePath("assets/.env"))
        XCTAssertFalse(BundledAssetSupport.isLikelyBundledRelativePath("file."))
        XCTAssertFalse(BundledAssetSupport.isLikelyBundledRelativePath("https://example.com"))
    }
}
