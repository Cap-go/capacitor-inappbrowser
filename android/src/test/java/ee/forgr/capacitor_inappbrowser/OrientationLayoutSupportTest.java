package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import org.junit.Test;

public class OrientationLayoutSupportTest {

    @Test
    public void refreshesWhenPreviousConfigurationIsMissing() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(null, null, null, null, null, null, 2, 800, 360, 360, 420, 0));
    }

    @Test
    public void refreshesOnOrientationChange() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 420, 16, 2, 800, 360, 360, 420, 16));
    }

    @Test
    public void refreshesOnScreenSizeChangeWithoutOrientationFlip() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 420, 16, 1, 600, 800, 600, 420, 16));
    }

    @Test
    public void refreshesOnDensityChange() {
        assertTrue(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 160, 16, 1, 360, 800, 360, 480, 16));
    }

    @Test
    public void refreshesOnUiModeChange() {
        assertTrue(
            OrientationLayoutSupport.shouldRefreshBrowserLayout(
                1,
                360,
                800,
                360,
                420,
                Configuration.UI_MODE_NIGHT_NO,
                1,
                360,
                800,
                360,
                420,
                Configuration.UI_MODE_NIGHT_YES
            )
        );
    }

    @Test
    public void skipsUnrelatedConfigurationChanges() {
        assertFalse(OrientationLayoutSupport.shouldRefreshBrowserLayout(1, 360, 800, 360, 420, 16, 1, 360, 800, 360, 420, 16));
    }
}
