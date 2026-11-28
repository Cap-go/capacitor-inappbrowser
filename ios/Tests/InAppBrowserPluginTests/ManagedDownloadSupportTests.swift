import XCTest
@testable import InappbrowserPlugin

final class ManagedDownloadSupportTests: XCTestCase {
    func testProgressThrottleAllowsAtMostFourPerSecond() {
        let base: TimeInterval = 10
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: 0, now: base))
        XCTAssertFalse(ManagedDownloadSupport.shouldEmitProgress(lastEmit: base, now: base + ManagedDownloadSupport.progressMinInterval - 0.01))
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: base, now: base + ManagedDownloadSupport.progressMinInterval))
    }

    func testUnknownTotalBecomesMinusOne() {
        XCTAssertEqual(ManagedDownloadSupport.resolvedBytesExpected(-1), -1)
        XCTAssertEqual(ManagedDownloadSupport.resolvedBytesExpected(0), -1)
        XCTAssertEqual(ManagedDownloadSupport.resolvedBytesExpected(4096), 4096)
    }

    func testEndingGuardAllowsSingleTerminalEvent() {
        let guardState = ManagedDownloadEndingGuard()
        XCTAssertTrue(guardState.markStarted())
        XCTAssertTrue(guardState.trySetEnding("completed"))
        XCTAssertFalse(guardState.trySetEnding("failed"))
        XCTAssertFalse(guardState.trySetEnding("cancelled"))
        XCTAssertTrue(guardState.hasEnded())
    }

    func testEndingGuardRejectsEndingBeforeStart() {
        let guardState = ManagedDownloadEndingGuard()
        XCTAssertFalse(guardState.trySetEnding("cancelled"))
        XCTAssertFalse(guardState.hasEnded())
        XCTAssertTrue(guardState.markStarted())
        XCTAssertFalse(guardState.markStarted())
        XCTAssertTrue(guardState.trySetEnding("cancelled"))
        XCTAssertFalse(guardState.trySetEnding("completed"))
    }
}
