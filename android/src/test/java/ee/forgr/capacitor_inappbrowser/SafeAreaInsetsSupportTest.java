package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SafeAreaInsetsSupportTest {

    @Test
    public void safeBottomInsetUsesLargestSystemOrGestureInset() {
        assertEquals(24, SafeAreaInsetsSupport.resolveSafeBottomInset(0, 16, 24, 8));
    }

    @Test
    public void bottomMarginFollowsSafeBottomOptionAndKeyboardInset() {
        assertEquals(16, SafeAreaInsetsSupport.resolveBottomMargin(true, 16, 0));
        assertEquals(0, SafeAreaInsetsSupport.resolveBottomMargin(false, 16, 0));
        assertEquals(280, SafeAreaInsetsSupport.resolveBottomMargin(false, 16, 280));
        assertEquals(280, SafeAreaInsetsSupport.resolveBottomMargin(true, 16, 280));
    }

    @Test
    public void topMarginRequiresSafeTopAndExplicitTopInsetWithoutAppBarHandling() {
        assertEquals(48, SafeAreaInsetsSupport.resolveTopMargin(true, true, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMargin(false, true, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMargin(true, false, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMargin(true, true, 48, true));
    }
}
