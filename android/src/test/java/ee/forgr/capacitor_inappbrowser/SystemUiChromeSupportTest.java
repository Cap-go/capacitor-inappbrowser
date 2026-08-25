package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import org.junit.Test;

public class SystemUiChromeSupportTest {

    @Test
    public void edgeToEdgeChromeRequiredFromApi35() {
        assertTrue(SystemUiChromeSupport.requiresEdgeToEdgeChrome(Build.VERSION_CODES.VANILLA_ICE_CREAM));
        assertTrue(SystemUiChromeSupport.requiresEdgeToEdgeChrome(Build.VERSION_CODES.VANILLA_ICE_CREAM + 1));
        assertFalse(SystemUiChromeSupport.requiresEdgeToEdgeChrome(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
    }

    @Test
    public void legacySystemBarColorsApplyBelowApi35() {
        assertTrue(SystemUiChromeSupport.shouldApplyLegacySystemBarColors(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
        assertFalse(SystemUiChromeSupport.shouldApplyLegacySystemBarColors(Build.VERSION_CODES.VANILLA_ICE_CREAM));
    }

    @Test
    public void preApi30LayoutFlagsOnlyApplyBelowApi30() {
        assertTrue(SystemUiChromeSupport.shouldUsePreApi30LayoutFlags(Build.VERSION_CODES.Q));
        assertFalse(SystemUiChromeSupport.shouldUsePreApi30LayoutFlags(Build.VERSION_CODES.R));
    }

    @Test
    public void layoutBehindNavigationBarOnlyOnPreApi30NonEdgeToEdge() {
        assertTrue(SystemUiChromeSupport.usesLayoutBehindNavigationBar(Build.VERSION_CODES.Q, false));
        assertFalse(SystemUiChromeSupport.usesLayoutBehindNavigationBar(Build.VERSION_CODES.R, false));
        assertFalse(SystemUiChromeSupport.usesLayoutBehindNavigationBar(Build.VERSION_CODES.Q, true));
    }
}
