package ee.forgr.capacitor_inappbrowser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class CustomSchemeInterceptSupport {

    private static final Set<String> EXCLUDED_SCHEMES = new HashSet<>(Arrays.asList("http", "https", "file", "tel", "mailto", "sms"));

    private CustomSchemeInterceptSupport() {}

    static boolean shouldEmitInterceptEvent(String rawUrl) {
        String scheme = extractScheme(rawUrl);
        return scheme != null && !EXCLUDED_SCHEMES.contains(scheme);
    }

    private static String extractScheme(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        int separatorIndex = rawUrl.indexOf(':');
        if (separatorIndex <= 0) {
            return null;
        }

        String scheme = rawUrl.substring(0, separatorIndex);
        if (!isAsciiLetter(scheme.charAt(0))) {
            return null;
        }

        for (int index = 1; index < scheme.length(); index++) {
            char character = scheme.charAt(index);
            if (!isAsciiLetter(character) && !isAsciiDigit(character) && character != '+' && character != '-' && character != '.') {
                return null;
            }
        }

        return scheme.toLowerCase(Locale.ROOT);
    }

    private static boolean isAsciiLetter(char character) {
        return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
