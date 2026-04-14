package ee.forgr.capacitor_inappbrowser;

import android.webkit.JavascriptInterface;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class ProxyBridge {

    static final int MAX_EXPECTED_STORED_REQUESTS = 256;
    static final long STORED_REQUEST_TTL_MS = TimeUnit.MINUTES.toMillis(2);

    public static class StoredRequest {

        public final String method;
        public final String headersJson;
        public final String base64Body;
        public final String credentialsMode;
        final long storedAtMs;

        public StoredRequest(String method, String headersJson, String base64Body, String credentialsMode, long storedAtMs) {
            this.method = method != null ? method : "";
            this.headersJson = headersJson != null ? headersJson : "{}";
            this.base64Body = base64Body != null ? base64Body : "";
            this.credentialsMode = credentialsMode != null ? credentialsMode : "same-origin";
            this.storedAtMs = storedAtMs;
        }
    }

    private final ConcurrentHashMap<String, StoredRequest> storedRequests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> storedRequestOrder = new ConcurrentLinkedQueue<>();
    private final String accessToken;
    private final LongSupplier clock;

    public ProxyBridge(String accessToken) {
        this(accessToken, System::currentTimeMillis);
    }

    ProxyBridge(String accessToken, LongSupplier clock) {
        this.accessToken = accessToken;
        this.clock = clock;
    }

    @JavascriptInterface
    public void storeRequest(String token, String requestId, String method, String headersJson, String base64Body, String credentialsMode) {
        if (token == null || !token.equals(accessToken) || requestId == null || requestId.isEmpty()) {
            return;
        }

        long now = clock.getAsLong();
        cleanupExpiredRequests(now);
        storedRequests.put(requestId, new StoredRequest(method, headersJson, base64Body, credentialsMode, now));
        storedRequestOrder.add(requestId);
    }

    public StoredRequest getAndRemove(String requestId) {
        if (requestId == null) {
            return null;
        }
        StoredRequest storedRequest = storedRequests.remove(requestId);
        if (storedRequest != null) {
            storedRequestOrder.remove(requestId);
        }
        return storedRequest;
    }

    private void cleanupExpiredRequests(long now) {
        while (true) {
            String oldestRequestId = storedRequestOrder.peek();
            if (oldestRequestId == null) {
                return;
            }

            StoredRequest oldestRequest = storedRequests.get(oldestRequestId);
            if (oldestRequest == null) {
                storedRequestOrder.poll();
                continue;
            }

            if ((now - oldestRequest.storedAtMs) < STORED_REQUEST_TTL_MS) {
                return;
            }

            if (storedRequests.remove(oldestRequestId, oldestRequest)) {
                storedRequestOrder.poll();
                continue;
            }

            storedRequestOrder.poll();
        }
    }
}
