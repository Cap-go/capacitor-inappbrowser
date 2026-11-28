import XCTest
@testable import InappbrowserPlugin

final class ManagedDownloadCoordinatorTests: XCTestCase {
    func testProgressThrottleAllowsFourPerSecond() {
        let base = 10.0
        XCTAssertTrue(ManagedDownloadCoordinator.shouldEmitProgress(lastEmit: 0, now: base))
        XCTAssertFalse(ManagedDownloadCoordinator.shouldEmitProgress(lastEmit: base, now: base + 0.1))
        XCTAssertTrue(ManagedDownloadCoordinator.shouldEmitProgress(lastEmit: base, now: base + ManagedDownloadCoordinator.progressMinInterval))
    }

    func testCancelEmitsOnce() {
        let coordinator = ManagedDownloadCoordinator()
        var cancelled: [String] = []
        coordinator.emitCancelled = { downloadId, _ in cancelled.append(downloadId) }
        let downloadId = coordinator.begin(sourceURL: "https://example.com/file.bin")
        XCTAssertTrue(coordinator.requestCancel(downloadId: downloadId))
        XCTAssertTrue(coordinator.requestCancel(downloadId: downloadId))
        XCTAssertEqual(cancelled, [downloadId])
    }
}