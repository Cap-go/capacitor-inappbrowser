package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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
        invokeClearPendingBundledAssetLoaderResolution(dialog);
        invokeClearCommittedBundledAssetNavigationIntent(dialog);

        assertFalse(invokeNeedsAssetLoaderForHistoryUrl(dialog, "https://portal.example.com/a.html", 0, false));
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

    private static void invokeClearPendingBundledAssetLoaderResolution(WebViewDialog dialog) throws Exception {
        Method method = WebViewDialog.class.getDeclaredMethod("clearPendingBundledAssetLoaderResolution");
        method.setAccessible(true);
        method.invoke(dialog);
    }

    private static void invokeClearCommittedBundledAssetNavigationIntent(WebViewDialog dialog) throws Exception {
        Method method = WebViewDialog.class.getDeclaredMethod("clearCommittedBundledAssetNavigationIntent");
        method.setAccessible(true);
        method.invoke(dialog);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
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
