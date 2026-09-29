import Foundation

enum WalletPassSupport {
    static let mimeType = "application/vnd.apple.pkpass"

    /// Servers often send passes as `application/octet-stream`, so the `.pkpass` extension counts too.
    static func isWalletPass(mimeType: String?, fileURL: URL) -> Bool {
        if fileURL.pathExtension.lowercased() == "pkpass" {
            return true
        }
        guard let mimeType else {
            return false
        }
        let essence = mimeType.split(separator: ";", maxSplits: 1).first ?? ""
        return essence.trimmingCharacters(in: .whitespaces).lowercased() == Self.mimeType
    }
}
