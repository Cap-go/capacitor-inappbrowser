import XCTest
@testable import InappbrowserPlugin

final class CustomSchemeInterceptSupportTests: XCTestCase {
    func testEmitsForNonStandardCustomSchemes() throws {
        XCTAssertTrue(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "myapp://callback/success")))
        )
        XCTAssertTrue(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "com.example.app://oauth/callback")))
        )
    }

    func testSkipsWebFileAndStandardOsSchemes() throws {
        XCTAssertFalse(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "https://example.com")))
        )
        XCTAssertFalse(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "http://example.com")))
        )
        XCTAssertFalse(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "file:///tmp/index.html")))
        )
        XCTAssertFalse(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "tel:+15555550123")))
        )
        XCTAssertFalse(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "mailto:test@example.com")))
        )
        XCTAssertFalse(
            CustomSchemeInterceptSupport.shouldEmitInterceptEvent(for: try XCTUnwrap(URL(string: "sms:+15555550123")))
        )
    }
}
