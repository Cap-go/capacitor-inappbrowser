package ee.forgr.capacitor_inappbrowser;

import android.webkit.CookieManager;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Applies cordova-style open-time cookie/cache clearing before the first navigation.
 */
final class OpenTimeBrowsingDataClearSupport {

    private static final long COOKIE_CLEAR_TIMEOUT_SECONDS = 10;

    private OpenTimeBrowsingDataClearSupport() {}

    static void applyBeforeFirstNavigation(
        CookieManager cookieManager,
        WebView webView,
        boolean clearCookiesOnOpen,
        boolean clearCacheOnOpen
    ) {
        if (clearCookiesOnOpen) {
            final CountDownLatch latch = new CountDownLatch(1);
            cookieManager.removeAllCookies((value) -> latch.countDown());
            try {
                if (!latch.await(COOKIE_CLEAR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for cookie clear");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cookieManager.flush();
        }

        if (clearCacheOnOpen && webView != null) {
            webView.clearCache(true);
        }
    }
}
