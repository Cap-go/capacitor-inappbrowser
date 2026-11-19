import XCTest
@testable import InappbrowserPlugin

final class DeepLinkPreventedSupportTests: XCTestCase {
    func testEmitsForBlockedCustomSchemesAndOsHandlers() throws {
        XCTAssertTrue(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "myapp://oauth")),
                authorizedAppLinks: []
            )
        )
        XCTAssertTrue(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "tel:+15555550123")),
                authorizedAppLinks: []
            )
        )
        XCTAssertTrue(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "mailto:test@example.com")),
                authorizedAppLinks: []
            )
        )
    }

    func testEmitsForAuthorizedHttpLinks() throws {
        XCTAssertTrue(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "https://partner.example/path")),
                authorizedAppLinks: ["https://partner.example"]
            )
        )
    }

    func testSkipsNormalHttpNavigationAndInternalSchemes() throws {
        XCTAssertFalse(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "https://example.com/page")),
                authorizedAppLinks: []
            )
        )
        XCTAssertFalse(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "about:blank")),
                authorizedAppLinks: []
            )
        )
    }

    func testEmitsForAppStoreHosts() throws {
        XCTAssertTrue(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                for: try XCTUnwrap(URL(string: "https://apps.apple.com/app/id123")),
                authorizedAppLinks: []
            )
        )
    }
}