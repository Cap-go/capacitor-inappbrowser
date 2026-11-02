package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WebViewDialogBundledAssetHistoryRobolectricTest {

    @Test
    public void committedNavigationIntentOverridesRemoteClassifierForCustomHostname() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://portal.example.com/index.html");
        options.setServeBundledAssets(true);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        dialog.preserveBundledAssetNavigationIntent("https://portal.example.com/index.html", true);

        assertTrue(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/index.html", 0, false));
    }

    @Test
    public void committedNavigationIntentClearsAfterInitializedHistorySync() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://portal.example.com/a.html");
        options.setServeBundledAssets(true);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        dialog.preserveBundledAssetNavigationIntent("https://portal.example.com/a.html", true);

        assertTrue(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/a.html", 0, false));

        setBooleanField(dialog, "isInitialized", true);
        WebView webView = webViewWithHistoryIndex(context, 0);
        invokeSyncBundledAssetLoaderForHistoryUrl(dialog, webView, "https://portal.example.com/a.html");

        assertFalse(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/a.html", 0, false));
    }

    @Test
    public void committedCrossDocumentSyncClearsPendingFlagBeforePushStatePreservesBundledLoader() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://portal.example.com/index.html");
        options.setServeBundledAssets(true);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        setBooleanField(dialog, "isInitialized", true);
        setBooleanField(dialog, "pendingCrossDocumentNavigation", true);
        setIntField(dialog, "lastWebHistoryIndex", -1);

        WebView webViewAtZero = webViewWithHistoryIndex(context, 0);
        invokeSyncBundledAssetLoaderForHistoryUrl(dialog, webViewAtZero, "https://portal.example.com/index.html");

        assertFalse(getBooleanField(dialog, "pendingCrossDocumentNavigation"));
        setBooleanObjectField(dialog, "activeDocumentBundledAssetLoaderState", true);
        addHistoryState(dialog, true);

        WebView webViewAtOne = webViewWithHistoryIndex(context, 1);
        invokeSyncBundledAssetLoaderForHistoryUrl(dialog, webViewAtOne, "https://portal.example.com/app/route?tab=1");

        assertTrue(options.getServeBundledAssets());
        assertTrue(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/app/route?tab=1", 1, true));
    }

    @Test
    public void rendererHistoryBackPreparesBundledLoaderBeforeNavigation() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://remote.example/page");
        options.setServeBundledAssets(false);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        addHistoryState(dialog, true);
        addHistoryState(dialog, false);

        String bundledUrl = "https://portal.example.com/index.html";
        WebView webView = webViewWithHistoryItems(context, 1, new String[] { bundledUrl, "https://remote.example/page" });

        invokePrepareBundledAssetLoaderForRendererHistoryNavigation(dialog, webView, bundledUrl);

        assertTrue(options.getServeBundledAssets());
    }

    @Test
    public void rendererHistoryBackPreparesBundledLoaderInShouldInterceptRequest() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://remote.example/page");
        options.setServeBundledAssets(false);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        addHistoryState(dialog, true);
        addHistoryState(dialog, false);

        String bundledUrl = "https://portal.example.com/index.html";
        WebView webView = webViewWithHistoryItems(context, 1, new String[] { bundledUrl, "https://remote.example/page" });
        setWebView(dialog, webView);

        Method setupClient = WebViewDialog.class.getDeclaredMethod("setWebViewClient");
        setupClient.setAccessible(true);
        setupClient.invoke(dialog);

        WebResourceRequest request = mainFrameRequest(bundledUrl);
        webView.getWebViewClient().shouldInterceptRequest(webView, request);

        assertTrue(options.getServeBundledAssets());
    }

    @Test
    public void pushStateHistoryEntryPreservesActiveDocumentBundledLoaderState() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://portal.example.com/index.html");
        options.setServeBundledAssets(true);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        setBooleanField(dialog, "isInitialized", true);
        setIntField(dialog, "lastWebHistoryIndex", 0);
        setBooleanField(dialog, "pendingCrossDocumentNavigation", false);
        setBooleanObjectField(dialog, "activeDocumentBundledAssetLoaderState", true);
        addHistoryState(dialog, true);

        WebView webView = webViewWithHistoryIndex(context, 1);
        invokeSyncBundledAssetLoaderForHistoryUrl(dialog, webView, "https://portal.example.com/app/route?tab=1");

        assertTrue(options.getServeBundledAssets());
        assertTrue(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/app/route?tab=1", 1, true));
    }

    @Test
    public void explicitRemoteNavigationIntentStaysRemoteForCustomHostname() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://portal.example.com/auth/signin?state=example");

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.setBundledAssetLocalUrl("https://portal.example.com/");
        dialog.preserveBundledAssetNavigationIntent("https://portal.example.com/auth/signin?state=example", false);

        assertFalse(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/auth/signin?state=example", 0, false));
    }

    private static void setWebView(WebViewDialog dialog, WebView webView) throws Exception {
        Field field = WebViewDialog.class.getDeclaredField("_webView");
        field.setAccessible(true);
        field.set(dialog, webView);
    }

    private static WebResourceRequest mainFrameRequest(String url) {
        return new WebResourceRequest() {
            @Override
            public Uri getUrl() {
                return Uri.parse(url);
            }

            @Override
            public boolean isForMainFrame() {
                return true;
            }

            @Override
            public boolean isRedirect() {
                return false;
            }

            @Override
            public boolean hasGesture() {
                return false;
            }

            @Override
            public String getMethod() {
                return "GET";
            }

            @Override
            public Map<String, String> getRequestHeaders() {
                return Map.of();
            }

            @Override
            public boolean isOnlyIfCached() {
                return false;
            }

            @Override
            public boolean isPrefetch() {
                return false;
            }
        };
    }

    private static void invokeSyncBundledAssetLoaderForHistoryUrl(WebViewDialog dialog, WebView webView, String url) throws Exception {
        Method method = WebViewDialog.class.getDeclaredMethod("syncBundledAssetLoaderForHistoryUrl", WebView.class, String.class);
        method.setAccessible(true);
        method.invoke(dialog, webView, url);
    }

    private static void invokePrepareBundledAssetLoaderForRendererHistoryNavigation(WebViewDialog dialog, WebView webView, String url)
        throws Exception {
        Method method = WebViewDialog.class.getDeclaredMethod(
            "prepareBundledAssetLoaderForRendererHistoryNavigation",
            WebView.class,
            String.class
        );
        method.setAccessible(true);
        method.invoke(dialog, webView, url);
    }

    private static boolean getBooleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setBooleanObjectField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static void addHistoryState(WebViewDialog dialog, boolean needsAssetLoader) throws Exception {
        Field field = WebViewDialog.class.getDeclaredField("bundledAssetLoaderHistoryStates");
        field.setAccessible(true);
        java.util.List<Boolean> states = (java.util.List<Boolean>) field.get(dialog);
        states.add(needsAssetLoader);
    }

    private static WebView webViewWithHistoryIndex(Context context, int currentIndex) {
        return webViewWithHistoryItems(context, currentIndex, null);
    }

    private static WebView webViewWithHistoryItems(Context context, int currentIndex, String[] urls) {
        return new WebView(context) {
            @Override
            public WebBackForwardList copyBackForwardList() {
                return new StubBackForwardList(currentIndex, urls);
            }
        };
    }

    private static final class StubBackForwardList extends WebBackForwardList {

        private final int currentIndex;
        private final String[] urls;

        private StubBackForwardList(int currentIndex) {
            this(currentIndex, null);
        }

        private StubBackForwardList(int currentIndex, String[] urls) {
            this.currentIndex = currentIndex;
            this.urls = urls;
        }

        @Override
        public WebBackForwardList clone() {
            return new StubBackForwardList(currentIndex, urls);
        }

        @Override
        public int getCurrentIndex() {
            return currentIndex;
        }

        @Override
        public WebHistoryItem getCurrentItem() {
            return getItemAtIndex(currentIndex);
        }

        @Override
        public WebHistoryItem getItemAtIndex(int index) {
            if (urls == null || index < 0 || index >= urls.length) {
                return null;
            }
            final String itemUrl = urls[index];
            return new WebHistoryItem() {
                @Override
                public WebHistoryItem clone() {
                    return this;
                }

                @Override
                public String getUrl() {
                    return itemUrl;
                }

                @Override
                public String getOriginalUrl() {
                    return itemUrl;
                }

                @Override
                public String getTitle() {
                    return null;
                }

                @Override
                public Bitmap getFavicon() {
                    return null;
                }
            };
        }

        @Override
        public int getSize() {
            return urls != null ? urls.length : currentIndex + 1;
        }
    }

    private static boolean invokeNeedsAssetLoaderForHistoryUrl(
        WebViewDialog dialog,
        String url,
        int historyIndex,
        boolean useStoredHistoryState
    ) throws Exception {
        Method method = WebViewDialog.class.getDeclaredMethod(
            "needsAssetLoaderForHistoryUrl",
            String.class,
            BundledAssetSupport.LocalConfig.class,
            int.class,
            boolean.class
        );
        method.setAccessible(true);
        BundledAssetSupport.LocalConfig localConfig = BundledAssetSupport.parseLocalConfig("https://portal.example.com/");
        return (boolean) method.invoke(dialog, url, localConfig, historyIndex, useStoredHistoryState);
    }

    private static WebViewDialog.PermissionHandler noopPermissionHandler() {
        return new WebViewDialog.PermissionHandler() {
            @Override
            public void handleCameraPermissionRequest(android.webkit.PermissionRequest request) {}

            @Override
            public void handleMicrophonePermissionRequest(android.webkit.PermissionRequest request) {}

            @Override
            public void clearPendingPermissionRequest(android.webkit.PermissionRequest request) {}

            @Override
            public boolean createManagedPopupWindow(
                WebViewDialog parentDialog,
                android.os.Message resultMsg,
                boolean isUserGesture,
                String popupUrl
            ) {
                return false;
            }
        };
    }
}
