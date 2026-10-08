package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.view.Window;
import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SystemBarsControllerStateTest {

    @Test
    public void applyToCopiesHiddenBarsAndTransientBehavior() {
        ComponentActivity host = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        Window hostWindow = host.getWindow();
        WindowCompat.setDecorFitsSystemWindows(hostWindow, false);
        WindowInsetsControllerCompat hostController = WindowCompat.getInsetsController(hostWindow, hostWindow.getDecorView());
        hostController.hide(WindowInsetsCompat.Type.systemBars());
        hostController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ViewCompat.dispatchApplyWindowInsets(
            hostWindow.getDecorView(),
            new WindowInsetsCompat.Builder()
                .setVisible(WindowInsetsCompat.Type.statusBars(), false)
                .setVisible(WindowInsetsCompat.Type.navigationBars(), false)
                .build()
        );

        Dialog dialog = new Dialog(host);
        dialog.create();
        Window dialogWindow = dialog.getWindow();
        assertNotNull(dialogWindow);
        WindowInsetsControllerCompat dialogController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.getDecorView());
        dialogController.show(WindowInsetsCompat.Type.systemBars());
        dialogController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);

        SystemBarsControllerState.capture(hostWindow).applyTo(dialogWindow);

        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, dialogController.getSystemBarsBehavior());
    }

    @Test
    public void applyToAppliesCapturedVisibilityFlags() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        Dialog dialog = new Dialog(activity);
        dialog.create();
        Window dialogWindow = dialog.getWindow();
        assertNotNull(dialogWindow);
        WindowInsetsControllerCompat dialogController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.getDecorView());
        dialogController.show(WindowInsetsCompat.Type.systemBars());

        new SystemBarsControllerState(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, false, false).applyTo(
            dialogWindow
        );

        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, dialogController.getSystemBarsBehavior());
    }

    @Test
    public void captureUsesInsetVisibilityWhenPresent() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        Window window = activity.getWindow();
        ViewCompat.dispatchApplyWindowInsets(
            window.getDecorView(),
            new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setVisible(WindowInsetsCompat.Type.statusBars(), false)
                .setVisible(WindowInsetsCompat.Type.navigationBars(), false)
                .build()
        );

        SystemBarsControllerState captured = SystemBarsControllerState.capture(window);
        assertFalse(captured.isStatusVisible());
        assertFalse(captured.isNavigationVisible());
    }
}
