package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.*;

import android.app.Dialog;
import androidx.activity.ComponentActivity;
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
public class FullscreenWindowStateTest {

    @Test
    public void enterAndRestoreRoundTripDecorFitsAndAttributes() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        Dialog dialog = new Dialog(activity);
        dialog.create();
        assertNotNull(dialog.getWindow());
        WindowCompat.setDecorFitsSystemWindows(dialog.getWindow(), true);
        boolean decorFitsBefore = dialog.getWindow().getDecorView().getFitsSystemWindows();

        FullscreenWindowState state = new FullscreenWindowState(dialog.getWindow());
        state.enter();
        state.restore();
        assertEquals(decorFitsBefore, dialog.getWindow().getDecorView().getFitsSystemWindows());
    }

    @Test
    public void enterHidesSystemBarsWithTransientBehavior() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        Dialog dialog = new Dialog(activity);
        dialog.create();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(dialog.getWindow(), dialog.getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());

        new FullscreenWindowState(dialog.getWindow()).enter();

        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, controller.getSystemBarsBehavior());
    }
}
