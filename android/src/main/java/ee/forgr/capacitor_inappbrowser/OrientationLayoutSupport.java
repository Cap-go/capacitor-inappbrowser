package ee.forgr.capacitor_inappbrowser;

/**
 * Helpers for deciding when a managed WebView dialog must relayout after a configuration
 * change. Capacitor activities declare {@code android:configChanges}, so the host activity
 * (and this dialog) are not recreated on rotation — layouts and window insets must be
 * refreshed explicitly or the WebView can stop scrolling after portrait→landscape.
 */
final class OrientationLayoutSupport {

    private OrientationLayoutSupport() {}

    static boolean shouldRefreshBrowserLayout(
        Integer previousOrientation,
        Integer previousScreenWidthDp,
        Integer previousScreenHeightDp,
        Integer previousSmallestScreenWidthDp,
        Integer previousDensityDpi,
        int currentOrientation,
        int currentScreenWidthDp,
        int currentScreenHeightDp,
        int currentSmallestScreenWidthDp,
        int currentDensityDpi
    ) {
        if (previousOrientation == null) {
            return true;
        }

        return (
            previousOrientation != currentOrientation ||
            previousScreenWidthDp == null ||
            previousScreenWidthDp != currentScreenWidthDp ||
            previousScreenHeightDp == null ||
            previousScreenHeightDp != currentScreenHeightDp ||
            previousSmallestScreenWidthDp == null ||
            previousSmallestScreenWidthDp != currentSmallestScreenWidthDp ||
            previousDensityDpi == null ||
            previousDensityDpi != currentDensityDpi
        );
    }
}
