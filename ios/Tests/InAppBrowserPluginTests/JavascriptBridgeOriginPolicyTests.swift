import XCTest
@testable import InAppBrowserPlugin

final class JavascriptBridgeOriginPolicyTests: XCTestCase {
    private enum Fixture {
        static func httpsUrl(host: String, path: String = "/") -> String {
            "https://\(host)\(path)"
        }

        static func httpsAllowRule(host: String) -> String {
            "https://\(host)"
        }

        static func httpsWildcardAllowRule(domain: String) -> String {
            "https://*.\(domain)"
        }
    }

    func testUnrestrictedAllowsAnyUrl() {
        let policy = JavascriptBridgeOriginPolicy.unrestricted
        XCTAssertTrue(policy.isUrlAllowed(Fixture.httpsUrl(host: "evil.example", path: "/phish")))
        XCTAssertTrue(policy.isUnrestricted)
    }

    func testDisabledRejectsUrls() {
        let policy = JavascriptBridgeOriginPolicy.disabled
        XCTAssertFalse(policy.isUrlAllowed(Fixture.httpsUrl(host: "trusted.example")))
        XCTAssertTrue(policy.isDisabled)
    }

    func testAllowlistMatchesExactOrigin() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: [Fixture.httpsAllowRule(host: "login.example.com")])
        XCTAssertTrue(policy.isUrlAllowed(Fixture.httpsUrl(host: "login.example.com", path: "/oauth")))
        XCTAssertFalse(policy.isUrlAllowed(Fixture.httpsUrl(host: "evil.example.com")))
    }

    func testAllowlistSupportsHostWildcards() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: [Fixture.httpsWildcardAllowRule(domain: "trusted.example")])
        XCTAssertTrue(policy.isUrlAllowed(Fixture.httpsUrl(host: "app.trusted.example", path: "/path")))
        XCTAssertFalse(policy.isUrlAllowed(Fixture.httpsUrl(host: "trusted.example")))
    }

    func testEmptyAllowlistIsDisabled() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: [])
        XCTAssertTrue(policy.isDisabled)
    }

    func testAllowlistMatchesNormalizedDefaultPortOrigin() {
        let policy = JavascriptBridgeOriginPolicy.from(allowlist: [Fixture.httpsAllowRule(host: "login.example.com")])
        let normalized = JavascriptBridgeOriginPolicy.buildOrigin(scheme: "https", host: "login.example.com", port: -1)
        XCTAssertEqual(normalized, Fixture.httpsAllowRule(host: "login.example.com"))
        XCTAssertTrue(policy.isOriginAllowed(normalized))
        XCTAssertFalse(policy.isOriginAllowed(Fixture.httpsUrl(host: "login.example.com:0")))
    }

    func testOriginFromSecurityOriginPortZeroUsesDefaultPort() {
        XCTAssertEqual(
            JavascriptBridgeOriginPolicy.origin(scheme: "https", host: "login.example.com", securityOriginPort: 0),
            Fixture.httpsAllowRule(host: "login.example.com")
        )
        XCTAssertEqual(
            JavascriptBridgeOriginPolicy.origin(scheme: "HTTPS", host: "Login.Example.com", securityOriginPort: 0),
            Fixture.httpsAllowRule(host: "login.example.com")
        )
    }

    func testBuildOriginOmitsExplicitDefaultHttpsPort() {
        XCTAssertEqual(
            JavascriptBridgeOriginPolicy.buildOrigin(scheme: "https", host: "example.com", port: 443),
            Fixture.httpsAllowRule(host: "example.com")
        )
    }

    func testParsePluginOptionRejectsNonArrayValues() {
        let result = JavascriptBridgeOriginPolicy.parsePluginOption("not-an-array")
        switch result {
        case .failure(let message):
            XCTAssertEqual(message, "javascriptBridgeOrigins must be an array of strings")
        case .success:
            XCTFail("Expected invalid type rejection")
        }
    }

    func testParsePluginOptionTreatsNullAsUnrestricted() {
        let result = JavascriptBridgeOriginPolicy.parsePluginOption(NSNull())
        switch result {
        case .success(let policy):
            XCTAssertTrue(policy.isUnrestricted)
        case .failure:
            XCTFail("Expected unrestricted policy")
        }
    }
}
