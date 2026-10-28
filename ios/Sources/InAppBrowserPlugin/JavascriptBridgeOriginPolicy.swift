import Foundation
import WebKit

public enum JavascriptBridgeOriginPolicy: Equatable {
    case unrestricted
    case disabled
    case allowlist([String])

    public var isDisabled: Bool {
        if case .disabled = self {
            return true
        }
        return false
    }

    public var isUnrestricted: Bool {
        if case .unrestricted = self {
            return true
        }
        return false
    }

    public enum PluginParseResult {
        case success(JavascriptBridgeOriginPolicy)
        case failure(String)
    }

    public static func parsePluginOption(_ rawValue: Any?) -> PluginParseResult {
        guard let rawValue else {
            return .success(.unrestricted)
        }
        if rawValue is NSNull {
            return .success(.unrestricted)
        }
        if let entries = rawValue as? [Any] {
            let rules = entries.compactMap { $0 as? String }
            return .success(from(allowlist: rules))
        }
        return .failure("javascriptBridgeOrigins must be an array of strings")
    }

    public var proxyBridgeOriginModeLabel: String {
        switch self {
        case .unrestricted:
            return "unrestricted"
        case .disabled:
            return "disabled"
        case .allowlist:
            return "allowlist"
        }
    }

    public var proxyBridgeOriginRulesJson: String {
        if case .allowlist(let rules) = self {
            guard let data = try? JSONSerialization.data(withJSONObject: rules),
                  let json = String(data: data, encoding: .utf8) else {
                return "[]"
            }
            return json
        }
        return "[]"
    }

    public static func from(allowlist rules: [String]) -> JavascriptBridgeOriginPolicy {
        let normalized = rules.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        if normalized.isEmpty {
            return .disabled
        }
        return .allowlist(normalized)
    }

    public func isUrlAllowed(_ url: String?) -> Bool {
        switch self {
        case .unrestricted:
            return true
        case .disabled:
            return false
        case .allowlist:
            guard let origin = Self.origin(fromUrl: url) else {
                return false
            }
            return isOriginAllowed(origin)
        }
    }

    public func isOriginAllowed(_ origin: String?) -> Bool {
        switch self {
        case .unrestricted:
            return true
        case .disabled:
            return false
        case .allowlist(let rules):
            guard let origin, !origin.isEmpty else {
                return false
            }
            return rules.contains { Self.matchesAllowRule(pageOrigin: origin, rule: $0) }
        }
    }

    public func allows(message: WKScriptMessage) -> Bool {
        switch self {
        case .unrestricted:
            return true
        case .disabled:
            return false
        case .allowlist:
            guard message.frameInfo.isMainFrame else {
                return false
            }
            let origin = Self.origin(from: message.frameInfo.securityOrigin)
            return isOriginAllowed(origin)
        }
    }

    static func origin(from securityOrigin: WKSecurityOrigin) -> String? {
        guard let schemeValue = securityOrigin.value(forKey: "protocol") as? String else {
            return nil
        }
        return origin(scheme: schemeValue, host: securityOrigin.host, securityOriginPort: Int(securityOrigin.port))
    }

    /// `WKSecurityOrigin.port` is 0 when the origin uses the scheme's default port.
    static func origin(scheme: String, host: String, securityOriginPort: Int) -> String? {
        let normalizedScheme = scheme.lowercased()
        let normalizedHost = host.lowercased()
        if normalizedScheme.isEmpty || normalizedHost.isEmpty {
            return nil
        }
        let port = securityOriginPort > 0 ? securityOriginPort : -1
        return buildOrigin(scheme: normalizedScheme, host: normalizedHost, port: port)
    }

    static func origin(fromUrl url: String?) -> String? {
        guard let url, !url.isEmpty, let components = URLComponents(string: url) else {
            return nil
        }
        guard let scheme = components.scheme?.lowercased(), let host = components.host?.lowercased() else {
            return nil
        }
        return buildOrigin(scheme: scheme, host: host, port: components.port ?? -1)
    }

    static func buildOrigin(scheme: String, host: String, port: Int) -> String {
        let defaultPort = Self.defaultPort(for: scheme)
        let normalizedPort = port < 0 ? defaultPort : port
        if normalizedPort == defaultPort {
            return "\(scheme)://\(host)"
        }
        return "\(scheme)://\(host):\(normalizedPort)"
    }

    static func matchesAllowRule(pageOrigin: String, rule: String) -> Bool {
        let trimmedRule = rule.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let normalizedRule = Self.origin(fromUrl: trimmedRule) ?? Self.origin(fromUrl: "https://\(trimmedRule)") else {
            return false
        }
        if pageOrigin.caseInsensitiveCompare(normalizedRule) == .orderedSame {
            return true
        }

        guard let originComponents = URLComponents(string: pageOrigin),
              let ruleComponents = URLComponents(string: normalizedRule),
              let originScheme = originComponents.scheme?.lowercased(),
              let ruleScheme = ruleComponents.scheme?.lowercased(),
              let originHost = originComponents.host?.lowercased(),
              let ruleHost = ruleComponents.host?.lowercased() else {
            return false
        }

        if originScheme != ruleScheme {
            return false
        }

        let originPort = originComponents.port ?? defaultPort(for: originScheme)
        let rulePort = ruleComponents.port ?? defaultPort(for: ruleScheme)
        if originPort != rulePort {
            return false
        }

        if originHost == ruleHost {
            return true
        }

        return matchesHostWildcard(host: originHost, patternHost: ruleHost)
    }

    private static func defaultPort(for scheme: String) -> Int {
        switch scheme.lowercased() {
        case "http":
            return 80
        case "https":
            return 443
        default:
            return -1
        }
    }

    private static func matchesHostWildcard(host: String, patternHost: String) -> Bool {
        guard patternHost.hasPrefix("*.") else {
            return false
        }
        let baseDomain = String(patternHost.dropFirst(2))
        if baseDomain.isEmpty || host.caseInsensitiveCompare(baseDomain) == .orderedSame {
            return false
        }
        return host.hasSuffix("." + baseDomain)
    }
}
