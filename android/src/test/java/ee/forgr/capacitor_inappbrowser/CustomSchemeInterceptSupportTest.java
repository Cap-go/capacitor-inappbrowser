package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CustomSchemeInterceptSupportTest {

    @Test
    public void emitsForNonStandardCustomSchemes() {
        assertTrue(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("myapp://callback/success"));
        assertTrue(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("com.example.app://oauth/callback"));
        assertTrue(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("intent://scan/#Intent;scheme=zxing;end"));
    }

    @Test
    public void skipsWebFileAndStandardOsSchemes() {
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("https://example.com"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("http://example.com"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("file:///android_asset/index.html"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("tel:+15555550123"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("MAILTO:test@example.com"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("sms:+15555550123"));
    }

    @Test
    public void skipsUrlsWithoutValidSchemes() {
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("/callback/success"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent("1app://callback/success"));
        assertFalse(CustomSchemeInterceptSupport.shouldEmitInterceptEvent(null));
    }
}
