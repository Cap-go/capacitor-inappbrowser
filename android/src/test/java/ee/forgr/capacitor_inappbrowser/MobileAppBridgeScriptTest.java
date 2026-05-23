package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MobileAppBridgeScriptTest {

    @Test
    public void postMessageWrapperStringifiesNonStringMessages() {
        String script = MobileAppBridgeScript.create(false, false);

        assertTrue(script.contains("window.mobileApp = {"));
        assertTrue(script.contains("var msg = typeof message === 'string' ? message : JSON.stringify(message);"));
        assertTrue(script.contains("msg = String(message);"));
        assertTrue(script.contains(MobileAppBridgeScript.POST_MESSAGE_BRIDGE_NAME));
        assertTrue(script.contains("postMessageBridge.postMessage(msg);"));
        assertTrue(script.contains("fallbackBridge.postMessage(msg);"));
    }

    @Test
    public void optionalBridgeMethodsFollowOptions() {
        String defaultScript = MobileAppBridgeScript.create(false, false);
        String fullScript = MobileAppBridgeScript.create(true, true);

        assertFalse(defaultScript.contains("hide: function()"));
        assertFalse(defaultScript.contains("takeScreenshot: function()"));
        assertTrue(fullScript.contains("hide: function()"));
        assertTrue(fullScript.contains("takeScreenshot: function()"));
    }
}
