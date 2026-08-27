package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import org.junit.Test;

public class WebViewCustomFullscreenSupportTest {

    @Test
    public void customFullscreenInactiveWhenViewIsNull() {
        assertFalse(WebViewCustomFullscreenSupport.isCustomFullscreenActive(null));
    }

    @Test
    public void customFullscreenActiveWhenViewIsPresent() {
        assertTrue(WebViewCustomFullscreenSupport.isCustomFullscreenActive(new View(null)));
    }

    @Test
    public void duplicateShowRejectedWhileFullscreenActive() {
        assertTrue(WebViewCustomFullscreenSupport.shouldRejectDuplicateShow(true));
        assertFalse(WebViewCustomFullscreenSupport.shouldRejectDuplicateShow(false));
    }

    @Test
    public void backPressConsumedWhileFullscreenActive() {
        assertTrue(WebViewCustomFullscreenSupport.shouldConsumeBackPress(true));
        assertFalse(WebViewCustomFullscreenSupport.shouldConsumeBackPress(false));
    }

    @Test
    public void immersiveFlagsIncludeFullscreenAndImmersiveSticky() {
        int flags = WebViewCustomFullscreenSupport.immersiveFullscreenSystemUiVisibility();
        assertTrue((flags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
        assertTrue((flags & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0);
    }
}
