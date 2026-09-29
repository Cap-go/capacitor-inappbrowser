import XCTest
@testable import InappbrowserPlugin

final class BlobDownloadSupportTests: XCTestCase {
    private let directoryURL = FileManager.default.temporaryDirectory
        .appendingPathComponent("BlobDownloadSupportTests-\(UUID().uuidString)", isDirectory: true)

    deinit {
        // Exists only to satisfy SwiftLint `required_deinit` (Sonar/CodeRabbit flag empty deinit).
    }

    override func setUpWithError() throws {
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directoryURL)
    }

    /// The reserved download destination does not exist yet, which is why opening it directly fails.
    func testFileHandleForWritingFailsForMissingFile() {
        let fileURL = directoryURL.appendingPathComponent("download")

        XCTAssertThrowsError(try FileHandle(forWritingTo: fileURL))
    }

    /// The helper creates the reserved destination before opening it.
    func testOpenWriteHandleCreatesMissingFile() throws {
        let fileURL = directoryURL.appendingPathComponent("download")

        let fileHandle = try BlobDownloadSupport.openWriteHandle(at: fileURL)
        try fileHandle.close()

        XCTAssertTrue(FileManager.default.fileExists(atPath: fileURL.path))
    }

    /// Chunks appended through the handle end up in the file in order.
    func testOpenWriteHandleWritesChunksInOrder() throws {
        let fileURL = directoryURL.appendingPathComponent("card.pkpass")

        let fileHandle = try BlobDownloadSupport.openWriteHandle(at: fileURL)
        try fileHandle.write(contentsOf: Data("first-".utf8))
        try fileHandle.write(contentsOf: Data("second".utf8))
        try fileHandle.close()

        XCTAssertEqual(try Data(contentsOf: fileURL), Data("first-second".utf8))
    }

    /// Starts are rejected once the active session limit is reached, before any file is created.
    func testCanStartSessionOnlyBelowLimit() {
        XCTAssertTrue(BlobDownloadSupport.canStartSession(activeSessionCount: 0))
        XCTAssertTrue(BlobDownloadSupport.canStartSession(activeSessionCount: BlobDownloadSupport.maxActiveSessions - 1))
        XCTAssertFalse(BlobDownloadSupport.canStartSession(activeSessionCount: BlobDownloadSupport.maxActiveSessions))
        XCTAssertFalse(BlobDownloadSupport.canStartSession(activeSessionCount: BlobDownloadSupport.maxActiveSessions + 1))
    }

    /// Failing to create the file surfaces as an error instead of a handle.
    func testOpenWriteHandleThrowsWhenDirectoryIsMissing() {
        let fileURL = directoryURL
            .appendingPathComponent("missing", isDirectory: true)
            .appendingPathComponent("download")

        XCTAssertThrowsError(try BlobDownloadSupport.openWriteHandle(at: fileURL))
    }
}
