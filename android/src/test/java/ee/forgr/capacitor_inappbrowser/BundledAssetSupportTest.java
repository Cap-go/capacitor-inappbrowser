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
        assertFalse(BundledAssetSupport.isBundledLocalUrl("https://example.com/app/index.html", localConfig));
    }

    @Test
    public void keepsAbsoluteHttpsSameHostRemoteWithQueryString() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("https", "portal.example.com");
        String remoteUrl = "https://portal.example.com/auth/signin?state=example";

        assertFalse(BundledAssetSupport.isBundledLocalUrl(remoteUrl, localConfig));

        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve(remoteUrl, localConfig);

        assertEquals(remoteUrl, resolution.url);
        assertFalse(resolution.needsAssetLoader);
    }

    @Test
    public void resolvesRelativePathForCustomHostnameLocalConfig() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("https", "portal.example.com");
        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("/index.html", localConfig);

        assertEquals("https://portal.example.com/index.html", resolution.url);
        assertTrue(resolution.needsAssetLoader);
    }

    @Test
    public void treatsLoopbackAbsoluteHttpsAsBundledLocalUrl() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("https", "127.0.0.1");

        assertTrue(BundledAssetSupport.isBundledLocalUrl("https://127.0.0.1/assets/app.js", localConfig));
    }

    @Test
    public void treatsFullIpv4LoopbackRangeAsBundledLocalUrl() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("https", "127.0.0.2");

        assertTrue(BundledAssetSupport.isBundledLocalUrl("https://127.0.0.2/assets/app.js", localConfig));
    }

    @Test
    public void keepsSameHostHttpsRemoteWithCustomAndroidScheme() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("myapp", "portal.example.com");
        String remoteUrl = "https://portal.example.com/auth/signin?state=example";

        assertFalse(BundledAssetSupport.isBundledLocalUrl(remoteUrl, localConfig));

        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve(remoteUrl, localConfig);

        assertEquals(remoteUrl, resolution.url);
        assertFalse(resolution.needsAssetLoader);
    }

    @Test
    public void restoresBundledAssetLoaderAfterRemoteNavigation() {
        BundledAssetSupport.LocalConfig localConfig = new BundledAssetSupport.LocalConfig("myapp", "portal.example.com");
        BundledAssetSupport.Resolution bundled = BundledAssetSupport.resolve("/index.html", localConfig);
        BundledAssetSupport.Resolution remote = BundledAssetSupport.resolve(
            "https://portal.example.com/auth/signin?state=example",
            localConfig
        );
        BundledAssetSupport.Resolution restored = BundledAssetSupport.resolve(bundled.url, localConfig);

        assertTrue(bundled.needsAssetLoader);
        assertFalse(remote.needsAssetLoader);
        assertTrue(restored.needsAssetLoader);
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
    public void rejectsUntrustedFileUrls() {
        assertNull(BundledAssetSupport.resolve("file:///tmp/index.html", (Bridge) null));
        assertNull(BundledAssetSupport.resolve("file:///data/data/com.example/files/secret.html", (Bridge) null));
        assertNull(BundledAssetSupport.resolve("file:///sdcard/a b.html", (Bridge) null));
        assertFalse(BundledAssetSupport.isTrustedBundledFileUrl("file:///tmp/index.html"));
        assertTrue(BundledAssetSupport.isFileUrl("file:///sdcard/a b.html"));
    }

    @Test
    public void rejectsFileUrlTraversalUnderAndroidAsset() {
        assertFalse(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/../../sdcard/evil.html"));
        assertNull(BundledAssetSupport.resolve("file:///android_asset/../../sdcard/evil.html", (Bridge) null));
        assertFalse(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/%2e%2e/%2e%2e/sdcard/evil.html"));
        assertFalse(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/%2E%2E/sdcard/evil.html"));
        assertFalse(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/./../sdcard/evil.html"));
        assertFalse(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/..\\..\\sdcard/evil.html"));
        assertNull(BundledAssetSupport.resolve("file:///android_asset/..\\..\\sdcard/evil.html", (Bridge) null));
    }

    @Test
    public void allowsTrustedBundledFileUrls() {
        assertTrue(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/public/index.html"));
        assertTrue(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/"));
        assertTrue(BundledAssetSupport.isTrustedBundledFileUrl("file:///android_asset/public/my page.html"));

        BundledAssetSupport.Resolution resolution = BundledAssetSupport.resolve("file:///android_asset/public/index.html", (Bridge) null);

        assertEquals("file:///android_asset/public/index.html", resolution.url);
        assertFalse(resolution.needsAssetLoader);

        BundledAssetSupport.Resolution spacedResolution = BundledAssetSupport.resolve(
            "file:///android_asset/public/my page.html",
            (Bridge) null
        );

        assertEquals("file:///android_asset/public/my page.html", spacedResolution.url);
        assertFalse(spacedResolution.needsAssetLoader);
    }

    @Test
    public void distinguishesBundledPathsFromBareHostnames() {
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("/index.html"));
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("assets/page.html"));
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("index.html"));
        assertTrue(BundledAssetSupport.isLikelyBundledRelativePath("index.html?v=2"));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath("www.example.com"));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath(".env"));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath("assets/.env"));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath("file."));
        assertFalse(BundledAssetSupport.isLikelyBundledRelativePath("https://example.com"));
    }
}
