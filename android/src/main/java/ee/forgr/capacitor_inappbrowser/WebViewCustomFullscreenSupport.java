package ee.forgr.capacitor_inappbrowser;

import android.view.View;
import android.view.Window;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Testable helpers for HTML5/iframe fullscreen routed through WebChromeClient custom views
 * (e.g. embedded YouTube fullscreen).
 */
final class WebViewCustomFullscreenSupport {

    private WebViewCustomFullscreenSupport() {}

    static boolean isCustomFullscreenActive(Object customFullscreenView) {
        return customFullscreenView != null;
    }

    static boolean shouldRejectDuplicateShow(boolean isActive) {
        return isActive;
    }

    static boolean shouldConsumeBackPress(boolean isActive) {
        return isActive;
    }

    static int immersiveSystemBarsBehavior() {
        return WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
    }

    static void enterImmersiveFullscreen(Window window, View decorView) {
        if (window == null || decorView == null) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(immersiveSystemBarsBehavior());
    }

    static void exitImmersiveFullscreen(Window window, View decorView) {
        if (window == null || decorView == null) {
            return;
        }

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
        controller.show(WindowInsetsCompat.Type.systemBars());
    }

    static boolean shouldUseHostActivityWindow(boolean backLayerActive) {
        return backLayerActive;
    }

    static boolean shouldRegisterHostBackHandler(boolean backLayerActive) {
        return backLayerActive;
    }
}
