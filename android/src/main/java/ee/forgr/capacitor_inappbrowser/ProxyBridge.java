package ee.forgr.capacitor_inappbrowser;

import android.webkit.JavascriptInterface;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ProxyBridge {

    private static final int MAX_STORED_REQUESTS = 256;

    public static class StoredRequest {

        public final String method;
        public final String headersJson;
        public final String base64Body;
        public final String credentialsMode;

        public StoredRequest(String method, String headersJson, String base64Body, String credentialsMode) {
            this.method = method != null ? method : "";
            this.headersJson = headersJson != null ? headersJson : "{}";
            this.base64Body = base64Body != null ? base64Body : "";
            this.credentialsMode = credentialsMode != null ? credentialsMode : "same-origin";
        }
    }

    private final ConcurrentHashMap<String, StoredRequest> storedRequests = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> storedRequestOrder = new ConcurrentLinkedQueue<>();
    private final String accessToken;

    public ProxyBridge(String accessToken) {
        this.accessToken = accessToken;
    }

    @JavascriptInterface
    public void storeRequest(String token, String requestId, String method, String headersJson, String base64Body, String credentialsMode) {
        if (token == null || !token.equals(accessToken) || requestId == null || requestId.isEmpty()) {
            return;
        }
        while (storedRequests.size() >= MAX_STORED_REQUESTS) {
            String oldestRequestId = storedRequestOrder.poll();
            if (oldestRequestId == null) {
                return;
            }
            storedRequests.remove(oldestRequestId);
        }
        storedRequests.put(requestId, new StoredRequest(method, headersJson, base64Body, credentialsMode));
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
}
