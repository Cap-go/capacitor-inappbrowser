import XCTest
@testable import InAppBrowserPlugin

final class JavascriptBridgeOriginPolicyTests: XCTestCase {
    func testUnrestrictedAllowsAnyUrl() {
        let policy = JavascriptBridgeOriginPolicy.unrestricted
        XCTAssertTrue(policy.isUrlAllowed("https://evil.example/phish"))
        XCTAssertTrue(policy.isUnrestricted)
    }

    func testDisabledRejectsUrls() {
        let policy = JavascriptBridgeOriginPolicy.disabled
        XCTAssertFalse(policy.isUrlAllowed("https://trusted.example/"))
        XCTAssertTrue(policy.isDisabled)
    }

    func testAllowlistMatchesExactOrigin() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: ["https://login.example.com"])
        XCTAssertTrue(policy.isUrlAllowed("https://login.example.com/oauth"))
        XCTAssertFalse(policy.isUrlAllowed("https://evil.example.com/"))
    }

    func testAllowlistSupportsHostWildcards() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: ["https://*.trusted.example"])
        XCTAssertTrue(policy.isUrlAllowed("https://app.trusted.example/path"))
        XCTAssertFalse(policy.isUrlAllowed("https://trusted.example/"))
    }

    func testEmptyAllowlistIsDisabled() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: [])
        XCTAssertTrue(policy.isDisabled)
    }
}
