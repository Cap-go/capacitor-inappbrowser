package ee.forgr.capacitor_inappbrowser;

import android.view.View;

/**
 * Testable helpers for HTML5/iframe fullscreen routed through WebChromeClient custom views
 * (e.g. embedded YouTube fullscreen).
 */
final class WebViewCustomFullscreenSupport {

    private WebViewCustomFullscreenSupport() {}

    static boolean isCustomFullscreenActive(View customFullscreenView) {
        return customFullscreenView != null;
    }

    static boolean shouldRejectDuplicateShow(boolean isActive) {
        return isActive;
    }

    static boolean shouldConsumeBackPress(boolean isActive) {
        return isActive;
    }

    @SuppressWarnings("deprecation")
    static int immersiveFullscreenSystemUiVisibility() {
        return (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    static boolean shouldUseHostActivityWindow(boolean backLayerActive) {
        return backLayerActive;
    }

    static boolean shouldRegisterHostBackHandler(boolean backLayerActive) {
        return backLayerActive;
    }

    @SuppressWarnings("deprecation")
    static int restoredSystemUiVisibility() {
        return View.SYSTEM_UI_FLAG_VISIBLE;
    }
}
