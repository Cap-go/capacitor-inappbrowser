// swiftlint:disable file_length
import Foundation
import WebKit
import Capacitor

struct NativeProxyRule {
    enum Action: String {
        case `continue`
        case cancel
        case delegateToJs
    }

    let id: String?
    let urlRegex: NSRegularExpression?
    let methodRegex: NSRegularExpression?
    let headerRegex: NSRegularExpression?
    let bodyRegex: NSRegularExpression?
    let statusRegex: NSRegularExpression?
    let responseHeaderRegex: NSRegularExpression?
    let responseBodyRegex: NSRegularExpression?
    let mainFrameOnly: Bool
    let action: Action

    func matches(
        url: String,
        method: String,
        headers: String,
        body: String?,
        isMainFrame: Bool,
        status: Int?,
        responseHeaders: String?,
        responseBody: String?
    ) -> Bool {
        if mainFrameOnly && !isMainFrame {
            return false
        }
        if !matches(urlRegex, value: url) { return false }
        if !matches(methodRegex, value: method) { return false }
        if !matches(headerRegex, value: headers) { return false }
        if !matches(bodyRegex, value: body) { return false }
        if !matches(statusRegex, value: status.map(String.init)) { return false }
        if !matches(responseHeaderRegex, value: responseHeaders) { return false }
        if !matches(responseBodyRegex, value: responseBody) { return false }
        return true
    }

    private func matches(_ regex: NSRegularExpression?, value: String?) -> Bool {
        guard let regex else { return true }
        guard let value else { return false }
        let range = NSRange(location: 0, length: value.utf16.count)
        return regex.firstMatch(in: value, options: [], range: range) != nil
    }

    static func from(dictionary: [String: Any]) throws -> NativeProxyRule {
        func compile(_ key: String) throws -> NSRegularExpression? {
            guard let raw = dictionary[key] as? String, !raw.isEmpty else { return nil }
            return try NSRegularExpression(pattern: raw, options: [.caseInsensitive])
        }

        let action = Action(rawValue: dictionary["action"] as? String ?? "continue") ?? .continue
        return try NativeProxyRule(
            id: dictionary["id"] as? String,
            urlRegex: compile("urlRegex"),
            methodRegex: compile("methodRegex"),
            headerRegex: compile("headerRegex"),
            bodyRegex: compile("bodyRegex"),
            statusRegex: compile("statusRegex"),
            responseHeaderRegex: compile("responseHeaderRegex"),
            responseBodyRegex: compile("responseBodyRegex"),
            mainFrameOnly: dictionary["mainFrameOnly"] as? Bool ?? false,
            action: action
        )
    }
}

struct NativeRequestContext {
    var url: String
    var method: String
    var headers: [String: String]
    var base64Body: String?
    var isMainFrame: Bool
}

struct NativeResponseData {
    var statusCode: Int
    var headers: [String: String]
    var body: Data
    var contentType: String
}

struct LegacyProxyRequestsConfiguration {
    let isEnabled: Bool
    let urlRegex: NSRegularExpression?
}

enum ProxySchemeRequestSupport {
    private static let httpMethodLocale = Locale(identifier: "en_US_POSIX")
    private static let crossOriginOverrideHeaderNames = [
        "Authorization",
        "Cookie",
        "Cookie2",
        "Origin",
        "Proxy-Authorization",
        "Referer"
    ]

    enum JsResponseResolutionAction {
        case finishCachedResponse
        case executeNativePipeline
        case executeInboundDecision
    }

    enum TimeoutResolutionAction {
        case fallbackToNative
        case finishCachedResponse
        case failRequest
    }

    enum RequestBuildError: LocalizedError, Equatable {
        case invalidURL
        case invalidBase64Body

        var errorDescription: String? {
            switch self {
            case .invalidURL:
                return "Invalid request URL"
            case .invalidBase64Body:
                return "Invalid base64 request body"
            }
        }
    }

    static func isMainFrameRequest(_ request: URLRequest, fallback: Bool = false) -> Bool {
        guard let url = request.url else { return fallback }
        guard let mainDocumentURL = request.mainDocumentURL else { return fallback }
        return mainDocumentURL.absoluteString == url.absoluteString
    }

    static func sanitizedOverrideURL(_ rawURL: String?, fallback: String) -> String {
        guard let rawURL = rawURL?.trimmingCharacters(in: .whitespacesAndNewlines), !rawURL.isEmpty else {
            return fallback
        }

        if let absoluteURL = URL(string: rawURL), absoluteURL.scheme?.isEmpty == false {
            return absoluteURL.absoluteString
        }

        guard
            let fallbackURL = URL(string: fallback),
            let resolvedURL = URL(string: rawURL, relativeTo: fallbackURL)?.absoluteURL,
            resolvedURL.scheme?.isEmpty == false
        else {
            return fallback
        }

        return resolvedURL.absoluteString
    }

    static func resolvedResponseURL(_ response: URLResponse?, fallback: String) -> String {
        guard
            let responseURL = response?.url?.absoluteString,
            !responseURL.isEmpty
        else {
            return fallback
        }
        return responseURL
    }

    static func prepareOverrideHeaders(
        originalHeaders: [String: String],
        requestURL: String,
        overrideURL: String
    ) -> [String: String] {
        guard isCrossOriginRequest(requestURL, overrideURL) else {
            return originalHeaders
        }

        return originalHeaders.filter { header, _ in
            !crossOriginOverrideHeaderNames.contains { $0.caseInsensitiveCompare(header) == .orderedSame }
        }
    }

    static func responseCookieURL(_ response: URLResponse?, fallback: String) -> URL? {
        if let responseURL = response?.url {
            return responseURL
        }
        return URL(string: fallback)
    }

