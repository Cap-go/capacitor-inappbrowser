package ee.forgr.capacitor_inappbrowser;

import android.webkit.WebView;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Selects only InAppBrowser-managed WebViews for browsing-data clears.
 * Never includes the Capacitor/Ionic bridge WebView.
 */
final class BrowsingDataClearSupport {

    private BrowsingDataClearSupport() {}

    static ArrayList<WebView> managedWebViewsOnly(Collection<WebViewDialog> dialogs, WebView bridgeWebView) {
        ArrayList<WebView> targetWebViews = new ArrayList<>();
        if (dialogs == null) {
            return targetWebViews;
        }

        for (WebViewDialog dialog : dialogs) {
            if (dialog == null) {
                continue;
            }
            WebView managedWebView = dialog.getManagedWebView();
            if (managedWebView != null && managedWebView != bridgeWebView) {
                targetWebViews.add(managedWebView);
            }
        }
        return targetWebViews;
    }

    /**
     * Android CookieManager and WebStorage are process-global and shared with the host WebView.
     * clearAllBrowsingData must not wipe those globals.
     */
    static boolean shouldClearProcessGlobalBrowsingData() {
        return false;
    }
}
