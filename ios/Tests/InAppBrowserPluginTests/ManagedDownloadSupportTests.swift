import XCTest
@testable import InappbrowserPlugin

final class ManagedDownloadSupportTests: XCTestCase {
    func testProgressThrottleAllowsAtMostFourPerSecond() {
        // Independent literals: four samples per second means one every 250 ms.
        let base: TimeInterval = 10
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: 0, now: base))
        XCTAssertFalse(ManagedDownloadSupport.shouldEmitProgress(lastEmit: base, now: base + 0.249))
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: base, now: base + 0.25))
        XCTAssertTrue(ManagedDownloadSupport.shouldEmitProgress(lastEmit: base, now: base + 1))
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
