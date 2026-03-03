import Foundation
import WebKit
import Capacitor

public class ProxySchemeHandler: NSObject, WKURLSchemeHandler {
    weak var plugin: InAppBrowserPlugin?
    private var pendingTasks: [String: WKURLSchemeTask] = [:]
    private let taskLock = NSLock()
    private let webviewId: String

    init(plugin: InAppBrowserPlugin, webviewId: String) {
        self.plugin = plugin
        self.webviewId = webviewId
        super.init()
    }

    public func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        let request = urlSchemeTask.request
        print("[InAppBrowser][Proxy] >>> webView(_:start:) CALLED! url=\(request.url?.absoluteString ?? "nil")")
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

        // Encode body to base64
        var base64Body: String? = nil
        if let bodyData = request.httpBody {
            base64Body = bodyData.base64EncodedString()
        } else if let bodyStream = request.httpBodyStream {
            let data = Data(reading: bodyStream)
            if !data.isEmpty {
                base64Body = data.base64EncodedString()
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

        print("[InAppBrowser][Proxy] Firing proxyRequest event: requestId=\(requestId), url=\(url.absoluteString), plugin=\(String(describing: plugin))")
        plugin?.notifyListeners("proxyRequest", data: eventData)
    }

    public func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        taskLock.lock()
        let requestIdToRemove = pendingTasks.first(where: { $0.value === urlSchemeTask })?.key
        if let key = requestIdToRemove {
            pendingTasks.removeValue(forKey: key)
        }
        taskLock.unlock()
    }

    /// Called from handleProxyRequest plugin method with the JS response
    func handleResponse(requestId: String, responseData: [String: Any]?) {
        taskLock.lock()
        guard let urlSchemeTask = pendingTasks.removeValue(forKey: requestId) else {
            taskLock.unlock()
            return
        }
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
            executePassThrough(urlSchemeTask: urlSchemeTask)
        }
    }

    private func executePassThrough(urlSchemeTask: WKURLSchemeTask) {
        let request = urlSchemeTask.request
        let session = URLSession.shared
        let task = session.dataTask(with: request) { data, response, error in
            if let error = error {
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
        task.resume()
    }

    func cancelAllPendingTasks() {
        taskLock.lock()
        let tasks = pendingTasks
        pendingTasks.removeAll()
        taskLock.unlock()

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
