import XCTest
@testable import InappbrowserPlugin

final class CloseButtonPositionSupportTests: XCTestCase {
    func testMapsOptionValuesToNavigationBarSides() {
        XCTAssertEqual(CloseButtonPositionSupport.navigationBarPosition(for: "start"), .left)
        XCTAssertEqual(CloseButtonPositionSupport.navigationBarPosition(for: "end"), .right)
    }

    func testKeepsPlatformDefaultWhenUnsetOrUnknown() {
        XCTAssertNil(CloseButtonPositionSupport.navigationBarPosition(for: nil))
        XCTAssertNil(CloseButtonPositionSupport.navigationBarPosition(for: "left"))
    }
}
