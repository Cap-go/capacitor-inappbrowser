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

    func testPullDistanceAndThresholdUseAdjustedInset() {
        // Resting at -47 with 47pt top inset → pull distance 0
        XCTAssertEqual(
            ReloadGestureSupport.pullDistance(contentOffsetY: -47, adjustedContentInsetTop: 47),
            0
        )
        // Pulled an extra 60pt → past default threshold
        XCTAssertTrue(
            ReloadGestureSupport.isPullPastRefreshThreshold(
                contentOffsetY: -107,
                adjustedContentInsetTop: 47
            )
        )
        // Pulled only 20pt → below threshold
        XCTAssertFalse(
            ReloadGestureSupport.isPullPastRefreshThreshold(
                contentOffsetY: -67,
                adjustedContentInsetTop: 47
            )
        )
    }

    func testReloadsOnTouchEndOnlyWhenPendingAndStillPastThreshold() {
        XCTAssertTrue(
            ReloadGestureSupport.shouldReloadOnTouchEnd(pendingReload: true, isPullPastThreshold: true)
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(pendingReload: true, isPullPastThreshold: false)
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(pendingReload: false, isPullPastThreshold: true)
        )
    }

    func testReloadResetClampsToRestingAdjustedInset() {
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -120, adjustedContentInsetTop: 47),
            -47
        )
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -47, adjustedContentInsetTop: 47),
            -47
        )
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: 80, adjustedContentInsetTop: 47),
            80
        )
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -20, adjustedContentInsetTop: 0),
            0
        )
    }
}
