package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebViewBackNavigationSupportTest {

    @Test
    public void consumeBackOnlyWhenVisibleOverlayDialog() {
        assertTrue(WebViewBackNavigationSupport.shouldConsumeBackPress(true, false, false));
        assertFalse(WebViewBackNavigationSupport.shouldConsumeBackPress(false, false, false));
        assertFalse(WebViewBackNavigationSupport.shouldConsumeBackPress(true, true, false));
        assertFalse(WebViewBackNavigationSupport.shouldConsumeBackPress(true, false, true));
    }

    @Test
    public void fullscreenTakesPriorityOverStayInWebView() {
        assertEquals(
            WebViewBackNavigationSupport.Action.EXIT_FULLSCREEN,
            WebViewBackNavigationSupport.resolveAction(true, true, true, true, true)
        );
    }

    @Test
    public void navigationToolbarGoesBackInWebViewHistory() {
        assertEquals(
            WebViewBackNavigationSupport.Action.WEBVIEW_GO_BACK,
            WebViewBackNavigationSupport.resolveAction(false, true, true, false, true)
        );
    }

    @Test
    public void activeNativeNavigationGoesBackInWebViewHistory() {
        assertEquals(
            WebViewBackNavigationSupport.Action.WEBVIEW_GO_BACK,
            WebViewBackNavigationSupport.resolveAction(false, true, false, true, true)
        );
    }

    @Test
    public void disableGoBackKeepsWebViewOpenWhenHistoryCannotGoBack() {
        assertEquals(
            WebViewBackNavigationSupport.Action.IGNORE,
            WebViewBackNavigationSupport.resolveAction(false, false, false, false, true)
        );
    }

    @Test
    public void disableGoBackKeepsWebViewOpenWhenHistoryBackIsNotEnabled() {
        assertEquals(
            WebViewBackNavigationSupport.Action.IGNORE,
            WebViewBackNavigationSupport.resolveAction(false, true, false, false, true)
        );
    }

    @Test
    public void backDismissesWhenStayInWebViewIsDisabled() {
        assertEquals(
            WebViewBackNavigationSupport.Action.DISMISS,
            WebViewBackNavigationSupport.resolveAction(false, false, false, false, false)
        );
    }
}
