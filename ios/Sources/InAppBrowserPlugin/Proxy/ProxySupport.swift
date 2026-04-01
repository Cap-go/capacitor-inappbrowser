import Foundation
import Security

enum ProxyError: Error, LocalizedError {
    case certGenerationFailed(String)
    case proxyStartFailed(String)
    case bindFailed
    case serverStopped

    var errorDescription: String? {
        switch self {
        case .certGenerationFailed(let msg): return "Certificate generation failed: \(msg)"
        case .proxyStartFailed(let msg): return "Proxy start failed: \(msg)"
        case .bindFailed: return "Failed to bind proxy server to a port"
        case .serverStopped: return "Proxy server has been stopped"
        }
    }
}

protocol ProxyEventDelegate: AnyObject {
    func onRequestIntercept(
        requestId: String,
        ruleName: String,
        requestData: [String: Any],
        completion: @escaping ([String: Any]?) -> Void
    )

    func onResponseIntercept(
        requestId: String,
        ruleName: String,
        responseData: [String: Any],
        completion: @escaping ([String: Any]?) -> Void
    )
}

#if !canImport(NIO) || !canImport(NIOCore) || !canImport(NIOPosix) || !canImport(NIOHTTP1) || !canImport(NIOSSL) || !canImport(X509) || !canImport(SwiftASN1) || !canImport(Crypto) || !canImport(_CryptoExtras)
private let proxyDependencyMessage = "Native iOS proxy interception currently requires the Swift Package Manager integration. CocoaPods builds do not include the proxy dependencies yet."

final class CertificateAuthority {
    func loadOrCreate() throws {
        throw ProxyError.proxyStartFailed(proxyDependencyMessage)
    }

    func getCACertFingerprint() -> String? {
        nil
    }

    func caSecCertificate() -> SecCertificate? {
        nil
    }

    var isLoaded: Bool {
        false
    }

    func isServerTrustSignedByCA(_ serverTrust: SecTrust) -> Bool {
        false
    }
}

final class SwiftNIOProxyServer {
    static let timeoutSeconds: Int64 = 10

    weak var delegate: ProxyEventDelegate?

    init(ruleMatcher: ProxyRuleMatcher, certificateAuthority: CertificateAuthority?) {
        _ = ruleMatcher
        _ = certificateAuthority
    }

    func start() throws -> Int {
        throw ProxyError.proxyStartFailed(proxyDependencyMessage)
    }

    func stop() {}
}
#endif
