import XCTest
@testable import InappbrowserPlugin

final class ReloadGestureSupportTests: XCTestCase {
    func testStopReloadGestureSkippedWhenNoGestureReloadInProgress() {
        XCTAssertFalse(
            ReloadGestureSupport.shouldApplyStopReloadGesture(reloadFromGestureInProgress: false)
        )
        XCTAssertTrue(
            ReloadGestureSupport.shouldApplyStopReloadGesture(reloadFromGestureInProgress: true)
        )
    }

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

    func testPullDistanceUsesAdjustedInset() {
        XCTAssertEqual(
            ReloadGestureSupport.pullDistance(contentOffsetY: -47, adjustedContentInsetTop: 47),
            0
        )
        XCTAssertEqual(
            ReloadGestureSupport.pullDistance(contentOffsetY: -107, adjustedContentInsetTop: 47),
            60
        )
    }

    func testReloadsOnTouchEndUsingArmedActivationDistance() {
        XCTAssertTrue(
            ReloadGestureSupport.shouldReloadOnTouchEnd(
                pendingReload: true,
                currentPullDistance: 55,
                armedPullDistance: 60
            )
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(
                pendingReload: true,
                currentPullDistance: 20,
                armedPullDistance: 60
            )
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(
                pendingReload: false,
                currentPullDistance: 60,
                armedPullDistance: 60
            )
        )
        XCTAssertFalse(
            ReloadGestureSupport.shouldReloadOnTouchEnd(
                pendingReload: true,
                currentPullDistance: 60,
                armedPullDistance: 0
            )
        )
    }

    func testReloadResetClampsOrSnapsToRestingAdjustedInset() {
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -120, adjustedContentInsetTop: 47),
            -47
        )
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -47, adjustedContentInsetTop: 47),
            -47
        )
        // Cancel path must not jump a scrolled page to the top.
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: 80, adjustedContentInsetTop: 47),
            80
        )
        // After gesture reload, force resting top even when offset drifted to 0 (clipped header).
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(
                currentY: 0,
                adjustedContentInsetTop: 47,
                forceToRestingTop: true
            ),
            -47
        )
        XCTAssertEqual(
            ReloadGestureSupport.contentOffsetYAfterReloadReset(currentY: -20, adjustedContentInsetTop: 0),
            0
        )
    }
}
