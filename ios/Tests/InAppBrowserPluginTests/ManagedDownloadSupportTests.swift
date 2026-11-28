import XCTest
@testable import InAppBrowserPlugin

final class ManagedDownloadSupportTests: XCTestCase {
    func testProgressThrottleAllowsAtMostFourPerSecond() {
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: 0, now: 1))
        XCTAssertFalse(ManagedDownloadSupport.shouldEmitProgress(lastEmit: 0, now: ManagedDownloadSupport.progressMinInterval - 0.01))
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: 0, now: ManagedDownloadSupport.progressMinInterval))
    }

    func testEndingGuardAllowsSingleTerminalEvent() {
        let guardState = ManagedDownloadEndingGuard()
        XCTAssertTrue(guardState.markStarted())
        XCTAssertTrue(guardState.trySetEnding("completed"))
        XCTAssertFalse(guardState.trySetEnding("failed"))
        XCTAssertTrue(guardState.hasEnded())
    }
}