package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.List;
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
}
