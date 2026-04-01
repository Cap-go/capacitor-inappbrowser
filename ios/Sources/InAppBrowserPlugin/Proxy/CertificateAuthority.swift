#if canImport(NIO) && canImport(NIOCore) && canImport(NIOPosix) && canImport(NIOHTTP1) && canImport(NIOSSL) && canImport(X509) && canImport(SwiftASN1) && canImport(Crypto) && canImport(_CryptoExtras)
import Foundation
import Security
import CryptoKit
import NIOSSL
import X509
import SwiftASN1
@preconcurrency import Crypto
import _CryptoExtras

/// Manages a self-signed CA certificate used by the MITM proxy.
///
/// Responsibilities:
///  1. Generate a self-signed CA cert (RSA 2048) at first launch.
///  2. Persist it to app storage (cert DER + key DER).
///  3. Generate domain-specific leaf certs signed by the CA on demand.
///  4. Cache domain `NIOSSLContext`s.
///  5. Expose CA fingerprint for selective trust.
class CertificateAuthority {

    // MARK: - State

    /// The CA RSA private key (swift-crypto type).
    private var caKey: _RSA.Signing.PrivateKey?

    /// The CA certificate DER bytes.
    private var caCertDER: [UInt8]?

    /// Cached NIOSSLContext per domain.
    private var domainContextCache: [String: NIOSSLContext] = [:]
    private let lock = NSLock()

    private static let caFile = "capgo_proxy_ca.dat"

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

    func caSecCertificate() -> SecCertificate? {
        guard let certDER = caCertDER else { return nil }
        return SecCertificateCreateWithData(nil, Data(certDER) as CFData)
    }

    /// Whether the CA material has been loaded / generated.
    var isLoaded: Bool {
        return caCertDER != nil && caKey != nil
    }

    func isServerTrustSignedByCA(_ serverTrust: SecTrust) -> Bool {
        guard let caCertificate = caSecCertificate() else { return false }

        SecTrustSetAnchorCertificates(serverTrust, [caCertificate] as CFArray)
        SecTrustSetAnchorCertificatesOnly(serverTrust, true)
        return SecTrustEvaluateWithError(serverTrust, nil)
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
        return appSupport.appendingPathComponent(caFile)
    }

    // MARK: - CA Generation

    private func generateNewCA() throws {
        // Generate RSA 2048 key pair using swift-crypto.
        let rsaKey = try _RSA.Signing.PrivateKey(keySize: .bits2048)
        self.caKey = rsaKey

        // Build a self-signed X.509 v3 CA certificate using swift-certificates.
        let caPrivKey = Certificate.PrivateKey(rsaKey)

        let now = Date()
        let threeYearsLater = Calendar.current.date(byAdding: .year, value: 3, to: now)!

        let caName = try DistinguishedName {
            CommonName("Capgo InAppBrowser Proxy CA")
        }

        let extensions = try Certificate.Extensions {
            Critical(BasicConstraints.isCertificateAuthority(maxPathLength: nil))
            KeyUsage(keyCertSign: true, cRLSign: true)
            SubjectKeyIdentifier(hash: caPrivKey.publicKey)
        }

        let caCert = try Certificate(
            version: .v3,
            serialNumber: Certificate.SerialNumber(),
            publicKey: caPrivKey.publicKey,
            notValidBefore: now,
            notValidAfter: threeYearsLater,
            issuer: caName,
            subject: caName,
            signatureAlgorithm: .sha256WithRSAEncryption,
            extensions: extensions,
            issuerPrivateKey: caPrivKey
        )

        // Serialize the certificate to DER.
        var serializer = DER.Serializer()
        try caCert.serialize(into: &serializer)
        self.caCertDER = serializer.serializedBytes

        print("[CertificateAuthority] Generated new CA cert (RSA 2048, SHA256).")
    }

    // MARK: - Persistence
    //
    // Storage format:
    //   [4 bytes: cert DER length, big-endian] [cert DER bytes] [key PKCS8 DER bytes]
    //
    // We intentionally avoid PKCS12 because `SecPKCS12Export` is macOS-only.

