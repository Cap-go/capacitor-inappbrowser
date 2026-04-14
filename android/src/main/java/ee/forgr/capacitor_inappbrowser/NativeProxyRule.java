package ee.forgr.capacitor_inappbrowser;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class NativeProxyRule {

    public enum Action {
        CONTINUE,
        CANCEL,
        DELEGATE_TO_JS;

        public static Action fromString(String value) {
            if (value == null) {
                return CONTINUE;
            }
            switch (value) {
                case "cancel":
                    return CANCEL;
                case "delegateToJs":
                    return DELEGATE_TO_JS;
                default:
                    return CONTINUE;
            }
        }
    }

    private final String id;
    private final Pattern urlPattern;
    private final Pattern methodPattern;
    private final Pattern headerPattern;
    private final Pattern bodyPattern;
    private final Pattern statusPattern;
    private final Pattern responseHeaderPattern;
    private final Pattern responseBodyPattern;
    private final boolean mainFrameOnly;
    private final Action action;

    public NativeProxyRule(
        String id,
        Pattern urlPattern,
        Pattern methodPattern,
        Pattern headerPattern,
        Pattern bodyPattern,
        Pattern statusPattern,
        Pattern responseHeaderPattern,
        Pattern responseBodyPattern,
        boolean mainFrameOnly,
        Action action
    ) {
        this.id = id;
        this.urlPattern = urlPattern;
        this.methodPattern = methodPattern;
        this.headerPattern = headerPattern;
        this.bodyPattern = bodyPattern;
        this.statusPattern = statusPattern;
        this.responseHeaderPattern = responseHeaderPattern;
        this.responseBodyPattern = responseBodyPattern;
        this.mainFrameOnly = mainFrameOnly;
        this.action = action;
    }

    public String getId() {
        return id;
    }

    public boolean isMainFrameOnly() {
        return mainFrameOnly;
    }

    public Action getAction() {
        return action;
    }

    public boolean matches(
        String url,
        String method,
        String serializedHeaders,
        String decodedBody,
        boolean isMainFrame,
        Integer statusCode,
        String serializedResponseHeaders,
        String decodedResponseBody
    ) {
        if (mainFrameOnly && !isMainFrame) {
            return false;
        }
        if (!matchesPattern(urlPattern, url)) {
            return false;
        }
        if (!matchesPattern(methodPattern, method)) {
            return false;
        }
        if (!matchesPattern(headerPattern, serializedHeaders)) {
            return false;
        }
        if (!matchesPattern(bodyPattern, decodedBody)) {
            return false;
        }
        if (!matchesPattern(statusPattern, statusCode != null ? String.valueOf(statusCode) : null)) {
            return false;
        }
        if (!matchesPattern(responseHeaderPattern, serializedResponseHeaders)) {
            return false;
        }
        if (!matchesPattern(responseBodyPattern, decodedResponseBody)) {
            return false;
        }
        return true;
    }

    private boolean matchesPattern(Pattern pattern, String value) {
        if (pattern == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return pattern.matcher(value).find();
    }

    public static Pattern compilePattern(String raw) throws PatternSyntaxException {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return Pattern.compile(raw, Pattern.CASE_INSENSITIVE);
    }
}
