import Foundation

/// A single proxy interception rule, parsed from the JS plugin call options.
struct NativeProxyRule {
    let ruleIndex: Int
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
    /// filter (if any) includes `method`.  Returns `nil` when no rule matches.
    func match(url: String, method: String) -> NativeProxyRule? {
        for rule in rules {
            let range = NSRange(url.startIndex..., in: url)
            if rule.urlPattern.firstMatch(in: url, range: range) == nil { continue }
            if let methods = rule.methods, !methods.contains(method.uppercased()) { continue }
            return rule
        }
        return nil
    }

    /// Quick check: could *any* rule potentially match requests to `host`?
    /// Used to decide whether CONNECT tunnels need MITM.
    func anyRuleCouldMatchHost(_ host: String) -> Bool {
        for rule in rules {
            let range = NSRange(host.startIndex..., in: host)
            if rule.urlPattern.firstMatch(in: host, range: range) != nil { return true }
        }
        return false
    }

    /// Parse an array of dictionaries (from the JS bridge) into typed rules.
    static func parse(from array: [[String: Any]]) throws -> [NativeProxyRule] {
        try array.map { dict in
            guard let ruleIndex = dict["ruleIndex"] as? Int,
                  let pattern = dict["urlPattern"] as? String,
                  let intercept = dict["intercept"] as? String else {
                throw ProxyError.proxyStartFailed("Invalid proxy rule: missing required fields")
            }
            let regex = try NSRegularExpression(pattern: pattern)
            let methods = (dict["methods"] as? [String])?.map { $0.uppercased() }
            let includeBody = dict["includeBody"] as? Bool ?? false
            return NativeProxyRule(
                ruleIndex: ruleIndex,
                urlPattern: regex,
                methods: methods,
                includeBody: includeBody,
                intercept: intercept
            )
        }
    }
}
