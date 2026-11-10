package ee.forgr.capacitor_inappbrowser;

import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Centralizes window chrome decisions so WebViewDialog can avoid deprecated system UI APIs on
 * Android 15+ while keeping API 24–34 behavior unchanged.
 */
final class SystemUiChromeSupport {

    static final int EDGE_TO_EDGE_SDK = Build.VERSION_CODES.VANILLA_ICE_CREAM;
    private static final WeakHashMap<Window, Boolean> decorFitsPolicyByWindow = new WeakHashMap<>();

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

    /**
     * Color painted behind the browser content, visible in the system bar insets. Follows the toolbar
     * color when one is set, otherwise the system theme, so the dialog theme's default never shows.
     */
    static int resolveWindowBackgroundColor(Integer toolbarColor, boolean darkTheme) {
        if (toolbarColor != null) {
            return toolbarColor;
        }
        return darkTheme ? Color.BLACK : Color.WHITE;
    }

    static void setDecorFitsSystemWindows(Window window, boolean decorFitsSystemWindows) {
        if (window == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(window, decorFitsSystemWindows);
        decorFitsPolicyByWindow.put(window, decorFitsSystemWindows);
    }

    /** Window policy from API 30; on older releases falls back to the decor view layout flag. */
    static boolean getDecorFitsSystemWindows(Window window) {
        if (window == null) {
            return true;
        }
        Boolean tracked = decorFitsPolicyByWindow.get(window);
        if (tracked != null) {
            return tracked;
        }
        Boolean policy = readWindowDecorFitsPolicy(window);
        if (policy != null) {
            return policy;
        }
        View decor = window.getDecorView();
        return decor == null || decor.getFitsSystemWindows();
    }

    private static Boolean readWindowDecorFitsPolicy(Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        try {
            Method method = Window.class.getMethod("getDecorFitsSystemWindows");
            return (Boolean) method.invoke(window);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
