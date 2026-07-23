package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;

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
    public void bottomMarginFollowsSafeBottomOptionAndKeyboardInset() {
        assertEquals(16, SafeAreaInsetsSupport.resolveBottomMargin(true, 16, 0));
        assertEquals(0, SafeAreaInsetsSupport.resolveBottomMargin(false, 16, 0));
        assertEquals(280, SafeAreaInsetsSupport.resolveBottomMargin(false, 16, 280));
        assertEquals(280, SafeAreaInsetsSupport.resolveBottomMargin(true, 16, 280));
    }

    @Test
    public void imeBottomInsetOnlyAppliedForEdgeToEdgeWindows() {
        assertEquals(0, SafeAreaInsetsSupport.resolveImeBottomInset(true, 280, false));
        assertEquals(280, SafeAreaInsetsSupport.resolveImeBottomInset(true, 280, true));
        assertEquals(0, SafeAreaInsetsSupport.resolveImeBottomInset(false, 280, true));
        assertEquals(0, SafeAreaInsetsSupport.resolveImeBottomInset(true, 0, true));
    }

    @Test
    public void topMarginRequiresSafeTopAndExplicitTopInsetWithoutAppBarHandling() {
        assertEquals(48, SafeAreaInsetsSupport.resolveTopMargin(true, true, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMargin(false, true, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMargin(true, false, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMargin(true, true, 48, true));
    }

    @Test
    public void topMarginUsesFallbackWhenInsetsAreZeroAndOptionEnabled() {
        assertEquals(48, SafeAreaInsetsSupport.resolveTopMarginWithFallback(true, true, 0, false, 48, true));
    }

    @Test
    public void topMarginIgnoresFallbackWhenInsetsArePresent() {
        assertEquals(32, SafeAreaInsetsSupport.resolveTopMarginWithFallback(true, true, 32, false, 48, true));
    }

    @Test
    public void topMarginIgnoresFallbackWhenOptionDisabled() {
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMarginWithFallback(true, true, 0, false, 48, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMarginWithFallback(false, true, 0, false, 48, true));
        assertEquals(0, SafeAreaInsetsSupport.resolveTopMarginWithFallback(true, false, 0, false, 48, true));
    }

    @Test
    public void containerBottomPaddingAddsAppBarDisplacementToNavigationBarInset() {
        // Android 15 device: 126px navigation bar + 87px status-bar appbar displacement = 213px.
        assertEquals(213, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, 126, true, 87));
    }

    @Test
    public void containerBottomPaddingOmitsCompensationWhenAppBarDoesNotHandleTopInset() {
        assertEquals(126, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, 126, false, 87));
    }

    @Test
    public void containerBottomPaddingCompensatesAppBarEvenWhenSafeMarginDisabled() {
        // The appbar top-margin displaces the WebView bottom regardless of the safe-margin option,
        // so the displacement must still be compensated to keep bottom content on-screen.
        assertEquals(87, SafeAreaInsetsSupport.resolveContainerBottomPadding(false, 126, true, 87));
    }

    @Test
    public void containerBottomPaddingIsZeroWithoutSafeMarginOrAppBarDisplacement() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerBottomPadding(false, 126, false, 87));
    }

    @Test
    public void containerBottomPaddingClampsNegativeInputsToZero() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerBottomPadding(true, -10, true, -5));
    }

    @Test
    public void containerTopPaddingIsZeroWhenAppBarHandlesTopInset() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(true, true, 87, true));
    }

    @Test
    public void containerTopPaddingUsesStatusBarWhenAppBarDoesNotHandleTop() {
        assertEquals(87, SafeAreaInsetsSupport.resolveContainerTopPadding(true, true, 87, false));
    }

    @Test
    public void containerTopPaddingRequiresBothSafeTopAndExplicitTopInset() {
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(false, true, 87, false));
        assertEquals(0, SafeAreaInsetsSupport.resolveContainerTopPadding(true, false, 87, false));
    }
}
