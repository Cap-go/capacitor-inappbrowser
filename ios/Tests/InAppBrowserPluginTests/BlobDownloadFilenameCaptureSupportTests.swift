import XCTest
@testable import InappbrowserPlugin

final class BlobDownloadFilenameCaptureSupportTests: XCTestCase {
    func testCaptureScriptWrapsAnchorClickAndDispatchEvent() {
        let script = BlobDownloadFilenameCaptureSupport.captureScript

        XCTAssertTrue(script.contains("HTMLAnchorElement.prototype"))
        XCTAssertTrue(script.contains("originalClick"))
        XCTAssertTrue(script.contains("originalDispatchEvent"))
        XCTAssertTrue(script.contains("event.type === 'click'"))
        XCTAssertTrue(script.contains("closest('a[download]')"))
        XCTAssertTrue(script.contains("__capgoBlobDownloadFilenameCapture"))
    }

    func testCaptureScriptRecordsBlobDownloadNames() {
        let script = BlobDownloadFilenameCaptureSupport.captureScript

        XCTAssertTrue(script.contains(BlobDownloadFilenameCaptureSupport.blobDownloadNamesGlobal))
        XCTAssertTrue(script.contains("indexOf('blob:')"))
        XCTAssertTrue(script.contains("__capgoBlobDownloadFilenameCaptureRegistered"))
    }

    func testFileNameResolutionPrefersRecordedNames() {
        let resolver = BlobDownloadFilenameCaptureSupport.fileNameResolutionJS

        XCTAssertTrue(resolver.contains(BlobDownloadFilenameCaptureSupport.blobDownloadNamesGlobal))
        XCTAssertTrue(resolver.contains("recorded"))
        XCTAssertTrue(resolver.contains("getAttribute('download')"))
        XCTAssertTrue(resolver.contains("hasAttribute('download')"))
        XCTAssertTrue(resolver.contains("fallbackFileName"))
    }

    func testCaptureScriptRefreshesNamesAfterClickAndClearsEmptyDownload() {
        let script = BlobDownloadFilenameCaptureSupport.captureScript

        XCTAssertTrue(script.contains("recordBlobDownloadName(this)"))
        XCTAssertTrue(script.contains("delete window.\(BlobDownloadFilenameCaptureSupport.blobDownloadNamesGlobal)"))
        XCTAssertTrue(script.contains("}, false)"))
    }
}