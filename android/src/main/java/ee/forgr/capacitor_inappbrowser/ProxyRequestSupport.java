package ee.forgr.capacitor_inappbrowser;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;

final class ProxyRequestSupport {

    private record ParsedJsonString(String value, int nextIndex) {}

    record WebResourceResponseMetadata(String mimeType, String encoding) {}

    private static final String[] SAFE_MARKER_HEADER_NAMES = {
        "Accept",
        "Accept-Encoding",
        "Accept-Language",
        "Cache-Control",
        "Pragma",
        "If-Modified-Since",
        "If-None-Match",
        "If-Match",
        "If-Unmodified-Since",
        "If-Range",
        "Range",
        "User-Agent"
    };

    private ProxyRequestSupport() {}

    static boolean shouldInjectBridge(Options options) {
        return options != null && options.shouldEnableNativeProxy();
    }

    static boolean usesLegacyJsProxyMode(Options options) {
        if (options == null) {
            return false;
        }
        return (
            (options.getProxyRequests() || options.getProxyRequestsPattern() != null) &&
            options.getOutboundProxyRules().isEmpty() &&
            options.getInboundProxyRules().isEmpty()
        );
    }

    static Map<String, String> mergeRequestHeaders(Map<String, String> nativeHeaders, String storedHeadersJson) throws JSONException {
        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        if (nativeHeaders != null) {
            mergedHeaders.putAll(nativeHeaders);
        }
        if (storedHeadersJson == null || storedHeadersJson.isEmpty()) {
            return mergedHeaders;
        }

        parseFlatJsonObject(storedHeadersJson, mergedHeaders);

        return mergedHeaders;
    }

    static Map<String, String> extractSafeMarkerHeaders(Map<String, String> markerHeaders) {
        Map<String, String> safeHeaders = new LinkedHashMap<>();
        if (markerHeaders == null || markerHeaders.isEmpty()) {
            return safeHeaders;
        }

        for (String safeHeaderName : SAFE_MARKER_HEADER_NAMES) {
            String existingKey = findHeaderKeyIgnoreCase(markerHeaders, safeHeaderName);
            if (existingKey == null) {
                continue;
            }
            String value = markerHeaders.get(existingKey);
            if (value == null || value.isBlank()) {
                continue;
            }
            safeHeaders.put(existingKey, value);
        }

        return safeHeaders;
    }

    static boolean supportsNativeHttpRequest(URL url) {
        if (url == null) {
            return false;
        }
        String protocol = url.getProtocol();
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }

    static boolean shouldLetWebViewHandleMissingBody(String requestUrl, String method, String base64Body) {
        if (requestUrl != null && requestUrl.contains("/_capgo_proxy_?")) {
            return false;
        }
        if (!requiresCapturedRequestBody(method)) {
            return false;
        }
        return base64Body == null || base64Body.isEmpty();
    }

    static WebResourceResponseMetadata resolveWebResourceResponseMetadata(String contentType, Map<String, String> responseHeaders) {
        String resolvedContentType = firstNonEmpty(contentType, findHeaderIgnoreCase(responseHeaders, "Content-Type"));
        if (resolvedContentType == null || resolvedContentType.isBlank()) {
            return new WebResourceResponseMetadata("application/octet-stream", null);
        }

        String[] contentTypeParts = resolvedContentType.split(";");
        String mimeType = contentTypeParts[0].trim();
        if (mimeType.isEmpty()) {
            mimeType = "application/octet-stream";
        }

        String encoding = null;
        if (isTextLikeMimeType(mimeType)) {
            String parsedCharset = null;
            for (int index = 1; index < contentTypeParts.length; index++) {
                String parameter = contentTypeParts[index].trim();
                int separatorIndex = parameter.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }
                String key = parameter.substring(0, separatorIndex).trim();
                if (!"charset".equalsIgnoreCase(key)) {
                    continue;
                }
                parsedCharset = stripOptionalQuotes(parameter.substring(separatorIndex + 1).trim());
                if (!parsedCharset.isEmpty()) {
                    break;
                }
            }
            encoding = parsedCharset != null && !parsedCharset.isEmpty() ? parsedCharset : "utf-8";
        }

