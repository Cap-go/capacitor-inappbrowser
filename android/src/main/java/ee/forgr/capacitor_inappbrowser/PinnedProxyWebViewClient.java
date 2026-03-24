package ee.forgr.capacitor_inappbrowser;

import android.content.Context;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.content.ContextCompat;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class PinnedProxyWebViewClient extends WebViewClient {

    public interface RequestObserver {
        void onRequestCaptured(CapturedRequest request);
    }

    public interface ProxyRequestHandler {
        WebResourceResponse onProxyRequest(WebResourceRequest request, CapturedRequest capturedRequest);
    }

    public static class CapturedRequest {

        public final String url;
        public final String method;
        public final String headersJson;
        public final String bodyBase64;
        public final boolean isMainFrame;
        public final boolean hasGesture;
        public final boolean isRedirect;

        public CapturedRequest(
            String url,
            String method,
            String headersJson,
            String bodyBase64,
            boolean isMainFrame,
            boolean hasGesture,
            boolean isRedirect
        ) {
            this.url = url;
            this.method = method;
            this.headersJson = headersJson;
            this.bodyBase64 = bodyBase64;
            this.isMainFrame = isMainFrame;
            this.hasGesture = hasGesture;
            this.isRedirect = isRedirect;
        }
    }

    private static final String TAG = "PinnedProxyWebView";
    private static final String PROXY_PATH = "/_capgo_proxy_";
    private static final String ORIGINAL_URL_QUERY = "u";
    private static final String REQUEST_ID_QUERY = "rid";
    private static final String X509_CERTIFICATE_BUNDLE_KEY = "x509-certificate";

    private static final String EXPECTED_PROXY_SUBJECT =
        "CN=Capgo Local Proxy,O=Capgo,L=Paris,ST=Ile-de-France,C=FR";
    private static final String EXPECTED_PROXY_ISSUER =
        "CN=Capgo Local Proxy CA,O=Capgo,L=Paris,ST=Ile-de-France,C=FR";
    private static final String EXPECTED_PROXY_CERT_SHA256 =
        "8A:45:2F:13:6D:90:34:8C:E9:A7:55:11:1A:F0:6B:42:4E:16:4D:63:A1:8B:0D:53:2C:3E:7F:6A:8D:2B:4F:C1";
    private static final String EXPECTED_PROXY_PUBLIC_KEY_PIN =
        "sha256/8Wl+qspM2qJ0Y9M8wK8sB+nKqfKZp5wA8n4m7pE0V7Q=";

    private final Context appContext;
    private final int proxyPort;
    private final boolean proxyRequestsEnabled;
    private final ProxyBridge proxyBridge;
    private final RequestObserver requestObserver;
    private final ProxyRequestHandler proxyRequestHandler;

    public PinnedProxyWebViewClient(Context appContext, int proxyPort) {
        this(appContext, proxyPort, false, null, null, null);
    }

    public PinnedProxyWebViewClient(Context appContext, int proxyPort, ProxyBridge proxyBridge, RequestObserver requestObserver) {
        this(appContext, proxyPort, false, proxyBridge, requestObserver, null);
    }

    public PinnedProxyWebViewClient(
        Context appContext,
        int proxyPort,
        boolean proxyRequestsEnabled,
        ProxyBridge proxyBridge,
        RequestObserver requestObserver,
        ProxyRequestHandler proxyRequestHandler
    ) {
        this.appContext = appContext.getApplicationContext();
        this.proxyPort = proxyPort;
        this.proxyRequestsEnabled = proxyRequestsEnabled;
        this.proxyBridge = proxyBridge;
        this.requestObserver = requestObserver;
        this.proxyRequestHandler = proxyRequestHandler;
    }

    public void enableLocalProxyOverride(Runnable onApplied) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.i(TAG, "Proxy override is not supported by the current WebView");
            if (onApplied != null) {
                onApplied.run();
            }
            return;
        }

        ProxyConfig proxyConfig = new ProxyConfig.Builder().addProxyRule("127.0.0.1:" + proxyPort).build();
        ProxyController.getInstance().setProxyOverride(proxyConfig, ContextCompat.getMainExecutor(appContext), () -> {
            Log.i(TAG, "Proxy override applied for 127.0.0.1:" + proxyPort);
            if (onApplied != null) {
                onApplied.run();
            }
        });
    }

    public CapturedRequest observeRequest(WebResourceRequest request) {
        CapturedRequest capturedRequest = captureRequest(request, false);
        if (requestObserver != null) {
            requestObserver.onRequestCaptured(capturedRequest);
        }
        Log.d(TAG, "Captured " + capturedRequest.method + " " + capturedRequest.url);
        return capturedRequest;
    }

    public void handlePinnedSslError(WebView view, SslErrorHandler handler, SslError error) {
        onReceivedSslError(view, handler, error);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String scheme = request.getUrl().getScheme();
        if (scheme == null) {
            return null;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.US);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            return null;
        }

        observeRequest(request);

        if (!proxyRequestsEnabled || proxyRequestHandler == null) {
            return null;
        }

        CapturedRequest capturedRequest = captureRequest(request, true);
        return proxyRequestHandler.onProxyRequest(request, capturedRequest);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        X509Certificate certificate = toX509Certificate(error.getCertificate());
        if (certificate == null) {
            Log.e(TAG, "Failed to parse SSL certificate for " + error.getUrl());
            handler.cancel();
            return;
        }

        try {
            String subject = certificate.getSubjectX500Principal().getName();
            String issuer = certificate.getIssuerX500Principal().getName();
            String certFingerprint = sha256Fingerprint(certificate.getEncoded());
            String publicKeyPin = sha256Pin(certificate.getPublicKey().getEncoded());

            boolean exactMatch =
                EXPECTED_PROXY_SUBJECT.equals(subject) &&
                EXPECTED_PROXY_ISSUER.equals(issuer) &&
                EXPECTED_PROXY_CERT_SHA256.equals(certFingerprint) &&
                EXPECTED_PROXY_PUBLIC_KEY_PIN.equals(publicKeyPin);

            if (exactMatch) {
                Log.i(TAG, "Pinned proxy certificate accepted for " + error.getUrl());
                handler.proceed();
            } else {
                Log.e(
                    TAG,
                    "Pinned proxy certificate mismatch for " + error.getUrl() +
                    ": subject=" + subject +
                    " issuer=" + issuer +
                    " fingerprint=" + certFingerprint +
                    " pin=" + publicKeyPin
                );
                handler.cancel();
            }
        } catch (Exception exception) {
            Log.e(TAG, "Unable to evaluate pinned proxy certificate", exception);
            handler.cancel();
        }
    }

    private CapturedRequest captureRequest(WebResourceRequest request, boolean consumeStoredRequest) {
        Uri interceptedUri = request.getUrl();
        boolean isBridgeRequest = PROXY_PATH.equals(interceptedUri.getPath());
        String bridgeRequestId = interceptedUri.getQueryParameter(REQUEST_ID_QUERY);

        ProxyBridge.StoredRequest storedRequest = null;
        if (isBridgeRequest && bridgeRequestId != null && !bridgeRequestId.isBlank() && proxyBridge != null) {
            storedRequest = consumeStoredRequest ? proxyBridge.getAndRemove(bridgeRequestId) : proxyBridge.peek(bridgeRequestId);
        }

        String originalUrl = isBridgeRequest
            ? interceptedUri.getQueryParameter(ORIGINAL_URL_QUERY)
            : interceptedUri.toString();
        if (originalUrl == null || originalUrl.isBlank()) {
            originalUrl = interceptedUri.toString();
        }

        String method = storedRequest != null ? storedRequest.method : request.getMethod();
        String headersJson = storedRequest != null ? storedRequest.headersJson : headersToJson(request.getRequestHeaders());
        String bodyBase64 = storedRequest != null && storedRequest.base64Body != null && !storedRequest.base64Body.isBlank()
            ? storedRequest.base64Body
            : null;

        return new CapturedRequest(
            originalUrl,
            method,
            headersJson,
            bodyBase64,
            request.isForMainFrame(),
            request.hasGesture(),
            request.isRedirect()
        );
    }

    private String headersToJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }

        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            try {
                json.put(entry.getKey(), entry.getValue());
            } catch (JSONException exception) {
                Log.w(TAG, "Skipping malformed request header " + entry.getKey(), exception);
            }
        }
        return json.toString();
    }

    private X509Certificate toX509Certificate(SslCertificate sslCertificate) {
        if (sslCertificate == null) {
            return null;
        }

        Bundle state = SslCertificate.saveState(sslCertificate);
        if (state == null) {
            return null;
        }

        byte[] encodedCertificate = state.getByteArray(X509_CERTIFICATE_BUNDLE_KEY);
        if (encodedCertificate == null) {
            return null;
        }

        try {
            return (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(encodedCertificate));
        } catch (Exception exception) {
            Log.e(TAG, "Unable to decode SSL certificate", exception);
            return null;
        }
    }

    private String sha256Fingerprint(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < digest.length; index++) {
            if (index > 0) {
                builder.append(':');
            }
            builder.append(String.format(Locale.US, "%02X", digest[index]));
        }
        return builder.toString();
    }

    private String sha256Pin(byte[] publicKeyBytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes);
        return "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP);
    }
}
