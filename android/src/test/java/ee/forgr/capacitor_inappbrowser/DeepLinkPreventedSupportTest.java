package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DeepLinkPreventedSupportTest {

    @Test
    public void emitsForBlockedCustomSchemesAndOsHandlers() {
        assertTrue(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("myapp://oauth", Collections.emptyList()));
        assertTrue(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("tel:+15555550123", Collections.emptyList()));
        assertTrue(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("mailto:test@example.com", Collections.emptyList()));
        assertTrue(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("sms:+15555550123", Collections.emptyList()));
    }

    @Test
    public void emitsForAuthorizedHttpLinks() {
        assertTrue(
            DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent(
                "https://partner.example/path",
                Arrays.asList("https://partner.example")
            )
        );
    }

    @Test
    public void skipsNormalHttpNavigationAndInternalSchemes() {
        assertFalse(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("https://example.com/page", Collections.emptyList()));
        assertFalse(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("data:text/html,hello", Collections.emptyList()));
        assertFalse(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("about:blank", Collections.emptyList()));
    }

    @Test
    public void emitsForAppStoreHosts() {
        assertTrue(DeepLinkPreventedSupport.shouldEmitDeepLinkPreventedEvent("https://apps.apple.com/app/id123", Collections.emptyList()));
    }
}
