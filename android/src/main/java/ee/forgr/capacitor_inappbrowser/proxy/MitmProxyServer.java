package ee.forgr.capacitor_inappbrowser.proxy;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import ee.forgr.capacitor_inappbrowser.proxy.ProxyRuleMatcher.NativeProxyRule;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.MitmManager;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

public class MitmProxyServer {

    private static final String TAG = "MitmProxyServer";
    private static final int INTERCEPT_TIMEOUT_SECONDS = 10;
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Callback interface for firing intercept events to the Capacitor layer.
     * Implementations bridge between Netty proxy threads and the JS plugin.
     */
    public interface ProxyEventListener {
        /**
         * Called when an outgoing request matches a rule with request interception.
         *
         * @param requestId   unique identifier for this request
         * @param ruleName    the name of the matched rule
         * @param requestData map containing url, method, headers, and optionally body (base64)
         * @return a future that resolves with modification instructions, or null/empty to pass through
         */
        CompletableFuture<Map<String, Object>> onRequestIntercept(String requestId, String ruleName, Map<String, Object> requestData);

        /**
         * Called when a response arrives for a request that matched a rule with response interception.
         *
         * @param requestId    unique identifier for the original request
         * @param ruleName     the name of the matched rule
         * @param responseData map containing status, headers, and optionally body (base64)
         * @return a future that resolves with modification instructions, or null/empty to pass through
         */
        CompletableFuture<Map<String, Object>> onResponseIntercept(String requestId, String ruleName, Map<String, Object> responseData);
    }

    private final CertificateAuthority ca;
    private final ProxyRuleMatcher ruleMatcher;
    private final ProxyEventListener listener;
    private HttpProxyServer server;
    private int port;

    public MitmProxyServer(Context context, ProxyRuleMatcher ruleMatcher, ProxyEventListener listener) {
        this.ca = new CertificateAuthority(context);
        this.ruleMatcher = ruleMatcher;
        this.listener = listener;
    }

    /**
     * Starts the proxy server on a random available port on the loopback interface.
     *
     * @return the port the server is listening on
     */
    public int start() {
        server = DefaultHttpProxyServer.bootstrap()
            .withAddress(new InetSocketAddress("127.0.0.1", 0))
            .withManInTheMiddle(new ProxyMitmManager())
            .withFiltersSource(new InterceptFiltersSource())
            .withAllowRequestToOriginServer(true)
            .start();
        port = server.getListenAddress().getPort();
        Log.i(TAG, "MITM proxy started on 127.0.0.1:" + port);
        return port;
    }

