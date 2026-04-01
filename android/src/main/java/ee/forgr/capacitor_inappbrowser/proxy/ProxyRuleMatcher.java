package ee.forgr.capacitor_inappbrowser.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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
            this.urlPattern = Pattern.compile(urlPattern);
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

    public NativeProxyRule match(String url, String method) {
        for (NativeProxyRule rule : rules) {
            if (!rule.urlPattern.matcher(url).find()) continue;
            if (rule.methods != null && !rule.methods.contains(method.toUpperCase())) continue;
            return rule;
        }
        return null;
    }

    public boolean anyRuleCouldMatchHost(String host) {
        for (NativeProxyRule rule : rules) {
            if (rule.urlPattern.matcher(host).find()) return true;
        }
        return false;
    }

    public static List<NativeProxyRule> parseFromJson(JSONArray jsonArray) throws Exception {
        List<NativeProxyRule> rules = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            String ruleName = obj.getString("ruleName");
            String urlPattern = obj.getString("urlPattern");
            List<String> methods = null;
            if (obj.has("methods") && !obj.isNull("methods")) {
                JSONArray methodsArr = obj.getJSONArray("methods");
                methods = new ArrayList<>();
                for (int j = 0; j < methodsArr.length(); j++) {
                    methods.add(methodsArr.getString(j).toUpperCase());
                }
            }
            boolean includeBody = obj.optBoolean("includeBody", false);
            String intercept = obj.getString("intercept");
            rules.add(new NativeProxyRule(ruleName, urlPattern, methods, includeBody, intercept));
        }
        return rules;
    }
}
