package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SafeAreaInsetsSupportTest {

    @Test
    public void safeBottomInsetUsesLargestSystemOrGestureInset() {
        assertEquals(24, SafeAreaInsetsSupport.resolveSafeBottomInset(0, 16, 24, 8));
    }

    @Test
    public void safeBottomInsetUsesFallbackWhenInsetsAreZeroAndOptionEnabled() {
        assertEquals(48, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 0, 0, 0, 0, 0, 0, 48, true));
    }

    @Test
    public void safeBottomInsetKeepsReportedInsetWhenItMatchesNavigationBarHeight() {
        assertEquals(24, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 16, 24, 8, 0, 0, 0, 0, 24, true));
    }

    @Test
    public void safeBottomInsetUsesFallbackWhenReportedInsetUndershootsNavigationBarHeight() {
        assertEquals(48, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 16, 24, 8, 0, 0, 0, 0, 48, true));
        assertEquals(48, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 16, 8, 0, 0, 0, 0, 48, true));
    }

    @Test
    public void safeBottomInsetUsesFallbackDespiteMinorHorizontalInsets() {
        assertEquals(48, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 0, 0, 8, 0, 0, 0, 48, true));
        assertEquals(48, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 0, 0, 0, 12, 0, 0, 48, true));
    }

    @Test
    public void safeBottomInsetIgnoresFallbackWhenOptionDisabled() {
        assertEquals(0, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 0, 0, 0, 0, 0, 0, 48, false));
    }

    @Test
    public void safeBottomInsetSkipsFallbackWhenNavigationBarIsOnTheSide() {
        assertEquals(0, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 0, 0, 48, 0, 0, 0, 48, true));
        assertEquals(0, SafeAreaInsetsSupport.resolveSafeBottomInsetWithFallback(0, 0, 0, 0, 0, 48, 0, 0, 48, true));
    }

    @Test
    public void imeBottomInsetOnlyAppliedForEdgeToEdgeWindows() {
        assertEquals(0, SafeAreaInsetsSupport.resolveImeBottomInset(true, 280, false));
        assertEquals(280, SafeAreaInsetsSupport.resolveImeBottomInset(true, 280, true));
        assertEquals(0, SafeAreaInsetsSupport.resolveImeBottomInset(false, 280, true));
        assertEquals(0, SafeAreaInsetsSupport.resolveImeBottomInset(true, 0, true));
    }

    @Test
    public void containerBottomPaddingFollowsSafeBottomOptionAndKeyboardInset() {
        assertEquals(126, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, 126, 0));
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerBottomPadding(false, 126, 0));
        assertEquals(280, SafeAreaInsetsSupport.resolveContainerBottomPadding(false, 126, 280));
        assertEquals(280, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, 126, 280));
    }

    @Test
    public void containerBottomPaddingClampsNegativeInputsToZero() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, -10, 0));
    }

    @Test
    public void containerBottomPaddingKeepsNavigationBarWhenLargerThanKeyboardInset() {
        assertEquals(126, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, 126, 50));
    }

    @Test
    public void statusBarTopUsesReportedInsetWhenPresent() {
        assertEquals(87, SafeAreaInsetsSupport.resolveStatusBarTop(87, 126, 0, 0, 63));
    }

    @Test
    public void statusBarTopFallsBackOnlyWhenInsetsLookUnpopulated() {
        assertEquals(63, SafeAreaInsetsSupport.resolveStatusBarTop(0, 0, 0, 0, 63));
    }

    @Test
    public void statusBarTopStaysZeroWhenOtherInsetsAreReported() {
        // Multi-window secondary window or hidden status bar: no status bar to avoid, so no padding.
        assertEquals(0, SafeAreaInsetsSupport.resolveStatusBarTop(0, 126, 0, 0, 63));
        assertEquals(0, SafeAreaInsetsSupport.resolveStatusBarTop(0, 0, 126, 0, 63));
        assertEquals(0, SafeAreaInsetsSupport.resolveStatusBarTop(0, 0, 0, 126, 63));
    }

    @Test
    public void containerTopPaddingIsZeroWhenAppBarHandlesTopInset() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(true, true, 87, true, true));
    }

    @Test
    public void containerTopPaddingUsesStatusBarWhenAppBarDoesNotHandleTop() {
        assertEquals(87, SafeAreaInsetsSupport.resolveContainerTopPadding(true, true, 87, false, false));
    }

    @Test
    public void containerTopPaddingAppliesOnEdgeToEdgeWithoutExplicitTopInsetOptIn() {
        // Blank or hidden toolbar on Android 15+: nothing consumes the status bar, so the safe-top
        // option alone must inset the container (#655).
        assertEquals(87, SafeAreaInsetsSupport.resolveContainerTopPadding(true, false, 87, false, true));
    }

    @Test
    public void containerTopPaddingKeepsOptInWhenWindowFitsSystemWindows() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(true, false, 87, false, false));
    }

    @Test
    public void containerTopPaddingRequiresSafeTopOption() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(false, true, 87, false, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(false, true, 87, false, true));
    }

    @Test
    public void bottomInsetIsForcedOnEdgeToEdgeEvenWithoutOptIn() {
        // Android 15 forces edge-to-edge, so the nav-bar inset applies regardless of the opt-in.
        assertTrue(SafeAreaInsetsSupport.shouldInsetBottomForContainer(false, true));
        assertTrue(SafeAreaInsetsSupport.shouldInsetBottomForContainer(true, true));
    }

    @Test
    public void bottomInsetHonoursOptInWhenNotEdgeToEdge() {
        // Pre-Android 15 keeps the original opt-in behaviour untouched.
        assertTrue(SafeAreaInsetsSupport.shouldInsetBottomForContainer(true, false));
        assertFalse(SafeAreaInsetsSupport.shouldInsetBottomForContainer(false, false));
    }

    @Test
    public void bottomInsetForcedWhenLayoutBehindTransparentNavigationBar() {
        assertTrue(SafeAreaInsetsSupport.shouldInsetBottomForContainer(false, false, true));
    }

    @Test
    public void containerBottomPaddingAppliesNavigationBarWhenForcedByEdgeToEdge() {
        // Opt-in off, but edge-to-edge forces applyBottomInset=true, so the 126px nav bar still insets.
        boolean applyBottomInset = SafeAreaInsetsSupport.shouldInsetBottomForContainer(false, true);
        assertEquals(126, SafeAreaInsetsSupport.resolveContainerBottomPadding(applyBottomInset, 126, 0));
    }
}
