import XCTest
@testable import InappbrowserPlugin

final class ToolbarNativeUIEngineSupportTests: XCTestCase {
    func testStaysOffUnlessExplicitlyEnabled() {
        XCTAssertFalse(ToolbarNativeUIEngineSupport.shouldRegister(enabled: false, toolbarType: ""))
    }

    func testRejectsBlankToolbar() {
        XCTAssertFalse(ToolbarNativeUIEngineSupport.shouldRegister(enabled: true, toolbarType: "blank"))
        XCTAssertFalse(ToolbarNativeUIEngineSupport.shouldRegister(enabled: true, toolbarType: "BLANK"))
    }

    func testAcceptsNavigationToolbar() {
        XCTAssertTrue(ToolbarNativeUIEngineSupport.shouldRegister(enabled: true, toolbarType: ""))
        XCTAssertTrue(ToolbarNativeUIEngineSupport.shouldRegister(enabled: true, toolbarType: "navigation"))
    }

    func testValidatesSelectorWhenEnabled() {
        XCTAssertNil(ToolbarNativeUIEngineSupport.validateOptions(enabled: false, projectionSelector: nil))
        XCTAssertEqual(
            ToolbarNativeUIEngineSupport.validateOptions(enabled: true, projectionSelector: nil),
            "nativeUIProjectionSelector is required when enableNativeUIEngine is true"
        )
        XCTAssertNil(ToolbarNativeUIEngineSupport.validateOptions(enabled: true, projectionSelector: "slot"))
        XCTAssertNil(ToolbarNativeUIEngineSupport.validateOptions(enabled: true, projectionSelector: "#slot"))
    }
}