    private func loadFromDisk(_ url: URL) throws {
        let data = try Data(contentsOf: url)
        guard data.count > 4 else {
            throw ProxyError.certGenerationFailed("CA file too small: \(url.lastPathComponent)")
        }

        // Read cert length (4 bytes, big-endian).
        let certLen = Int(
            UInt32(data[0]) << 24 | UInt32(data[1]) << 16 |
            UInt32(data[2]) << 8  | UInt32(data[3])
        )
        guard data.count > 4 + certLen else {
            throw ProxyError.certGenerationFailed("CA file truncated: \(url.lastPathComponent)")
        }

        let certDER = [UInt8](data[4 ..< 4 + certLen])
        let keyDER  = [UInt8](data[(4 + certLen)...])

        // Validate the cert can be parsed.
        _ = try NIOSSLCertificate(bytes: certDER, format: .der)

        // Reconstruct the swift-crypto RSA key from PKCS#8 DER bytes.
        let rsaKey = try _RSA.Signing.PrivateKey(derRepresentation: keyDER)

        self.caCertDER = certDER
        self.caKey = rsaKey

        print("[CertificateAuthority] Loaded CA from \(url.lastPathComponent)")
    }

    private func saveToDisk(_ url: URL) throws {
        guard let certDER = caCertDER, let key = caKey else {
            print("[CertificateAuthority] Skipping disk persistence -- no cert generated yet")
            return
        }

        // Save cert DER + key PKCS8 DER.
        let keyDER = [UInt8](key.pkcs8DERRepresentation)

        var blob = Data()
        let certLen = UInt32(certDER.count).bigEndian
        withUnsafeBytes(of: certLen) { blob.append(contentsOf: $0) }
        blob.append(contentsOf: certDER)
        blob.append(contentsOf: keyDER)

        try blob.write(to: url, options: .atomic)
        try FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: url.path
        )
        print("[CertificateAuthority] Saved CA to \(url.lastPathComponent)")
    }

    // MARK: - Domain Context

    private func buildDomainContext(_ domain: String) throws -> NIOSSLContext {
        guard let caKeyUnwrapped = caKey, let certDER = caCertDER else {
            throw ProxyError.certGenerationFailed("CA not loaded -- cannot generate domain cert for: \(domain)")
        }

        // Generate a new RSA key pair for the leaf certificate.
        let leafKey = try _RSA.Signing.PrivateKey(keySize: .bits2048)
        let leafPrivKey = Certificate.PrivateKey(leafKey)
        let caPrivKey = Certificate.PrivateKey(caKeyUnwrapped)

        let now = Date()
        let thirtyDaysLater = Calendar.current.date(byAdding: .day, value: 30, to: now)!

        let issuerName = try DistinguishedName {
            CommonName("Capgo InAppBrowser Proxy CA")
        }

        let subjectName = try DistinguishedName {
            CommonName(domain)
        }

        // Build the authority key identifier from the CA public key.
        let caSKI = SubjectKeyIdentifier(hash: caPrivKey.publicKey)

        let extensions = try Certificate.Extensions {
            Critical(BasicConstraints.notCertificateAuthority)
            KeyUsage(digitalSignature: true, keyEncipherment: true)
            SubjectAlternativeNames([.dnsName(domain)])
            SubjectKeyIdentifier(hash: leafPrivKey.publicKey)
            AuthorityKeyIdentifier(keyIdentifier: caSKI.keyIdentifier)
        }

        let leafCert = try Certificate(
            version: .v3,
            serialNumber: Certificate.SerialNumber(),
            publicKey: leafPrivKey.publicKey,
            notValidBefore: now,
            notValidAfter: thirtyDaysLater,
            issuer: issuerName,
            subject: subjectName,
            signatureAlgorithm: .sha256WithRSAEncryption,
            extensions: extensions,
            issuerPrivateKey: caPrivKey
        )

        // Serialize the leaf cert to DER.
        var leafSerializer = DER.Serializer()
        try leafCert.serialize(into: &leafSerializer)
        let leafCertDER = leafSerializer.serializedBytes

        // Convert to NIOSSL types.
        let nioLeafCert = try NIOSSLCertificate(bytes: leafCertDER, format: .der)
        let nioCACert = try NIOSSLCertificate(bytes: certDER, format: .der)

        // NIOSSLPrivateKey expects PKCS#8 DER format for RSA keys.
        let leafPKCS8DER = [UInt8](leafKey.pkcs8DERRepresentation)
        let nioLeafKey = try NIOSSLPrivateKey(bytes: leafPKCS8DER, format: .der)

        // Create the TLS server configuration with the leaf cert chain.
        var tlsConfig = TLSConfiguration.makeServerConfiguration(
            certificateChain: [.certificate(nioLeafCert), .certificate(nioCACert)],
            privateKey: .privateKey(nioLeafKey)
        )
        tlsConfig.minimumTLSVersion = .tlsv12

        let sslContext = try NIOSSLContext(configuration: tlsConfig)

        print("[CertificateAuthority] Generated leaf cert for domain: \(domain)")
        return sslContext
    }
}
#endif
