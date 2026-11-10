package ee.forgr.capacitor_inappbrowser;

import android.view.View;
import android.view.Window;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Captures status/navigation bar visibility and transient behavior for one window. */
final class SystemBarsControllerState {

    private final int behavior;
    private final boolean statusVisible;
    private final boolean navigationVisible;

    SystemBarsControllerState(int behavior, boolean statusVisible, boolean navigationVisible) {
        this.behavior = behavior;
        this.statusVisible = statusVisible;
        this.navigationVisible = navigationVisible;
    }

    static SystemBarsControllerState capture(Window window) {
        if (window == null) {
            return new SystemBarsControllerState(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT, true, true);
        }

        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decor);
        int behavior = controller.getSystemBarsBehavior();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        boolean statusVisible;
        boolean navigationVisible;
        if (insets != null) {
            statusVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars());
            navigationVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars());
        } else {
            statusVisible = LegacySystemBarsVisibilitySupport.isStatusBarVisible(window, decor);
            navigationVisible = LegacySystemBarsVisibilitySupport.isNavigationBarVisible(decor);
        }
        return new SystemBarsControllerState(behavior, statusVisible, navigationVisible);
    }

    void applyTo(Window window) {
        if (window == null) {
            return;
        }

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        if (statusVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars());
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars());
        }
        if (navigationVisible) {
            controller.show(WindowInsetsCompat.Type.navigationBars());
        } else {
            controller.hide(WindowInsetsCompat.Type.navigationBars());
        }
        controller.setSystemBarsBehavior(behavior);
    }

    int getBehavior() {
        return behavior;
    }

    boolean isStatusVisible() {
        return statusVisible;
    }

    boolean isNavigationVisible() {
        return navigationVisible;
    }
}
