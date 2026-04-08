package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
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
    public void usesLegacyJsProxyModeForRegexPatternWithoutNativeRules() {
        Options options = new Options();
        options.setProxyRequestsPattern(Pattern.compile("grailed"));

        assertTrue(ProxyRequestSupport.usesLegacyJsProxyMode(options));
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
    public void shouldLetWebViewHandleMissingBodyForOriginalMutatingRequests() {
        assertTrue(ProxyRequestSupport.shouldLetWebViewHandleMissingBody("https://example.com/login", "POST", ""));
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
}
