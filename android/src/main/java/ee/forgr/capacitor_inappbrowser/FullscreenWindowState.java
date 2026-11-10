package ee.forgr.capacitor_inappbrowser;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Saves each nesting level so media fullscreen can return to browser fullscreen. */
final class FullscreenWindowState {

    private final Window window;
    private final WindowManager.LayoutParams attributes = new WindowManager.LayoutParams();
    private final boolean decorFitsSystemWindows;
    private final SystemBarsControllerState systemBars;

    FullscreenWindowState(Window window) {
        this.window = window;
        attributes.copyFrom(window.getAttributes());
        decorFitsSystemWindows = window.getDecorView().getFitsSystemWindows();
        systemBars = SystemBarsControllerState.capture(window);
    }

    void enter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // DEFAULT excludes the cutout once bars hide, leaving a black band in immersive mode.
            WindowManager.LayoutParams fullscreenAttributes = window.getAttributes();
            fullscreenAttributes.layoutInDisplayCutoutMode =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(fullscreenAttributes);
        }
        View decor = window.getDecorView();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decor);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    void restore() {
        systemBars.applyTo(window);
        window.setAttributes(attributes);
        WindowCompat.setDecorFitsSystemWindows(window, decorFitsSystemWindows);
    }
}
