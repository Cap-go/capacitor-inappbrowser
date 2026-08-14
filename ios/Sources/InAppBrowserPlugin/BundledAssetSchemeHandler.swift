import Foundation
import ObjectiveC
import UniformTypeIdentifiers
import WebKit

final class BundledAssetSchemeHandler: NSObject, WKURLSchemeHandler {
    private let basePath: String
    private let expectedHost: String
    private var activeTasks: [ObjectIdentifier: WKURLSchemeTask] = [:]
    private let tasksLock = NSLock()

    init(expectedHost: String) {
        self.basePath = Self.resolveBasePath()
        self.expectedHost = expectedHost.lowercased()
        super.init()
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

        guard Self.isAllowedBundledAssetRequest(requestURL, expectedHost: expectedHost) else {
            finish(task: urlSchemeTask, taskID: taskID) {
                urlSchemeTask.didFailWithError(
                    NSError(
                        domain: "BundledAssetSchemeHandler",
                        code: 403,
                        userInfo: [NSLocalizedDescriptionKey: "Blocked bundled asset host: \(requestURL.absoluteString)"]
                    )
                )
            }
            return
        }

        guard let filePath = BundledAssetSupport.routeAssetPath(for: requestURL.path, basePath: basePath) else {
            finish(task: urlSchemeTask, taskID: taskID) {
                urlSchemeTask.didFailWithError(
                    NSError(
                        domain: "BundledAssetSchemeHandler",
                        code: 403,
                        userInfo: [NSLocalizedDescriptionKey: "Blocked bundled asset path: \(requestURL.path)"]
                    )
                )
            }
            return
        }

        let fileURL = URL(fileURLWithPath: filePath)

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else {
                return
            }

            guard FileManager.default.fileExists(atPath: fileURL.path) else {
                self.finish(task: urlSchemeTask, taskID: taskID) {
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
                let mimeType = self.mimeType(for: requestURL.pathExtension)
                let response = HTTPURLResponse(
                    url: requestURL,
                    statusCode: 200,
                    httpVersion: nil,
                    headerFields: [
                        "Content-Type": mimeType,
                        "Cache-Control": "no-cache"
                    ]
                ) ?? URLResponse(url: requestURL, mimeType: mimeType, expectedContentLength: data.count, textEncodingName: nil)

                self.finish(task: urlSchemeTask, taskID: taskID) {
                    urlSchemeTask.didReceive(response)
                    urlSchemeTask.didReceive(data)
                    urlSchemeTask.didFinish()
                }
            } catch {
                self.finish(task: urlSchemeTask, taskID: taskID) {
                    urlSchemeTask.didFailWithError(error)
                }
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

    private func finish(task: WKURLSchemeTask, taskID: ObjectIdentifier, block: @escaping () -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                return
            }

            guard !task.stopped else {
                return
            }

            self.tasksLock.lock()
            let isActive = self.activeTasks[taskID] != nil
            if isActive {
                self.activeTasks.removeValue(forKey: taskID)
            }
            self.tasksLock.unlock()

            guard isActive, !task.stopped else {
                return
            }

            block()
        }
    }

    private static func isAllowedBundledAssetRequest(_ requestURL: URL, expectedHost: String) -> Bool {
        guard let host = requestURL.host?.lowercased(), host == expectedHost else {
            return false
        }

        return requestURL.port == nil
    }

    private func mimeType(for pathExtension: String) -> String {
        if !pathExtension.isEmpty,
           let uti = UTType(filenameExtension: pathExtension),
           let mimeType = uti.preferredMIMEType {
            return mimeType
        }

        return pathExtension.isEmpty ? "text/html" : "application/octet-stream"
    }

    private static func resolveBasePath() -> String {
        if let publicDirectory = publicDirectoryURL(),
           FileManager.default.fileExists(atPath: publicDirectory.path) {
            return publicDirectory.path
        }

        if let wwwDirectory = wwwDirectoryURL(),
           FileManager.default.fileExists(atPath: wwwDirectory.path) {
            return wwwDirectory.path
        }

        return publicDirectoryURL()?.path ?? wwwDirectoryURL()?.path ?? ""
    }

    private static func publicDirectoryURL() -> URL? {
        Bundle.main.resourceURL?.appendingPathComponent("public", isDirectory: true)
    }

    private static func wwwDirectoryURL() -> URL? {
        Bundle.main.resourceURL?.appendingPathComponent("www", isDirectory: true)
    }
}

private var bundledAssetStoppedKey: UInt8 = 0

private extension WKURLSchemeTask {
    var stopped: Bool {
        get {
            objc_getAssociatedObject(self, &bundledAssetStoppedKey) as? Bool ?? false
        }
        set {
            objc_setAssociatedObject(self, &bundledAssetStoppedKey, newValue, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        }
    }
}
