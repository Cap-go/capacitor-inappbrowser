package ee.forgr.capacitor_inappbrowser.proxy;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

public class CertificateAuthority {

    private static final String TAG = "MitmCA";
    private static final String CA_ALIAS = "mitm-ca";
    private static final String KEYSTORE_FILE = "mitm_ca.p12";
    private static final char[] KEYSTORE_PASS = "mitmproxy".toCharArray();

    private X509Certificate caCert;
    private PrivateKey caPrivateKey;
    private final ConcurrentHashMap<String, SSLContext> domainContextCache = new ConcurrentHashMap<>();

    static {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    public CertificateAuthority(Context context) {
        try {
            File ksFile = new File(context.getFilesDir(), KEYSTORE_FILE);
            if (ksFile.exists()) {
                loadExistingCA(ksFile);
            } else {
                generateNewCA(ksFile);
            }
            Log.i(TAG, "CA ready: " + caCert.getSubjectX500Principal());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CA", e);
        }
    }

    private void loadExistingCA(File ksFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, KEYSTORE_PASS);
        }
        caCert = (X509Certificate) ks.getCertificate(CA_ALIAS);
        caPrivateKey = (PrivateKey) ks.getKey(CA_ALIAS, KEYSTORE_PASS);
        Log.i(TAG, "Loaded existing CA from disk");
    }

    private void generateNewCA(File ksFile) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair caKeyPair = kpg.generateKeyPair();

        X500Name issuer = new X500Name("CN=MITM Proxy CA, O=PoC, L=Local");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);
        Date notAfter = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, caKeyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(caKeyPair.getPrivate());

        caCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(builder.build(signer));
        caPrivateKey = caKeyPair.getPrivate();

        // Persist to PKCS12 keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, KEYSTORE_PASS);
        ks.setKeyEntry(CA_ALIAS, caPrivateKey, KEYSTORE_PASS, new X509Certificate[]{caCert});
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, KEYSTORE_PASS);
        }
        Log.i(TAG, "Generated new CA and saved to disk");
    }

    /**
     * Creates an SSLEngine for the proxy->upstream server connection.
     * Uses system default trust (validates real server certificates).
     */
    public SSLEngine createServerSSLEngine(String peerHost, int peerPort) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
            SSLEngine engine = ctx.createSSLEngine(peerHost, peerPort);
            engine.setUseClientMode(true);
            return engine;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create server SSLEngine", e);
        }
    }

    /**
     * Creates an SSLEngine for the proxy->client (WebView) connection,
     * using a dynamically generated cert for the given domain.
     */
    public SSLEngine createClientSSLEngine(String domain) {
        try {
            SSLContext ctx = domainContextCache.computeIfAbsent(domain, this::buildDomainContext);
            SSLEngine engine = ctx.createSSLEngine();
            engine.setUseClientMode(false);
            return engine;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create client SSLEngine for " + domain, e);
        }
    }

    private SSLContext buildDomainContext(String domain) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            KeyPair domainKeyPair = kpg.generateKeyPair();

            X500Name issuer = new X500Name("CN=MITM Proxy CA, O=PoC, L=Local");
            X500Name subject = new X500Name("CN=" + domain);
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);
            Date notAfter = new Date(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, subject, domainKeyPair.getPublic());

            // SAN extension so the cert matches the domain
            GeneralNames sans = new GeneralNames(new GeneralName(GeneralName.dNSName, domain));
            builder.addExtension(Extension.subjectAlternativeName, false, sans);

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .build(caPrivateKey);

            X509Certificate domainCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(builder.build(signer));

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, KEYSTORE_PASS);
            ks.setKeyEntry("domain", domainKeyPair.getPrivate(), KEYSTORE_PASS,
                    new X509Certificate[]{domainCert, caCert});

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KEYSTORE_PASS);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, new SecureRandom());

            Log.d(TAG, "Generated cert for domain: " + domain);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SSL context for " + domain, e);
        }
    }

    public X509Certificate getCaCert() {
        return caCert;
    }

    /**
     * Returns the SHA-256 hex fingerprint of the CA certificate.
     */
    public String getCaCertFingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(caCert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