    static func responseCookies(
        from storage: HTTPCookieStorage = .shared,
        response: URLResponse?,
        fallback: String
    ) -> [HTTPCookie] {
        guard let cookieURL = responseCookieURL(response, fallback: fallback) else {
            return []
        }
        return storage.cookies(for: cookieURL) ?? []
    }

    static func resolvedRedirectHeaders(_ headers: [String: String]?, fallback: [String: String]) -> [String: String] {
        guard let headers else {
            return [:]
        }
        return headers
    }

    static func resolvedRedirectBody(_ body: String?, method: String, fallback: String?) -> String? {
        guard supportsRequestBody(method: method) else {
            return nil
        }
        return body ?? fallback
    }

    static func responseCookies(from headers: [String: String], fallback: String) -> [HTTPCookie] {
        guard let cookieURL = URL(string: fallback) else {
            return []
        }

        let cookieHeaders = headers.reduce(into: [String: String]()) { result, entry in
            if entry.key.caseInsensitiveCompare("Set-Cookie") == .orderedSame ||
                entry.key.caseInsensitiveCompare("Set-Cookie2") == .orderedSame {
                result[entry.key] = entry.value
            }
        }

        guard !cookieHeaders.isEmpty else {
            return []
        }

        return HTTPCookie.cookies(withResponseHeaderFields: cookieHeaders, for: cookieURL)
    }

    static func timeoutResolutionAction(phase: String, hasCachedResponse: Bool, hasPendingRedirect: Bool) -> TimeoutResolutionAction {
        if phase == "outbound" || hasPendingRedirect {
            return .fallbackToNative
        }
        if phase == "inbound", hasCachedResponse {
            return .finishCachedResponse
        }
        return .failRequest
    }

    static func jsResponseResolutionAction(phase: String, hasCachedResponse: Bool) -> JsResponseResolutionAction {
        if hasCachedResponse {
            return .finishCachedResponse
        }
        if phase == "outbound" {
            return .executeNativePipeline
        }
        return .executeInboundDecision
    }

    static func shouldFollowDelegatedRedirect(
        phase: String,
        hasPendingRedirect: Bool,
        hasDirectResponse: Bool,
        isCanceled: Bool
    ) -> Bool {
        phase == "inbound" && hasPendingRedirect && !hasDirectResponse && !isCanceled
    }

    static func timeoutTokenMatches(scheduledToken: UUID, currentToken: UUID?) -> Bool {
        currentToken == scheduledToken
    }

    static func normalizedRequestMethod(_ method: String?) -> String {
        guard let trimmedMethod = method?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmedMethod.isEmpty else {
            return "GET"
        }
        return trimmedMethod.uppercased(with: httpMethodLocale)
    }

    static func legacyProxyRequestsConfiguration(from rawValue: Any?) -> LegacyProxyRequestsConfiguration {
        if let enabled = rawValue as? Bool {
            return LegacyProxyRequestsConfiguration(isEnabled: enabled, urlRegex: nil)
        }

        // String regex mode is Android-only. iOS ignores it instead of rejecting cross-platform calls.
        return LegacyProxyRequestsConfiguration(isEnabled: false, urlRegex: nil)
    }

    static func shouldUseLegacyCatchAllRule(
        legacyProxyRequests: Bool,
        hasOutboundRules: Bool,
        hasInboundRules: Bool,
        phase: String
    ) -> Bool {
        legacyProxyRequests && phase == "outbound" && !hasOutboundRules && !hasInboundRules
    }

    static func decodedRequestBody(from base64Body: String?) throws -> Data? {
        guard let base64Body else {
            return nil
        }
        guard !base64Body.isEmpty else {
            return nil
        }
        guard let bodyData = Data(base64Encoded: base64Body) else {
            throw RequestBuildError.invalidBase64Body
        }
        return bodyData
    }

    static func decodedResponseBody(from base64Body: String?) throws -> Data {
        guard let base64Body else {
            return Data()
        }
        guard !base64Body.isEmpty else {
            return Data()
        }
        guard let bodyData = Data(base64Encoded: base64Body) else {
            throw RequestBuildError.invalidBase64Body
        }
        return bodyData
    }

    static func supportsRequestBody(method: String) -> Bool {
        let uppercasedMethod = method.uppercased()
        return uppercasedMethod != "GET" && uppercasedMethod != "HEAD"
    }

    static func resolvedOverrideBody(
        from override: [String: Any],
        method: String,
        fallback: String?
    ) -> String? {
        guard supportsRequestBody(method: method) else {
            return nil
        }

        guard override.keys.contains("body") else {
            return fallback
        }

        if override["body"] is NSNull {
            return nil
        }

        return override["body"] as? String ?? fallback
    }

    static func normalizedResponseHeaders(from response: HTTPURLResponse?) -> [String: String] {
        normalizedResponseHeaders(from: response?.allHeaderFields ?? [:])
    }

    static func normalizedResponseHeaders(from headerFields: [AnyHashable: Any]) -> [String: String] {
        var headers: [String: String] = [:]
        for (key, value) in headerFields {
            guard let headerName = key as? String else {
                continue
            }

            if let headerValue = value as? String {
                headers[headerName] = headerValue
                continue
            }

            if let headerValues = value as? [String] {
                headers[headerName] = headerValues.joined(separator: ", ")
                continue
            }

            if let headerValues = value as? [Any] {
                let stringValues = headerValues.map { String(describing: $0) }
                headers[headerName] = stringValues.joined(separator: ", ")
                continue
            }

            headers[headerName] = String(describing: value)
        }

        return headers
    }

