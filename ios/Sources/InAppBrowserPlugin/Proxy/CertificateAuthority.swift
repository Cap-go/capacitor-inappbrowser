import Foundation
import Security
import CryptoKit
import NIOSSL

/// Errors specific to the proxy subsystem.
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

/// Manages a self-signed CA certificate used by the MITM proxy.
///
/// Responsibilities:
///  1. Generate a self-signed CA cert (RSA 2048) at first launch.
///  2. Persist it to app storage (PKCS12 keystore).
///  3. Generate domain-specific leaf certs signed by the CA on demand.
///  4. Cache domain `NIOSSLContext`s.
///  5. Expose CA fingerprint for selective trust.
///
/// **NOTE:** Full certificate generation (ASN.1 / leaf certs) is a TODO.
/// This file is a *skeleton* that compiles and provides the public API
/// contract the rest of the proxy system depends on.  The `buildDomainContext`
/// and `generateNewCA` methods contain placeholders that will be filled once a
/// proper ASN.1 builder (e.g. swift-certificates or a manual DER encoder) is
/// integrated.
class CertificateAuthority {

    // MARK: - State

    private var caPrivateKeyData: Data?
    private var caCertDER: [UInt8]?
    private var domainContextCache: [String: NIOSSLContext] = [:]
    private let lock = NSLock()

    private static let keystoreFile = "capgo_proxy_ca.p12"
    private static let keystorePassword = "capgo-proxy"

    // MARK: - Public API

    /// Load an existing CA from disk, or generate a new one.
    func loadOrCreate() throws {
        let fileURL = Self.caFileURL()
        if FileManager.default.fileExists(atPath: fileURL.path) {
            try loadFromDisk(fileURL)
        } else {
            try generateNewCA()
            try saveToDisk(fileURL)
        }
    }

    /// SHA-256 fingerprint of the CA certificate in hex.
    func getCACertFingerprint() -> String? {
        guard let certData = caCertDER else { return nil }
        let hash = SHA256.hash(data: Data(certData))
        return hash.map { String(format: "%02x", $0) }.joined()
    }

    /// Whether the CA material has been loaded / generated.
    var isLoaded: Bool {
        return caCertDER != nil && caPrivateKeyData != nil
    }

    /// Heuristic check used by `WKNavigationDelegate` to decide whether to
    /// trust a server certificate presented through the local proxy.
    ///
    /// For the initial implementation we trust any certificate when the proxy
    /// is active — matching the Android approach.  A more robust check will
    /// compare the issuer against the CA cert once full cert generation is done.
    func isCertSignedByCA(certificate: SecCertificate) -> Bool {
        guard caCertDER != nil else { return false }
        // TODO: Verify the certificate chain against our CA.
        // For now, trust any cert when proxy is active (same as Android approach).
        return true
    }

    /// Returns (or creates and caches) an `NIOSSLContext` containing a leaf
    /// certificate for `domain`, signed by the CA.
    func sslContextForDomain(_ domain: String) throws -> NIOSSLContext {
        lock.lock()
        defer { lock.unlock() }

        if let cached = domainContextCache[domain] {
            return cached
        }

        let context = try buildDomainContext(domain)
        domainContextCache[domain] = context
        return context
    }

    // MARK: - File Location

    private static func caFileURL() -> URL {
        let appSupport = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        try? FileManager.default.createDirectory(at: appSupport, withIntermediateDirectories: true)
        return appSupport.appendingPathComponent(keystoreFile)
    }

    // MARK: - CA Generation (skeleton)

    private func generateNewCA() throws {
        // Generate RSA 2048 key pair using the Security framework.
        let keyParams: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits as String: 2048
        ]

        var error: Unmanaged<CFError>?
        guard let privateKey = SecKeyCreateRandomKey(keyParams as CFDictionary, &error) else {
            throw ProxyError.certGenerationFailed(
                "Failed to generate CA key: \(error?.takeRetainedValue().localizedDescription ?? "unknown")")
        }

        guard let privateKeyData = SecKeyCopyExternalRepresentation(privateKey, &error) as Data? else {
            throw ProxyError.certGenerationFailed("Failed to export private key DER representation")
        }

        self.caPrivateKeyData = privateKeyData

