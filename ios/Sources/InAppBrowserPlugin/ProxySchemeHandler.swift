import Foundation
import WebKit
import Capacitor

public class ProxySchemeHandler: NSObject, WKURLSchemeHandler {
    weak var plugin: InAppBrowserPlugin?
    private var pendingTasks: [String: WKURLSchemeTask] = [:]
    private var pendingBodies: [String: Data] = [:]
    private var pendingNetworkTasks: [String: URLSessionDataTask] = [:]
    private var stoppedRequests: Set<String> = []
    private let taskLock = NSLock()
    private let webviewId: String
    private let proxyTimeoutSeconds: TimeInterval = 10

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

        // Encode body to base64, buffering stream data for later pass-through
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

        // Timeout: if JS never responds, fail the request after proxyTimeoutSeconds
        DispatchQueue.global().asyncAfter(deadline: .now() + proxyTimeoutSeconds) { [weak self] in
            guard let self = self else { return }
            self.taskLock.lock()
            guard let task = self.pendingTasks.removeValue(forKey: requestId) else {
                self.taskLock.unlock()
                return
            }
            self.pendingBodies.removeValue(forKey: requestId)
            self.taskLock.unlock()

            print("[InAppBrowser] Proxy request timed out after \(Int(self.proxyTimeoutSeconds))s: \(requestId)")
            task.didFailWithError(NSError(
                domain: "ProxySchemeHandler",
                code: NSURLErrorTimedOut,
                userInfo: [NSLocalizedDescriptionKey: "Proxy handler did not respond within \(Int(self.proxyTimeoutSeconds)) seconds"]
            ))
        }
    }

    public func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        taskLock.lock()
        let requestIdToRemove = pendingTasks.first(where: { $0.value === urlSchemeTask })?.key
        var networkTask: URLSessionDataTask?
        if let key = requestIdToRemove {
            pendingTasks.removeValue(forKey: key)
            pendingBodies.removeValue(forKey: key)
            networkTask = pendingNetworkTasks.removeValue(forKey: key)
            stoppedRequests.insert(key)
        }
        taskLock.unlock()
        networkTask?.cancel()
    }

    /// Called from handleProxyRequest plugin method with the JS response
    func handleResponse(requestId: String, responseData: [String: Any]?) {
        taskLock.lock()
        let isStopped = stoppedRequests.remove(requestId) != nil
        guard let urlSchemeTask = pendingTasks[requestId] else {
            pendingTasks.removeValue(forKey: requestId)
            pendingBodies.removeValue(forKey: requestId)
            taskLock.unlock()
            return
        }
        let bufferedBody = pendingBodies.removeValue(forKey: requestId)

        if isStopped {
            pendingTasks.removeValue(forKey: requestId)
            taskLock.unlock()
            return
        }

        if responseData != nil {
            // JS provided a response — remove from pending now
            pendingTasks.removeValue(forKey: requestId)
        }
        // For pass-through (nil), keep pendingTasks entry so stop can find it
        taskLock.unlock()

        if let responseData = responseData {
            let statusCode = responseData["status"] as? Int ?? 200
            let headersDict = responseData["headers"] as? [String: String] ?? [:]
            let base64Body = responseData["body"] as? String ?? ""

            let bodyData = Data(base64Encoded: base64Body) ?? Data()

            guard let url = urlSchemeTask.request.url else {
                urlSchemeTask.didFailWithError(NSError(
                    domain: "ProxySchemeHandler",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "No URL"]
                ))
                return
            }

            guard let httpResponse = HTTPURLResponse(
                url: url,
                statusCode: statusCode,
                httpVersion: "HTTP/1.1",
                headerFields: headersDict
            ) else {
                urlSchemeTask.didFailWithError(NSError(
                    domain: "ProxySchemeHandler",
                    code: -2,
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
        // Restore body if it was consumed from httpBodyStream during interception
        if request.httpBody == nil, let body = bufferedBody {
            request.httpBody = body
        }
        let session = URLSession.shared
        let task = session.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }

            // Clean up and check if this request was stopped while in-flight
            self.taskLock.lock()
            self.pendingTasks.removeValue(forKey: requestId)
            self.pendingNetworkTasks.removeValue(forKey: requestId)
            let wasStopped = self.stoppedRequests.remove(requestId) != nil
            self.taskLock.unlock()

            if wasStopped {
                return
            }

            if let error = error {
                if (error as NSError).code == NSURLErrorCancelled {
                    return
                }
                urlSchemeTask.didFailWithError(error)
                return
            }

            guard let httpResponse = response as? HTTPURLResponse else {
                urlSchemeTask.didFailWithError(NSError(
                    domain: "ProxySchemeHandler",
                    code: -3,
                    userInfo: [NSLocalizedDescriptionKey: "Non-HTTP response"]
                ))
                return
            }

            urlSchemeTask.didReceive(httpResponse)
            if let data = data {
                urlSchemeTask.didReceive(data)
            }
            urlSchemeTask.didFinish()
        }

        taskLock.lock()
        pendingNetworkTasks[requestId] = task
        taskLock.unlock()
        task.resume()
    }

    func cancelAllPendingTasks() {
        taskLock.lock()
        let tasks = pendingTasks
        let networkTasks = pendingNetworkTasks
        pendingTasks.removeAll()
        pendingBodies.removeAll()
        pendingNetworkTasks.removeAll()
        stoppedRequests.removeAll()
        taskLock.unlock()

        for (_, networkTask) in networkTasks {
            networkTask.cancel()
        }
        for (_, task) in tasks {
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
