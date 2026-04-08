package ee.forgr.capacitor_inappbrowser;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;

final class ProxyRequestSupport {

    private record ParsedJsonString(String value, int nextIndex) {}

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
        Map<String, String> mergedHeaders = new HashMap<>();
        if (nativeHeaders != null) {
            mergedHeaders.putAll(nativeHeaders);
        }
        if (storedHeadersJson == null || storedHeadersJson.isEmpty()) {
            return mergedHeaders;
        }

        parseFlatJsonObject(storedHeadersJson, mergedHeaders);

        return mergedHeaders;
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
            target.put(key.value(), value.value());

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
}