    /**
     * Stops the proxy server and releases resources.
     */
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
            Log.i(TAG, "MITM proxy stopped");
        }
    }

    public int getPort() {
        return port;
    }

    public CertificateAuthority getCa() {
        return ca;
    }

    // ---- MitmManager: delegates to CertificateAuthority for SSL ----

    private class ProxyMitmManager implements MitmManager {

        @Override
        public SSLEngine serverSslEngine(String peerHost, int peerPort) {
            return ca.createServerSSLEngine(peerHost, peerPort);
        }

        @Override
        public SSLEngine serverSslEngine() {
            return ca.createServerSSLEngine("localhost", 443);
        }

        @Override
        public SSLEngine clientSslEngineFor(HttpRequest httpRequest, SSLSession serverSslSession) {
            String host = extractHost(httpRequest);
            return ca.createClientSSLEngine(host);
        }

        private String extractHost(HttpRequest request) {
            String host = request.headers().get(HttpHeaderNames.HOST);
            if (host == null) {
                host = request.uri();
            }
            int colonIdx = host.indexOf(':');
            if (colonIdx > 0) {
                host = host.substring(0, colonIdx);
            }
            return host;
        }
    }

    // ---- HttpFiltersSource: creates a filter per connection ----

    private class InterceptFiltersSource extends HttpFiltersSourceAdapter {

        @Override
        public HttpFilters filterRequest(HttpRequest originalRequest) {
            return new InterceptFilter(originalRequest);
        }

        @Override
        public int getMaximumRequestBufferSizeInBytes() {
            return MAX_BUFFER_SIZE;
        }

        @Override
        public int getMaximumResponseBufferSizeInBytes() {
            return MAX_BUFFER_SIZE;
        }
    }

    // ---- HttpFilters: intercept requests and responses based on rules ----

    private class InterceptFilter extends HttpFiltersAdapter {

        private NativeProxyRule requestRule;
        private NativeProxyRule responseRule;
        private String requestId;
        private String requestUrl;

        InterceptFilter(HttpRequest originalRequest) {
            super(originalRequest);
        }

        @Override
        public HttpResponse clientToProxyRequest(HttpObject httpObject) {
            if (!(httpObject instanceof FullHttpRequest)) {
                return null;
            }

            FullHttpRequest fullRequest = (FullHttpRequest) httpObject;
            String url = fullRequest.uri();
            String method = fullRequest.method().name();

            requestRule = ruleMatcher.matchRequest(url, method);
            responseRule = ruleMatcher.matchResponse(url, method);
            if (requestRule == null && responseRule == null) {
                return null; // No match, pass through
            }

            requestId = UUID.randomUUID().toString();
            requestUrl = url;

            if (requestRule == null) {
                return null;
            }

            Log.d(TAG, "Request matched rule " + requestRule.ruleName + ": " + method + " " + url);

            // Build request data map for the event
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("url", fullRequest.uri());
            requestData.put("method", fullRequest.method().name());
            requestData.put("headers", headersToMap(fullRequest.headers()));
            if (requestRule.includeBody && fullRequest.content().readableBytes() > 0) {
                byte[] bodyBytes = new byte[fullRequest.content().readableBytes()];
                fullRequest.content().getBytes(0, bodyBytes);
                requestData.put("body", Base64.encodeToString(bodyBytes, Base64.NO_WRAP));
            }

            // Fire event and wait for response
            CompletableFuture<Map<String, Object>> requestFuture = listener.onRequestIntercept(
                requestId,
                requestRule.ruleName,
                requestData
            );
            try {
                Map<String, Object> modifications = requestFuture.get(INTERCEPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                applyRequestModifications(fullRequest, modifications);
                requestUrl = fullRequest.uri();
                responseRule = ruleMatcher.matchResponse(requestUrl, fullRequest.method().name());
            } catch (TimeoutException e) {
                Log.w(TAG, "Request intercept timed out for " + requestId + ", passing through unmodified");
                futureCompleteSilently(requestId, requestFuture);
            } catch (Exception e) {
                Log.e(TAG, "Error during request intercept for " + requestId, e);
            }

            return null; // Continue with (possibly modified) request
        }

        @Override
        public HttpObject serverToProxyResponse(HttpObject httpObject) {
            if (responseRule == null) {
                return httpObject;
            }

            if (!(httpObject instanceof FullHttpResponse)) {
                return httpObject;
            }

            FullHttpResponse fullResponse = (FullHttpResponse) httpObject;

            Log.d(TAG, "Response matched rule " + responseRule.ruleName + " for request " + requestId);

            // Build response data map for the event
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("url", requestUrl);
            responseData.put("status", fullResponse.status().code());
            responseData.put("headers", headersToMap(fullResponse.headers()));
            if (responseRule.includeBody && fullResponse.content().readableBytes() > 0) {
                byte[] bodyBytes = new byte[fullResponse.content().readableBytes()];
                fullResponse.content().getBytes(0, bodyBytes);
                responseData.put("body", Base64.encodeToString(bodyBytes, Base64.NO_WRAP));
            }

            // Fire event and wait for response
            CompletableFuture<Map<String, Object>> responseFuture = listener.onResponseIntercept(
                requestId,
                responseRule.ruleName,
                responseData
            );
            try {
                Map<String, Object> modifications = responseFuture.get(INTERCEPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                applyResponseModifications(fullResponse, modifications);
            } catch (TimeoutException e) {
                Log.w(TAG, "Response intercept timed out for " + requestId + ", passing through unmodified");
                futureCompleteSilently(requestId, responseFuture);
            } catch (Exception e) {
                Log.e(TAG, "Error during response intercept for " + requestId, e);
            }

            return fullResponse;
        }

        @SuppressWarnings("unchecked")
        private void applyRequestModifications(FullHttpRequest fullReq, Map<String, Object> modifications) {
            if (modifications == null || modifications.isEmpty()) {
                return;
            }

            if (modifications.containsKey("url")) {
                String updatedUrl = (String) modifications.get("url");
                fullReq.setUri(updatedUrl);
                try {
                    java.net.URI rewrittenUri = java.net.URI.create(updatedUrl);
                    if (rewrittenUri.getHost() != null) {
                        String authority = rewrittenUri.getAuthority();
                        if (authority != null) {
                            fullReq.headers().set(HttpHeaderNames.HOST, authority);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Ignoring invalid rewritten URL for " + requestId + ": " + updatedUrl, e);
                }
            }
            if (modifications.containsKey("method")) {
                fullReq.setMethod(HttpMethod.valueOf((String) modifications.get("method")));
            }
            if (modifications.containsKey("headers")) {
                fullReq.headers().clear();
                applyHeaders(fullReq.headers(), (Map<String, Object>) modifications.get("headers"));
            }
            if (modifications.containsKey("body")) {
                byte[] newBody = Base64.decode((String) modifications.get("body"), Base64.DEFAULT);
                fullReq.content().clear().writeBytes(newBody);
                fullReq.headers().set(HttpHeaderNames.CONTENT_LENGTH, newBody.length);
            }

            Log.d(TAG, "Applied request modifications for " + requestId);
        }

        @SuppressWarnings("unchecked")
        private void applyResponseModifications(FullHttpResponse fullResp, Map<String, Object> modifications) {
            if (modifications == null || modifications.isEmpty()) {
                return;
            }

            if (modifications.containsKey("status")) {
                int statusCode = ((Number) modifications.get("status")).intValue();
                fullResp.setStatus(HttpResponseStatus.valueOf(statusCode));
            }
            if (modifications.containsKey("headers")) {
                fullResp.headers().clear();
                applyHeaders(fullResp.headers(), (Map<String, Object>) modifications.get("headers"));
            }
            if (modifications.containsKey("body")) {
                byte[] newBody = Base64.decode((String) modifications.get("body"), Base64.DEFAULT);
                fullResp.content().clear().writeBytes(newBody);
                fullResp.headers().set(HttpHeaderNames.CONTENT_LENGTH, newBody.length);
            }

            Log.d(TAG, "Applied response modifications for " + requestId);
        }
    }

    // ---- Utility ----

    private static Map<String, Object> headersToMap(io.netty.handler.codec.http.HttpHeaders headers) {
        Map<String, List<String>> grouped = new HashMap<>();
        for (Map.Entry<String, String> entry : headers) {
            grouped.computeIfAbsent(entry.getKey(), (ignored) -> new java.util.ArrayList<>()).add(entry.getValue());
        }

        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> values = entry.getValue();
            map.put(entry.getKey(), values.size() == 1 ? values.get(0) : values);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static void applyHeaders(io.netty.handler.codec.http.HttpHeaders target, Map<String, Object> headers) {
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Iterable<?>) {
                for (Object item : (Iterable<Object>) value) {
                    if (item != null) {
                        target.add(entry.getKey(), String.valueOf(item));
                    }
                }
            } else if (value != null) {
                target.add(entry.getKey(), String.valueOf(value));
            }
        }
    }

    private void futureCompleteSilently(String requestId, CompletableFuture<Map<String, Object>> future) {
        if (future != null) {
            future.complete(null);
            Log.d(TAG, "Completed timed out intercept future for " + requestId);
        }
    }
}
