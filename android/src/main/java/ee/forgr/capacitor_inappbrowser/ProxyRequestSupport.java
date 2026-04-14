package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

final class ProxyRequestSupport {

    private record ParsedJsonString(String value, int nextIndex) {}

    record WebResourceResponseMetadata(String mimeType, String encoding) {}

    record ParsedResponseHeaders(Map<String, String> responseHeaders, List<String> cookieHeaders) {}

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
    private static final String[] CROSS_ORIGIN_REDIRECT_HEADER_NAMES = {
        "Authorization",
        "Cookie",
        "Cookie2",
        "Origin",
        "Proxy-Authorization",
        "Referer"
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

    static Pattern compileProxyRequestsPattern(Object rawProxyRequests) {
        if (!(rawProxyRequests instanceof String proxyRequestsPattern) || proxyRequestsPattern.isBlank()) {
            return null;
        }
        return Pattern.compile(proxyRequestsPattern);
    }

    static boolean matchesProxyRequestsPattern(Pattern pattern, String requestUrl) {
        if (pattern == null) {
            return true;
        }
        if (requestUrl == null || requestUrl.isBlank()) {
            return false;
        }
        return pattern.matcher(requestUrl).find();
    }

    static boolean shouldDelegateLegacyJsProxyRequest(Options options, String requestUrl) {
        return usesLegacyJsProxyMode(options) && matchesProxyRequestsPattern(options.getProxyRequestsPattern(), requestUrl);
    }

    static boolean shouldHandleNonBridgeRequest(Options options, String requestUrl) {
        if (options == null) {
            return false;
        }
        if (
            !options.getProxyRequests() &&
            options.getProxyRequestsPattern() == null &&
            options.getOutboundProxyRules().isEmpty() &&
            options.getInboundProxyRules().isEmpty()
        ) {
            return false;
        }
        return !usesLegacyJsProxyMode(options) || matchesProxyRequestsPattern(options.getProxyRequestsPattern(), requestUrl);
    }

    static boolean isBridgeMarkerRequestUrl(String requestUrl) {
        if (requestUrl == null || requestUrl.isBlank()) {
            return false;
        }
        try {
            URL parsedUrl = new URL(requestUrl);
            return "/_capgo_proxy_".equals(parsedUrl.getPath()) && parsedUrl.getQuery() != null && !parsedUrl.getQuery().isBlank();
        } catch (Exception error) {
            return false;
        }
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

    static Map<String, String> mergeMissingHeaders(Map<String, String> primaryHeaders, Map<String, String> fallbackHeaders) {
        Map<String, String> mergedHeaders = new LinkedHashMap<>();
        if (primaryHeaders != null) {
            mergedHeaders.putAll(primaryHeaders);
        }
        if (fallbackHeaders == null || fallbackHeaders.isEmpty()) {
            return mergedHeaders;
        }

        for (Map.Entry<String, String> entry : fallbackHeaders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || value.isBlank()) {
                continue;
            }
            if (findHeaderKeyIgnoreCase(mergedHeaders, key) != null) {
                continue;
            }
            mergedHeaders.put(key, value);
        }

        return mergedHeaders;
    }

    static boolean shouldInjectCookies(String credentialsMode, String initiatorUrl, String requestUrl, Map<String, String> requestHeaders) {
        if (findHeaderKeyIgnoreCase(requestHeaders, "Cookie") != null) {
            return false;
        }

        String normalizedCredentialsMode = normalizeCredentialsMode(credentialsMode);
        if ("omit".equals(normalizedCredentialsMode)) {
            return false;
        }
        if ("include".equals(normalizedCredentialsMode)) {
            return true;
        }
        return isSameOrigin(initiatorUrl, requestUrl);
    }

    static boolean supportsNativeHttpRequest(URL url) {
        if (url == null) {
            return false;
        }
        String protocol = url.getProtocol();
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }

    static String resolveRedirectUrl(String requestUrl, int statusCode, Map<String, String> responseHeaders) {
        if (requestUrl == null || !isFollowableRedirectStatus(statusCode)) {
            return null;
        }

        String location = findHeaderIgnoreCase(responseHeaders, "Location");
        if (location == null || location.isBlank()) {
            return null;
        }

        try {
            return new URL(new URL(requestUrl), location).toString();
        } catch (Exception error) {
            return null;
        }
    }

    static String resolveRedirectMethod(String method, int statusCode) {
        String normalizedMethod = normalizeMethod(method);
        if ("HEAD".equals(normalizedMethod)) {
            return normalizedMethod;
        }
        if (statusCode == 303) {
            return "GET";
        }
        if ("GET".equals(normalizedMethod)) {
            return normalizedMethod;
        }
        if ((statusCode == 301 || statusCode == 302) && "POST".equals(normalizedMethod)) {
            return "GET";
        }
        return normalizedMethod;
    }

    static boolean shouldPreserveRequestBodyOnRedirect(String method, int statusCode) {
        String normalizedMethod = normalizeMethod(method);
        if ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod)) {
            return false;
        }
        if (statusCode == 301 || statusCode == 302) {
            return !"POST".equals(normalizedMethod);
        }
        return statusCode == 307 || statusCode == 308;
    }

    static String resolveOverrideUrl(String requestUrl, String overrideUrl) {
        if (overrideUrl == null || overrideUrl.isBlank()) {
            return requestUrl;
        }

        try {
            return new URL(new URL(requestUrl), overrideUrl).toString();
        } catch (Exception error) {
            return overrideUrl;
        }
    }

    static String normalizeOverrideMethod(String method) {
        return normalizeMethod(method);
    }

    static String resolveBootstrapBaseUrl(String initialUrl, String effectiveUrl) {
        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            return initialUrl;
        }
        return effectiveUrl;
    }

    static Map<String, String> prepareRedirectHeaders(
        Map<String, String> originalHeaders,
        boolean preserveRequestBody,
        String requestUrl,
        String redirectUrl
    ) {
        Map<String, String> redirectedHeaders = new LinkedHashMap<>();
        if (originalHeaders != null) {
            redirectedHeaders.putAll(originalHeaders);
        }
        if (isCrossOriginRedirect(requestUrl, redirectUrl)) {
            dropHeadersIgnoreCase(redirectedHeaders, CROSS_ORIGIN_REDIRECT_HEADER_NAMES);
        }
        if (preserveRequestBody) {
            return redirectedHeaders;
        }

        dropHeaderIgnoreCase(redirectedHeaders, "Content-Encoding");
        dropHeaderIgnoreCase(redirectedHeaders, "Content-Language");
        dropHeaderIgnoreCase(redirectedHeaders, "Content-Length");
        dropHeaderIgnoreCase(redirectedHeaders, "Content-Location");
        dropHeaderIgnoreCase(redirectedHeaders, "Content-Type");
        dropHeaderIgnoreCase(redirectedHeaders, "Transfer-Encoding");
        return redirectedHeaders;
    }

    static Map<String, String> prepareOverrideHeaders(Map<String, String> originalHeaders, String requestUrl, String overrideUrl) {
        Map<String, String> overrideHeaders = new LinkedHashMap<>();
        if (originalHeaders != null) {
            overrideHeaders.putAll(originalHeaders);
        }
        if (isCrossOriginRedirect(requestUrl, overrideUrl)) {
            dropHeadersIgnoreCase(overrideHeaders, CROSS_ORIGIN_REDIRECT_HEADER_NAMES);
        }
        return overrideHeaders;
    }

    static String resolveOverrideBody(String currentBase64Body, String method, boolean hasBodyOverride, String overrideBody) {
        if (shouldDropRequestBody(method)) {
            return "";
        }
        if (!hasBodyOverride) {
            return currentBase64Body != null ? currentBase64Body : "";
        }
        return overrideBody != null ? overrideBody : "";
    }

    static byte[] decodeBase64Body(String base64Body) throws IOException {
        if (base64Body == null || base64Body.isEmpty()) {
            return new byte[0];
        }

        try {
            return Base64.getMimeDecoder().decode(base64Body);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid base64 request body", error);
        }
    }

    static boolean shouldLetWebViewHandleMissingBody(String requestUrl, String method, String base64Body) {
        if (isBridgeMarkerRequestUrl(requestUrl)) {
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

    static ParsedResponseHeaders splitResponseHeaders(Map<String, List<String>> rawHeaders) {
        Map<String, String> responseHeaders = new HashMap<>();
        List<String> cookieHeaders = new java.util.ArrayList<>();
        if (rawHeaders == null || rawHeaders.isEmpty()) {
            return new ParsedResponseHeaders(responseHeaders, cookieHeaders);
        }

        for (Map.Entry<String, List<String>> entry : rawHeaders.entrySet()) {
            String headerName = entry.getKey();
            List<String> headerValues = entry.getValue();
            if (headerName == null || headerValues == null || headerValues.isEmpty()) {
                continue;
            }

            if (isCookieResponseHeader(headerName)) {
                for (String headerValue : headerValues) {
                    if (headerValue != null && !headerValue.isBlank()) {
                        cookieHeaders.add(headerValue);
                    }
                }
                continue;
            }

            String headerValue = headerValues
                .stream()
                .filter((value) -> value != null && !value.isBlank())
                .reduce((first, second) -> first + ", " + second)
                .orElse(null);
            if (headerValue == null) {
                continue;
            }
            responseHeaders.put(headerName, headerValue);
        }

        return new ParsedResponseHeaders(responseHeaders, cookieHeaders);
    }

    static ParsedResponseHeaders splitSyntheticResponseHeaders(Map<String, String> rawHeaders) {
        Map<String, String> responseHeaders = new HashMap<>();
        List<String> cookieHeaders = new java.util.ArrayList<>();
        if (rawHeaders == null || rawHeaders.isEmpty()) {
            return new ParsedResponseHeaders(responseHeaders, cookieHeaders);
        }

        for (Map.Entry<String, String> entry : rawHeaders.entrySet()) {
            String headerName = entry.getKey();
            String headerValue = entry.getValue();
            if (headerName == null || headerValue == null || headerValue.isBlank()) {
                continue;
            }

            if (isCookieResponseHeader(headerName)) {
                cookieHeaders.add(headerValue);
                continue;
            }

            responseHeaders.put(headerName, headerValue);
        }

        return new ParsedResponseHeaders(responseHeaders, cookieHeaders);
    }

    static JSObject normalizeLegacySyntheticResponse(JSONObject legacyResponse) {
        JSObject normalizedResponse = new JSObject();
        if (legacyResponse == null) {
            normalizedResponse.put("status", 200);
            normalizedResponse.put("body", "");
            return normalizedResponse;
        }

        normalizedResponse.put(
            "status",
            normalizeLegacySyntheticStatus(
                legacyResponse.has("status") ? legacyResponse.optInt("status") : null,
                legacyResponse.has("code") ? legacyResponse.optInt("code") : null
            )
        );
        normalizedResponse.put(
            "body",
            normalizeLegacySyntheticBody(
                legacyResponse.opt("body"),
                legacyResponse.optBoolean("base64Encoded", false) ||
                    legacyResponse.optBoolean("bodyIsBase64", false) ||
                    legacyResponse.optBoolean("isBase64", false)
            )
        );

        JSONObject headers = legacyResponse.optJSONObject("headers");
        if (headers != null) {
            Map<String, Object> normalizedHeaderMap = new LinkedHashMap<>();
            java.util.Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = headers.opt(key);
                normalizedHeaderMap.put(key, value);
            }
            JSObject normalizedHeaders = new JSObject();
            for (Map.Entry<String, String> entry : normalizeLegacyStringMap(normalizedHeaderMap).entrySet()) {
                normalizedHeaders.put(entry.getKey(), entry.getValue());
            }
            normalizedResponse.put("headers", normalizedHeaders);
        }

        return normalizedResponse;
    }

    static int normalizeLegacySyntheticStatus(Integer status, Integer code) {
        if (status != null) {
            return status;
        }
        if (code != null) {
            return code;
        }
        return 200;
    }

    static String normalizeLegacySyntheticBody(Object rawBody, boolean isBase64Body) {
        if (rawBody == null || rawBody == JSONObject.NULL) {
            return "";
        }

        String normalizedBody = rawBody.toString();
        if (isBase64Body) {
            return normalizedBody;
        }
        return Base64.getEncoder().encodeToString(normalizedBody.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> normalizeLegacyStringMap(Map<String, ?> rawMap) {
        Map<String, String> normalizedMap = new LinkedHashMap<>();
        if (rawMap == null || rawMap.isEmpty()) {
            return normalizedMap;
        }

        for (Map.Entry<String, ?> entry : rawMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null || value == JSONObject.NULL) {
                continue;
            }
            normalizedMap.put(key, value.toString());
        }

        return normalizedMap;
    }

    private static boolean requiresCapturedRequestBody(String method) {
        return !shouldDropRequestBody(method);
    }

    private static boolean shouldDropRequestBody(String method) {
        String normalizedMethod = normalizeMethod(method);
        return "GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod);
    }

    private static String normalizeMethod(String method) {
        if (method == null) {
            return "GET";
        }
        return method.trim().toUpperCase(Locale.US);
    }

    private static String normalizeCredentialsMode(String credentialsMode) {
        if (credentialsMode == null) {
            return "same-origin";
        }
        String normalizedCredentialsMode = credentialsMode.trim().toLowerCase(Locale.US);
        if ("omit".equals(normalizedCredentialsMode) || "include".equals(normalizedCredentialsMode)) {
            return normalizedCredentialsMode;
        }
        return "same-origin";
    }

    private static boolean isFollowableRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private static boolean isCrossOriginRedirect(String requestUrl, String redirectUrl) {
        try {
            URL request = new URL(requestUrl);
            URL redirect = new URL(redirectUrl);
            return (
                !request.getProtocol().equalsIgnoreCase(redirect.getProtocol()) ||
                !request.getHost().equalsIgnoreCase(redirect.getHost()) ||
                effectivePort(request) != effectivePort(redirect)
            );
        } catch (Exception error) {
            return true;
        }
    }

    private static boolean isSameOrigin(String firstUrl, String secondUrl) {
        if (firstUrl == null || secondUrl == null || firstUrl.isBlank() || secondUrl.isBlank()) {
            return false;
        }
        return !isCrossOriginRedirect(firstUrl, secondUrl);
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

    private static void dropHeaderIgnoreCase(Map<String, String> headers, String expectedKey) {
        String existingKey = findHeaderKeyIgnoreCase(headers, expectedKey);
        if (existingKey != null) {
            headers.remove(existingKey);
        }
    }

    private static void dropHeadersIgnoreCase(Map<String, String> headers, String[] expectedKeys) {
        for (String expectedKey : expectedKeys) {
            dropHeaderIgnoreCase(headers, expectedKey);
        }
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

    private static boolean isCookieResponseHeader(String headerName) {
        return "Set-Cookie".equalsIgnoreCase(headerName) || "Set-Cookie2".equalsIgnoreCase(headerName);
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

    private static int effectivePort(URL url) {
        int port = url.getPort();
        if (port != -1) {
            return port;
        }
        return url.getDefaultPort();
    }
}
