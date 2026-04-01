import Foundation

/// A single proxy interception rule, parsed from the JS plugin call options.
struct NativeProxyRule {
    let ruleName: String
    let urlPattern: NSRegularExpression
    let methods: [String]?   // nil = all methods
    let includeBody: Bool
    let intercept: String    // "request" | "response" | "both"

    var interceptsRequest: Bool {
        intercept == "request" || intercept == "both"
    }

    var interceptsResponse: Bool {
        intercept == "response" || intercept == "both"
    }
}

/// Matches URLs and HTTP methods against a set of `NativeProxyRule`s.
///
/// This mirrors the Android `ProxyRuleMatcher` implementation.
class ProxyRuleMatcher {
    private let rules: [NativeProxyRule]

    init(rules: [NativeProxyRule]) {
        self.rules = rules
    }

    /// Returns the first rule whose URL pattern matches `url` and whose method
    /// filter (if any) includes `method` for request interception.
    func matchRequest(url: String, method: String) -> NativeProxyRule? {
        for rule in rules {
            guard rule.interceptsRequest else { continue }
            guard matches(rule: rule, url: url, method: method) else { continue }
            return rule
        }
        return nil
    }

    /// Returns the first rule whose URL pattern matches `url` and whose method
    /// filter (if any) includes `method` for response interception.
    func matchResponse(url: String, method: String) -> NativeProxyRule? {
        for rule in rules {
            guard rule.interceptsResponse else { continue }
            guard matches(rule: rule, url: url, method: method) else { continue }
            return rule
        }
        return nil
    }

    /// Quick check: could *any* rule potentially match requests to `host`?
    /// Used to decide whether CONNECT tunnels need MITM.
    func anyRuleCouldMatchHost(_ host: String) -> Bool {
        let candidates = [
            host,
            "http://\(host)",
            "http://\(host)/",
            "https://\(host)",
            "https://\(host)/",
        ]
        for rule in rules {
            for candidate in candidates {
                let range = NSRange(candidate.startIndex..., in: candidate)
                if rule.urlPattern.firstMatch(in: candidate, range: range) != nil {
                    return true
                }
            }

            let pattern = rule.urlPattern.pattern.lowercased()
            guard pattern.contains("://") else { continue }
            if let hostHint = urlScopedHostHint(from: pattern) {
                if hostHint == host.lowercased() {
                    return true
                }
                continue
            }

            // For URL-scoped regexes with dynamic host components, prefer MITM so
            // later request/response matching still has the full URL available.
            return true
        }
        return false
    }

    /// Parse an array of dictionaries (from the JS bridge) into typed rules.
    static func parse(from array: [[String: Any]]) throws -> [NativeProxyRule] {
        try array.map { dict in
            guard let ruleName = dict["ruleName"] as? String,
                  let pattern = dict["urlPattern"] as? String,
                  let intercept = dict["intercept"] as? String else {
                throw ProxyError.proxyStartFailed("Invalid proxy rule: missing required fields")
            }
            guard ["request", "response", "both"].contains(intercept) else {
                throw ProxyError.proxyStartFailed("Invalid intercept '\(intercept)' for rule '\(ruleName)'")
            }
            let regex = try NSRegularExpression(pattern: pattern)
            let methods = (dict["methods"] as? [String])?.map { $0.uppercased() }
            let includeBody = dict["includeBody"] as? Bool ?? false
            return NativeProxyRule(
                ruleName: ruleName,
                urlPattern: regex,
                methods: methods,
                includeBody: includeBody,
                intercept: intercept
            )
        }
    }

    private func urlScopedHostHint(from pattern: String) -> String? {
        guard let schemeRange = pattern.range(of: "://") else {
            return nil
        }

        let remainder = pattern[schemeRange.upperBound...]
        let authorityEnd = remainder.firstIndex(of: "/") ?? remainder.endIndex
        var authority = String(remainder[..<authorityEnd])
        authority = authority.replacingOccurrences(of: "^", with: "")
        authority = authority.replacingOccurrences(of: "$", with: "")
        authority = authority.replacingOccurrences(of: "\\.", with: ".")
        authority = authority.replacingOccurrences(of: "\\-", with: "-")

        if authority.range(of: #"[\\\[\]\(\)\{\}\+\*\?\|]"#, options: .regularExpression) != nil {
            return nil
        }

        return authority.lowercased()
    }

    private func matches(rule: NativeProxyRule, url: String, method: String) -> Bool {
        let range = NSRange(url.startIndex..., in: url)
        guard rule.urlPattern.firstMatch(in: url, range: range) != nil else {
            return false
        }
        if let methods = rule.methods, !methods.contains(method.uppercased()) {
            return false
        }
        return true
    }
}
