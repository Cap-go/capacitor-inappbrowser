package ee.forgr.capacitor_inappbrowser;

import android.webkit.JavascriptInterface;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
    private volatile BooleanSupplier bridgeAllowed = () -> true;

    public ProxyBridge(String accessToken) {
        this(accessToken, System::currentTimeMillis);
    }

    ProxyBridge(String accessToken, LongSupplier clock) {
        this.accessToken = accessToken;
        this.clock = clock;
    }

    public void setBridgeAllowedSupplier(BooleanSupplier bridgeAllowed) {
        this.bridgeAllowed = bridgeAllowed != null ? bridgeAllowed : () -> true;
    }

    @JavascriptInterface
    public boolean storeRequest(
        String token,
        String requestId,
        String method,
        String headersJson,
        String base64Body,
        String credentialsMode
    ) {
        if (!bridgeAllowed.getAsBoolean()) {
            return false;
        }
        if (token == null || !token.equals(accessToken) || requestId == null || requestId.isEmpty()) {
            return false;
        }

        long now = clock.getAsLong();
        cleanupExpiredRequests(now);
        storedRequests.put(requestId, new StoredRequest(method, headersJson, base64Body, credentialsMode, now));
        storedRequestOrder.add(requestId);
        return true;
    }

    public StoredRequest getAndRemove(String requestId) {
        if (requestId == null) {
            return null;
        }
        // Do not scan storedRequestOrder here: ConcurrentLinkedQueue.remove is O(n) and
        // serializes parallel bridge-backed proxy requests. Stale queue ids are purged lazily
        // in cleanupExpiredRequests().
        return storedRequests.remove(requestId);
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