        // TODO: Build a self-signed X.509 v3 certificate (DER encoded) using either:
        //   - swift-certificates (https://github.com/apple/swift-certificates)
        //   - A manual ASN.1/DER builder
        //
        // The certificate should:
        //   - Use the RSA key pair generated above
        //   - Have Subject: CN=Capgo InAppBrowser Proxy CA
        //   - Be a CA cert (basicConstraints: CA:TRUE)
        //   - Have a reasonable validity period (e.g. 3 years)
        //   - Include subjectKeyIdentifier and authorityKeyIdentifier extensions
        //
        // For now we store a nil placeholder.  The proxy will operate in
        // "blind tunnel" mode (no MITM) until this is completed.
        self.caCertDER = nil

        print("[CertificateAuthority] RSA key pair generated. Full cert generation is TODO.")
    }

    // MARK: - Persistence

    private func loadFromDisk(_ url: URL) throws {
        let data = try Data(contentsOf: url)

        var importResult: CFArray?
        let options: [String: Any] = [
            kSecImportExportPassphrase as String: Self.keystorePassword
        ]
        let status = SecPKCS12Import(data as CFData, options as CFDictionary, &importResult)
        guard status == errSecSuccess,
              let items = importResult as? [[String: Any]],
              let first = items.first else {
            throw ProxyError.certGenerationFailed("Failed to import PKCS12: OSStatus \(status)")
        }

        // Extract identity (cert + key) from the PKCS12 bundle.
        if let identityRef = first[kSecImportItemIdentity as String] {
            let identity = identityRef as! SecIdentity  // swiftlint:disable:this force_cast

            // Extract certificate
            var certRef: SecCertificate?
            SecIdentityCopyCertificate(identity, &certRef)
            if let cert = certRef {
                let certData = SecCertificateCopyData(cert) as Data
                self.caCertDER = [UInt8](certData)
            }

            // Extract private key
            var keyRef: SecKey?
            SecIdentityCopyPrivateKey(identity, &keyRef)
            if let key = keyRef {
                var error: Unmanaged<CFError>?
                if let keyData = SecKeyCopyExternalRepresentation(key, &error) as Data? {
                    self.caPrivateKeyData = keyData
                }
            }
        }

        if caPrivateKeyData == nil {
            throw ProxyError.certGenerationFailed("PKCS12 did not contain a usable private key")
        }

        print("[CertificateAuthority] Loaded CA from \(url.lastPathComponent)")
    }

    private func saveToDisk(_ url: URL) throws {
        // TODO: Export CA cert + private key as PKCS12 and write to `url`.
        // This requires a valid DER certificate to be available in `caCertDER`.
        // Until generateNewCA produces a real cert, we skip persistence.

        guard caCertDER != nil else {
            print("[CertificateAuthority] Skipping disk persistence — no cert generated yet (TODO)")
            return
        }

        // Placeholder for PKCS12 export:
        // 1. Create SecCertificate from caCertDER
        // 2. Reconstruct SecKey from caPrivateKeyData
        // 3. Create SecIdentity
        // 4. Export with SecPKCS12Export (or manual PKCS12 builder)
        // 5. Write to url
    }

    // MARK: - Domain Context (skeleton)

    private func buildDomainContext(_ domain: String) throws -> NIOSSLContext {
        // TODO: Generate a leaf certificate for `domain` signed by the CA,
        // then create an NIOSSLContext with that leaf cert and the CA key.
        //
        // Steps:
        //  1. Generate a new RSA 2048 key pair for the leaf
        //  2. Build an X.509 v3 cert with:
        //     - Subject: CN=<domain>
        //     - SAN: DNS:<domain>
        //     - Issuer: CN=Capgo InAppBrowser Proxy CA
        //     - Signed by the CA private key
        //  3. Convert to NIOSSLCertificate / NIOSSLPrivateKey
        //  4. Build NIOSSLContext with the cert chain

        // For now, throw an error indicating MITM is not yet available.
        // The proxy server handles this gracefully by falling back to blind tunneling.
        throw ProxyError.certGenerationFailed(
            "Domain cert generation not yet implemented for: \(domain). Using blind tunnel mode.")
    }
}
