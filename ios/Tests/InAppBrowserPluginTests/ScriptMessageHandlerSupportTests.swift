import XCTest
import WebKit
@testable import InappbrowserPlugin

final class ScriptMessageHandlerSupportTests: XCTestCase {
    deinit {}

    func testAllNamesIncludesBlobDownloadHandlers() {
        let names = Set(ScriptMessageHandlerSupport.allNames)
        XCTAssertTrue(names.contains("blobDownload"))
        XCTAssertTrue(names.contains("blobDownloadStart"))
        XCTAssertTrue(names.contains("blobDownloadChunk"))
        XCTAssertTrue(names.contains("blobDownloadFinish"))
        XCTAssertTrue(names.contains("blobDownloadAbort"))
    }

    func testRemoveAllAllowsReRegisteringHandlers() {
        let controller = WKUserContentController()
        let handler = DummyScriptMessageHandler()

        for name in ScriptMessageHandlerSupport.allNames {
            controller.add(handler, name: name)
        }

        // Re-adding without remove would throw; removeAll must clear every name.
        ScriptMessageHandlerSupport.removeAll(from: controller)
        for name in ScriptMessageHandlerSupport.allNames {
            XCTAssertNoThrow(controller.add(handler, name: name))
        }
    }
}

private final class DummyScriptMessageHandler: NSObject, WKScriptMessageHandler {
    deinit {}

    func userContentController(
        _ _: WKUserContentController,
        didReceive _: WKScriptMessage
    ) {
        // Intentionally empty; this test only exercises handler registration.
    }
}
