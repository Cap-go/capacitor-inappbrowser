import Foundation
import WebKit

/// Tracks managed download lifecycle callbacks for one browser instance.
final class ManagedDownloadCoordinator {
    static let progressMinInterval: TimeInterval = 0.25

    struct ManagedDownload {
        let downloadId: String
        let sourceURL: String?
        var terminalEmitted = false
        var cancelRequested = false
        var bytesReceived: Int64 = 0
        var bytesExpected: Int64 = -1
        var fileName: String?
        var lastProgressEmitTime: TimeInterval = 0
        weak var wkDownload: WKDownload?
        var progressObservation: NSKeyValueObservation?
    }

    private var downloads: [String: ManagedDownload] = [:]
    private let lock = NSLock()

    var emitStarted: ((String, String?) -> Void)?
    var emitProgress: ((String, Int64, Int64, String?) -> Void)?
    var emitCancelled: ((String, String?) -> Void)?

    static func shouldEmitProgress(lastEmit: TimeInterval, now: TimeInterval) -> Bool {
        lastEmit == 0 || now - lastEmit >= progressMinInterval
    }

    @discardableResult
    func begin(sourceURL: String?) -> String {
        let downloadId = UUID().uuidString
        lock.lock()
        downloads[downloadId] = ManagedDownload(downloadId: downloadId, sourceURL: sourceURL)
        lock.unlock()
        emitStarted?(downloadId, sourceURL)
        return downloadId
    }

    func registerExisting(downloadId: String, sourceURL: String?) {
        lock.lock()
        downloads[downloadId] = ManagedDownload(downloadId: downloadId, sourceURL: sourceURL)
        lock.unlock()
        emitStarted?(downloadId, sourceURL)
    }

    func download(for downloadId: String) -> ManagedDownload? {
        lock.lock()
        defer { lock.unlock() }
        return downloads[downloadId]
    }

    func updateDownload(_ downloadId: String, _ update: (inout ManagedDownload) -> Void) {
        lock.lock()
        defer { lock.unlock() }
        guard var state = downloads[downloadId] else {
            return
        }
        update(&state)
        downloads[downloadId] = state
    }

  @discardableResult
    func markTerminal(downloadId: String) -> ManagedDownload? {
        lock.lock()
        defer { lock.unlock() }
        guard var state = downloads.removeValue(forKey: downloadId) else {
            return nil
        }
        state.terminalEmitted = true
        state.progressObservation?.invalidate()
        return state
    }

    func reportProgress(downloadId: String, bytesReceived: Int64, bytesExpected: Int64, fileName: String?) {
        let now = CFAbsoluteTimeGetCurrent()
        lock.lock()
        guard var state = downloads[downloadId], !state.terminalEmitted else {
            lock.unlock()
            return
        }
        state.bytesReceived = bytesReceived
        if bytesExpected >= 0 {
            state.bytesExpected = bytesExpected
        }
        if let fileName {
            state.fileName = fileName
        }
        guard ManagedDownloadCoordinator.shouldEmitProgress(lastEmit: state.lastProgressEmitTime, now: now) else {
            downloads[downloadId] = state
            lock.unlock()
            return
        }
        state.lastProgressEmitTime = now
        downloads[downloadId] = state
        let expected = state.bytesExpected
        let name = state.fileName
        lock.unlock()
        emitProgress?(downloadId, bytesReceived, expected, name)
    }

    func emitFinalProgress(downloadId: String) {
        lock.lock()
        guard let state = downloads[downloadId], !state.terminalEmitted else {
            lock.unlock()
            return
        }
        let bytesReceived = state.bytesReceived
        let bytesExpected = state.bytesExpected
        let fileName = state.fileName
        lock.unlock()
        emitProgress?(downloadId, bytesReceived, bytesExpected, fileName)
    }

    @discardableResult
    func requestCancel(downloadId: String) -> Bool {
        lock.lock()
        guard var state = downloads[downloadId] else {
            lock.unlock()
            return false
        }
        state.cancelRequested = true
        if state.terminalEmitted {
            downloads[downloadId] = state
            lock.unlock()
            return true
        }
        state.terminalEmitted = true
        state.progressObservation?.invalidate()
        downloads.removeValue(forKey: downloadId)
        let sourceURL = state.sourceURL
        let wkDownload = state.wkDownload
        lock.unlock()
        wkDownload?.cancel(nil)
        emitCancelled?(downloadId, sourceURL)
        return true
    }

    func cancelAll() {
        lock.lock()
        let ids = Array(downloads.keys)
        lock.unlock()
        for downloadId in ids {
            requestCancel(downloadId: downloadId)
        }
    }
}