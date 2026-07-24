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

    /**
     * Bottom padding to apply to the WebView container (a SwipeRefreshLayout, which honours its own
     * padding but ignores child margins). Takes the larger of the safe bottom inset and the keyboard
     * (IME) inset via {@link #resolveBottomMargin}, then adds a compensation term: on Android 15+ the
     * AppBarLayout is pushed down by the status-bar height, which shifts the container's bottom edge
     * off-screen by that same amount, so it must be added back as padding regardless of the options.
     */
    static int resolveContainerBottomPadding(
        boolean enabledSafeBottomMargin,
        int safeBottomInset,
        int imeBottom,
        boolean appBarHandlesTopInset,
        int statusBarTop
    ) {
        int base = resolveBottomMargin(enabledSafeBottomMargin, safeBottomInset, imeBottom);
        int appBarCompensation = appBarHandlesTopInset ? Math.max(0, statusBarTop) : 0;
        return base + appBarCompensation;
    }

    /**
     * Top padding for the WebView container. When the AppBarLayout handles the top inset it already
     * sits below the status bar, so no additional padding is needed; otherwise the status-bar height
     * is applied when the safe-top options request it.
     */
    static int resolveContainerTopPadding(
        boolean enabledSafeTopMargin,
        boolean useTopInset,
        int statusBarTop,
        boolean appBarHandlesTopInset
    ) {
        if (appBarHandlesTopInset || !enabledSafeTopMargin || !useTopInset) {
            return 0;
        }

        return Math.max(0, statusBarTop);
    }

    /**
     * Whether the WebView container must be inset for the bottom system bar. On Android 15+ the dialog
     * is forced edge-to-edge (decorFitsSystemWindows=false), so the window always draws under the
     * navigation bar and insetting is mandatory to keep bottom content on-screen, regardless of the
     * enabledSafeBottomMargin opt-in that older versions honour.
     */
    static boolean shouldInsetBottomForContainer(boolean enabledSafeBottomMargin, boolean isEdgeToEdge) {
        return enabledSafeBottomMargin || isEdgeToEdge;
    }
}
