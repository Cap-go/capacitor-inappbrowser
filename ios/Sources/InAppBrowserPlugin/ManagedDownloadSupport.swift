import Foundation
import WebKit

enum ManagedDownloadSupport {
    static let progressMinInterval: TimeInterval = 0.25

    static func shouldEmitProgress(lastEmit: TimeInterval, now: TimeInterval) -> Bool {
        lastEmit <= 0 || now - lastEmit >= progressMinInterval
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
    var lastProgressEmit: CFAbsoluteTime = 0
    let endingGuard = ManagedDownloadEndingGuard()

    init(downloadId: String, sourceURL: String?, mimeType: String?) {
        self.downloadId = downloadId
        self.sourceURL = sourceURL
        self.mimeType = mimeType
    }
}