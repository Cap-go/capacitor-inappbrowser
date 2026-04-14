package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;

public class ProxyRequestSupportTest {

    @Test
    public void shouldInjectBridgeWhenRulesEnableNativeProxy() {
        Options options = new Options();
        options.setOutboundProxyRules(
            List.of(new NativeProxyRule(null, null, null, null, null, null, null, null, false, NativeProxyRule.Action.DELEGATE_TO_JS))
        );

        assertTrue(ProxyRequestSupport.shouldInjectBridge(options));
    }

    @Test
    public void supportsNativeHttpRequestOnlyForHttpSchemes() throws Exception {
        assertTrue(ProxyRequestSupport.supportsNativeHttpRequest(new URL("https://example.com/path")));
        assertTrue(ProxyRequestSupport.supportsNativeHttpRequest(new URL("http://example.com/path")));
        assertFalse(ProxyRequestSupport.supportsNativeHttpRequest(new URL("file:///tmp/index.html")));
    }

    @Test
    public void resolveRedirectUrlHandlesRelativeLocations() {
        String redirectUrl = ProxyRequestSupport.resolveRedirectUrl("https://example.com/login", 302, Map.of("Location", "/dashboard"));

        assertEquals("https://example.com/dashboard", redirectUrl);
        assertNull(ProxyRequestSupport.resolveRedirectUrl("https://example.com/login", 200, Map.of("Location", "/dashboard")));
    }

    @Test
    public void resolveRedirectMethodPreservesNonPostMethodsOnLegacyRedirects() {
        assertEquals("GET", ProxyRequestSupport.resolveRedirectMethod("POST", 302));
        assertEquals("PUT", ProxyRequestSupport.resolveRedirectMethod("PUT", 302));
        assertEquals("DELETE", ProxyRequestSupport.resolveRedirectMethod("DELETE", 301));
        assertEquals("POST", ProxyRequestSupport.resolveRedirectMethod("POST", 307));
        assertFalse(ProxyRequestSupport.shouldPreserveRequestBodyOnRedirect("POST", 302));
        assertTrue(ProxyRequestSupport.shouldPreserveRequestBodyOnRedirect("PUT", 302));
        assertTrue(ProxyRequestSupport.shouldPreserveRequestBodyOnRedirect("POST", 307));
    }

    @Test
    public void resolveOverrideUrlHandlesRelativeUrls() {
        assertEquals("https://example.com/api/v2", ProxyRequestSupport.resolveOverrideUrl("https://example.com/api/v1", "/api/v2"));
        assertEquals(
            "https://cdn.example.net/file.js",
            ProxyRequestSupport.resolveOverrideUrl("https://example.com/api/v1", "https://cdn.example.net/file.js")
        );
    }

    @Test
    public void resolveBootstrapBaseUrlPrefersEffectiveReplayUrl() {
        assertEquals(
            "https://accounts.example.com/consent",
            ProxyRequestSupport.resolveBootstrapBaseUrl("https://example.com/login", "https://accounts.example.com/consent")
        );
        assertEquals("https://example.com/login", ProxyRequestSupport.resolveBootstrapBaseUrl("https://example.com/login", ""));
        assertEquals("https://example.com/login", ProxyRequestSupport.resolveBootstrapBaseUrl("https://example.com/login", null));
    }

    @Test
    public void prepareRedirectHeadersDropsEntityHeadersWhenBodyChanges() {
        Map<String, String> redirectHeaders = ProxyRequestSupport.prepareRedirectHeaders(
            Map.of("Content-Type", "application/json", "Content-Length", "10", "Accept", "application/json"),
            false,
            "https://example.com/login",
            "https://example.com/dashboard"
        );

        assertFalse(redirectHeaders.containsKey("Content-Type"));
        assertFalse(redirectHeaders.containsKey("Content-Length"));
        assertEquals("application/json", redirectHeaders.get("Accept"));
    }

    @Test
    public void prepareRedirectHeadersDropsOriginBoundHeadersAcrossOrigins() {
        Map<String, String> redirectHeaders = ProxyRequestSupport.prepareRedirectHeaders(
            Map.of(
                "Authorization",
                "Bearer abc",
                "Cookie",
                "session=123",
                "Origin",
                "https://example.com",
                "Referer",
                "https://example.com/login",
                "Accept",
                "application/json"
            ),
            true,
            "https://example.com/login",
            "https://accounts.example.net/oauth"
        );

        assertFalse(redirectHeaders.containsKey("Authorization"));
        assertFalse(redirectHeaders.containsKey("Cookie"));
        assertFalse(redirectHeaders.containsKey("Origin"));
        assertFalse(redirectHeaders.containsKey("Referer"));
        assertEquals("application/json", redirectHeaders.get("Accept"));
    }

