package ee.forgr.capacitor_inappbrowser;

import android.webkit.JavascriptInterface;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyBridge {

    private static final int MAX_STORED_REQUESTS = 256;

    public static class StoredRequest {

        public final String method;
        public final String headersJson;
        public final String base64Body;

        public StoredRequest(String method, String headersJson, String base64Body) {
            this.method = method != null ? method : "";
            this.headersJson = headersJson != null ? headersJson : "{}";
            this.base64Body = base64Body != null ? base64Body : "";
        }
    }

    private final ConcurrentHashMap<String, StoredRequest> storedRequests = new ConcurrentHashMap<>();
    private final String accessToken;

    /**
     * @param accessToken Random token generated per webview instance.
     *     The injected proxy-bridge script receives this token and must
     *     pass it on every storeRequest call. Page JS that doesn't know
     *     the token cannot abuse the interface.
     */
    public ProxyBridge(String accessToken) {
        this.accessToken = accessToken;
    }

    @JavascriptInterface
    public void storeRequest(String token, String requestId, String method, String headersJson, String base64Body) {
        if (token == null || !token.equals(accessToken)) {
            return;
        }
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        if (storedRequests.size() >= MAX_STORED_REQUESTS) {
            // Remove an arbitrary entry to prevent unbounded growth
            var it = storedRequests.keys().asIterator();
            if (it.hasNext()) {
                storedRequests.remove(it.next());
            }
        }
        storedRequests.put(requestId, new StoredRequest(method, headersJson, base64Body));
    }

    public StoredRequest getAndRemove(String requestId) {
        if (requestId == null) {
            return null;
        }
        return storedRequests.remove(requestId);
    }

    public StoredRequest peek(String requestId) {
        if (requestId == null) {
            return null;
        }
        return storedRequests.get(requestId);
    }
}
