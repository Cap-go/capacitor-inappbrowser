package ee.forgr.capacitor_inappbrowser;

import android.webkit.JavascriptInterface;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyBridge {

    public static class StoredRequest {
        public final String method;
        public final String headersJson;
        public final String base64Body;

        public StoredRequest(String method, String headersJson, String base64Body) {
            this.method = method;
            this.headersJson = headersJson;
            this.base64Body = base64Body;
        }
    }

    private final ConcurrentHashMap<String, StoredRequest> storedRequests = new ConcurrentHashMap<>();

    @JavascriptInterface
    public void storeRequest(String requestId, String method, String headersJson, String base64Body) {
        storedRequests.put(requestId, new StoredRequest(method, headersJson, base64Body));
    }

    public StoredRequest getAndRemove(String requestId) {
        return storedRequests.remove(requestId);
    }
}
