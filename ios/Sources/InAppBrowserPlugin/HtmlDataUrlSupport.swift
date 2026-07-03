import Foundation

enum HtmlDataUrlSupport {
    private static let mimePrefix = "data:text/html"
    private static let base64Marker = ";base64,"

    static func isHtmlBase64DataUrl(_ urlString: String) -> Bool {
        parseHtml(from: urlString) != nil
    }

    static func isDataUrl(_ urlString: String) -> Bool {
        urlString.lowercased().hasPrefix("data:")
    }

    static func parseHtml(from urlString: String) -> String? {
        let lowercased = urlString.lowercased()
        guard lowercased.hasPrefix(mimePrefix) else {
            return nil
        }

        guard let base64Range = lowercased.range(of: base64Marker) else {
            return nil
        }

        let payloadStart = urlString.index(
            urlString.startIndex,
            offsetBy: lowercased.distance(from: lowercased.startIndex, to: base64Range.upperBound)
        )
        let payload = String(urlString[payloadStart...])
        guard !payload.isEmpty, let data = Data(base64Encoded: payload) else {
            return nil
        }

        return String(data: data, encoding: .utf8)
    }
}
