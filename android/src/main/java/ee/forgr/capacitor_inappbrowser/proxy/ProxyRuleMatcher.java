package ee.forgr.capacitor_inappbrowser.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProxyRuleMatcher {

    public static class NativeProxyRule {

        public final String ruleName;
        public final Pattern urlPattern;
        public final List<String> methods; // null = all methods
        public final boolean includeBody;
        public final String intercept; // "request" | "response" | "both"

        public NativeProxyRule(String ruleName, String urlPattern, List<String> methods, boolean includeBody, String intercept) {
            this.ruleName = ruleName;
            try {
                this.urlPattern = Pattern.compile(urlPattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex for rule '" + ruleName + "': " + e.getMessage(), e);
            }
            this.methods = methods;
            this.includeBody = includeBody;
            this.intercept = intercept;
        }

        public boolean interceptsRequest() {
            return "request".equals(intercept) || "both".equals(intercept);
        }

        public boolean interceptsResponse() {
            return "response".equals(intercept) || "both".equals(intercept);
        }
    }

    private final List<NativeProxyRule> rules;

    public ProxyRuleMatcher(List<NativeProxyRule> rules) {
        this.rules = rules;
    }

    public NativeProxyRule matchRequest(String url, String method) {
        for (NativeProxyRule rule : rules) {
            if (!rule.interceptsRequest()) continue;
            if (!matches(rule, url, method)) continue;
            return rule;
        }
        return null;
    }

    public NativeProxyRule matchResponse(String url, String method) {
        for (NativeProxyRule rule : rules) {
            if (!rule.interceptsResponse()) continue;
            if (!matches(rule, url, method)) continue;
            return rule;
        }
        return null;
    }

    public boolean anyRuleCouldMatchHost(String host) {
        String[] candidates = new String[] { host, "http://" + host, "http://" + host + "/", "https://" + host, "https://" + host + "/" };
        for (NativeProxyRule rule : rules) {
            for (String candidate : candidates) {
                if (matchesUrl(rule.urlPattern, candidate)) {
                    return true;
                }
            }

            String pattern = rule.urlPattern.pattern().toLowerCase();
            if (!pattern.contains("://")) continue;

            String hostHint = urlScopedHostHint(pattern);
            if (hostHint != null) {
                if (hostHint.equals(host.toLowerCase())) {
                    return true;
                }
                continue;
            }

            return true;
        }
        return false;
    }

    public static List<NativeProxyRule> parseFromJson(JSONArray jsonArray) throws Exception {
        List<NativeProxyRule> rules = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            String ruleName = obj.getString("ruleName");
            String urlPattern = obj.optString("regex", obj.optString("urlPattern", null));
            if (urlPattern == null || urlPattern.isEmpty()) {
                throw new IllegalArgumentException("Missing regex for rule '" + ruleName + "'");
            }
            List<String> methods = null;
            if (obj.has("methods") && !obj.isNull("methods")) {
                JSONArray methodsArr = obj.getJSONArray("methods");
                methods = new ArrayList<>();
                for (int j = 0; j < methodsArr.length(); j++) {
                    methods.add(methodsArr.getString(j).toUpperCase());
                }
            }
            boolean includeBody = obj.optBoolean("includeBody", false);
            String intercept = obj.optString("mode", obj.optString("intercept", null));
            if (intercept == null || intercept.isEmpty()) {
                throw new IllegalArgumentException("Missing mode for rule '" + ruleName + "'");
            }
            if (!"request".equals(intercept) && !"response".equals(intercept) && !"both".equals(intercept)) {
                throw new IllegalArgumentException("Invalid mode '" + intercept + "' for rule '" + ruleName + "'");
            }
            rules.add(new NativeProxyRule(ruleName, urlPattern, methods, includeBody, intercept));
        }
        return rules;
    }

    private boolean matches(NativeProxyRule rule, String url, String method) {
        if (!matchesUrl(rule.urlPattern, url)) return false;
        return rule.methods == null || rule.methods.contains(method.toUpperCase());
    }

    private boolean matchesUrl(Pattern pattern, String url) {
        return pattern.matcher(url).matches();
    }

    private String urlScopedHostHint(String pattern) {
        int schemeIndex = pattern.indexOf("://");
        if (schemeIndex < 0) {
            return null;
        }

        String remainder = pattern.substring(schemeIndex + 3);
        int slashIndex = remainder.indexOf('/');
        String authority = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
        authority = authority.replace("^", "").replace("$", "").replace("\\.", ".").replace("\\-", "-");

        if (authority.matches(".*[\\\\\\[\\]\\(\\)\\{\\}\\+\\*\\?\\|].*")) {
            return null;
        }

        return authority.toLowerCase();
    }
}
