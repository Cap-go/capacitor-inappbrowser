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

    public ProxyBridge(String accessToken) {
        this.accessToken = accessToken;
    }

    @JavascriptInterface
    public void storeRequest(String token, String requestId, String method, String headersJson, String base64Body) {
        if (token == null || !token.equals(accessToken) || requestId == null || requestId.isEmpty()) {
            return;
        }
        if (storedRequests.size() >= MAX_STORED_REQUESTS) {
            var iterator = storedRequests.keys().asIterator();
            if (iterator.hasNext()) {
                storedRequests.remove(iterator.next());
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
}
