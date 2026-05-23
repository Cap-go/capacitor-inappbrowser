package ee.forgr.capacitor_inappbrowser;

final class SafeAreaInsetsSupport {

    private SafeAreaInsetsSupport() {}

    static int resolveSafeBottomInset(
        int systemBarsBottom,
        int navigationBarsBottom,
        int systemGesturesBottom,
        int mandatoryGesturesBottom
    ) {
        return Math.max(systemBarsBottom, Math.max(navigationBarsBottom, Math.max(systemGesturesBottom, mandatoryGesturesBottom)));
    }

    static int resolveBottomMargin(boolean enabledSafeBottomMargin, int safeBottomInset, int imeBottom) {
        int bottomInset = enabledSafeBottomMargin ? safeBottomInset : 0;
        return Math.max(bottomInset, imeBottom);
    }

    static int resolveTopMargin(boolean enabledSafeTopMargin, boolean useTopInset, int systemBarsTop, boolean appBarHandlesTopInset) {
        if (!enabledSafeTopMargin || !useTopInset || appBarHandlesTopInset) {
            return 0;
        }

        return systemBarsTop;
    }
}
