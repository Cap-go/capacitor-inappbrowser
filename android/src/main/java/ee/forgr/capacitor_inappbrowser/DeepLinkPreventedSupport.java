package ee.forgr.capacitor_inappbrowser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DeepLinkPreventedSupport {

    private static final Set<String> INTERNAL_SCHEMES = new HashSet<>(Arrays.asList("about", "data", "blob", "javascript"));

    private static final Set<String> APP_STORE_HOSTS = new HashSet<>(Arrays.asList("itunes.apple.com", "apps.apple.com"));

    private DeepLinkPreventedSupport() {}

    static boolean shouldEmitDeepLinkPreventedEvent(String rawUrl, List<String> authorizedAppLinks) {
        if (rawUrl == null || rawUrl.isBlank() || HtmlDataUrlSupport.isDataUrl(rawUrl)) {
            return false;
        }

        String scheme = extractScheme(rawUrl);
        if (scheme == null) {
            return false;
        }

        if (INTERNAL_SCHEMES.contains(scheme)) {
            return false;
        }

        if (!scheme.equals("http") && !scheme.equals("https")) {
            return true;
        }

        if (isAuthorizedAppLink(rawUrl, authorizedAppLinks)) {
            return true;
        }

        try {
            URI uri = new URI(rawUrl);
            String host = normalizeHost(uri.getHost());
            return host != null && APP_STORE_HOSTS.contains(host);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    static boolean isAuthorizedAppLink(String url, List<String> authorizedLinks) {
        if (authorizedLinks == null || authorizedLinks.isEmpty() || url == null) {
            return false;
        }

        final String urlHost;
        try {
            URI uri = new URI(url);
            urlHost = normalizeHost(uri.getHost());
            if (urlHost == null) {
                return false;
            }
        } catch (URISyntaxException e) {
            return false;
        }

        for (String authorized : authorizedLinks) {
            if (authorized == null) {
                continue;
            }

            try {
                URI authUri = new URI(authorized);
                String authHost = normalizeHost(authUri.getHost());
                if (authHost != null && urlHost.equalsIgnoreCase(authHost)) {
                    return true;
                }
            } catch (URISyntaxException ignored) {
                // skip invalid configured entries
            }
        }

        return false;
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        if (host.startsWith("www.")) {
            return host.substring(4);
        }
        return host;
    }

    private static String extractScheme(String rawUrl) {
        int separatorIndex = rawUrl.indexOf(':');
        if (separatorIndex <= 0) {
            return null;
        }

        String scheme = rawUrl.substring(0, separatorIndex);
        if (scheme.isEmpty()) {
            return null;
        }

        return scheme.toLowerCase(Locale.ROOT);
    }
}
