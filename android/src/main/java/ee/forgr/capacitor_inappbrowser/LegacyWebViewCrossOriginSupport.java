package ee.forgr.capacitor_inappbrowser;

import android.webkit.WebSettings;

/** WebSettings file-URL cross-origin toggles for validated bundled file pages. */
final class LegacyWebViewCrossOriginSupport {

    private LegacyWebViewCrossOriginSupport() {}

    static void applyTrustedBundledFileAccess(WebSettings settings, boolean enabled) {
        if (settings == null) {
            return;
        }
        applyTrustedBundledFileAccessLegacy(settings, enabled);
    }

    @SuppressWarnings("deprecation")
    private static void applyTrustedBundledFileAccessLegacy(WebSettings settings, boolean enabled) {
        settings.setAllowFileAccessFromFileURLs(enabled);
        settings.setAllowUniversalAccessFromFileURLs(enabled);
    }
}