    private static func isCrossOriginRequest(_ firstURL: String, _ secondURL: String) -> Bool {
        guard
            let first = URL(string: firstURL),
            let second = URL(string: secondURL)
        else {
            return true
        }

        return
            first.scheme?.caseInsensitiveCompare(second.scheme ?? "") != .orderedSame ||
            first.host?.caseInsensitiveCompare(second.host ?? "") != .orderedSame ||
            effectivePort(first) != effectivePort(second)
    }

    private static func effectivePort(_ url: URL) -> Int {
        if let port = url.port {
            return port
        }
        switch url.scheme?.lowercased() {
        case "http":
            return 80
        case "https":
            return 443
        default:
            return -1
        }
    }
}

final class PendingProxyTask {
    let requestId: String
    let schemeTask: WKURLSchemeTask
    var requestContext: NativeRequestContext
    var responseData: NativeResponseData?
    var phase: String
    var urlSessionTask: URLSessionDataTask?
    var redirectRequest: URLRequest?
    var timeoutToken: UUID?
    var canceled = false
    /// Set true when WebKit calls `webView(_:stop:)` for this task. Once stopped,
    /// the `WKURLSchemeTask` must never receive `didReceive`/`didFinish`/`didFailWithError`
    /// again or WebKit raises an uncatchable NSException.
    var isStopped = false
    /// Set true the first time a terminal scheme-task call sequence is committed.
    /// Guarantees at-most-once completion even when multiple async sources
    /// (URLSession delegate queue, cookie-sync main hop, global timeout, cancel-all)
    /// race to finish the same request.
    private var isComplete = false

    init(requestId: String, schemeTask: WKURLSchemeTask, requestContext: NativeRequestContext, phase: String) {
        self.requestId = requestId
        self.schemeTask = schemeTask
        self.requestContext = requestContext
        self.phase = phase
    }

    /// Claims the right to issue the single allowed terminal call sequence on the
    /// scheme task. Returns true exactly once, and only while the task is live
    /// (not stopped). Callers MUST hold the handler's `taskLock` so the claim is
    /// atomic with respect to `webView(_:stop:)` marking the task stopped.
    func beginCompletionIfLive() -> Bool {
        if isComplete || isStopped { return false }
        isComplete = true
        return true
    }
}

// swiftlint:disable type_body_length
public class ProxySchemeHandler: NSObject, WKURLSchemeHandler, URLSessionTaskDelegate {
    /// HTTP status codes for which URLSession actually triggers
    /// `urlSession(_:task:willPerformHTTPRedirection:newRequest:completionHandler:)`.
    /// Scoped narrowly so other 3xx responses (300, 304, 305, 306) flow through the
    /// dataTask completion handler normally — 304 Not Modified in particular must
    /// still reach finish() or the scheme task strands.
    static let redirectStatusCodes: Set<Int> = [301, 302, 303, 307, 308]

    weak var plugin: CapgoInAppBrowserPlugin?
    private var pendingTasks: [String: PendingProxyTask] = [:]
    private var stoppedRequests: [String: UUID] = [:]
    private var timedOutRequests: [String: String] = [:]
    private let taskLock = NSLock()
    private let webviewId: String
    private let proxyTimeoutSeconds: TimeInterval = 10
    private let legacyProxyRequests: Bool
    private let legacyProxyRequestURLRegex: NSRegularExpression?
    private let outboundRules: [NativeProxyRule]
    private let inboundRules: [NativeProxyRule]
    private weak var proxyBridge: ProxyBridge?

    private lazy var session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    init(
        plugin: CapgoInAppBrowserPlugin,
        webviewId: String,
        legacyProxyRequests: Bool,
        legacyProxyRequestURLRegex: NSRegularExpression?,
        outboundRules: [NativeProxyRule],
        inboundRules: [NativeProxyRule],
        proxyBridge: ProxyBridge? = nil
    ) {
        self.plugin = plugin
        self.webviewId = webviewId
        self.legacyProxyRequests = legacyProxyRequests
        self.legacyProxyRequestURLRegex = legacyProxyRequestURLRegex
        self.outboundRules = outboundRules
        self.inboundRules = inboundRules
        self.proxyBridge = proxyBridge
        super.init()
    }

    func duplicate(for webviewId: String) -> ProxySchemeHandler? {
        guard let plugin else { return nil }
        return ProxySchemeHandler(
            plugin: plugin,
            webviewId: webviewId,
            legacyProxyRequests: legacyProxyRequests,
            legacyProxyRequestURLRegex: legacyProxyRequestURLRegex,
            outboundRules: outboundRules,
            inboundRules: inboundRules,
            proxyBridge: proxyBridge
        )
    }

