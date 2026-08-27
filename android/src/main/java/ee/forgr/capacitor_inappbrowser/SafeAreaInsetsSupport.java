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
     * Absolute IME insets from the window decor must only be inset when the dialog is edge-to-edge.
     * Pre-Android 15 windows still fit system windows and are resized for the soft keyboard;
     * applying decor IME height again leaves a black gap above the keyboard.
     */
    static int resolveImeBottomInset(boolean keyboardVisible, int imeBottom, boolean applyImeInset) {
        if (!keyboardVisible || !applyImeInset || imeBottom <= 0) {
            return 0;
        }

        return imeBottom;
    }

    /**
     * Bottom padding for the WebView container (a SwipeRefreshLayout, which honours its own padding
     * but ignores child margins): the larger of the safe bottom inset and the keyboard (IME) inset.
     */
    static int resolveContainerBottomPadding(boolean applyBottomInset, int safeBottomInset, int imeBottom) {
        int bottomInset = applyBottomInset ? safeBottomInset : 0;
        return Math.max(0, Math.max(bottomInset, imeBottom));
    }

    /**
     * The status-bar height resource stands in for the reported top inset only when the whole inset
     * set looks unpopulated, which happens on devices that hand out zeroed insets. A window that
     * reports other insets but no top one has no status bar to avoid — a secondary multi-window
     * window, or one whose status bar is hidden — and padding it would open a phantom gap.
     */
    static int resolveStatusBarTop(
        int systemBarsTop,
        int systemBarsBottom,
        int systemBarsLeft,
        int systemBarsRight,
        int fallbackStatusBarTop
    ) {
        if (systemBarsTop > 0) {
            return systemBarsTop;
        }

        boolean insetsPopulated = systemBarsBottom > 0 || systemBarsLeft > 0 || systemBarsRight > 0;
        return insetsPopulated ? 0 : Math.max(0, fallbackStatusBarTop);
    }

    /**
     * Top padding for the WebView container. When a visible AppBarLayout handles the top inset it
     * already sits below the status bar, so no additional padding is needed. Otherwise nothing
     * consumes the status bar on edge-to-edge windows (Android 15+, blank or hidden toolbar), so the
     * status-bar height is applied whenever enabledSafeTopMargin is on (#655). Windows that still fit
     * system windows keep the legacy useTopInset opt-in to avoid padding twice.
     */
    static int resolveContainerTopPadding(
        boolean enabledSafeTopMargin,
        boolean useTopInset,
        int statusBarTop,
        boolean appBarHandlesTopInset,
        boolean isEdgeToEdge
    ) {
        if (appBarHandlesTopInset || !enabledSafeTopMargin || (!useTopInset && !isEdgeToEdge)) {
            return 0;
        }

        return Math.max(0, statusBarTop);
    }

    /**
     * Whether the WebView container must be inset for the bottom system bar. Follows the
     * enabledSafeBottomMargin opt-in on all API levels, including Android 15+ edge-to-edge windows,
     * so disabling the flag matches Capacitor full-screen behaviour (#672). Pre-API 30 windows that
     * lay out behind a transparent navigation bar still need the inset regardless of the opt-in.
     */
    static boolean shouldInsetBottomForContainer(boolean enabledSafeBottomMargin, boolean isEdgeToEdge, boolean layoutBehindNavigationBar) {
        return enabledSafeBottomMargin || layoutBehindNavigationBar;
    }

    static boolean shouldInsetBottomForContainer(boolean enabledSafeBottomMargin, boolean isEdgeToEdge) {
        return shouldInsetBottomForContainer(enabledSafeBottomMargin, isEdgeToEdge, false);
    }

    /**
     * CSS custom-property inset for the top safe area. Only published when enabledSafeTopMargin is
     * on; otherwise apps rely on env(safe-area-inset-*) like the main Capacitor WebView (#672).
     */
    static int cssTopInset(boolean enabledSafeTopMargin, int containerTopPadding) {
        return enabledSafeTopMargin ? Math.max(0, containerTopPadding) : 0;
    }

    /**
     * CSS custom-property inset for the bottom safe area. Only published when enabledSafeBottomMargin
     * is on; container padding and env() insets are handled separately (#672).
     */
    static int cssBottomInset(boolean enabledSafeBottomMargin, int safeBottomInset) {
        return enabledSafeBottomMargin ? Math.max(0, safeBottomInset) : 0;
    }
}
