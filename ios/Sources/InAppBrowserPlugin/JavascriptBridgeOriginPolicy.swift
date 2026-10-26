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
        let scheme = securityOrigin.`protocol`.lowercased()
        let host = securityOrigin.host.lowercased()
        if scheme.isEmpty || host.isEmpty {
            return nil
        }
        let port = securityOrigin.port > 0 ? Int(securityOrigin.port) : -1
        return buildOrigin(scheme: scheme, host: host, port: port)
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
