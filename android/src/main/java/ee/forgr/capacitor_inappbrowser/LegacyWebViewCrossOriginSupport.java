package ee.forgr.capacitor_inappbrowser;

import android.os.Build;
import android.webkit.WebSettings;

/** WebSettings file-URL cross-origin toggles; required only below API 30. */
final class LegacyWebViewCrossOriginSupport {

    private LegacyWebViewCrossOriginSupport() {}

    static void applyTrustedBundledFileAccess(WebSettings settings, boolean enabled) {
        if (settings == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