    public func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(
                NSError(
                    domain: "ProxySchemeHandler",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "No URL in request"]
                )
            )
            return
        }

        let requestId = UUID().uuidString
        guard let requestContext = makeInitialRequestContext(from: urlSchemeTask, requestURL: url) else {
            finishCanceledSchemeTask(urlSchemeTask)
            return
        }
        let pendingTask = PendingProxyTask(requestId: requestId, schemeTask: urlSchemeTask, requestContext: requestContext, phase: "outbound")

        taskLock.lock()
        pendingTasks[requestId] = pendingTask
        taskLock.unlock()

        routeCurrentRequest(requestId: requestId)
    }

    public func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        taskLock.lock()
        if let entry = pendingTasks.first(where: { $0.value.schemeTask === urlSchemeTask }) {
            entry.value.isStopped = true
            pendingTasks.removeValue(forKey: entry.key)
            let cleanupToken = UUID()
            stoppedRequests[entry.key] = cleanupToken
            let sessionTask = entry.value.urlSessionTask
            taskLock.unlock()
            sessionTask?.cancel()
            scheduleStoppedRequestCleanup(requestId: entry.key, token: cleanupToken)
            return
        }
        taskLock.unlock()
    }

    func handleResponse(requestId: String, phase: String?, responseData: [String: Any]?) {
        taskLock.lock()
        let wasStopped = stoppedRequests.removeValue(forKey: requestId) != nil
        guard let pendingTask = pendingTasks[requestId] else {
            taskLock.unlock()
            return
        }
        if let timedOutPhase = timedOutRequests[requestId], (phase ?? pendingTask.phase) == timedOutPhase {
            timedOutRequests.removeValue(forKey: requestId)
            taskLock.unlock()
            return
        }
        if let phase, phase != pendingTask.phase {
            taskLock.unlock()
            return
        }
        pendingTask.timeoutToken = nil
        taskLock.unlock()

        if wasStopped { return }

        if let responseData {
            if let cancel = responseData["cancel"] as? Bool, cancel {
                pendingTask.canceled = true
            }

            let directResponse = (responseData["response"] as? [String: Any]) ?? responseData
            let hasDirectResponse = directResponse["status"] != nil
            if directResponse["status"] != nil {
                do {
                    pendingTask.responseData = try makeNativeResponse(from: directResponse)
                    pendingTask.redirectRequest = nil
                } catch {
                    pendingTask.canceled = true
                }
            }

            if ProxySchemeRequestSupport.shouldFollowDelegatedRedirect(
                phase: pendingTask.phase,
                hasPendingRedirect: pendingTask.redirectRequest != nil,
                hasDirectResponse: hasDirectResponse,
                isCanceled: pendingTask.canceled
            ) {
                if let requestOverride = responseData["request"] as? [String: Any] {
                    followPendingRedirect(requestId: requestId, requestOverride: requestOverride)
                } else {
                    followPendingRedirect(requestId: requestId)
                }
                return
            }

            if let requestOverride = responseData["request"] as? [String: Any] {
                pendingTask.requestContext = applyRequestOverride(requestOverride, to: pendingTask.requestContext)
            }
        }

        if pendingTask.canceled {
            finishWithCanceledResponse(task: pendingTask)
            return
        }

        switch ProxySchemeRequestSupport.jsResponseResolutionAction(
            phase: pendingTask.phase,
            hasCachedResponse: pendingTask.responseData != nil
        ) {
        case .finishCachedResponse:
            guard let responseData = pendingTask.responseData else { return }
            syncResponseCookies(from: responseData, fallbackURL: pendingTask.requestContext.url) {
                self.finish(task: pendingTask, with: responseData)
            }
        case .executeNativePipeline:
            executeNativePipeline(requestId: requestId)
        case .executeInboundDecision:
            executeInboundDecision(requestId: requestId)
        }
    }

    private func executeNativePipeline(requestId: String) {
        guard let pendingTask = pendingTask(for: requestId) else { return }
        let request: URLRequest
        do {
            request = try makeURLRequest(from: pendingTask.requestContext)
        } catch {
            failSchemeTask(
                pendingTask,
                with: NSError(
                    domain: "ProxySchemeHandler",
                    code: -3,
                    userInfo: [NSLocalizedDescriptionKey: error.localizedDescription]
                )
            )
            return
        }
        let task = session.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            guard let pendingTask = self.pendingTask(for: requestId) else { return }

            if let error {
                if (error as NSError).code == NSURLErrorCancelled {
                    // The data task is only canceled by webView(_:stop:) or
                    // cancelAllPendingTasks(), and both own the task's lifecycle: stop has
                    // already removed it (and marked it stopped) before canceling, while
                    // cancelAll routes a terminal failSchemeTask through completeSchemeTask,
                    // which removes it on the main queue. Removing it here on the URLSession
                    // background queue would unhook it from pendingTasks before a racing
                    // stop could mark it stopped, reopening the post-stop completion crash.
                    // So just stop processing and let the owning path complete it.
                    return
                }
                self.failSchemeTask(pendingTask, with: error)
                return
            }

            let httpResponse = response as? HTTPURLResponse
            // Actual HTTP redirect status codes are the exclusive responsibility of
            // urlSession(_:task:willPerformHTTPRedirection:newRequest:completionHandler:).
            // When that delegate calls completionHandler(nil) to suppress URLSession's
            // automatic redirect-following, URLSession still surfaces the redirect body
            // to this completion handler as a normal response. Without this guard, the
            // willPerformHTTPRedirection path and this completion handler both race to
            // mutate pendingTask.responseData and call executeInboundDecision; the late-
            // firing one overwrites the redirect-target state and ends up handing the
            // empty redirect body to the WKURLSchemeTask via finish(), short-circuiting
            // the actual redirect-target fetch.
            //
            // Scoped narrowly to the codes that actually trigger willPerformHTTPRedirection
            // (301, 302, 303, 307, 308). Other 3xx — 300, 304 Not Modified, 305, 306 — are
            // NOT redirects and the delegate doesn't fire for them, so they must flow
            // through this completion handler normally (a 304 in particular needs the
            // scheme task to be didFinish()'d with the cached/empty body, not stranded).
            if let httpResponse, ProxySchemeHandler.redirectStatusCodes.contains(httpResponse.statusCode) {
                return
            }
            let responseHeaders = ProxySchemeRequestSupport.normalizedResponseHeaders(from: httpResponse)
            pendingTask.requestContext.url = ProxySchemeRequestSupport.resolvedResponseURL(
                response,
                fallback: pendingTask.requestContext.url
            )
            let nativeResponse = NativeResponseData(
                statusCode: httpResponse?.statusCode ?? 200,
                headers: responseHeaders,
                body: data ?? Data(),
                contentType: responseHeaders["Content-Type"] ?? responseHeaders["content-type"] ?? "application/octet-stream"
            )
            self.syncResponseCookies(
                from: response,
                fallbackURL: pendingTask.requestContext.url
            ) {
                guard let pendingTask = self.pendingTask(for: requestId) else { return }
                pendingTask.responseData = nativeResponse
                self.executeInboundDecision(requestId: requestId)
            }
        }

        taskLock.lock()
        pendingTasks[requestId]?.urlSessionTask = task
        taskLock.unlock()
        task.resume()
    }

    private func executeInboundDecision(requestId: String) {
        guard let pendingTask = pendingTask(for: requestId), let responseData = pendingTask.responseData else { return }
        pendingTask.phase = "inbound"

        if let inboundRule = firstMatchingRule(for: pendingTask.requestContext, responseData: responseData, phase: "inbound") {
            switch inboundRule.action {
            case .cancel:
                finishWithCanceledResponse(task: pendingTask)
            case .continue:
                if pendingTask.redirectRequest != nil {
                    followPendingRedirect(requestId: requestId)
                } else {
                    finish(task: pendingTask, with: responseData)
                }
            case .delegateToJs:
                emitProxyEvent(requestId: requestId, pendingTask: pendingTask)
                scheduleTimeout(for: requestId)
            }
        } else {
            if pendingTask.redirectRequest != nil {
                followPendingRedirect(requestId: requestId)
            } else {
                finish(task: pendingTask, with: responseData)
            }
        }
    }

    private func emitProxyEvent(requestId: String, pendingTask: PendingProxyTask) {
        var eventData: [String: Any] = [
            "requestId": requestId,
            "phase": pendingTask.phase,
            "url": pendingTask.requestContext.url,
            "method": pendingTask.requestContext.method,
            "headers": pendingTask.requestContext.headers,
            "body": pendingTask.requestContext.base64Body as Any,
            "webviewId": webviewId
        ]
        if let responseData = pendingTask.responseData {
            eventData["status"] = responseData.statusCode
            eventData["responseHeaders"] = responseData.headers
            eventData["responseBody"] = responseData.body.base64EncodedString()
        }
        plugin?.notifyListeners("proxyRequest", data: eventData)
    }

    private func scheduleTimeout(for requestId: String) {
        let timeoutToken = UUID()
        taskLock.lock()
        guard let pendingTask = pendingTasks[requestId] else {
            taskLock.unlock()
            return
        }
        pendingTask.timeoutToken = timeoutToken
        taskLock.unlock()

        DispatchQueue.global().asyncAfter(deadline: .now() + proxyTimeoutSeconds) { [weak self] in
            guard let self else { return }
            self.taskLock.lock()
            guard
                let pendingTask = self.pendingTasks[requestId],
                ProxySchemeRequestSupport.timeoutTokenMatches(
                    scheduledToken: timeoutToken,
                    currentToken: pendingTask.timeoutToken
                )
            else {
                self.taskLock.unlock()
                return
            }
            let responseData = pendingTask.responseData
            let phase = pendingTask.phase
            let hasPendingRedirect = pendingTask.redirectRequest != nil
            pendingTask.timeoutToken = nil

            switch ProxySchemeRequestSupport.timeoutResolutionAction(
                phase: phase,
                hasCachedResponse: responseData != nil,
                hasPendingRedirect: hasPendingRedirect
            ) {
            case .fallbackToNative:
                self.timedOutRequests[requestId] = phase
                self.taskLock.unlock()
                self.fallbackToNativePipeline(requestId: requestId)
                return
            case .finishCachedResponse:
                self.taskLock.unlock()
                guard let responseData else { return }
                self.syncResponseCookies(from: responseData, fallbackURL: pendingTask.requestContext.url) {
                    self.finish(task: pendingTask, with: responseData)
                }
                return
            case .failRequest:
                self.taskLock.unlock()
            }

            self.failSchemeTask(
                pendingTask,
                with: NSError(
                    domain: "ProxySchemeHandler",
                    code: NSURLErrorTimedOut,
                    userInfo: [NSLocalizedDescriptionKey: "Proxy handler did not respond within \(Int(self.proxyTimeoutSeconds)) seconds"]
                )
            )
        }
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let requestId = requestId(for: task) else {
            completionHandler(request)
            return
        }

        guard let pendingTask = pendingTask(for: requestId) else {
            completionHandler(nil)
            return
        }

        let redirectResponse = NativeResponseData(
            statusCode: response.statusCode,
            headers: ProxySchemeRequestSupport.normalizedResponseHeaders(from: response),
            body: Data(),
            contentType: response.value(forHTTPHeaderField: "Content-Type") ?? "application/octet-stream"
        )

        pendingTask.requestContext.url = ProxySchemeRequestSupport.resolvedResponseURL(
            response,
            fallback: pendingTask.requestContext.url
        )
        pendingTask.responseData = redirectResponse
        pendingTask.redirectRequest = request
        completionHandler(nil)

        syncResponseCookies(from: response, fallbackURL: pendingTask.requestContext.url) { [weak self] in
            self?.executeInboundDecision(requestId: requestId)
        }
    }

    private func pendingTask(for requestId: String) -> PendingProxyTask? {
        taskLock.lock()
        let task = pendingTasks[requestId]
        taskLock.unlock()
        return task
    }

    private func routeCurrentRequest(requestId: String) {
        guard let pendingTask = pendingTask(for: requestId) else { return }
        pendingTask.phase = "outbound"
        pendingTask.responseData = nil

        if let outboundRule = firstMatchingRule(for: pendingTask.requestContext, responseData: nil, phase: "outbound") {
            switch outboundRule.action {
            case .cancel:
                finishWithCanceledResponse(task: pendingTask)
            case .continue:
                executeNativePipeline(requestId: requestId)
            case .delegateToJs:
                emitProxyEvent(requestId: requestId, pendingTask: pendingTask)
                scheduleTimeout(for: requestId)
            }
        } else {
            executeNativePipeline(requestId: requestId)
        }
    }

    private func followPendingRedirect(requestId: String, requestOverride: [String: Any]? = nil) {
        guard let pendingTask = pendingTask(for: requestId), let redirectRequest = pendingTask.redirectRequest else {
            executeNativePipeline(requestId: requestId)
            return
        }

        var redirectContext = makeRequestContext(from: redirectRequest, fallback: pendingTask.requestContext)
        if let requestOverride {
            redirectContext = applyRequestOverride(requestOverride, to: redirectContext)
        }

        pendingTask.requestContext = redirectContext
        pendingTask.responseData = nil
        pendingTask.redirectRequest = nil
        pendingTask.urlSessionTask = nil
        routeCurrentRequest(requestId: requestId)
    }

    private func fallbackToNativePipeline(requestId: String) {
        guard let pendingTask = pendingTask(for: requestId) else { return }
        if pendingTask.phase == "inbound", pendingTask.redirectRequest != nil {
            followPendingRedirect(requestId: requestId)
            return
        }
        executeNativePipeline(requestId: requestId)
    }

    private func requestId(for task: URLSessionTask) -> String? {
        taskLock.lock()
        defer { taskLock.unlock() }
        return pendingTasks.first(where: { $0.value.urlSessionTask?.taskIdentifier == task.taskIdentifier })?.key
    }

    private func scheduleStoppedRequestCleanup(requestId: String, token: UUID) {
        DispatchQueue.global().asyncAfter(deadline: .now() + proxyTimeoutSeconds) { [weak self] in
            guard let self else { return }
            self.taskLock.lock()
            defer { self.taskLock.unlock() }
            guard self.stoppedRequests[requestId] == token else { return }
            self.stoppedRequests.removeValue(forKey: requestId)
        }
    }

    private func firstMatchingRule(for requestContext: NativeRequestContext, responseData: NativeResponseData?, phase: String) -> NativeProxyRule? {
        let usesLegacyCatchAllRule = ProxySchemeRequestSupport.shouldUseLegacyCatchAllRule(
            legacyProxyRequests: legacyProxyRequests,
            hasOutboundRules: !outboundRules.isEmpty,
            hasInboundRules: !inboundRules.isEmpty,
            phase: phase
        )
        let rules: [NativeProxyRule]

        if usesLegacyCatchAllRule {
            rules = [
                NativeProxyRule(
                    id: nil,
                    urlRegex: legacyProxyRequestURLRegex,
                    methodRegex: nil,
                    headerRegex: nil,
                    bodyRegex: nil,
                    statusRegex: nil,
                    responseHeaderRegex: nil,
                    responseBodyRegex: nil,
                    mainFrameOnly: false,
                    action: .delegateToJs
                )
            ]
        } else {
            rules = phase == "outbound" ? outboundRules : inboundRules
        }

        let serializedHeaders = serialize(headers: requestContext.headers)
        let decodedBody = decodeBase64Body(requestContext.base64Body)
        let serializedResponseHeaders = responseData.map { serialize(headers: $0.headers) }
        let decodedResponseBody = responseData.flatMap { String(data: $0.body, encoding: .utf8) }
        let status = responseData?.statusCode

        return rules.first {
            $0.matches(
                url: requestContext.url,
                method: requestContext.method,
                headers: serializedHeaders,
                body: decodedBody,
                isMainFrame: requestContext.isMainFrame,
                status: status,
                responseHeaders: serializedResponseHeaders,
                responseBody: decodedResponseBody
            )
        }
    }

    private func makeURLRequest(from context: NativeRequestContext) throws -> URLRequest {
        guard let url = URL(string: context.url) else {
            throw ProxySchemeRequestSupport.RequestBuildError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = context.method
        // URLSession will auto-decompress gzip/br/zstd responses only when
        // Accept-Encoding is NOT present in the request headers (URLSession
        // adds its own and owns the decompression in that case). If we forward
        // an Accept-Encoding value supplied by the JS handler (or carried over
        // by URLSession's own redirect-rewriting), URLSession assumes the
        // caller will handle decompression and passes the raw compressed bytes
        // through, which the WKURLSchemeTask then renders as gibberish in the
        // WebView. Strip it so URLSession's transparent decompression kicks in.
        let filteredHeaders = context.headers.filter { key, _ in
            key.caseInsensitiveCompare("Accept-Encoding") != .orderedSame
        }
        request.allHTTPHeaderFields = filteredHeaders
        if
            ProxySchemeRequestSupport.supportsRequestBody(method: context.method),
            let bodyData = try ProxySchemeRequestSupport.decodedRequestBody(from: context.base64Body) {
            request.httpBody = bodyData
        }
        return request
    }

    private func makeRequestContext(from request: URLRequest, fallback: NativeRequestContext) -> NativeRequestContext {
        let method = ProxySchemeRequestSupport.normalizedRequestMethod(request.httpMethod)
        return NativeRequestContext(
            url: request.url?.absoluteString ?? fallback.url,
            method: method,
            headers: ProxySchemeRequestSupport.resolvedRedirectHeaders(request.allHTTPHeaderFields, fallback: fallback.headers),
            base64Body: ProxySchemeRequestSupport.resolvedRedirectBody(
                extractBody(from: request),
                method: method,
                fallback: fallback.base64Body
            ),
            isMainFrame: ProxySchemeRequestSupport.isMainFrameRequest(request, fallback: fallback.isMainFrame)
        )
    }

    private func makeNativeResponse(from responseData: [String: Any]) throws -> NativeResponseData {
        let status = responseData["status"] as? Int ?? 200
        let headers = responseData["headers"] as? [String: String] ?? [:]
        let body = try ProxySchemeRequestSupport.decodedResponseBody(from: responseData["body"] as? String)
        return NativeResponseData(
            statusCode: status,
            headers: headers,
            body: body,
            contentType: headers["Content-Type"] ?? headers["content-type"] ?? "application/octet-stream"
        )
    }

    private func applyRequestOverride(_ override: [String: Any], to context: NativeRequestContext) -> NativeRequestContext {
        let resolvedURL = ProxySchemeRequestSupport.sanitizedOverrideURL(override["url"] as? String, fallback: context.url)
        let resolvedMethod = ProxySchemeRequestSupport.normalizedRequestMethod((override["method"] as? String) ?? context.method)
        let resolvedHeaders = (override["headers"] as? [String: String]) ??
            ProxySchemeRequestSupport.prepareOverrideHeaders(
                originalHeaders: context.headers,
                requestURL: context.url,
                overrideURL: resolvedURL
            )
        let resolvedBody = ProxySchemeRequestSupport.resolvedOverrideBody(
            from: override,
            method: resolvedMethod,
            fallback: context.base64Body
        )

        return NativeRequestContext(
            url: resolvedURL,
            method: resolvedMethod,
            headers: resolvedHeaders,
            base64Body: resolvedBody,
            isMainFrame: context.isMainFrame
        )
    }

    /// Runs a terminal `WKURLSchemeTask` call sequence at most once, and never after
    /// the task has been stopped.
    ///
    /// `WKURLSchemeTask` raises an uncatchable NSException if `didReceive`/`didFinish`/
    /// `didFailWithError` is called after `webView(_:stop:)`, after the task already
    /// completed, or out of order. Completion can be triggered from several racing
    /// sources — the URLSession delegate queue, the cookie-sync main-thread hop, the
    /// global timeout closure, `webView(_:stop:)`, and `cancelAllPendingTasks()`. This
    /// helper funnels every terminal call onto the main thread (the same queue WebKit
    /// uses for `start`/`stop`) and gates it behind `beginCompletionIfLive()`, so the
    /// liveness check and the actual call cannot be separated by a `stop`.
    ///
    /// This helper is also the single owner of task removal. Callers MUST NOT remove
    /// the task from `pendingTasks` eagerly on a background thread before calling a
    /// terminal method: doing so creates a window where `webView(_:stop:)` (which only
    /// finds tasks via `pendingTasks`) can no longer mark the task stopped, so a queued
    /// completion would then call `didReceive`/`didFinish`/`didFailWithError` on an
    /// already-stopped task and crash. Keeping the task tracked until this main-queue
    /// block runs lets a racing `stop` win the liveness claim.
    private func completeSchemeTask(_ task: PendingProxyTask, _ body: @escaping (WKURLSchemeTask) -> Void) {
        let runBody: () -> Void = { [weak self] in
            guard let self else { return }
            self.taskLock.lock()
            let isLive = task.beginCompletionIfLive()
            self.pendingTasks.removeValue(forKey: task.requestId)
            self.stoppedRequests.removeValue(forKey: task.requestId)
            self.timedOutRequests.removeValue(forKey: task.requestId)
            self.taskLock.unlock()
            guard isLive else { return }
            body(task.schemeTask)
        }
        if Thread.isMainThread {
            runBody()
        } else {
            DispatchQueue.main.async(execute: runBody)
        }
    }

    private func failSchemeTask(_ task: PendingProxyTask, with error: Error) {
        completeSchemeTask(task) { schemeTask in
            schemeTask.didFailWithError(error)
        }
    }

    private func finish(task: PendingProxyTask, with responseData: NativeResponseData) {
        completeSchemeTask(task) { schemeTask in
            guard let url = URL(string: task.requestContext.url),
                  let httpResponse = HTTPURLResponse(
                    url: url,
                    statusCode: responseData.statusCode,
                    httpVersion: "HTTP/1.1",
                    headerFields: responseData.headers
                  )
            else {
                schemeTask.didFailWithError(
                    NSError(
                        domain: "ProxySchemeHandler",
                        code: -2,
                        userInfo: [NSLocalizedDescriptionKey: "Failed to create response"]
                    )
                )
                return
            }
            schemeTask.didReceive(httpResponse)
            schemeTask.didReceive(responseData.body)
            schemeTask.didFinish()
        }
    }

    private func finishWithCanceledResponse(task: PendingProxyTask) {
        let responseData = NativeResponseData(statusCode: 204, headers: [:], body: Data(), contentType: "text/plain")
        finish(task: task, with: responseData)
    }

    private func syncResponseCookies(from response: URLResponse?, fallbackURL: String, completion: @escaping () -> Void) {
        guard !fallbackURL.isEmpty else {
            completion()
            return
        }
        let cookies = ProxySchemeRequestSupport.responseCookies(
            response: response,
            fallback: fallbackURL
        )
        guard !cookies.isEmpty else {
            completion()
            return
        }
        writeCookiesOnMain(cookies, completion: completion)
    }

    private func syncResponseCookies(from responseData: NativeResponseData, fallbackURL: String, completion: @escaping () -> Void) {
        guard !fallbackURL.isEmpty else {
            completion()
            return
        }
        let cookies = ProxySchemeRequestSupport.responseCookies(from: responseData.headers, fallback: fallbackURL)
        guard !cookies.isEmpty else {
            completion()
            return
        }
        writeCookiesOnMain(cookies, completion: completion)
    }

    /// Writes cookies into the WebView cookie store on the main thread.
    ///
    /// `WKWebsiteDataStore.httpCookieStore` and `WKHTTPCookieStore.setCookie` are both
    /// `@MainActor`-only on iOS 17+. URLSession delegate callbacks (including
    /// `urlSession(_:task:willPerformHTTPRedirection:newRequest:)`) and the timeout
    /// closure dispatched via `DispatchQueue.global().asyncAfter` run on background
    /// queues, so reaching the cookie store from there triggers Main Thread Checker
    /// warnings and risks runtime crashes in release builds. Hop to main here so all
    /// `syncResponseCookies` callers stay correct regardless of their own thread context.
    private func writeCookiesOnMain(_ cookies: [HTTPCookie], completion: @escaping () -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard
                let self,
                let plugin = self.plugin,
                let cookieStore = plugin.cookieStore(for: self.webviewId)
            else {
                completion()
                return
            }
            let group = DispatchGroup()
            for cookie in cookies {
                group.enter()
                cookieStore.setCookie(cookie) {
                    group.leave()
                }
            }
            group.notify(queue: .main) {
                completion()
            }
        }
    }


    private func makeInitialRequestContext(from urlSchemeTask: WKURLSchemeTask, requestURL: URL) -> NativeRequestContext? {
        let requestURLString = requestURL.absoluteString

        if let bridgeRequest = ProxyBridgeSupport.parseBridgeMarkerRequest(requestURLString) {
            if ProxyBridgeSupport.usesLegacyJsProxyMode(
                legacyProxyRequests: legacyProxyRequests,
                legacyProxyRequestURLRegex: legacyProxyRequestURLRegex,
                hasOutboundRules: !outboundRules.isEmpty,
                hasInboundRules: !inboundRules.isEmpty
            ),
            !ProxyBridgeSupport.shouldDelegateLegacyJsProxyRequest(
                legacyProxyRequests: legacyProxyRequests,
                legacyProxyRequestURLRegex: legacyProxyRequestURLRegex,
                hasOutboundRules: !outboundRules.isEmpty,
                hasInboundRules: !inboundRules.isEmpty,
                requestURL: bridgeRequest.originalURL
            ) {
                _ = proxyBridge?.getAndRemove(requestId: bridgeRequest.requestId)
                return nil
            }

            guard let stored = proxyBridge?.getAndRemove(requestId: bridgeRequest.requestId) else {
                return nil
            }

            var headers = ProxyBridgeSupport.decodeStoredHeaders(stored.headersJson)
            let markerHeaders = urlSchemeTask.request.allHTTPHeaderFields ?? [:]
            for (key, value) in markerHeaders where headers[key] == nil {
                headers[key] = value
            }

            let base64Body = stored.base64Body.isEmpty ? nil : stored.base64Body
            return NativeRequestContext(
                url: bridgeRequest.originalURL,
                method: ProxySchemeRequestSupport.normalizedRequestMethod(stored.method),
                headers: headers,
                base64Body: base64Body,
                isMainFrame: ProxySchemeRequestSupport.isMainFrameRequest(urlSchemeTask.request)
            )
        }

        return NativeRequestContext(
            url: requestURLString,
            method: ProxySchemeRequestSupport.normalizedRequestMethod(urlSchemeTask.request.httpMethod),
            headers: urlSchemeTask.request.allHTTPHeaderFields ?? [:],
            base64Body: extractBody(from: urlSchemeTask.request),
            isMainFrame: ProxySchemeRequestSupport.isMainFrameRequest(urlSchemeTask.request)
        )
    }

    private func finishCanceledSchemeTask(_ schemeTask: WKURLSchemeTask) {
        guard let url = schemeTask.request.url,
              let httpResponse = HTTPURLResponse(
                url: url,
                statusCode: 204,
                httpVersion: "HTTP/1.1",
                headerFields: [:]
              )
        else {
            schemeTask.didFailWithError(
                NSError(
                    domain: "ProxySchemeHandler",
                    code: -4,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to create canceled response"]
                )
            )
            return
        }

        schemeTask.didReceive(httpResponse)
        schemeTask.didReceive(Data())
        schemeTask.didFinish()
    }
    private func extractBody(from request: URLRequest) -> String? {
        if let body = request.httpBody {
            return body.base64EncodedString()
        }
        if let stream = request.httpBodyStream {
            let data = Data(reading: stream)
            return data.isEmpty ? nil : data.base64EncodedString()
        }
        return nil
    }

    private func decodeBase64Body(_ body: String?) -> String? {
        guard let body, let data = Data(base64Encoded: body) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func serialize(headers: [String: String]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: headers, options: [.sortedKeys]),
              let string = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return string
    }

    func cancelAllPendingTasks() {
        taskLock.lock()
        let pending = pendingTasks
        // Do not clear pendingTasks here. completeSchemeTask removes each task on the
        // main queue once its failure is claimed, so a concurrent webView(_:stop:) can
        // still find and mark the task stopped before the queued failure runs. Clearing
        // eagerly would reopen the post-stop completion crash this fix closes.
        stoppedRequests.removeAll()
        taskLock.unlock()
        session.invalidateAndCancel()

        for (_, task) in pending {
            task.urlSessionTask?.cancel()
            failSchemeTask(
                task,
                with: NSError(
                    domain: "ProxySchemeHandler",
                    code: NSURLErrorCancelled,
                    userInfo: [NSLocalizedDescriptionKey: "WebView closed"]
                )
            )
        }
    }

    func hasPendingProxyRequest(_ requestId: String) -> Bool {
        taskLock.lock()
        defer { taskLock.unlock() }
        return pendingTasks[requestId] != nil
    }
}
// swiftlint:enable type_body_length

extension ProxySchemeHandler: ProxyRequestLocating {}

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
// swiftlint:enable file_length
