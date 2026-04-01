package ee.forgr.capacitor_inappbrowser.proxy;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
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

public class CertificateAuthority {

    private static final String TAG = "MitmCA";
    private static final String CA_FILE = "mitm_ca.bin";

    private X509Certificate caCert;
    private PrivateKey caPrivateKey;
    private final ConcurrentHashMap<String, SSLContext> domainContextCache = new ConcurrentHashMap<>();

    static {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    public CertificateAuthority(Context context) {
        try {
            File caFile = new File(context.getNoBackupFilesDir(), CA_FILE);
            if (!caFile.exists()) {
                generateNewCA(caFile);
            } else {
                try {
                    loadExistingCA(caFile);
                } catch (Exception e) {
                    Log.w(TAG, "Stored CA could not be loaded, generating a new one", e);
                    if (caFile.exists() && !caFile.delete()) {
                        Log.w(TAG, "Failed to delete unreadable CA file: " + caFile.getAbsolutePath());
                    }
                    generateNewCA(caFile);
                }
            }
            Log.i(TAG, "CA ready: " + caCert.getSubjectX500Principal());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CA", e);
        }
    }

    private void loadExistingCA(File caFile) throws Exception {
        try (DataInputStream input = new DataInputStream(new FileInputStream(caFile))) {
            int certLength = input.readInt();
            if (certLength <= 0) {
                throw new IllegalStateException("Invalid certificate length in stored CA");
            }

            byte[] certBytes = input.readNBytes(certLength);
            if (certBytes.length != certLength) {
                throw new IllegalStateException("Stored CA certificate is truncated");
            }

            byte[] keyBytes = input.readAllBytes();
            if (keyBytes.length == 0) {
                throw new IllegalStateException("Stored CA private key is missing");
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            caCert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
            caPrivateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        }
        Log.i(TAG, "Loaded existing CA from disk");
    }

    private void generateNewCA(File caFile) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair caKeyPair = kpg.generateKeyPair();

        X500Name issuer = new X500Name("CN=MITM Proxy CA, O=PoC, L=Local");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);
        Date notAfter = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            issuer,
            caKeyPair.getPublic()
        );

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(caKeyPair.getPrivate());

        caCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
        caPrivateKey = caKeyPair.getPrivate();

        byte[] certBytes = caCert.getEncoded();
        byte[] keyBytes = caPrivateKey.getEncoded();
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(caFile))) {
            output.writeInt(certBytes.length);
            output.write(certBytes);
            output.write(keyBytes);
        }
        Log.i(TAG, "Generated new CA and saved to disk");
    }

    /**
     * Creates an SSLEngine for the proxy->upstream server connection.
     * Uses system default trust (validates real server certificates).
     */
    public SSLEngine createServerSSLEngine(String peerHost, int peerPort) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
            SSLEngine engine = ctx.createSSLEngine(peerHost, peerPort);
            engine.setUseClientMode(true);
            SSLParameters parameters = engine.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(parameters);
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
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                domainKeyPair.getPublic()
            );

            // SAN extension so the cert matches the domain
            GeneralNames sans = new GeneralNames(new GeneralName(GeneralName.dNSName, domain));
            builder.addExtension(Extension.subjectAlternativeName, false, sans);

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(caPrivateKey);

            X509Certificate domainCert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));

            char[] ephemeralPassword = randomPassword();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, ephemeralPassword);
            ks.setKeyEntry("domain", domainKeyPair.getPrivate(), ephemeralPassword, new X509Certificate[] { domainCert, caCert });

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, ephemeralPassword);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, new SecureRandom());

            Log.d(TAG, "Generated cert for domain: " + domain);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SSL context for " + domain, e);
        }
    }

    private char[] randomPassword() {
        byte[] passwordBytes = new byte[24];
        new SecureRandom().nextBytes(passwordBytes);
        return Base64.encodeToString(passwordBytes, Base64.NO_WRAP).toCharArray();
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
