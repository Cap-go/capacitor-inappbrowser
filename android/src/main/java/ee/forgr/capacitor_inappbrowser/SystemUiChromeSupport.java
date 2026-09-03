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

    static boolean usesLayoutBehindNavigationBar(int sdkInt, boolean edgeToEdge) {
        return !edgeToEdge && shouldUsePreApi30LayoutFlags(sdkInt);
    }

    static void setDecorFitsSystemWindows(Window window, boolean decorFitsSystemWindows) {
        if (window == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(window, decorFitsSystemWindows);
    }

    static void prepareInitialDialogWindow(Window window, View statusBarColorView) {
        if (window == null) {
            return;
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (shouldApplyLegacySystemBarColors(Build.VERSION.SDK_INT)) {
            applyLegacyStatusBarColorViaView(statusBarColorView, Color.TRANSPARENT);
            clearLegacyTranslucentStatusFlag(window);
        }
    }

    static void applyDialogWindowChrome(
        Window window,
        View decorView,
        View statusBarColorView,
        boolean edgeToEdge,
        Integer statusBarColor,
        Boolean lightStatusBars
    ) {
        if (window == null || decorView == null) {
            return;
        }

        if (edgeToEdge) {
            setDecorFitsSystemWindows(window, false);
        } else if (!shouldUsePreApi30LayoutFlags(Build.VERSION.SDK_INT)) {
            setDecorFitsSystemWindows(window, true);
            applyLegacyStatusBarColorViaView(statusBarColorView, statusBarColor);
        } else {
            setDecorFitsSystemWindows(window, false);
            applyLegacyStatusBarColorViaView(statusBarColorView, statusBarColor);
        }

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
        if (lightStatusBars != null) {
            controller.setAppearanceLightStatusBars(lightStatusBars);
        }
    }

    static void applyLegacyStatusBarColorViaView(View statusBarColorView, Integer statusBarColor) {
        if (!shouldApplyLegacySystemBarColors(Build.VERSION.SDK_INT)) {
            return;
        }

        if (statusBarColor != null && statusBarColorView != null) {
            statusBarColorView.setBackgroundColor(statusBarColor);
        }
    }

    @SuppressWarnings("deprecation")
    private static void clearLegacyTranslucentStatusFlag(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }
}
