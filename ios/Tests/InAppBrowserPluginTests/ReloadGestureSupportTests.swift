import XCTest
@testable import InappbrowserPlugin

final class ReloadGestureSupportTests: XCTestCase {
    func testDefersReloadWhileFingerStillDown() {
        XCTAssertTrue(
            ReloadGestureSupport.shouldDeferReloadUntilTouchEnd(isTracking: true, isDragging: false)
        )
        XCTAssertTrue(
            ReloadGestureSupport.shouldDeferReloadUntilTouchEnd(isTracking: false, isDragging: true)
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldDeferReloadUntilTouchEnd(isTracking: false, isDragging: false)
        )
    }

    func testReloadsOnTouchEndOnlyWhenPendingAndRefreshing() {
        XCTAssertTrue(
            ReloadGestureSupport.shouldReloadOnTouchEnd(pendingReload: true, isRefreshing: true)
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(pendingReload: true, isRefreshing: false)
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(pendingReload: false, isRefreshing: true)
        )
    }

    func testReloadResetClearsNegativeContentOffsetGap() {
        XCTAssertEqual(ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -64), 0)
        XCTAssertEqual(ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: 0), 0)
        XCTAssertEqual(ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: 120), 120)
    }
}
