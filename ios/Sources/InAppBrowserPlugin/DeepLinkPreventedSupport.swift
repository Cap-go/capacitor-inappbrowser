import Foundation

enum DeepLinkPreventedSupport {
    private static let internalSchemes = ["about", "data", "blob", "javascript"]
    private static let appStoreHosts = ["itunes.apple.com", "apps.apple.com"]

    static func shouldEmitDeepLinkPreventedEvent(for url: URL, authorizedAppLinks: [String]) -> Bool {
        let scheme = url.scheme?.lowercased() ?? ""
        if scheme.isEmpty {
            return false
        }
        if internalSchemes.contains(scheme) {
            return false
        }

        if scheme != "http" && scheme != "https" && scheme != "file" {
            return true
        }

        if isAuthorizedAppLink(url, authorizedLinks: authorizedAppLinks) {
            return true
        }

        if let host = normalizeHost(url.host), appStoreHosts.contains(host) {
            return true
        }

        return false
    }

    static func isAuthorizedAppLink(_ url: URL, authorizedLinks: [String]) -> Bool {
        guard !authorizedLinks.isEmpty else {
            return false
        }

        let urlHostNorm = normalizeHost(url.host)
        for auth in authorizedLinks {
            guard let components = URLComponents(string: auth) else {
                continue
            }
            let authHostNorm = normalizeHost(components.host)
            if urlHostNorm == authHostNorm {
                return true
            }
        }

        return false
    }

    private static func normalizeHost(_ host: String?) -> String? {
        guard var hostValue = host?.lowercased() else {
            return nil
        }
        if hostValue.hasPrefix("www.") {
            hostValue.removeFirst(4)
        }
        return hostValue
    }
}