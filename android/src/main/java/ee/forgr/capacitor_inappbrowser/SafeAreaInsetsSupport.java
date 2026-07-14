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

    static int resolveSafeBottomInsetWithFallback(
        int systemBarsBottom,
        int navigationBarsBottom,
        int systemGesturesBottom,
        int mandatoryGesturesBottom,
        int systemBarsLeft,
        int systemBarsRight,
        int navigationBarsLeft,
        int navigationBarsRight,
        int fallbackBottomInset,
        boolean applyFallbackWhenZero
    ) {
        int inset = resolveSafeBottomInset(systemBarsBottom, navigationBarsBottom, systemGesturesBottom, mandatoryGesturesBottom);
        if (!applyFallbackWhenZero || fallbackBottomInset <= 0) {
            return inset;
        }

        if (hasSideNavigationBarInsets(systemBarsLeft, systemBarsRight, navigationBarsLeft, navigationBarsRight, fallbackBottomInset)) {
            return inset;
        }

        return Math.max(inset, fallbackBottomInset);
    }

    static boolean hasSideNavigationBarInsets(
        int systemBarsLeft,
        int systemBarsRight,
        int navigationBarsLeft,
        int navigationBarsRight,
        int minSideNavBarInset
    ) {
        if (minSideNavBarInset <= 0) {
            return false;
        }

        return (
            Math.max(systemBarsLeft, navigationBarsLeft) >= minSideNavBarInset ||
            Math.max(systemBarsRight, navigationBarsRight) >= minSideNavBarInset
        );
    }

    /**
     * Absolute IME insets from the window decor must only be applied as layout margins when the
     * dialog is edge-to-edge. Pre-Android 15 windows still fit system windows and are resized for
     * the soft keyboard; applying decor IME height again leaves a black gap above the keyboard.
     */
    static int resolveImeBottomInset(boolean keyboardVisible, int imeBottom, boolean applyImeAsLayoutMargin) {
        if (!keyboardVisible || !applyImeAsLayoutMargin || imeBottom <= 0) {
            return 0;
        }

        return imeBottom;
    }

    static int resolveBottomMargin(boolean enabledSafeBottomMargin, int safeBottomInset, int imeBottom) {
        int bottomInset = enabledSafeBottomMargin ? safeBottomInset : 0;
        return Math.max(bottomInset, imeBottom);
    }

    static int resolveTopMargin(boolean enabledSafeTopMargin, boolean useTopInset, int systemBarsTop, boolean appBarHandlesTopInset) {
        return resolveTopMarginWithFallback(enabledSafeTopMargin, useTopInset, systemBarsTop, appBarHandlesTopInset, 0, false);
    }

    static int resolveTopMarginWithFallback(
        boolean enabledSafeTopMargin,
        boolean useTopInset,
        int systemBarsTop,
        boolean appBarHandlesTopInset,
        int fallbackTopInset,
        boolean applyFallbackWhenZero
    ) {
        if (!enabledSafeTopMargin || !useTopInset || appBarHandlesTopInset) {
            return 0;
        }

        if (systemBarsTop > 0 || !applyFallbackWhenZero || fallbackTopInset <= 0) {
            return systemBarsTop;
        }

        return fallbackTopInset;
    }
}
