package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class MobileAppBridgeScriptTest {

    // String.formatted needs API 34 and D8 does not backport it, so it throws NoSuchMethodError on
    // older devices. The host JDK has the method, which is why this checks the source instead.
    @Test
    public void mainSourcesDoNotUseStringFormatted() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            offenders = files
                .filter((file) -> file.toString().endsWith(".java"))
                .filter((file) -> {
                    try {
                        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(".formatted(");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
        }

        assertTrue("String.formatted used in " + offenders, offenders.isEmpty());
    }

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
    public void webMessageOriginBoundModeRoutesActionsThroughPostMessageBridge() {
        String script = MobileAppBridgeScript.create(true, true, true);

        assertTrue(script.contains(MobileAppBridgeScript.BRIDGE_ACTION_PREFIX + "close"));
        assertTrue(script.contains(MobileAppBridgeScript.BRIDGE_ACTION_PREFIX + "print"));
        assertFalse(script.contains("window.AndroidInterface.hide()"));
        assertFalse(script.contains("window.PrintInterface.print()"));
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