    @Test
    public void prepareOverrideHeadersDropsOriginBoundHeadersAcrossOrigins() {
        Map<String, String> overrideHeaders = ProxyRequestSupport.prepareOverrideHeaders(
            Map.of(
                "Authorization",
                "Bearer abc",
                "Cookie",
                "session=123",
                "Origin",
                "https://example.com",
                "Referer",
                "https://example.com/login",
                "Accept",
                "application/json"
            ),
            "https://example.com/login",
            "https://accounts.example.net/oauth"
        );

        assertFalse(overrideHeaders.containsKey("Authorization"));
        assertFalse(overrideHeaders.containsKey("Cookie"));
        assertFalse(overrideHeaders.containsKey("Origin"));
        assertFalse(overrideHeaders.containsKey("Referer"));
        assertEquals("application/json", overrideHeaders.get("Accept"));
    }

    @Test
    public void resolveOverrideBodyClearsExplicitNullOverride() {
        assertEquals("", ProxyRequestSupport.resolveOverrideBody("aGVsbG8=", "POST", true, null));
    }

    @Test
    public void resolveOverrideBodyClearsBodyForGetAndHeadOverrides() {
        assertEquals("", ProxyRequestSupport.resolveOverrideBody("cHJldmlvdXM=", "GET", true, "aGVsbG8="));
        assertEquals("", ProxyRequestSupport.resolveOverrideBody("cHJldmlvdXM=", "HEAD", true, "aGVsbG8="));
    }

    @Test
    public void resolveOverrideBodyPreservesExistingPayloadWhenMissing() {
        assertEquals("cHJldmlvdXM=", ProxyRequestSupport.resolveOverrideBody("cHJldmlvdXM=", "POST", false, null));
    }

    @Test
    public void normalizeOverrideMethodUppercasesValidMethods() {
        assertEquals("POST", ProxyRequestSupport.normalizeOverrideMethod("post"));
        assertEquals("PATCH", ProxyRequestSupport.normalizeOverrideMethod(" patch "));
        assertEquals("GET", ProxyRequestSupport.normalizeOverrideMethod(null));
    }

