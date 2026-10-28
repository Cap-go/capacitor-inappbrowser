package ee.forgr.capacitor_inappbrowser;

import androidx.annotation.Nullable;
import com.getcapacitor.JSArray;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Controls which document origins may invoke native JavaScript bridges in {@link WebViewDialog}.
 */
public final class JavascriptBridgeOriginPolicy {

    public enum Mode {
        UNRESTRICTED,
        DISABLED,
        ALLOWLIST
    }

    private static final JavascriptBridgeOriginPolicy UNRESTRICTED = new JavascriptBridgeOriginPolicy(
        Mode.UNRESTRICTED,
        Collections.emptyList()
    );

    private static final JavascriptBridgeOriginPolicy DISABLED = new JavascriptBridgeOriginPolicy(Mode.DISABLED, Collections.emptyList());

    private final Mode mode;
    private final List<String> allowlistRules;

    private JavascriptBridgeOriginPolicy(Mode mode, List<String> allowlistRules) {
        this.mode = mode;
        this.allowlistRules = allowlistRules;
    }

    public static JavascriptBridgeOriginPolicy unrestricted() {
        return UNRESTRICTED;
    }

    public static JavascriptBridgeOriginPolicy disabled() {
        return DISABLED;
    }

    public static JavascriptBridgeOriginPolicy fromAllowlist(List<String> rules) {
        List<String> normalized = new ArrayList<>();
        if (rules != null) {
            for (String rule : rules) {
                if (rule != null && !rule.trim().isEmpty()) {
                    normalized.add(rule.trim());
                }
            }
        }
        if (normalized.isEmpty()) {
            return DISABLED;
        }
        return new JavascriptBridgeOriginPolicy(Mode.ALLOWLIST, normalized);
    }

    @Nullable
    public static String validatePluginOriginsRawValue(@Nullable Object rawValue) {
        if (rawValue == null || rawValue == JSONObject.NULL) {
            return null;
        }
        if (!(rawValue instanceof JSONArray)) {
            return "javascriptBridgeOrigins must be an array of strings";
        }
        return null;
    }

    public String proxyBridgeOriginModeLabel() {
        switch (mode) {
            case UNRESTRICTED:
                return "unrestricted";
            case DISABLED:
                return "disabled";
            case ALLOWLIST:
            default:
                return "allowlist";
        }
    }

