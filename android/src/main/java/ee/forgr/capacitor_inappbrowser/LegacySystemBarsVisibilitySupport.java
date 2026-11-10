package ee.forgr.capacitor_inappbrowser;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Legacy system bar visibility reads for API 24 to 29 when window insets are not yet available. */
final class LegacySystemBarsVisibilitySupport {

    private LegacySystemBarsVisibilitySupport() {}

    static boolean isStatusBarVisible(Window window, View decor) {
        if (window == null || decor == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true;
        }
        return isStatusBarVisibleLegacy(window, decor);
    }

    static boolean isNavigationBarVisible(View decor) {
        if (decor == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true;
        }
        return isNavigationBarVisibleLegacy(decor);
    }

    @SuppressWarnings("deprecation")
    private static boolean isStatusBarVisibleLegacy(Window window, View decor) {
        WindowManager.LayoutParams attributes = window.getAttributes();
        int visibility = decor.getSystemUiVisibility();
        return (attributes.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0 && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
    }

    @SuppressWarnings("deprecation")
    private static boolean isNavigationBarVisibleLegacy(View decor) {
        return (decor.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    }
}
