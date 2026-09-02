package ee.forgr.capacitor_inappbrowser;

import android.webkit.WebView;

/**
 * Applies cordova-style open-time cookie/cache clearing before the first navigation.
 *
 * CookieManager.removeAllCookies() delivers its callback on the calling thread's Looper.
 * Never block that Looper waiting for the callback — that deadlocks the UI thread.
 */
final class OpenTimeBrowsingDataClearSupport {

    @FunctionalInterface
    interface CookieClearer {
        void clear(Runnable onCleared);
    }

    private OpenTimeBrowsingDataClearSupport() {}

    static void applyBeforeFirstNavigation(
        CookieClearer cookieClearer,
        WebView webView,
        boolean clearCookiesOnOpen,
        boolean clearCacheOnOpen,
        Runnable onComplete
    ) {
        Runnable finish = () -> {
            if (clearCacheOnOpen && webView != null) {
                webView.clearCache(true);
            }
            onComplete.run();
        };

        if (!clearCookiesOnOpen) {
            finish.run();
            return;
        }

        cookieClearer.clear(finish);
    }
}
