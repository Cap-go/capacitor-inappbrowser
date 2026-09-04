package ee.forgr.capacitor_inappbrowser;

/**
 * Testable Android back-button decisions for the in-app browser dialog.
 *
 * Predictive back (API 33+) no longer delivers {@code KEYCODE_BACK} to dialog
 * {@code OnKeyListener}s, so {@link WebViewDialog} must honor these actions from
 * {@link androidx.activity.OnBackPressedCallback}.
 */
final class WebViewBackNavigationSupport {

    enum Action {
        EXIT_FULLSCREEN,
        WEBVIEW_GO_BACK,
        IGNORE,
        DISMISS
    }

    private WebViewBackNavigationSupport() {}

    static boolean shouldConsumeBackPress(boolean isShowing, boolean hiddenMode, boolean backLayerActive) {
        return isShowing && !hiddenMode && !backLayerActive;
    }

    /**
     * When {@code disableGoBackOnNativeApplication} is true, the dialog must not be cancelable on
     * back/gesture. Otherwise {@link androidx.activity.ComponentDialog}'s built-in back callback
     * dismisses the dialog before our handler can return {@link Action#IGNORE}.
     */
    static boolean isCancelableOnBack(boolean disableGoBackOnNativeApplication) {
        return !disableGoBackOnNativeApplication;
    }

    /**
     * Activity-level back handling is required when the overlay is hosted on the activity window
     * (back layer) or when predictive-back can bypass the dialog dispatcher while the flag blocks
     * dismiss.
     */
    static boolean shouldRegisterActivityBackHandler(
        boolean isShowing,
        boolean backLayerActive,
        boolean hiddenMode,
        boolean disableGoBackOnNativeApplication,
        boolean isActiveForBackNavigation
    ) {
        if (!isActiveForBackNavigation) {
            return false;
        }
        if (hiddenMode) {
            return false;
        }
        if (!isShowing && !backLayerActive) {
            return false;
        }
        return backLayerActive || disableGoBackOnNativeApplication;
    }

    static Action resolveAction(
        boolean customFullscreenActive,
        boolean webViewCanGoBack,
        boolean navigationToolbar,
        boolean activeNativeNavigationForWebview,
        boolean disableGoBackOnNativeApplication
    ) {
        if (customFullscreenActive) {
            return Action.EXIT_FULLSCREEN;
        }
        if (webViewCanGoBack && (navigationToolbar || activeNativeNavigationForWebview)) {
            return Action.WEBVIEW_GO_BACK;
        }
        if (disableGoBackOnNativeApplication) {
            return Action.IGNORE;
        }
        return Action.DISMISS;
    }
}
