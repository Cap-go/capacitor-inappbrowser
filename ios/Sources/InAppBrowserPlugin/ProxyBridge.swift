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
        // Avoid O(n) queue scans on every parallel bridge request; stale ids are purged lazily.
        return storedRequests.removeValue(forKey: requestId)
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

    /// Keeps a proxy scheme handler only after HTTP/HTTPS registration succeeds.
    /// A handler that was not registered must not stay attached, or the proxy bridge
    /// rewrites requests that native code can no longer serve.
    static func resolvedProxySchemeHandler<Handler>(
        _ handler: Handler?,
        registrationSucceeded: Bool
    ) -> Handler? {
        guard registrationSucceeded else {
            return nil
        }
        return handler
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
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\u{2028}", with: "\\u2028")
            .replacingOccurrences(of: "\u{2029}", with: "\\u2029")
    }

    static func javascriptBridgeDocumentOriginAllowedCheckScript(
        bridgeOriginMode: String,
        bridgeOriginRulesJson: String
    ) -> String {
        let modeLiteral = escapeJavaScriptLiteral(bridgeOriginMode)
        let rulesLiteral = escapeJavaScriptLiteral(bridgeOriginRulesJson)
        return """
        function __capgoInAppBrowserBridgeDocumentOriginAllowed() {
          var mode = "\(modeLiteral)";
          if (mode === 'unrestricted') {
            return true;
          }
          if (mode !== 'allowlist') {
            return false;
          }
          var rulesJson = "\(rulesLiteral)";
          var allowlistRules = [];
          try {
            var parsed = JSON.parse(rulesJson);
            if (Array.isArray(parsed)) {
              allowlistRules = parsed.filter(function(entry) {
                return typeof entry === 'string' && entry.trim().length > 0;
              });
            }
          } catch (e) {}
          function defaultPortForScheme(scheme) {
            if (scheme === 'http') { return 80; }
            if (scheme === 'https') { return 443; }
            return -1;
          }
          function originFromUrl(url) {
            try {
              var parts = new URL(url);
              var scheme = parts.protocol.replace(/:$/, '').toLowerCase();
              var host = parts.hostname.toLowerCase();
              if (!scheme || !host) { return null; }
              var port = parts.port ? Number(parts.port) : defaultPortForScheme(scheme);
              var defaultPort = defaultPortForScheme(scheme);
              if (port === defaultPort) {
                return scheme + '://' + host;
              }
              return scheme + '://' + host + ':' + port;
            } catch (e) {
              return null;
            }
          }
          function matchesHostWildcard(host, patternHost) {
            if (!patternHost.startsWith('*.')) { return false; }
            var baseDomain = patternHost.slice(2);
            if (!baseDomain || host.toLowerCase() === baseDomain.toLowerCase()) { return false; }
            return host.toLowerCase().endsWith('.' + baseDomain.toLowerCase());
          }
          function matchesAllowRule(pageOrigin, rule) {
            var trimmedRule = (rule || '').trim();
            var normalizedRule = originFromUrl(trimmedRule) || originFromUrl('https://' + trimmedRule);
            if (!normalizedRule) { return false; }
            if (pageOrigin.toLowerCase() === normalizedRule.toLowerCase()) { return true; }
            var originParts = new URL(pageOrigin);
            var ruleParts = new URL(normalizedRule);
            var originScheme = originParts.protocol.replace(/:$/, '').toLowerCase();
            var ruleScheme = ruleParts.protocol.replace(/:$/, '').toLowerCase();
            var originHost = originParts.hostname.toLowerCase();
            var ruleHost = ruleParts.hostname.toLowerCase();
            if (originScheme !== ruleScheme) { return false; }
            var originPort = originParts.port ? Number(originParts.port) : defaultPortForScheme(originScheme);
            var rulePort = ruleParts.port ? Number(ruleParts.port) : defaultPortForScheme(ruleScheme);
            if (originPort !== rulePort) { return false; }
            if (originHost === ruleHost) { return true; }
            return matchesHostWildcard(originHost, ruleHost);
          }
          var origin = originFromUrl(window.location.href);
          if (!origin) { return false; }
          return allowlistRules.some(function(rule) { return matchesAllowRule(origin, rule); });
        }
        """
    }

    static func mobileAppWindowCloseOverrideScript(
        bridgeOriginMode: String,
        bridgeOriginRulesJson: String
    ) -> String {
        let originCheck = javascriptBridgeDocumentOriginAllowedCheckScript(
            bridgeOriginMode: bridgeOriginMode,
            bridgeOriginRulesJson: bridgeOriginRulesJson
        )
        return """
        \(originCheck)
        if (!window.__capgoInAppBrowserWindowCloseInstalled) {
                window.__capgoInAppBrowserWindowCloseInstalled = true;
                window.__capgoInAppBrowserOriginalClose = window.close;
                window.close = function() {
                        if (__capgoInAppBrowserBridgeDocumentOriginAllowed()
                                && window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.close) {
                                window.webkit.messageHandlers.close.postMessage(null);
                                return;
                        }
                        if (typeof window.__capgoInAppBrowserOriginalClose === 'function') {
                                return window.__capgoInAppBrowserOriginalClose.apply(window, arguments);
                        }
                };
        }
        """
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

    static func loadBundledBridgeScript(
        accessToken: String,
        proxyRegexSource: String,
        bridgeOriginMode: String,
        bridgeOriginRulesJson: String
    ) -> String? {
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
            .replacingOccurrences(of: "___CAPGO_BRIDGE_ORIGIN_MODE___", with: escapeJavaScriptLiteral(bridgeOriginMode))
            .replacingOccurrences(
                of: "___CAPGO_BRIDGE_ORIGIN_RULES___",
                with: escapeJavaScriptLiteral(bridgeOriginRulesJson)
            )
    }
}
