package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.json.JSONArray;
import org.junit.Test;

public class JavascriptBridgeOriginPolicyTest {

    @Test
    public void unrestrictedAllowsAnyUrl() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.unrestricted();
        assertTrue(policy.isUrlAllowed("https://evil.example/phish"));
        assertTrue(policy.isUnrestricted());
        assertEquals(Collections.singleton("*"), policy.getWebMessageAllowedOriginRules());
    }

    @Test
    public void disabledRejectsAllUrls() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.disabled();
        assertFalse(policy.isUrlAllowed("https://trusted.example/"));
        assertTrue(policy.isDisabled());
        assertTrue(policy.getWebMessageAllowedOriginRules().isEmpty());
    }

    @Test
    public void allowlistMatchesExactOrigin() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Collections.singletonList("https://login.example.com")
        );
        assertTrue(policy.isUrlAllowed("https://login.example.com/oauth"));
        assertFalse(policy.isUrlAllowed("https://evil.example.com/"));
        assertFalse(policy.isUrlAllowed("http://login.example.com/"));
    }

    @Test
    public void allowlistSupportsHostWildcards() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Collections.singletonList("https://*.trusted.example")
        );
        assertTrue(policy.isUrlAllowed("https://app.trusted.example/path"));
        assertFalse(policy.isUrlAllowed("https://trusted.example/"));
        assertFalse(policy.isUrlAllowed("https://evil.example/"));
    }

    @Test
    public void allowlistHonorsNonDefaultPorts() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Collections.singletonList("https://localhost:8443")
        );
        assertTrue(policy.isUrlAllowed("https://localhost:8443/app"));
        assertFalse(policy.isUrlAllowed("https://localhost/app"));
    }

    @Test
    public void allowlistRejectsUserInfoAndSuffixConfusion() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Arrays.asList("https://login.example.com", "https://*.trusted.example")
        );
        assertFalse(policy.isUrlAllowed("https://login.example.com@evil.example/"));
        assertFalse(policy.isUrlAllowed("https://eviltrusted.example/"));
    }

    @Test
    public void originFromUrlNormalizesDefaultPorts() {
        assertEquals("https://example.com", JavascriptBridgeOriginPolicy.originFromUrl("https://example.com:443/page"));
        assertEquals("http://example.com", JavascriptBridgeOriginPolicy.originFromUrl("http://example.com:80/"));
    }

    @Test
    public void webMessageRulesMirrorAllowlist() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Arrays.asList("https://login.example.com", "https://*.trusted.example")
        );
        assertTrue(policy.getWebMessageAllowedOriginRules().contains("https://login.example.com"));
        assertTrue(policy.getWebMessageAllowedOriginRules().contains("https://*.trusted.example"));
    }

    @Test
    public void webMessageRulesRetainWildcardHostPorts() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Collections.singletonList("https://*.trusted.example:8443")
        );
        assertTrue(policy.getWebMessageAllowedOriginRules().contains("https://*.trusted.example:8443"));
    }

    @Test
    public void webMessageRulesUseSchemeOnlyForCustomSchemes() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("myapp://localhost"));
        assertTrue(policy.getWebMessageAllowedOriginRules().contains("myapp://"));
    }

    @Test
    public void emptyAllowlistIsDisabled() {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(Collections.emptyList());
        assertTrue(policy.isDisabled());
    }

    @Test
    public void validatePluginOriginsRawValueRejectsNonArray() {
        assertEquals(
            "javascriptBridgeOrigins must be an array of strings",
            JavascriptBridgeOriginPolicy.validatePluginOriginsRawValue("https://login.example.com")
        );
        assertNull(JavascriptBridgeOriginPolicy.validatePluginOriginsRawValue(null));
        assertNull(JavascriptBridgeOriginPolicy.validatePluginOriginsRawValue(new JSONArray()));
    }

    @Test
    public void proxyBridgeOriginSerializationUsesAllowlistRules() throws Exception {
        JavascriptBridgeOriginPolicy policy = JavascriptBridgeOriginPolicy.fromAllowlist(
            Arrays.asList("https://login.example.com", "https://*.trusted.example")
        );
        assertEquals("allowlist", policy.proxyBridgeOriginModeLabel());
        JSONArray rules = new JSONArray(policy.proxyBridgeOriginRulesJson());
        assertEquals(2, rules.length());
        assertEquals("https://login.example.com", rules.getString(0));
    }
}
