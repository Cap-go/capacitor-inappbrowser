import XCTest
@testable import InAppBrowserPlugin

final class HtmlDataUrlSupportTests: XCTestCase {
    func testParsesHtmlBase64DataUrl() {
        let html = "<html><body><form id=\"loginForm\"></form></body></html>"
        let payload = Data(html.utf8).base64EncodedString()
        let url = "data:text/html;base64,\(payload)"

        XCTAssertTrue(HtmlDataUrlSupport.isHtmlBase64DataUrl(url))
        XCTAssertEqual(HtmlDataUrlSupport.parseHtml(from: url), html)
    }

    func testRejectsNonHtmlDataUrls() {
        XCTAssertFalse(HtmlDataUrlSupport.isHtmlBase64DataUrl("data:image/png;base64,abc"))
        XCTAssertNil(HtmlDataUrlSupport.parseHtml(from: "https://example.com"))
    }
}
