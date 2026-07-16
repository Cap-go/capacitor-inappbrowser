package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReloadGestureSupportTest {

    @Test
    public void gestureReloadAlwaysResetsScrollToTop() {
        assertEquals(0, ReloadGestureSupport.webViewScrollYAfterGestureReload(120));
        assertEquals(0, ReloadGestureSupport.webViewScrollYAfterGestureReload(0));
        assertEquals(0, ReloadGestureSupport.webViewScrollYAfterGestureReload(-8));
    }

    @Test
    public void shouldClearRefreshingOnlyWhenActive() {
        assertTrue(ReloadGestureSupport.shouldClearRefreshing(true));
        assertFalse(ReloadGestureSupport.shouldClearRefreshing(false));
    }
}
