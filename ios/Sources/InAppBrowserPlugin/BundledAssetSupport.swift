import Foundation

enum BundledAssetSupport {
    struct LocalConfig: Equatable {
        let scheme: String
        let host: String
    }

    struct Resolution: Equatable {
        let url: String
        let needsHandler: Bool
    }

    static let iosDefaults = LocalConfig(scheme: "capacitor", host: "localhost")
    static let androidDefaults = LocalConfig(scheme: "https", host: "localhost")

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

    static func resolve(_ urlString: String, localURL: URL?) -> Resolution {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        let localConfig = parseLocalConfig(from: localURL) ?? iosDefaults

        if isRelativeBundledPath(trimmed) {
            let path = normalizeBundledPath(trimmed)
            return Resolution(
                url: "\(localConfig.scheme)://\(localConfig.host)\(path)",
                needsHandler: true
            )
        }

        if isBundledLocalURL(trimmed, localConfig: localConfig) {
            return Resolution(url: trimmed, needsHandler: true)
        }

        return Resolution(url: trimmed, needsHandler: false)
    }

    static func isBundledLocalURL(_ urlString: String, localConfig: LocalConfig) -> Bool {
        guard let url = URL(string: urlString),
              let scheme = url.scheme?.lowercased(),
              let host = url.host?.lowercased() else {
            return false
        }

        return scheme == localConfig.scheme && host == localConfig.host
    }

    static func isRelativeBundledPath(_ urlString: String) -> Bool {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return false
        }

        return !isAbsoluteURL(trimmed)
    }

    static func normalizeBundledPath(_ path: String) -> String {
        let trimmed = path.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "/" {
            return "/"
        }

        return trimmed.hasPrefix("/") ? trimmed : "/\(trimmed)"
    }

    static func routeAssetPath(for requestPath: String, basePath: String) -> String {
        let normalizedPath = requestPath.isEmpty ? "/" : requestPath
        let pathURL = URL(fileURLWithPath: normalizedPath)

        if pathURL.pathExtension.isEmpty {
            return basePath + "/index.html"
        }

        return basePath + normalizedPath
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
