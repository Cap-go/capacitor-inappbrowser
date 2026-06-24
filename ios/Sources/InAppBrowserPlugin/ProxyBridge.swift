import Foundation

struct ProxyBridgeStoredRequest {
    let method: String
    let headersJson: String
    let base64Body: String
    let credentialsMode: String
    let storedAt: Date
}

public final class ProxyBridge {
    static let storedRequestTTL: TimeInterval = 120

    private let accessToken: String
    private var storedRequests: [String: ProxyBridgeStoredRequest] = [:]
    private var storedRequestOrder: [String] = []
    private let lock = NSLock()
    private let clock: () -> Date

    init(accessToken: String, clock: @escaping () -> Date = Date.init) {
        self.accessToken = accessToken
        self.clock = clock
    }

    func storeRequest(
        token: String,
        requestId: String,
        method: String,
        headersJson: String,
        base64Body: String,
        credentialsMode: String
    ) {
        guard token == accessToken, !requestId.isEmpty else {
            return
        }

        let now = clock()
        lock.lock()
        cleanupExpiredRequests(now: now)
        storedRequests[requestId] = ProxyBridgeStoredRequest(
            method: method,
            headersJson: headersJson,
            base64Body: base64Body,
            credentialsMode: credentialsMode,
            storedAt: now
        )
        storedRequestOrder.append(requestId)
        lock.unlock()
    }

    func getAndRemove(requestId: String) -> ProxyBridgeStoredRequest? {
        lock.lock()
        defer { lock.unlock() }
        guard let storedRequest = storedRequests.removeValue(forKey: requestId) else {
            return nil
        }
        storedRequestOrder.removeAll { $0 == requestId }
        return storedRequest
    }

    private func cleanupExpiredRequests(now: Date) {
        while let oldestRequestId = storedRequestOrder.first {
            guard let oldestRequest = storedRequests[oldestRequestId] else {
                storedRequestOrder.removeFirst()
                continue
            }

            if now.timeIntervalSince(oldestRequest.storedAt) < Self.storedRequestTTL {
                return
            }

            storedRequests.removeValue(forKey: oldestRequestId)
            storedRequestOrder.removeFirst()
        }
    }
}

enum ProxyBridgeSupport {
    struct BridgeMarkerRequest {
        let originalURL: String
        let requestId: String
    }

    static func isBridgeMarkerRequestURL(_ requestURL: String) -> Bool {
        guard let components = URLComponents(string: requestURL),
              components.path == "/_capgo_proxy_",
              let query = components.query,
              !query.isEmpty else {
            return false
        }
        return true
    }

    static func parseBridgeMarkerRequest(_ requestURL: String) -> BridgeMarkerRequest? {
        guard isBridgeMarkerRequestURL(requestURL),
              let components = URLComponents(string: requestURL) else {
            return nil
        }

        let queryItems = components.queryItems ?? []
        guard let originalURL = queryItems.first(where: { $0.name == "u" })?.value,
              let requestId = queryItems.first(where: { $0.name == "rid" })?.value,
              !originalURL.isEmpty,
              !requestId.isEmpty else {
            return nil
        }

        return BridgeMarkerRequest(originalURL: originalURL, requestId: requestId)
    }

    static func decodeStoredHeaders(_ headersJson: String) -> [String: String] {
        guard let data = headersJson.data(using: .utf8),
              let headers = try? JSONSerialization.jsonObject(with: data) as? [String: String] else {
            return [:]
        }
        return headers
    }

    static func shouldInjectBridge(hasProxySchemeHandler: Bool) -> Bool {
        hasProxySchemeHandler
    }

    static func usesLegacyJsProxyMode(
        legacyProxyRequests: Bool,
        legacyProxyRequestURLRegex: NSRegularExpression?,
        hasOutboundRules: Bool,
        hasInboundRules: Bool
    ) -> Bool {
        (legacyProxyRequests || legacyProxyRequestURLRegex != nil) &&
            hasOutboundRules == false &&
            hasInboundRules == false
    }

    static func shouldDelegateLegacyJsProxyRequest(
        legacyProxyRequests: Bool,
        legacyProxyRequestURLRegex: NSRegularExpression?,
        hasOutboundRules: Bool,
        hasInboundRules: Bool,
        requestURL: String
    ) -> Bool {
        guard usesLegacyJsProxyMode(
            legacyProxyRequests: legacyProxyRequests,
            legacyProxyRequestURLRegex: legacyProxyRequestURLRegex,
            hasOutboundRules: hasOutboundRules,
            hasInboundRules: hasInboundRules
        ) else {
            return false
        }

        guard let legacyProxyRequestURLRegex else {
            return legacyProxyRequests
        }

        let range = NSRange(location: 0, length: requestURL.utf16.count)
        return legacyProxyRequestURLRegex.firstMatch(in: requestURL, options: [], range: range) != nil
    }

    static func escapeJavaScriptLiteral(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\u{2028}", with: "\\u2028")
            .replacingOccurrences(of: "\u{2029}", with: "\\u2029")
    }

    static func bootstrapScriptSource() -> String {
        """
        (function() {
          if (window.__capgoProxy && window.__capgoProxy.__capgoNativeBridgeInstalled) {
            return;
          }
          window.__capgoProxy = {
            __capgoNativeBridgeInstalled: true,
            storeRequest: function(token, requestId, method, headersJson, base64Body, credentialsMode) {
              if (!window.webkit || !window.webkit.messageHandlers || !window.webkit.messageHandlers.capgoProxyBridge) {
                return;
              }
              window.webkit.messageHandlers.capgoProxyBridge.postMessage({
                token: token,
                requestId: requestId,
                method: method,
                headersJson: headersJson,
                base64Body: base64Body,
                credentialsMode: credentialsMode
              });
            }
          };
        })();
        """
    }

    static func loadBundledBridgeScript(accessToken: String, proxyRegexSource: String) -> String? {
        #if SWIFT_PACKAGE
        let bundle = Bundle.module
        #else
        let bundle = Bundle(for: ProxyBridge.self)
        #endif

        guard let scriptURL = bundle.url(forResource: "proxy-bridge", withExtension: "js"),
              let rawScript = try? String(contentsOf: scriptURL) else {
            return nil
        }

        return rawScript
            .replacingOccurrences(of: "___CAPGO_PROXY_TOKEN___", with: escapeJavaScriptLiteral(accessToken))
            .replacingOccurrences(of: "___CAPGO_PROXY_REGEX___", with: escapeJavaScriptLiteral(proxyRegexSource))
    }
}