    public String proxyBridgeOriginRulesJson() {
        if (mode != Mode.ALLOWLIST || allowlistRules.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < allowlistRules.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(quoteJsonString(allowlistRules.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String quoteJsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Nullable
    public static JavascriptBridgeOriginPolicy fromPluginArray(@Nullable JSArray array) {
        if (array == null) {
            return unrestricted();
        }
        if (array.length() == 0) {
            return disabled();
        }
        List<String> rules = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                String rule = array.getString(i);
                if (rule != null && !rule.trim().isEmpty()) {
                    rules.add(rule.trim());
                }
            } catch (Exception ignored) {}
        }
        return fromAllowlist(rules);
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isUnrestricted() {
        return mode == Mode.UNRESTRICTED;
    }

    public boolean isDisabled() {
        return mode == Mode.DISABLED;
    }

    public boolean isUrlAllowed(@Nullable String url) {
        if (isUnrestricted()) {
            return true;
        }
        if (isDisabled()) {
            return false;
        }
        String origin = originFromUrl(url);
        if (origin == null) {
            return false;
        }
        return isOriginAllowed(origin);
    }

    public boolean isOriginAllowed(@Nullable String origin) {
        if (isUnrestricted()) {
            return true;
        }
        if (isDisabled() || isBlank(origin)) {
            return false;
        }
        String normalizedOrigin = normalizeOrigin(origin);
        if (normalizedOrigin == null) {
            return false;
        }
        for (String rule : allowlistRules) {
            if (matchesAllowRule(normalizedOrigin, rule)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getWebMessageAllowedOriginRules() {
        if (isUnrestricted()) {
            return Collections.singleton("*");
        }
        if (isDisabled()) {
            return Collections.emptySet();
        }
        Set<String> rules = new HashSet<>();
        for (String rule : allowlistRules) {
            String converted = toWebMessageOriginRule(rule);
            if (converted != null) {
                rules.add(converted);
            }
        }
        return rules.isEmpty() ? Collections.emptySet() : rules;
    }

    @Nullable
    static String originFromUrl(@Nullable String url) {
        if (isBlank(url)) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            return buildOrigin(scheme, host, uri.getPort());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    static String normalizeOrigin(String origin) {
        if (isBlank(origin)) {
            return null;
        }
        String trimmed = origin.trim();
        String candidate = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        ParsedOrigin parts = parseOriginParts(candidate);
        if (parts == null) {
            return null;
        }
        return buildOrigin(parts.scheme, parts.host, parts.port);
    }

    @Nullable
    static String buildOrigin(String scheme, String host, int port) {
        if (scheme == null || host == null) {
            return null;
        }
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        String lowerHost = host.toLowerCase(Locale.ROOT);
        int normalizedPort = port;
        if (normalizedPort < 0) {
            normalizedPort = defaultPort(lowerScheme);
        }
        int defaultPort = defaultPort(lowerScheme);
        if (normalizedPort == defaultPort) {
            return lowerScheme + "://" + lowerHost;
        }
        return lowerScheme + "://" + lowerHost + ":" + normalizedPort;
    }

    static int defaultPort(String scheme) {
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }

    static boolean matchesAllowRule(String normalizedOrigin, String rule) {
        if (normalizedOrigin == null || isBlank(rule)) {
            return false;
        }
        String normalizedRule = normalizeOrigin(rule.trim());
        if (normalizedRule == null) {
            return false;
        }
        if (normalizedOrigin.equalsIgnoreCase(normalizedRule)) {
            return true;
        }

        ParsedOrigin originParts = parseOriginParts(normalizedOrigin);
        ParsedOrigin ruleParts = parseOriginParts(normalizedRule);
        if (originParts == null || ruleParts == null) {
            return false;
        }
        if (!originParts.scheme.equalsIgnoreCase(ruleParts.scheme)) {
            return false;
        }
        if (originParts.port != ruleParts.port) {
            return false;
        }
        if (originParts.host.equalsIgnoreCase(ruleParts.host)) {
            return true;
        }
        return matchesHostWildcard(originParts.host.toLowerCase(Locale.ROOT), ruleParts.host.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesHostWildcard(String host, String patternHost) {
        if (!patternHost.startsWith("*.")) {
            return false;
        }
        String baseDomain = patternHost.substring(2);
        if (baseDomain.isEmpty() || host.equalsIgnoreCase(baseDomain)) {
            return false;
        }
        return host.endsWith("." + baseDomain);
    }

    @Nullable
    private static String toWebMessageOriginRule(String rule) {
        String normalized = normalizeOrigin(rule);
        if (normalized == null) {
            return null;
        }
        ParsedOrigin parts = parseOriginParts(normalized);
        if (parts == null) {
            return null;
        }
        if (!"http".equals(parts.scheme) && !"https".equals(parts.scheme)) {
            // Custom schemes must not carry a host or port in WebMessageListener rules.
            return parts.scheme + "://";
        }
        boolean hasWildcard = parts.host.indexOf('*') >= 0;
        if (hasWildcard && !(parts.host.startsWith("*.") && parts.host.indexOf('*', 1) < 0)) {
            // Not a valid WebMessageListener host pattern; matchesAllowRule never matches it either.
            return null;
        }
        int defaultPort = defaultPort(parts.scheme);
        if (parts.port != defaultPort) {
            return parts.scheme + "://" + parts.host + ":" + parts.port;
        }
        return parts.scheme + "://" + parts.host;
    }

    @Nullable
    private static ParsedOrigin parseOriginParts(String origin) {
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme != null && host != null) {
                int port = uri.getPort() >= 0 ? uri.getPort() : defaultPort(scheme);
                return new ParsedOrigin(scheme.toLowerCase(Locale.ROOT), host, port);
            }
        } catch (Exception ignored) {}

        return parseOriginPartsManually(origin);
    }

    @Nullable
    private static ParsedOrigin parseOriginPartsManually(String origin) {
        int schemeSep = origin.indexOf("://");
        if (schemeSep <= 0) {
            return null;
        }
        String scheme = origin.substring(0, schemeSep).toLowerCase(Locale.ROOT);
        String remainder = origin.substring(schemeSep + 3);
        int pathSep = remainder.indexOf('/');
        if (pathSep >= 0) {
            remainder = remainder.substring(0, pathSep);
        }
        if (remainder.isEmpty()) {
            return null;
        }

        String host;
        int port;
        int portSep = remainder.lastIndexOf(':');
        if (portSep > 0 && remainder.indexOf(':') == portSep && !remainder.contains("]")) {
            host = remainder.substring(0, portSep);
            try {
                port = Integer.parseInt(remainder.substring(portSep + 1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        } else {
            host = remainder;
            port = defaultPort(scheme);
        }
        if (host.isEmpty()) {
            return null;
        }
        return new ParsedOrigin(scheme, host, port);
    }

    private static final class ParsedOrigin {

        final String scheme;
        final String host;
        final int port;

        ParsedOrigin(String scheme, String host, int port) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
