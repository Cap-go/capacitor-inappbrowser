import Foundation
import WebKit
import Capacitor

public class ProxySchemeHandler: NSObject, WKURLSchemeHandler {
    weak var plugin: InAppBrowserPlugin?
    private var pendingTasks: [String: WKURLSchemeTask] = [:]
    private var pendingBodies: [String: Data] = [:]
    private var activeTasks: [Int: (requestId: String, schemeTask: WKURLSchemeTask)] = [:]
    private var stoppedRequests: Set<String> = []
    private let taskLock = NSLock()
    private let webviewId: String
    private let proxyTimeoutSeconds: TimeInterval = 10

    private static var session: URLSession = {
        return URLSession(configuration: .default)
    }()

    init(plugin: InAppBrowserPlugin, webviewId: String) {
        self.plugin = plugin
        self.webviewId = webviewId
        super.init()
    }

    public func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        let request = urlSchemeTask.request
        guard let url = request.url else {
            urlSchemeTask.didFailWithError(NSError(
                domain: "ProxySchemeHandler",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "No URL in request"]
            ))
            return
        }

        let requestId = UUID().uuidString

        taskLock.lock()
        pendingTasks[requestId] = urlSchemeTask
        taskLock.unlock()

        // Buffer body from stream if needed (stream can only be read once)
        var base64Body: String? = nil
        if let bodyData = request.httpBody {
            base64Body = bodyData.base64EncodedString()
        } else if let bodyStream = request.httpBodyStream {
            let data = Data(reading: bodyStream)
            if !data.isEmpty {
                base64Body = data.base64EncodedString()
                taskLock.lock()
                pendingBodies[requestId] = data
                taskLock.unlock()
            }
        }

        // Build headers dict
        var headers: [String: String] = [:]
        if let allHeaders = request.allHTTPHeaderFields {
            headers = allHeaders
        }

        let eventData: [String: Any] = [
            "requestId": requestId,
            "url": url.absoluteString,
            "method": request.httpMethod ?? "GET",
            "headers": headers,
            "body": base64Body as Any,
            "webviewId": webviewId,
        ]

        plugin?.notifyListeners("proxyRequest", data: eventData)

        // Timeout: if JS never responds, fail the request
        DispatchQueue.global().asyncAfter(deadline: .now() + proxyTimeoutSeconds) { [weak self] in
            guard let self = self else { return }
            self.taskLock.lock()
            guard let task = self.pendingTasks.removeValue(forKey: requestId) else {
                self.taskLock.unlock()
                return
            }
            self.pendingBodies.removeValue(forKey: requestId)
            self.taskLock.unlock()

            task.didFailWithError(NSError(
                domain: "ProxySchemeHandler",
                code: NSURLErrorTimedOut,
                userInfo: [NSLocalizedDescriptionKey: "Proxy handler did not respond within \(Int(self.proxyTimeoutSeconds)) seconds"]
            ))
        }
    }

    public func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        taskLock.lock()
        // Check pending (waiting for JS)
        if let key = pendingTasks.first(where: { $0.value === urlSchemeTask })?.key {
            pendingTasks.removeValue(forKey: key)
            pendingBodies.removeValue(forKey: key)
            stoppedRequests.insert(key)
        }
        // Check active network tasks (pass-through)
        if let entry = activeTasks.first(where: { $0.value.schemeTask === urlSchemeTask }) {
            activeTasks.removeValue(forKey: entry.key)
            stoppedRequests.insert(entry.value.requestId)
        }
        taskLock.unlock()
    }

    /// Called from handleProxyRequest plugin method with the JS response
    func handleResponse(requestId: String, responseData: [String: Any]?) {
        taskLock.lock()
        let isStopped = stoppedRequests.remove(requestId) != nil
        guard let urlSchemeTask = pendingTasks.removeValue(forKey: requestId) else {
            taskLock.unlock()
            return
        }
        let bufferedBody = pendingBodies.removeValue(forKey: requestId)
        taskLock.unlock()

        if isStopped { return }

        if let responseData = responseData {
            // JS provided a response — return it directly
            let statusCode = responseData["status"] as? Int ?? 200
            let headersDict = responseData["headers"] as? [String: String] ?? [:]
            let base64Body = responseData["body"] as? String ?? ""
            let bodyData = Data(base64Encoded: base64Body) ?? Data()

            guard let url = urlSchemeTask.request.url,
                  let httpResponse = HTTPURLResponse(
                      url: url, statusCode: statusCode,
                      httpVersion: "HTTP/1.1", headerFields: headersDict
                  ) else {
                urlSchemeTask.didFailWithError(NSError(
                    domain: "ProxySchemeHandler", code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to create response"]
                ))
                return
            }

            urlSchemeTask.didReceive(httpResponse)
            urlSchemeTask.didReceive(bodyData)
            urlSchemeTask.didFinish()
        } else {
            // Null response = pass-through via URLSession
            executePassThrough(requestId: requestId, urlSchemeTask: urlSchemeTask, bufferedBody: bufferedBody)
        }
    }

    private func executePassThrough(requestId: String, urlSchemeTask: WKURLSchemeTask, bufferedBody: Data?) {
        var request = urlSchemeTask.request
        if request.httpBody == nil, let body = bufferedBody {
            request.httpBody = body
        }

        let task = Self.session.dataTask(with: request) { [weak self, weak urlSchemeTask] data, response, error in
            guard let urlSchemeTask = urlSchemeTask else { return }
            guard let self = self else { return }

            // Check not stopped
            self.taskLock.lock()
            let wasStopped = self.stoppedRequests.remove(requestId) != nil
            self.taskLock.unlock()
            if wasStopped { return }

            if let error = error {
                if (error as NSError).code != NSURLErrorCancelled {
                    urlSchemeTask.didFailWithError(error)
                }
            } else {
                if let response = response {
                    urlSchemeTask.didReceive(response)
                }
                if let data = data {
                    urlSchemeTask.didReceive(data)
                }
                urlSchemeTask.didFinish()
            }
        }

        taskLock.lock()
        activeTasks[task.taskIdentifier] = (requestId: requestId, schemeTask: urlSchemeTask)
        taskLock.unlock()

        task.resume()
    }

    func cancelAllPendingTasks() {
        taskLock.lock()
        let pending = pendingTasks
        pendingTasks.removeAll()
        pendingBodies.removeAll()
        activeTasks.removeAll()
        stoppedRequests.removeAll()
        taskLock.unlock()

        for (_, task) in pending {
            task.didFailWithError(NSError(
                domain: "ProxySchemeHandler",
                code: NSURLErrorCancelled,
                userInfo: [NSLocalizedDescriptionKey: "WebView closed"]
            ))
        }
    }
}

extension Data {
    init(reading input: InputStream) {
        self.init()
        input.open()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        while input.hasBytesAvailable {
            let read = input.read(buffer, maxLength: bufferSize)
            if read < 0 {
                break
            }
            self.append(buffer, count: read)
        }
        buffer.deallocate()
        input.close()
    }
}
