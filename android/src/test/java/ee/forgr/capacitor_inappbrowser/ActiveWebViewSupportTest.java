package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ActiveWebViewSupportTest {

    @Test
    public void hiddenPopupDoesNotReplaceExistingActiveWebView() {
        assertFalse(ActiveWebViewSupport.shouldActivateNewWebView(true, true));
    }

    @Test
    public void visibleOrFirstWebViewBecomesActive() {
        assertTrue(ActiveWebViewSupport.shouldActivateNewWebView(false, true));
        assertTrue(ActiveWebViewSupport.shouldActivateNewWebView(true, false));
    }
}
