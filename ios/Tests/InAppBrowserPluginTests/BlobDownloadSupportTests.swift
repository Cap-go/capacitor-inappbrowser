import XCTest
@testable import InappbrowserPlugin

final class BlobDownloadSupportTests: XCTestCase {
    private var directoryURL: URL!

    deinit {
        // Exists only to satisfy SwiftLint `required_deinit` (Sonar/CodeRabbit flag empty deinit).
    }

    override func setUpWithError() throws {
        directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("BlobDownloadSupportTests-\(UUID().uuidString)", isDirectory: true)
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

    func testOpenWriteHandleCreatesMissingFile() throws {
        let fileURL = directoryURL.appendingPathComponent("download")

        let fileHandle = try BlobDownloadSupport.openWriteHandle(at: fileURL)
        try fileHandle.close()

        XCTAssertTrue(FileManager.default.fileExists(atPath: fileURL.path))
    }

    func testOpenWriteHandleWritesChunksInOrder() throws {
        let fileURL = directoryURL.appendingPathComponent("card.pkpass")

        let fileHandle = try BlobDownloadSupport.openWriteHandle(at: fileURL)
        fileHandle.write(Data("first-".utf8))
        fileHandle.write(Data("second".utf8))
        try fileHandle.close()

        XCTAssertEqual(try Data(contentsOf: fileURL), Data("first-second".utf8))
    }

    func testOpenWriteHandleThrowsWhenDirectoryIsMissing() {
        let fileURL = directoryURL
            .appendingPathComponent("missing", isDirectory: true)
            .appendingPathComponent("download")

        XCTAssertThrowsError(try BlobDownloadSupport.openWriteHandle(at: fileURL))
    }
}