    @Test
    public void decodeBase64BodyDecodesValidPayloads() throws Exception {
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), ProxyRequestSupport.decodeBase64Body("aGVsbG8="));
        assertArrayEquals(new byte[0], ProxyRequestSupport.decodeBase64Body(""));
    }

    @Test
    public void decodeBase64BodyRejectsInvalidPayloads() {
        try {
            ProxyRequestSupport.decodeBase64Body("%not-base64%");
            fail("Expected IOException for invalid base64 request body");
        } catch (IOException error) {
            assertTrue(error.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void usesLegacyJsProxyModeForRegexPatternWithoutNativeRules() {
        Options options = new Options();
        options.setProxyRequestsPattern(Pattern.compile("grailed"));

        assertTrue(ProxyRequestSupport.usesLegacyJsProxyMode(options));
    }

    @Test
    public void compileProxyRequestsPatternIgnoresNonStringValues() {
        assertNull(ProxyRequestSupport.compileProxyRequestsPattern(Boolean.TRUE));
        assertNull(ProxyRequestSupport.compileProxyRequestsPattern(""));
        assertEquals("grailed", ProxyRequestSupport.compileProxyRequestsPattern("grailed").pattern());
    }

    @Test
    public void shouldDelegateLegacyJsProxyRequestHonorsRegexMatches() {
        Options options = new Options();
        options.setProxyRequestsPattern(Pattern.compile("api\\.example\\.com"));

        assertTrue(ProxyRequestSupport.shouldDelegateLegacyJsProxyRequest(options, "https://api.example.com/login"));
        assertFalse(ProxyRequestSupport.shouldDelegateLegacyJsProxyRequest(options, "https://cdn.example.com/script.js"));
    }

    @Test
    public void shouldDelegateLegacyJsProxyRequestDisablesLegacyModeWhenNativeRulesExist() {
        Options options = new Options();
        options.setProxyRequestsPattern(Pattern.compile("api\\.example\\.com"));
        options.setOutboundProxyRules(
            List.of(new NativeProxyRule(null, null, null, null, null, null, null, null, false, NativeProxyRule.Action.CONTINUE))
        );

        assertFalse(ProxyRequestSupport.shouldDelegateLegacyJsProxyRequest(options, "https://api.example.com/login"));
    }

    @Test
    public void shouldHandleNonBridgeRequestKeepsNativeRulesActiveWhenLegacyRegexMisses() {
        Options options = new Options();
        options.setProxyRequestsPattern(Pattern.compile("api\\.example\\.com"));
        options.setOutboundProxyRules(
            List.of(new NativeProxyRule(null, null, null, null, null, null, null, null, false, NativeProxyRule.Action.CANCEL))
        );

        assertTrue(ProxyRequestSupport.shouldHandleNonBridgeRequest(options, "https://cdn.example.com/script.js"));
        assertFalse(ProxyRequestSupport.shouldHandleNonBridgeRequest(new Options(), "https://cdn.example.com/script.js"));
    }

    @Test
    public void isBridgeMarkerRequestUrlMatchesOnlyDedicatedRoute() {
        assertTrue(
            ProxyRequestSupport.isBridgeMarkerRequestUrl("https://example.com/_capgo_proxy_?u=https%3A%2F%2Fapi.example.com%2Flogin&rid=1")
        );
        assertFalse(ProxyRequestSupport.isBridgeMarkerRequestUrl("https://example.com/api/_capgo_proxy_?foo=bar"));
        assertFalse(ProxyRequestSupport.isBridgeMarkerRequestUrl("https://example.com/_capgo_proxy_"));
    }

    @Test
    public void mergeRequestHeadersPreservesNativeHeaders() throws Exception {
        Map<String, String> headers = ProxyRequestSupport.mergeRequestHeaders(
            Map.of("Cookie", "session=abc", "User-Agent", "Native"),
            "{\"X-Test\":\"1\",\"User-Agent\":\"Spoofed\"}"
        );

        assertEquals("session=abc", headers.get("Cookie"));
        assertEquals("Spoofed", headers.get("User-Agent"));
        assertEquals("1", headers.get("X-Test"));
    }

    @Test
    public void mergeRequestHeadersOverridesCaseInsensitiveMatches() throws Exception {
        Map<String, String> headers = ProxyRequestSupport.mergeRequestHeaders(
            Map.of("User-Agent", "Native"),
            "{\"user-agent\":\"Spoofed\"}"
        );

        assertFalse(headers.containsKey("User-Agent"));
        assertEquals("Spoofed", headers.get("user-agent"));
    }

    @Test
    public void extractSafeMarkerHeadersDropsOriginBoundHeaders() {
        Map<String, String> markerHeaders = new LinkedHashMap<>();
        markerHeaders.put("Accept", "application/json");
        markerHeaders.put("Cookie", "session=marker");
        markerHeaders.put("Origin", "https://marker.example");
        markerHeaders.put("Referer", "https://marker.example/page");
        markerHeaders.put("User-Agent", "MarkerAgent");

        Map<String, String> headers = ProxyRequestSupport.extractSafeMarkerHeaders(markerHeaders);

        assertEquals("application/json", headers.get("Accept"));
        assertEquals("MarkerAgent", headers.get("User-Agent"));
        assertFalse(headers.containsKey("Cookie"));
        assertFalse(headers.containsKey("Origin"));
        assertFalse(headers.containsKey("Referer"));
    }

    @Test
    public void mergeMissingHeadersOnlyBackfillsAbsentKeys() {
        Map<String, String> headers = ProxyRequestSupport.mergeMissingHeaders(
            Map.of("Accept", "application/json"),
            Map.of("accept", "text/plain", "User-Agent", "MarkerAgent")
        );

        assertEquals("application/json", headers.get("Accept"));
        assertEquals("MarkerAgent", headers.get("User-Agent"));
        assertFalse(headers.containsKey("accept"));
    }

    @Test
    public void shouldInjectCookiesHonorsCredentialsMode() {
        assertTrue(
            ProxyRequestSupport.shouldInjectCookies(
                "include",
                "https://app.example.com/account",
                "https://api.example.net/login",
                new LinkedHashMap<>()
            )
        );
        assertTrue(
            ProxyRequestSupport.shouldInjectCookies(
                "same-origin",
                "https://app.example.com/account",
                "https://app.example.com/api/me",
                new LinkedHashMap<>()
            )
        );
        assertFalse(
            ProxyRequestSupport.shouldInjectCookies(
                "same-origin",
                "https://app.example.com/account",
                "https://api.example.net/login",
                new LinkedHashMap<>()
            )
        );
        assertFalse(
            ProxyRequestSupport.shouldInjectCookies(
                "omit",
                "https://app.example.com/account",
                "https://app.example.com/api/me",
                new LinkedHashMap<>()
            )
        );
        assertFalse(
            ProxyRequestSupport.shouldInjectCookies(
                "include",
                "https://app.example.com/account",
                "https://app.example.com/api/me",
                new LinkedHashMap<>(Map.of("Cookie", "session=existing"))
            )
        );
    }

    @Test
    public void shouldLetWebViewHandleMissingBodyForOriginalMutatingRequests() {
        assertTrue(ProxyRequestSupport.shouldLetWebViewHandleMissingBody("https://example.com/login", "POST", ""));
        assertTrue(ProxyRequestSupport.shouldLetWebViewHandleMissingBody("https://example.com/login", "DELETE", ""));
        assertFalse(ProxyRequestSupport.shouldLetWebViewHandleMissingBody("https://example.com/login", "GET", ""));
        assertFalse(
            ProxyRequestSupport.shouldLetWebViewHandleMissingBody(
                "https://example.com/_capgo_proxy_?u=https%3A%2F%2Fexample.com%2Flogin&rid=1",
                "POST",
                ""
            )
        );
        assertFalse(ProxyRequestSupport.shouldLetWebViewHandleMissingBody("https://example.com/login", "POST", "Ym9keQ=="));
    }

    @Test
    public void resolveWebResourceResponseMetadataSeparatesMimeTypeAndCharset() {
        ProxyRequestSupport.WebResourceResponseMetadata htmlMetadata = ProxyRequestSupport.resolveWebResourceResponseMetadata(
            "text/html; charset=iso-8859-1",
            Map.of()
        );

        assertEquals("text/html", htmlMetadata.mimeType());
        assertEquals("iso-8859-1", htmlMetadata.encoding());

        ProxyRequestSupport.WebResourceResponseMetadata imageMetadata = ProxyRequestSupport.resolveWebResourceResponseMetadata(
            "image/png",
            Map.of()
        );

        assertEquals("image/png", imageMetadata.mimeType());
        assertNull(imageMetadata.encoding());
    }

    @Test
    public void splitResponseHeadersPreservesAllCookieValuesSeparately() {
        ProxyRequestSupport.ParsedResponseHeaders parsedHeaders = ProxyRequestSupport.splitResponseHeaders(
            Map.of(
                "Content-Type",
                List.of("text/html; charset=utf-8"),
                "Set-Cookie",
                List.of("session=abc; Path=/", "csrf=def; Path=/"),
                "Cache-Control",
                List.of("no-store")
            )
        );

        assertEquals("text/html; charset=utf-8", parsedHeaders.responseHeaders().get("Content-Type"));
        assertEquals("no-store", parsedHeaders.responseHeaders().get("Cache-Control"));
        assertFalse(parsedHeaders.responseHeaders().containsKey("Set-Cookie"));
        assertEquals(List.of("session=abc; Path=/", "csrf=def; Path=/"), parsedHeaders.cookieHeaders());
    }

    @Test
    public void splitResponseHeadersCombinesRepeatedNonCookieValues() {
        ProxyRequestSupport.ParsedResponseHeaders parsedHeaders = ProxyRequestSupport.splitResponseHeaders(
            Map.of("WWW-Authenticate", List.of("Bearer realm=one", "Basic realm=two"), "Cache-Control", List.of("no-cache", "no-store"))
        );

        assertEquals("Bearer realm=one, Basic realm=two", parsedHeaders.responseHeaders().get("WWW-Authenticate"));
        assertEquals("no-cache, no-store", parsedHeaders.responseHeaders().get("Cache-Control"));
    }

    @Test
    public void splitSyntheticResponseHeadersSeparatesCookieHeaders() {
        ProxyRequestSupport.ParsedResponseHeaders parsedHeaders = ProxyRequestSupport.splitSyntheticResponseHeaders(
            Map.of("Content-Type", "application/json", "Set-Cookie", "session=abc; Path=/; Secure", "Cache-Control", "no-store")
        );

        assertEquals("application/json", parsedHeaders.responseHeaders().get("Content-Type"));
        assertEquals("no-store", parsedHeaders.responseHeaders().get("Cache-Control"));
        assertFalse(parsedHeaders.responseHeaders().containsKey("Set-Cookie"));
        assertEquals(List.of("session=abc; Path=/; Secure"), parsedHeaders.cookieHeaders());
    }

    @Test
    public void normalizeLegacySyntheticResponseMapsCodeAndPlainTextBody() throws Exception {
        assertEquals(201, ProxyRequestSupport.normalizeLegacySyntheticStatus(null, 201));
        assertEquals(202, ProxyRequestSupport.normalizeLegacySyntheticStatus(202, 201));
        assertEquals(
            java.util.Base64.getEncoder().encodeToString("<html>ok</html>".getBytes(StandardCharsets.UTF_8)),
            ProxyRequestSupport.normalizeLegacySyntheticBody("<html>ok</html>", false)
        );
        assertEquals("<html>ok</html>", ProxyRequestSupport.normalizeLegacySyntheticBody("<html>ok</html>", true));
        Map<String, Object> rawHeaders = new LinkedHashMap<>();
        rawHeaders.put("Content-Type", "text/html");
        rawHeaders.put("Ignored", null);
        assertEquals(Map.of("Content-Type", "text/html"), ProxyRequestSupport.normalizeLegacyStringMap(rawHeaders));
    }
}
