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
