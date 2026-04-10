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

    static func isMainFrameRequest(_ request: URLRequest) -> Bool {
        guard let url = request.url else { return false }
        guard let mainDocumentURL = request.mainDocumentURL else { return true }
        return mainDocumentURL.absoluteString == url.absoluteString
    }

    static func sanitizedOverrideURL(_ rawURL: String?, fallback: String) -> String {
        guard
            let rawURL,
            let url = URL(string: rawURL),
            url.scheme?.isEmpty == false
        else {
            return fallback
        }
        return rawURL
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

    static func timeoutResolutionAction(phase: String, hasCachedResponse: Bool) -> TimeoutResolutionAction {
        if phase == "outbound" {
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

    static func timeoutTokenMatches(scheduledToken: UUID, currentToken: UUID?) -> Bool {
        currentToken == scheduledToken
    }

    static func legacyProxyRequestsConfiguration(from rawValue: Any?) throws -> LegacyProxyRequestsConfiguration {
        if let enabled = rawValue as? Bool {
            return LegacyProxyRequestsConfiguration(isEnabled: enabled, urlRegex: nil)
        }

        guard let rawPattern = rawValue as? String else {
            return LegacyProxyRequestsConfiguration(isEnabled: false, urlRegex: nil)
        }

        let pattern = rawPattern.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !pattern.isEmpty else {
            return LegacyProxyRequestsConfiguration(isEnabled: false, urlRegex: nil)
        }

        return LegacyProxyRequestsConfiguration(
            isEnabled: true,
            urlRegex: try NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
        )
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
    let schemeTask: WKURLSchemeTask
    var requestContext: NativeRequestContext
    var responseData: NativeResponseData?
    var phase: String
    var urlSessionTask: URLSessionDataTask?
    var timeoutToken: UUID?
    var canceled = false

    init(schemeTask: WKURLSchemeTask, requestContext: NativeRequestContext, phase: String) {
        self.schemeTask = schemeTask
        self.requestContext = requestContext
        self.phase = phase
    }
}

// swiftlint:disable type_body_length
public class ProxySchemeHandler: NSObject, WKURLSchemeHandler {
    weak var plugin: InAppBrowserPlugin?
    private var pendingTasks: [String: PendingProxyTask] = [:]
    private var stoppedRequests: Set<String> = []
    private var timedOutRequests: [String: String] = [:]
    private let taskLock = NSLock()
    private let webviewId: String
    private let proxyTimeoutSeconds: TimeInterval = 10
    private let legacyProxyRequests: Bool
    private let legacyProxyRequestURLRegex: NSRegularExpression?
    private let outboundRules: [NativeProxyRule]
    private let inboundRules: [NativeProxyRule]

    private static var session: URLSession = {
        URLSession(configuration: .default)
    }()

    init(
        plugin: InAppBrowserPlugin,
        webviewId: String,
        legacyProxyRequests: Bool,
        legacyProxyRequestURLRegex: NSRegularExpression?,
        outboundRules: [NativeProxyRule],
        inboundRules: [NativeProxyRule]
    ) {
        self.plugin = plugin
        self.webviewId = webviewId
        self.legacyProxyRequests = legacyProxyRequests
        self.legacyProxyRequestURLRegex = legacyProxyRequestURLRegex
        self.outboundRules = outboundRules
        self.inboundRules = inboundRules
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
            inboundRules: inboundRules
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
        let requestContext = NativeRequestContext(
            url: url.absoluteString,
            method: urlSchemeTask.request.httpMethod ?? "GET",
            headers: urlSchemeTask.request.allHTTPHeaderFields ?? [:],
            base64Body: extractBody(from: urlSchemeTask.request),
            isMainFrame: ProxySchemeRequestSupport.isMainFrameRequest(urlSchemeTask.request)
        )
        let pendingTask = PendingProxyTask(schemeTask: urlSchemeTask, requestContext: requestContext, phase: "outbound")

        taskLock.lock()
        pendingTasks[requestId] = pendingTask
        taskLock.unlock()

        if let outboundRule = firstMatchingRule(for: requestContext, responseData: nil, phase: "outbound") {
            switch outboundRule.action {
            case .cancel:
                finishWithCanceledResponse(task: pendingTask)
                removePendingTask(requestId: requestId)
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

    public func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        taskLock.lock()
        if let entry = pendingTasks.first(where: { $0.value.schemeTask === urlSchemeTask }) {
            pendingTasks.removeValue(forKey: entry.key)
            stoppedRequests.insert(entry.key)
            let sessionTask = entry.value.urlSessionTask
            taskLock.unlock()
            sessionTask?.cancel()
            return
        }
        taskLock.unlock()
    }

    func handleResponse(requestId: String, phase: String?, responseData: [String: Any]?) {
        taskLock.lock()
        let wasStopped = stoppedRequests.remove(requestId) != nil
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

            if let requestOverride = responseData["request"] as? [String: Any] {
                pendingTask.requestContext = applyRequestOverride(requestOverride, to: pendingTask.requestContext)
            }

            let directResponse = (responseData["response"] as? [String: Any]) ?? responseData
            if directResponse["status"] != nil {
                pendingTask.responseData = makeNativeResponse(from: directResponse)
            }
        }

        if pendingTask.canceled {
            finishWithCanceledResponse(task: pendingTask)
            removePendingTask(requestId: requestId)
            return
        }

        switch ProxySchemeRequestSupport.jsResponseResolutionAction(
            phase: pendingTask.phase,
            hasCachedResponse: pendingTask.responseData != nil
        ) {
        case .finishCachedResponse:
            guard let responseData = pendingTask.responseData else { return }
            finish(task: pendingTask, with: responseData)
            removePendingTask(requestId: requestId)
        case .executeNativePipeline:
            executeNativePipeline(requestId: requestId)
        case .executeInboundDecision:
            executeInboundDecision(requestId: requestId)
        }
    }

    private func executeNativePipeline(requestId: String) {
        guard let pendingTask = pendingTask(for: requestId) else { return }
        guard let request = makeURLRequest(from: pendingTask.requestContext) else {
            pendingTask.schemeTask.didFailWithError(
                NSError(
                    domain: "ProxySchemeHandler",
                    code: -3,
                    userInfo: [NSLocalizedDescriptionKey: "Invalid request URL"]
                )
            )
            removePendingTask(requestId: requestId)
            return
        }
        let task = Self.session.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            guard let pendingTask = self.pendingTask(for: requestId) else { return }

            if let error {
                if (error as NSError).code != NSURLErrorCancelled {
                    pendingTask.schemeTask.didFailWithError(error)
                }
                self.removePendingTask(requestId: requestId)
                return
            }

            let httpResponse = response as? HTTPURLResponse
            let responseHeaders = (httpResponse?.allHeaderFields as? [String: String]) ?? [:]
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
                removePendingTask(requestId: requestId)
            case .continue:
                finish(task: pendingTask, with: responseData)
                removePendingTask(requestId: requestId)
            case .delegateToJs:
                emitProxyEvent(requestId: requestId, pendingTask: pendingTask)
                scheduleTimeout(for: requestId)
            }
        } else {
            finish(task: pendingTask, with: responseData)
            removePendingTask(requestId: requestId)
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
            pendingTask.timeoutToken = nil

            switch ProxySchemeRequestSupport.timeoutResolutionAction(phase: phase, hasCachedResponse: responseData != nil) {
            case .fallbackToNative:
                self.timedOutRequests[requestId] = phase
                self.taskLock.unlock()
                self.executeNativePipeline(requestId: requestId)
                return
            case .finishCachedResponse:
                self.pendingTasks.removeValue(forKey: requestId)
                self.taskLock.unlock()
                guard let responseData else { return }
                self.finish(task: pendingTask, with: responseData)
                return
            case .failRequest:
                self.pendingTasks.removeValue(forKey: requestId)
                self.taskLock.unlock()
            }

            pendingTask.schemeTask.didFailWithError(
                NSError(
                    domain: "ProxySchemeHandler",
                    code: NSURLErrorTimedOut,
                    userInfo: [NSLocalizedDescriptionKey: "Proxy handler did not respond within \(Int(self.proxyTimeoutSeconds)) seconds"]
                )
            )
        }
    }

    private func pendingTask(for requestId: String) -> PendingProxyTask? {
        taskLock.lock()
        let task = pendingTasks[requestId]
        taskLock.unlock()
        return task
    }

    private func removePendingTask(requestId: String) {
        taskLock.lock()
        pendingTasks.removeValue(forKey: requestId)
        stoppedRequests.remove(requestId)
        timedOutRequests.removeValue(forKey: requestId)
        taskLock.unlock()
    }

    private func firstMatchingRule(for requestContext: NativeRequestContext, responseData: NativeResponseData?, phase: String) -> NativeProxyRule? {
        let usesLegacyCatchAllRule = legacyProxyRequests && outboundRules.isEmpty && inboundRules.isEmpty
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

    private func makeURLRequest(from context: NativeRequestContext) -> URLRequest? {
        guard let url = URL(string: context.url) else {
            return nil
        }
        var request = URLRequest(url: url)
        request.httpMethod = context.method
        request.allHTTPHeaderFields = context.headers
        if let base64Body = context.base64Body, let bodyData = Data(base64Encoded: base64Body) {
            request.httpBody = bodyData
        }
        return request
    }

    private func makeNativeResponse(from responseData: [String: Any]) -> NativeResponseData {
        let status = responseData["status"] as? Int ?? 200
        let headers = responseData["headers"] as? [String: String] ?? [:]
        let body = Data(base64Encoded: responseData["body"] as? String ?? "") ?? Data()
        return NativeResponseData(
            statusCode: status,
            headers: headers,
            body: body,
            contentType: headers["Content-Type"] ?? headers["content-type"] ?? "application/octet-stream"
        )
    }

    private func applyRequestOverride(_ override: [String: Any], to context: NativeRequestContext) -> NativeRequestContext {
        let resolvedURL = ProxySchemeRequestSupport.sanitizedOverrideURL(override["url"] as? String, fallback: context.url)
        let resolvedHeaders = (override["headers"] as? [String: String]) ??
            ProxySchemeRequestSupport.prepareOverrideHeaders(
                originalHeaders: context.headers,
                requestURL: context.url,
                overrideURL: resolvedURL
            )

        return NativeRequestContext(
            url: resolvedURL,
            method: override["method"] as? String ?? context.method,
            headers: resolvedHeaders,
            base64Body: override["body"] as? String ?? context.base64Body,
            isMainFrame: context.isMainFrame
        )
    }

    private func finish(task: PendingProxyTask, with responseData: NativeResponseData) {
        guard let url = URL(string: task.requestContext.url),
              let httpResponse = HTTPURLResponse(
                url: url,
                statusCode: responseData.statusCode,
                httpVersion: "HTTP/1.1",
                headerFields: responseData.headers
              )
        else {
            task.schemeTask.didFailWithError(
                NSError(
                    domain: "ProxySchemeHandler",
                    code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Failed to create response"]
                )
            )
            return
        }
        task.schemeTask.didReceive(httpResponse)
        task.schemeTask.didReceive(responseData.body)
        task.schemeTask.didFinish()
    }

    private func finishWithCanceledResponse(task: PendingProxyTask) {
        let responseData = NativeResponseData(statusCode: 204, headers: [:], body: Data(), contentType: "text/plain")
        finish(task: task, with: responseData)
    }

    private func syncResponseCookies(from response: URLResponse?, fallbackURL: String, completion: @escaping () -> Void) {
        guard
            let plugin,
            let cookieStore = plugin.cookieStore(for: webviewId),
            !fallbackURL.isEmpty
        else {
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
        pendingTasks.removeAll()
        stoppedRequests.removeAll()
        taskLock.unlock()

        for (_, task) in pending {
            task.urlSessionTask?.cancel()
            task.schemeTask.didFailWithError(
                NSError(
                    domain: "ProxySchemeHandler",
                    code: NSURLErrorCancelled,
                    userInfo: [NSLocalizedDescriptionKey: "WebView closed"]
                )
            )
        }
    }
}
// swiftlint:enable type_body_length

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
