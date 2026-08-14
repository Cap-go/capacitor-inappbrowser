import Foundation
import ObjectiveC
import UniformTypeIdentifiers
import WebKit

final class BundledAssetSchemeHandler: NSObject, WKURLSchemeHandler {
    private let router = CapacitorRouter()
    private var activeTasks: [ObjectIdentifier: WKURLSchemeTask] = [:]
    private let tasksLock = NSLock()

    override init() {
        super.init()
        if let publicDirectory = Self.publicDirectoryURL() {
            router.basePath = publicDirectory.path
        } else if let wwwDirectory = Self.wwwDirectoryURL() {
            router.basePath = wwwDirectory.path
        }
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let requestURL = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(
                NSError(domain: "BundledAssetSchemeHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "Missing request URL"])
            )
            return
        }

        let taskID = ObjectIdentifier(urlSchemeTask)
        tasksLock.lock()
        activeTasks[taskID] = urlSchemeTask
        tasksLock.unlock()

        let filePath = router.route(for: requestURL.path)
        let fileURL = URL(fileURLWithPath: filePath)

        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            finish(task: urlSchemeTask, taskID: taskID) {
                urlSchemeTask.didFailWithError(
                    NSError(
                        domain: "BundledAssetSchemeHandler",
                        code: 404,
                        userInfo: [NSLocalizedDescriptionKey: "Bundled asset not found: \(requestURL.path)"]
                    )
                )
            }
            return
        }

        do {
            let data = try Data(contentsOf: fileURL)
            let mimeType = mimeType(for: requestURL.pathExtension)
            let response = HTTPURLResponse(
                url: requestURL,
                statusCode: 200,
                httpVersion: nil,
                headerFields: [
                    "Content-Type": mimeType,
                    "Cache-Control": "no-cache"
                ]
            ) ?? URLResponse(url: requestURL, mimeType: mimeType, expectedContentLength: data.count, textEncodingName: nil)

            finish(task: urlSchemeTask, taskID: taskID) {
                urlSchemeTask.didReceive(response)
                urlSchemeTask.didReceive(data)
                urlSchemeTask.didFinish()
            }
        } catch {
            finish(task: urlSchemeTask, taskID: taskID) {
                urlSchemeTask.didFailWithError(error)
            }
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        let taskID = ObjectIdentifier(urlSchemeTask)
        tasksLock.lock()
        activeTasks.removeValue(forKey: taskID)
        tasksLock.unlock()
        urlSchemeTask.stopped = true
    }

    private func finish(task: WKURLSchemeTask, taskID: ObjectIdentifier, block: () -> Void) {
        tasksLock.lock()
        let isActive = activeTasks[taskID] != nil
        activeTasks.removeValue(forKey: taskID)
        tasksLock.unlock()

        guard isActive, !task.stopped else {
            return
        }

        block()
    }

    private func mimeType(for pathExtension: String) -> String {
        if !pathExtension.isEmpty,
           let uti = UTType(filenameExtension: pathExtension),
           let mimeType = uti.preferredMIMEType {
            return mimeType
        }

        return pathExtension.isEmpty ? "text/html" : "application/octet-stream"
    }

    private static func publicDirectoryURL() -> URL? {
        Bundle.main.resourceURL?.appendingPathComponent("public", isDirectory: true)
    }

    private static func wwwDirectoryURL() -> URL? {
        Bundle.main.resourceURL?.appendingPathComponent("www", isDirectory: true)
    }
}

private struct CapacitorRouter {
    var basePath: String = ""

    func route(for path: String) -> String {
        let pathURL = URL(fileURLWithPath: path)
        if pathURL.pathExtension.isEmpty {
            return basePath + "/index.html"
        }

        return basePath + path
    }
}

private var bundledAssetStoppedKey = malloc(1)

private extension WKURLSchemeTask {
    var stopped: Bool {
        get {
            objc_getAssociatedObject(self, &bundledAssetStoppedKey) as? Bool ?? false
        }
        set {
            objc_setAssociatedObject(self, &bundledAssetStoppedKey, newValue, .OBJC_ASSOCIATION_ASSIGN)
        }
    }
}
