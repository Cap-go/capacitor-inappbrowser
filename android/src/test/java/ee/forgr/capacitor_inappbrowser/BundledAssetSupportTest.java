package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.getcapacitor.Bridge;
import org.junit.Test;

public class BundledAssetSupportTest {

    @Test
    public void resolveRelativePathForLocalhost() {
        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("/page.html", (Bridge) null);

        assertEquals("https://localhost/page.html", resolution.url);
        assertTrue(resolution.needsAssetLoader);
    }

    @Test
    public void assetLoaderSchemeUsesHttpWhenConfigured() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("http", "localhost");
        assertEquals("http", BundledAssetSupport.assetLoaderScheme(localConfig));
    }

    @Test
    public void resolveRelativePathForHttpLocalConfig() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("http", "localhost");
        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("/page.html", localConfig);

        assertEquals("http://localhost/page.html", resolution.url);
        assertTrue(resolution.needsAssetLoader);
    }

    @Test
    public void rejectsPathTraversal() {
        assertNull(BundledAssetSupport.normalizeBundledPath("/../secret.txt"));
        assertNull(BundledAssetSupport.resolve("/../secret.txt", (Bridge) null));
        assertTrue(BundledAssetSupport.isProxyBridgeMarkerPath("/_capgo_proxy_"));
    }

    @Test
    public void keepsRemoteUrlsUnchanged() {
        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("https://example.com/page.html", (Bridge) null);

        assertEquals("https://example.com/page.html", resolution.url);
        assertFalse(resolution.needsAssetLoader);
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

    @Test
    public void encodingForMimeTypeReturnsUtf8ForTextLikeTypes() {
        assertEquals("utf-8", BundledAssetSupport.encodingForMimeType("text/html"));
        assertEquals("utf-8", BundledAssetSupport.encodingForMimeType("application/json"));
        assertEquals("utf-8", BundledAssetSupport.encodingForMimeType("application/javascript"));
        assertEquals("utf-8", BundledAssetSupport.encodingForMimeType("application/xml"));
        assertNull(BundledAssetSupport.encodingForMimeType("image/png"));
    }

    @Test
    public void distinguishesBundledPathsFromBareHostnames() {
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("/index.html"));
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("assets/page.html"));
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("index.html"));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath("www.example.com"));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath("https://example.com"));
    }
}
