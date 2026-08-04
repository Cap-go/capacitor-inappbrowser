import XCTest
@testable import InappbrowserPlugin

final class SecureWindowRedirectSupportTests: XCTestCase {
    private func matches(_ callback: String, _ redirectUri: String) throws -> Bool {
        let callbackURL = try XCTUnwrap(URL(string: callback))
        return SecureWindowRedirectSupport.matches(callbackURL, redirectUri: redirectUri)
    }

    func testAcceptsRedirectWithProviderResponseParameters() throws {
        XCTAssertTrue(try matches("myapp://callback?code=abc&state=xyz", "myapp://callback"))
        XCTAssertTrue(try matches("https://example.com/callback?code=abc", "https://example.com/callback"))
    }

    func testAcceptsConfiguredQueryItemsInAnyOrder() throws {
        XCTAssertTrue(try matches("myapp://callback?code=abc&type=login", "myapp://callback?type=login"))
        XCTAssertFalse(try matches("myapp://callback?code=abc", "myapp://callback?type=login"))
        XCTAssertFalse(try matches("myapp://callback?type=logout", "myapp://callback?type=login"))
    }

    func testRejectsConflictingDuplicatesOfConfiguredQueryItems() throws {
        XCTAssertFalse(try matches("myapp://callback?type=login&type=evil", "myapp://callback?type=login"))
        XCTAssertFalse(try matches("myapp://callback?type=evil&type=login", "myapp://callback?type=login"))
        XCTAssertFalse(try matches("myapp://callback?type=login&t%79pe=evil", "myapp://callback?type=login"))
    }

    func testRequiresEveryOccurrenceOfARepeatedQueryItemInOrder() throws {
        XCTAssertFalse(try matches("myapp://callback?scope=a", "myapp://callback?scope=a&scope=b"))
        XCTAssertTrue(try matches("myapp://callback?scope=a&scope=b&code=x", "myapp://callback?scope=a&scope=b"))
        XCTAssertFalse(try matches("myapp://callback?scope=b&scope=a", "myapp://callback?scope=a&scope=b"))
    }

    func testRejectsCallbacksThatOnlyShareThePrefix() throws {
        XCTAssertFalse(try matches("myapp://callback-evil?code=abc", "myapp://callback"))
        XCTAssertFalse(try matches("myapp://callback/evil?code=abc", "myapp://callback"))
        XCTAssertFalse(try matches("https://example.com/callback.evil.test/x", "https://example.com/callback"))
    }

    func testKeepsEncodedAndLiteralComponentsDistinct() throws {
        XCTAssertFalse(try matches("https://example.com/a%2Fb", "https://example.com/a/b"))
        XCTAssertFalse(try matches("https://example.com/a/b", "https://example.com/a%2Fb"))
        XCTAssertTrue(try matches("https://example.com/a%2Fb?code=x", "https://example.com/a%2Fb"))
        XCTAssertFalse(try matches("myapp://c%61llback", "myapp://callback"))
        XCTAssertFalse(try matches("myapp://callback?type=b%2Bc", "myapp://callback?type=b+c"))
        XCTAssertTrue(try matches("myapp://callback?type=b+c&code=x", "myapp://callback?type=b+c"))
    }

    func testRejectsInjectedUserInfo() throws {
        XCTAssertFalse(try matches("myapp://evil@callback?code=abc", "myapp://callback"))
        XCTAssertFalse(try matches("https://evil:secret@example.com/callback", "https://example.com/callback"))
    }

    func testComparesSchemeAndHostCaseInsensitivelyAndPortExactly() throws {
        XCTAssertTrue(try matches("MYAPP://CALLBACK?code=abc", "myapp://callback"))
        XCTAssertFalse(try matches("https://example.com:8080/callback", "https://example.com/callback"))
    }

    func testTreatsRootPathAsEquivalentToEmptyPath() throws {
        XCTAssertTrue(try matches("myapp://callback/?code=abc", "myapp://callback"))
        XCTAssertTrue(try matches("myapp://callback?code=abc", "myapp://callback/"))
    }

    func testRejectsRedirectUriThatCannotBeParsed() throws {
        XCTAssertFalse(try matches("myapp://callback", ""))
    }
}
