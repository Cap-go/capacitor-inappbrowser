package ee.forgr.capacitor_inappbrowser;

import java.util.Locale;

/**
 * Maps {@code downloadPreview} openWebView option values for managed download handling.
 */
final class DownloadPreviewSupport {

    static final String DEFAULT_MODE = "inAppBrowser";

    private DownloadPreviewSupport() {}

    static String normalize(String value) {
        if (value == null) {
            return null;
        }

        switch (value.toLowerCase(Locale.ROOT)) {
            case "inappbrowser":
                return "inAppBrowser";
            case "systempreview":
                return "systemPreview";
            case "none":
                return "none";
            default:
                return null;
        }
    }

    static boolean skipsPreview(String mode) {
        return "none".equals(mode);
    }

    static boolean usesInAppBrowserPreview(String mode) {
        return "inAppBrowser".equals(mode);
    }
}
