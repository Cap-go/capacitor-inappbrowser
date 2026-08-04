package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrientationLayoutSupportTest {

    @Test
    public void refreshesWhenPreviousConfigurationIsMissing() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(null, null, null, null, null, 2, 800, 360, 360, 420));
    }

    @Test
    public void refreshesOnOrientationChange() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 420, 2, 800, 360, 360, 420));
    }

    @Test
    public void refreshesOnScreenSizeChangeWithoutOrientationFlip() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 420, 1, 600, 800, 600, 420));
    }

    @Test
    public void refreshesOnDensityChange() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 160, 1, 360, 800, 360, 480));
    }

    @Test
    public void skipsUnrelatedConfigurationChanges() {
        assertFalse(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 420, 1, 360, 800, 360, 420));
    }
}
