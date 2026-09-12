package ee.forgr.capacitor_inappbrowser;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Saves each nesting level so media fullscreen can return to browser fullscreen. */
final class FullscreenWindowState {

    private final Window window;
    private final WindowManager.LayoutParams attributes = new WindowManager.LayoutParams();
    private final int visibility;
    private final int behavior;
    private final boolean statusVisible;
    private final boolean navigationVisible;

    FullscreenWindowState(Window window) {
        this.window = window;
        attributes.copyFrom(window.getAttributes());
        View decor = window.getDecorView();
        visibility = decor.getSystemUiVisibility();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decor);
        behavior = controller.getSystemBarsBehavior();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
        statusVisible =
            insets != null
                ? insets.isVisible(WindowInsetsCompat.Type.statusBars())
                : (attributes.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0;
        navigationVisible =
            insets != null
                ? insets.isVisible(WindowInsetsCompat.Type.navigationBars())
                : (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
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
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
            decor.getSystemUiVisibility() |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decor);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    void restore() {
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars());
        else controller.hide(WindowInsetsCompat.Type.statusBars());
        if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars());
        else controller.hide(WindowInsetsCompat.Type.navigationBars());
        controller.setSystemBarsBehavior(behavior);
        window.setAttributes(attributes);
        window.getDecorView().setSystemUiVisibility(visibility);
    }
}
