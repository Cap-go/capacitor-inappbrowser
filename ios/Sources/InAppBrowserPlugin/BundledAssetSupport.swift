import Foundation

enum BundledAssetSupport {
    struct LocalConfig: Equatable {
        let scheme: String
        let host: String
    }

    struct Resolution: Equatable {
        let url: String
    }

    static let iosDefaults = LocalConfig(scheme: "capacitor", host: "localhost")
    static let reservedWebKitSchemes = ["http", "https"]

    static func parseLocalConfig(from localURL: URL?) -> LocalConfig? {
        guard let localURL else {
            return nil
        }

        guard let scheme = localURL.scheme?.lowercased(), !scheme.isEmpty else {
            return nil
        }

        let host = localURL.host?.lowercased() ?? ""
        guard !host.isEmpty else {
            return nil
        }

        return LocalConfig(scheme: scheme, host: host)
    }

    static func handlerScheme(for localConfig: LocalConfig) -> String {
        if reservedWebKitSchemes.contains(localConfig.scheme) {
            return iosDefaults.scheme
        }
        return localConfig.scheme
    }

    static func resolve(_ urlString: String, localURL: URL?) -> Resolution? {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        let localConfig = parseLocalConfig(from: localURL) ?? iosDefaults
        let navigationScheme = handlerScheme(for: localConfig)

        if isRelativeBundledPath(trimmed) {
            guard let path = normalizeBundledPath(trimmed) else {
                return nil
            }
            return Resolution(url: "\(navigationScheme)://\(localConfig.host)\(path)")
        }

        if isBundledLocalURL(trimmed, localConfig: localConfig) {
            if let rewritten = rewriteBundledLocalURL(trimmed, localConfig: localConfig, navigationScheme: navigationScheme) {
                return Resolution(url: rewritten)
            }
            return Resolution(url: trimmed)
        }

        return Resolution(url: trimmed)
    }

    static func isBundledLocalURL(_ urlString: String, localConfig: LocalConfig) -> Bool {
        guard let url = URL(string: urlString),
              let scheme = url.scheme?.lowercased(),
              let host = url.host?.lowercased() else {
            return false
        }

        guard host == localConfig.host else {
            return false
        }

        if url.port != nil {
            return false
        }

        if scheme == localConfig.scheme || scheme == handlerScheme(for: localConfig) {
            return true
        }

        return reservedWebKitSchemes.contains(scheme) && reservedWebKitSchemes.contains(localConfig.scheme)
    }

    static func isRelativeBundledPath(_ urlString: String) -> Bool {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return false
        }

        return !isAbsoluteURL(trimmed)
    }

    private static let bundledAssetExtensions: Set<String> = [
        "html", "htm", "js", "css", "json", "xml", "svg",
        "png", "jpg", "jpeg", "gif", "webp", "woff", "woff2", "ttf", "map"
    ]

    static func isLikelyBundledRelativePath(_ urlString: String) -> Bool {
        guard isRelativeBundledPath(urlString) else {
            return false
        }

        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("/") || trimmed.contains("/") {
            return true
        }

        guard let dot = trimmed.lastIndex(of: "."), dot > trimmed.startIndex else {
            return true
        }

        let extensionStart = trimmed.index(after: dot)
        guard extensionStart < trimmed.endIndex else {
            return false
        }

        let fileExtension = String(trimmed[extensionStart...]).lowercased()
        return bundledAssetExtensions.contains(fileExtension)
    }

    static func normalizeBundledPath(_ path: String) -> String? {
        let trimmed = path.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "/" {
            return "/"
        }

        guard !containsPathTraversal(trimmed) else {
            return nil
        }

        return trimmed.hasPrefix("/") ? trimmed : "/\(trimmed)"
    }

    static func routeAssetPath(for requestPath: String, basePath: String) -> String? {
        guard !containsPathTraversal(requestPath) else {
            return nil
        }

        let normalizedPath = requestPath.isEmpty ? "/" : requestPath
        let relativePath: String
        if URL(fileURLWithPath: normalizedPath).pathExtension.isEmpty {
            relativePath = "/index.html"
        } else {
            relativePath = normalizedPath.hasPrefix("/") ? normalizedPath : "/\(normalizedPath)"
        }

        let resolvedURL = URL(fileURLWithPath: basePath).appendingPathComponent(relativePath, isDirectory: false).standardizedFileURL
        let baseURL = URL(fileURLWithPath: basePath).standardizedFileURL
        let resolvedPath = resolvedURL.path
        let basePathValue = baseURL.path

        guard resolvedPath == basePathValue || resolvedPath.hasPrefix(basePathValue + "/") else {
            return nil
        }

        return resolvedPath
    }

    static func containsPathTraversal(_ path: String) -> Bool {
        path.split(separator: "/").contains(where: { $0 == ".." })
    }

    private static func rewriteBundledLocalURL(
        _ urlString: String,
        localConfig: LocalConfig,
        navigationScheme: String
    ) -> String? {
        guard let url = URL(string: urlString),
              let host = url.host?.lowercased(),
              host == localConfig.host else {
            return nil
        }

        guard let scheme = url.scheme?.lowercased(), scheme != navigationScheme else {
            return nil
        }

        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.scheme = navigationScheme
        return components?.url?.absoluteString
    }

    private static func isAbsoluteURL(_ urlString: String) -> Bool {
        if urlString.hasPrefix("//") {
            return true
        }

        guard let colonIndex = urlString.firstIndex(of: ":"),
              colonIndex > urlString.startIndex else {
            return false
        }

        let scheme = urlString[..<colonIndex]
        guard let first = scheme.first, first.isLetter else {
            return false
        }

        return scheme.allSatisfy { $0.isLetter || $0.isNumber || $0 == "+" || $0 == "-" || $0 == "." }
    }
}