        return new WebResourceResponseMetadata(mimeType, encoding);
    }

    private static boolean requiresCapturedRequestBody(String method) {
        if (method == null) {
            return false;
        }
        String normalizedMethod = method.trim().toUpperCase(Locale.US);
        return "POST".equals(normalizedMethod) || "PUT".equals(normalizedMethod) || "PATCH".equals(normalizedMethod);
    }

    private static void parseFlatJsonObject(String json, Map<String, String> target) throws JSONException {
        String trimmedJson = json.trim();
        if (trimmedJson.isEmpty() || "{}".equals(trimmedJson)) {
            return;
        }
        if (trimmedJson.charAt(0) != '{' || trimmedJson.charAt(trimmedJson.length() - 1) != '}') {
            throw new JSONException("Expected JSON object");
        }

        int index = 1;
        while (index < trimmedJson.length() - 1) {
            index = skipWhitespace(trimmedJson, index);
            if (index >= trimmedJson.length() - 1) {
                break;
            }

            ParsedJsonString key = parseJsonString(trimmedJson, index);
            index = skipWhitespace(trimmedJson, key.nextIndex());
            if (index >= trimmedJson.length() || trimmedJson.charAt(index) != ':') {
                throw new JSONException("Expected ':' after JSON object key");
            }

            index = skipWhitespace(trimmedJson, index + 1);
            ParsedJsonString value = parseJsonString(trimmedJson, index);
            putHeaderCaseInsensitive(target, key.value(), value.value());

            index = skipWhitespace(trimmedJson, value.nextIndex());
            if (index >= trimmedJson.length() - 1) {
                break;
            }
            char separator = trimmedJson.charAt(index);
            if (separator == ',') {
                index++;
                continue;
            }
            if (separator == '}') {
                break;
            }
            throw new JSONException("Expected ',' or '}' in JSON object");
        }
    }

    private static ParsedJsonString parseJsonString(String json, int startIndex) throws JSONException {
        if (startIndex >= json.length() || json.charAt(startIndex) != '"') {
            throw new JSONException("Expected JSON string");
        }

        StringBuilder value = new StringBuilder();
        int index = startIndex + 1;
        while (index < json.length()) {
            char currentChar = json.charAt(index);
            if (currentChar == '"') {
                return new ParsedJsonString(value.toString(), index + 1);
            }
            if (currentChar != '\\') {
                value.append(currentChar);
                index++;
                continue;
            }

            index++;
            if (index >= json.length()) {
                throw new JSONException("Invalid escape sequence in JSON string");
            }

            char escapedChar = json.charAt(index);
            switch (escapedChar) {
                case '"':
                case '\\':
                case '/':
                    value.append(escapedChar);
                    index++;
                    break;
                case 'b':
                    value.append('\b');
                    index++;
                    break;
                case 'f':
                    value.append('\f');
                    index++;
                    break;
                case 'n':
                    value.append('\n');
                    index++;
                    break;
                case 'r':
                    value.append('\r');
                    index++;
                    break;
                case 't':
                    value.append('\t');
                    index++;
                    break;
                case 'u':
                    if (index + 4 >= json.length()) {
                        throw new JSONException("Invalid unicode escape in JSON string");
                    }
                    String hexValue = json.substring(index + 1, index + 5);
                    try {
                        value.append((char) Integer.parseInt(hexValue, 16));
                    } catch (NumberFormatException error) {
                        throw new JSONException("Invalid unicode escape in JSON string");
                    }
                    index += 5;
                    break;
                default:
                    throw new JSONException("Unsupported escape sequence in JSON string");
            }
        }

        throw new JSONException("Unterminated JSON string");
    }

    private static int skipWhitespace(String value, int index) {
        int currentIndex = index;
        while (currentIndex < value.length() && Character.isWhitespace(value.charAt(currentIndex))) {
            currentIndex++;
        }
        return currentIndex;
    }

    private static void putHeaderCaseInsensitive(Map<String, String> target, String key, String value) {
        String existingKey = findHeaderIgnoreCase(target, key) != null ? findHeaderKeyIgnoreCase(target, key) : null;
        if (existingKey != null) {
            target.remove(existingKey);
        }
        target.put(key, value);
    }

    private static String findHeaderIgnoreCase(Map<String, String> headers, String expectedKey) {
        String resolvedKey = findHeaderKeyIgnoreCase(headers, expectedKey);
        return resolvedKey != null ? headers.get(resolvedKey) : null;
    }

    private static String findHeaderKeyIgnoreCase(Map<String, String> headers, String expectedKey) {
        if (headers == null || expectedKey == null) {
            return null;
        }
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(expectedKey)) {
                return key;
            }
        }
        return null;
    }

    private static String firstNonEmpty(String primaryValue, String fallbackValue) {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return primaryValue;
        }
        if (fallbackValue != null && !fallbackValue.isBlank()) {
            return fallbackValue;
        }
        return null;
    }

    private static boolean isTextLikeMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String normalizedMimeType = mimeType.trim().toLowerCase(Locale.US);
        return (
            normalizedMimeType.startsWith("text/") ||
            normalizedMimeType.equals("application/json") ||
            normalizedMimeType.equals("application/javascript") ||
            normalizedMimeType.equals("application/x-javascript") ||
            normalizedMimeType.equals("application/xml") ||
            normalizedMimeType.equals("application/xhtml+xml") ||
            normalizedMimeType.equals("application/x-www-form-urlencoded") ||
            normalizedMimeType.endsWith("+json") ||
            normalizedMimeType.endsWith("+xml") ||
            normalizedMimeType.endsWith("+javascript")
        );
    }

    private static String stripOptionalQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value != null ? value : "";
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
