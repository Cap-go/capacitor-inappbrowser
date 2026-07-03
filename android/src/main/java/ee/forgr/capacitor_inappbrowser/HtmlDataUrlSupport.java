package ee.forgr.capacitor_inappbrowser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

final class HtmlDataUrlSupport {

    private static final String MIME_PREFIX = "data:text/html";
    private static final String BASE64_MARKER = ";base64,";

    private HtmlDataUrlSupport() {}

    static boolean isHtmlBase64DataUrl(String url) {
        return parseHtml(url) != null;
    }

    static boolean isDataUrl(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("data:");
    }

    static String parseHtml(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(MIME_PREFIX)) {
            return null;
        }

        int base64Marker = lower.indexOf(BASE64_MARKER);
        if (base64Marker < 0) {
            return null;
        }

        String payload = url.substring(base64Marker + BASE64_MARKER.length());
        if (payload.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
