package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebViewCustomFullscreenSupportTest {

    @Test
    public void customFullscreenInactiveWhenViewIsNull() {
        assertFalse(WebViewCustomFullscreenSupport.isCustomFullscreenActive(null));
    }

    @Test
    public void customFullscreenActiveForNonNullReference() {
        assertTrue(WebViewCustomFullscreenSupport.isCustomFullscreenActive(new Object()));
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
    public void enterImmersiveFullscreenReturnsNullForMissingWindowOrDecorView() {
        assertNull(WebViewCustomFullscreenSupport.enterImmersiveFullscreen(null, null));
    }

    @Test
    public void exitImmersiveFullscreenAcceptsMissingWindowOrDecorView() {
        WebViewCustomFullscreenSupport.exitImmersiveFullscreen(null, null);
    }

    @Test
    public void backLayerModeUsesHostActivityWindow() {
        assertTrue(WebViewCustomFullscreenSupport.shouldUseHostActivityWindow(true));
        assertFalse(WebViewCustomFullscreenSupport.shouldUseHostActivityWindow(false));
    }

    @Test
    public void backLayerModeRegistersHostBackHandler() {
        assertTrue(WebViewCustomFullscreenSupport.shouldRegisterHostBackHandler(true));
        assertFalse(WebViewCustomFullscreenSupport.shouldRegisterHostBackHandler(false));
    }
}
