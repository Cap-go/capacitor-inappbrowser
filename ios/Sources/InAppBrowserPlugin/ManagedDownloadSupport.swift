import Foundation
import WebKit

enum ManagedDownloadSupport {
    static let progressMinInterval: TimeInterval = 0.25

    static func shouldEmitProgress(lastEmit: TimeInterval, now: TimeInterval) -> Bool {
        lastEmit <= 0 || now - lastEmit >= progressMinInterval
    }

    /// Foundation reports an unknown total as -1 (or 0 before the response arrives); events use -1.
    static func resolvedBytesExpected(_ totalUnitCount: Int64) -> Int64 {
        totalUnitCount > 0 ? totalUnitCount : -1
    }
}

final class ManagedDownloadEndingGuard {
    private var started = false
    private var ending: String?

    func markStarted() -> Bool {
        guard !started else {
            return false
        }
        started = true
        return true
    }

    func trySetEnding(_ kind: String) -> Bool {
        guard started, ending == nil else {
            return false
        }
        ending = kind
        return true
    }

    func hasEnded() -> Bool {
        ending != nil
    }
}

final class ManagedDownloadRecord {
    let downloadId: String
    weak var wkDownload: WKDownload?
    var destinationURL: URL?
    var mimeType: String?
    var sourceURL: String?
    var fileName: String?
    var progressObservation: NSKeyValueObservation?
    var lastProgressEmit: TimeInterval = 0
    var cancelRequested = false
    let endingGuard = ManagedDownloadEndingGuard()

    init(downloadId: String, sourceURL: String?, mimeType: String?) {
        self.downloadId = downloadId
        self.sourceURL = sourceURL
        self.mimeType = mimeType
    }
}
