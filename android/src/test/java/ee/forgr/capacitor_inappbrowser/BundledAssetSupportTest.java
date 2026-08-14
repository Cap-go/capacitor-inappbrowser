package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BundledAssetSupportTest {

    @Test
    public void resolveRelativePathForLocalhost() {
        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("/page.html", null);

        assertEquals("https://localhost/page.html", resolution.url);
    }

    @Test
    public void keepsHttpSchemeWhenConfigured() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("http", "localhost");
        assertEquals("http", BundledAssetSupport.assetLoaderScheme(localConfig));

        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("/page.html", null);
        assertEquals("https://localhost/page.html", resolution.url);
    }

    @Test
    public void rejectsPathTraversal() {
        assertNull(BundledAssetSupport.normalizeBundledPath("/../secret.txt"));
        assertNull(BundledAssetSupport.resolve("/../secret.txt", null));
        assertTrue(BundledAssetSupport.isProxyBridgeMarkerPath("/_capgo_proxy_"));
    }

    @Test
    public void keepsRemoteUrlsUnchanged() {
        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("https://example.com/page.html", null);

        assertEquals("https://example.com/page.html", resolution.url);
    }

    @Test
    public void recognizesBundledLocalUrl() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("https", "localhost");

        assertTrue(BundledAssetSupport.isBundledLocalUrl("https://localhost/assets/app.js", localConfig));
        assertFalse(BundledAssetSupport.isBundledLocalUrl("http://localhost:3000/assets/app.js", localConfig));
    }

    @Test
    public void parsesCustomLocalConfig() {
        BundledAssetSupport.LocalConfig localConfig = BundledAssetSupport.parseLocalConfig("https://example.com/");

        assertEquals("https", localConfig.scheme);
        assertEquals("example.com", localConfig.host);
        assertTrue(BundledAssetSupport.isBundledLocalUrl("https://example.com/app/index.html", localConfig));
    }
}
