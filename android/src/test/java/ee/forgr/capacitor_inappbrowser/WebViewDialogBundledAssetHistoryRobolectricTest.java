package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    private static void invokeSyncBundledAssetLoaderForHistoryUrl(WebViewDialog dialog, WebView webView, String url) throws Exception {
        Method method = WebViewDialog.class.getDeclaredMethod("syncBundledAssetLoaderForHistoryUrl", WebView.class, String.class);
        method.setAccessible(true);
        method.invoke(dialog, webView, url);
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
        return new WebView(context) {
            @Override
            public WebBackForwardList copyBackForwardList() {
                return new StubBackForwardList(currentIndex);
            }
        };
    }

    private static final class StubBackForwardList extends WebBackForwardList {
        private final int currentIndex;

        private StubBackForwardList(int currentIndex) {
            this.currentIndex = currentIndex;
        }

        @Override
        public WebBackForwardList clone() {
            return new StubBackForwardList(currentIndex);
        }

        @Override
        public int getCurrentIndex() {
            return currentIndex;
        }

        @Override
        public WebHistoryItem getCurrentItem() {
            return null;
        }

        @Override
        public WebHistoryItem getItemAtIndex(int index) {
            return null;
        }

        @Override
        public int getSize() {
            return currentIndex + 1;
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
