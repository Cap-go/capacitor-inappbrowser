package ee.forgr.capacitor_inappbrowser;

/**
 * Pure helpers for pull-to-refresh reset after a gesture reload.
 * Sticky scrollY greater than 0 makes SwipeRefreshLayout think the child can scroll up,
 * which disables the next pull until a full document navigation.
 */
final class ReloadGestureSupport {

    private ReloadGestureSupport() {}

    /** Pin to top after a gesture reload so the next pull can start. */
    static int webViewScrollYAfterGestureReload(int currentScrollY) {
        return 0;
    }

    static boolean shouldClearRefreshing(boolean isRefreshing) {
        return isRefreshing;
    }
}
