package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReloadGestureSupportTest {

    @Test
    public void gestureReloadAlwaysResetsScrollToTop() {
        assertEquals(0, ReloadGestureSupport.webViewScrollYAfterGestureReload(120));
        assertEquals(0, ReloadGestureSupport.webViewScrollYAfterGestureReload(0));
        assertEquals(0, ReloadGestureSupport.webViewScrollYAfterGestureReload(-8));
    }
}
