package ee.forgr.capacitor_inappbrowser;

import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Centralizes window chrome decisions so WebViewDialog can avoid deprecated system UI APIs on
 * Android 15+ while keeping API 24–34 behavior unchanged.
 */
final class SystemUiChromeSupport {

    static final int EDGE_TO_EDGE_SDK = Build.VERSION_CODES.VANILLA_ICE_CREAM;

    private SystemUiChromeSupport() {}

    static boolean requiresEdgeToEdgeChrome(int sdkInt) {
        return sdkInt >= EDGE_TO_EDGE_SDK;
    }

    static boolean shouldApplyLegacySystemBarColors(int sdkInt) {
        return sdkInt < EDGE_TO_EDGE_SDK;
    }

    static boolean shouldUsePreApi30LayoutFlags(int sdkInt) {
        return sdkInt < Build.VERSION_CODES.R;
    }

    static void setDecorFitsSystemWindows(Window window, boolean decorFitsSystemWindows) {
        if (window == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(window, decorFitsSystemWindows);
    }

    static void prepareInitialDialogWindow(Window window) {
        if (window == null) {
            return;
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (shouldApplyLegacySystemBarColors(Build.VERSION.SDK_INT)) {
            applyLegacySystemBarColors(window, Color.TRANSPARENT, null);
            clearLegacyTranslucentStatusFlag(window);
        }
    }

    static void applyDialogWindowChrome(
        Window window,
        View decorView,
        boolean edgeToEdge,
        Integer statusBarColor,
        Integer navigationBarColor,
        Boolean lightStatusBars
    ) {
        if (window == null || decorView == null) {
            return;
        }

        if (edgeToEdge) {
            WindowCompat.enableEdgeToEdge(window);
            if (shouldApplyLegacySystemBarColors(Build.VERSION.SDK_INT)) {
                applyLegacySystemBarColors(window, Color.TRANSPARENT, Color.TRANSPARENT);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setDecorFitsSystemWindows(window, true);
            applyLegacySystemBarColors(window, statusBarColor, navigationBarColor);
        } else {
            applyPreApi30LayoutBehindNavigationBar(decorView);
            applyLegacySystemBarColors(window, statusBarColor, navigationBarColor);
        }

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
        if (lightStatusBars != null) {
            controller.setAppearanceLightStatusBars(lightStatusBars);
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyPreApi30LayoutBehindNavigationBar(View decorView) {
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @SuppressWarnings("deprecation")
    static void applyLegacySystemBarColors(Window window, Integer statusBarColor, Integer navigationBarColor) {
        if (window == null || !shouldApplyLegacySystemBarColors(Build.VERSION.SDK_INT)) {
            return;
        }

        if (statusBarColor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(statusBarColor);
        }
        if (navigationBarColor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(navigationBarColor);
        }
    }

    @SuppressWarnings("deprecation")
    private static void clearLegacyTranslucentStatusFlag(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }
}
