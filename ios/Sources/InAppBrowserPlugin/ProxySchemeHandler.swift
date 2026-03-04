import Foundation
import WebKit
import Capacitor

public class ProxySchemeHandler: NSObject, WKURLSchemeHandler {
    weak var plugin: InAppBrowserPlugin?
    private var pendingTasks: [String: WKURLSchemeTask] = [:]
    private var pendingBodies: [String: Data] = [:]
    private var pendingNetworkTasks: [String: URLSessionDataTask] = [:]
    private var pendingWebViews: [String: WKWebView] = [:]
    private var stoppedRequests: Set<String> = []
    private let taskLock = NSLock()
    private let webviewId: String
    private let proxyTimeoutSeconds: TimeInterval = 10

    // Delegate-based networking state for cookie-aware pass-through
    private var networkTaskRequestIds: [Int: String] = [:]
    private var passThroughResponseData: [String: Data] = [:]
    private var passThroughHTTPResponses: [String: HTTPURLResponse] = [:]

    // Ephemeral session with self as delegate for redirect cookie handling.
    // Note: URLSession strongly retains its delegate, creating a retain cycle.
    // Call invalidateSession() when the handler is no longer needed.
    private lazy var proxySession: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
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
        pendingWebViews[requestId] = webView
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
            self.pendingWebViews.removeValue(forKey: requestId)
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
            pendingWebViews.removeValue(forKey: key)
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
            pendingWebViews.removeValue(forKey: requestId)
            taskLock.unlock()
            return
        }
        let bufferedBody = pendingBodies.removeValue(forKey: requestId)
        let webView = pendingWebViews[requestId]

        if isStopped {
            pendingTasks.removeValue(forKey: requestId)
            pendingWebViews.removeValue(forKey: requestId)
            taskLock.unlock()
            return
        }

        if responseData != nil {
            // JS provided a response — remove from pending now
            pendingTasks.removeValue(forKey: requestId)
            pendingWebViews.removeValue(forKey: requestId)
        }
        // For pass-through (nil), keep pendingTasks/pendingWebViews so stop can find them
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

            // Sync Set-Cookie from JS-provided response to WKWebView cookie store
            if let webView = webView {
                syncCookies(from: httpResponse, to: webView) {
                    urlSchemeTask.didReceive(httpResponse)
                    urlSchemeTask.didReceive(bodyData)
                    urlSchemeTask.didFinish()
                }
            } else {
                urlSchemeTask.didReceive(httpResponse)
                urlSchemeTask.didReceive(bodyData)
                urlSchemeTask.didFinish()
            }
        } else {
            // Null response = pass-through via URLSession
            executePassThrough(requestId: requestId, urlSchemeTask: urlSchemeTask, bufferedBody: bufferedBody, webView: webView)
        }
    }

    private func executePassThrough(requestId: String, urlSchemeTask: WKURLSchemeTask, bufferedBody: Data?, webView: WKWebView?) {
        var request = urlSchemeTask.request
        // Restore body if it was consumed from httpBodyStream during interception
        if request.httpBody == nil, let body = bufferedBody {
            request.httpBody = body
        }
        // WKWebView already includes its cookies in the request headers.
        // Disable URLSession's own cookie handling to prevent duplicates/conflicts.
        request.httpShouldHandleCookies = false

        // Use delegate-based task (no completion handler) so willPerformHTTPRedirection fires
        let task = proxySession.dataTask(with: request)

        taskLock.lock()
        networkTaskRequestIds[task.taskIdentifier] = requestId
        pendingNetworkTasks[requestId] = task
        taskLock.unlock()

        task.resume()
    }

    /// Sync Set-Cookie headers from an HTTP response into WKWebView's cookie store.
    /// Calls completion after all cookies have been stored.
    private func syncCookies(from response: HTTPURLResponse, to webView: WKWebView, completion: @escaping () -> Void) {
        guard let url = response.url else {
            completion()
            return
        }
        let headerFields = response.allHeaderFields as? [String: String] ?? [:]
        let cookies = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
        guard !cookies.isEmpty else {
            completion()
            return
        }
        let cookieStore = webView.configuration.websiteDataStore.httpCookieStore
        let group = DispatchGroup()
        for cookie in cookies {
            group.enter()
            cookieStore.setCookie(cookie) { group.leave() }
        }
        group.notify(queue: .global(), execute: completion)
    }

    /// Break the URLSession ↔ delegate retain cycle
    func invalidateSession() {
        proxySession.invalidateAndCancel()
    }

    func cancelAllPendingTasks() {
        proxySession.invalidateAndCancel()

        taskLock.lock()
        let tasks = pendingTasks
        let networkTasks = pendingNetworkTasks
        pendingTasks.removeAll()
        pendingBodies.removeAll()
        pendingNetworkTasks.removeAll()
        pendingWebViews.removeAll()
        networkTaskRequestIds.removeAll()
        passThroughResponseData.removeAll()
        passThroughHTTPResponses.removeAll()
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

// MARK: - URLSessionDataDelegate (cookie-aware pass-through)
extension ProxySchemeHandler: URLSessionDataDelegate {

    /// Intercept redirects to sync Set-Cookie headers to WKWebView and inject
    /// WKWebView cookies into the redirect request for the new domain.
    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        taskLock.lock()
        let requestId = networkTaskRequestIds[task.taskIdentifier]
        let webView = requestId.flatMap { pendingWebViews[$0] }
        taskLock.unlock()

        guard let webView = webView else {
            completionHandler(request)
            return
        }

        // 1. Sync Set-Cookie from the redirect response into WKWebView
        syncCookies(from: response, to: webView) {
            // 2. Inject WKWebView cookies for the redirect target domain
            guard let redirectURL = request.url else {
                completionHandler(request)
                return
            }
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { allCookies in
                var modifiedRequest = request
                modifiedRequest.httpShouldHandleCookies = false

                let matchingCookies = allCookies.filter { cookie in
                    guard let host = redirectURL.host else { return false }
                    // Domain matching
                    let domain = cookie.domain
                    let domainMatch: Bool
                    if host == domain {
                        domainMatch = true
                    } else if domain.hasPrefix(".") {
                        domainMatch = host.hasSuffix(domain) || host == String(domain.dropFirst())
                    } else {
                        domainMatch = false
                    }
                    guard domainMatch else { return false }
                    // Path matching
                    guard redirectURL.path.hasPrefix(cookie.path) else { return false }
                    // Secure cookies only over HTTPS
                    if cookie.isSecure && redirectURL.scheme != "https" { return false }
                    return true
                }
                // Always set Cookie header explicitly — clear it when no cookies match
                // to prevent leaking source-domain cookies to the redirect target
                if matchingCookies.isEmpty {
                    modifiedRequest.setValue("", forHTTPHeaderField: "Cookie")
                } else {
                    let headers = HTTPCookie.requestHeaderFields(with: matchingCookies)
                    for (key, value) in headers {
                        modifiedRequest.setValue(value, forHTTPHeaderField: key)
                    }
                }
                completionHandler(modifiedRequest)
            }
        }
    }

    public func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        taskLock.lock()
        if let requestId = networkTaskRequestIds[dataTask.taskIdentifier] {
            passThroughHTTPResponses[requestId] = response as? HTTPURLResponse
        }
        taskLock.unlock()
        completionHandler(.allow)
    }

    public func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive data: Data
    ) {
        taskLock.lock()
        if let requestId = networkTaskRequestIds[dataTask.taskIdentifier] {
            if passThroughResponseData[requestId] == nil {
                passThroughResponseData[requestId] = Data()
            }
            passThroughResponseData[requestId]?.append(data)
        }
        taskLock.unlock()
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        taskLock.lock()
        guard let requestId = networkTaskRequestIds.removeValue(forKey: task.taskIdentifier) else {
            taskLock.unlock()
            return
        }
        let urlSchemeTask = pendingTasks.removeValue(forKey: requestId)
        let response = passThroughHTTPResponses.removeValue(forKey: requestId)
        let data = passThroughResponseData.removeValue(forKey: requestId) ?? Data()
        let webView = pendingWebViews.removeValue(forKey: requestId)
        pendingNetworkTasks.removeValue(forKey: requestId)
        let wasStopped = stoppedRequests.remove(requestId) != nil
        taskLock.unlock()

        guard !wasStopped, let urlSchemeTask = urlSchemeTask else { return }

        if let error = error {
            if (error as NSError).code == NSURLErrorCancelled { return }
            urlSchemeTask.didFailWithError(error)
            return
        }

        guard let httpResponse = response else {
            urlSchemeTask.didFailWithError(NSError(
                domain: "ProxySchemeHandler",
                code: -3,
                userInfo: [NSLocalizedDescriptionKey: "Non-HTTP response"]
            ))
            return
        }

        // Sync Set-Cookie from final response to WKWebView, then deliver to scheme task
        if let webView = webView {
            syncCookies(from: httpResponse, to: webView) {
                urlSchemeTask.didReceive(httpResponse)
                if !data.isEmpty {
                    urlSchemeTask.didReceive(data)
                }
                urlSchemeTask.didFinish()
            }
        } else {
            urlSchemeTask.didReceive(httpResponse)
            if !data.isEmpty {
                urlSchemeTask.didReceive(data)
            }
            urlSchemeTask.didFinish()
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
