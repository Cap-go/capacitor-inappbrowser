package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class ConsoleMessageEventHelperTest {

    @Test
    public void createBuildsConsoleEventPayload() {
        Map<String, Object> payload = ConsoleMessageEventHelper.createData(
            "webview-1",
            "WARN",
            "popup blocked",
            "https://example.com/app.js",
            42,
            7
        );

        assertEquals("webview-1", payload.get("id"));
        assertEquals("warn", payload.get("level"));
        assertEquals("popup blocked", payload.get("message"));
        assertEquals("https://example.com/app.js", payload.get("source"));
        assertEquals(Integer.valueOf(42), payload.get("line"));
        assertEquals(Integer.valueOf(7), payload.get("column"));
    }

    @Test
    public void copyForPopupPreservesConsoleCaptureFlag() {
        Options options = new Options();
        options.setHidden(true);
        options.setHiddenPopupWindow(true);
        options.setCaptureConsoleLogs(true);

        Options popupCopy = options.copyForPopup();

        assertTrue(popupCopy.isPopupWindowMode());
        assertTrue(popupCopy.isHidden());
        assertTrue(popupCopy.getCaptureConsoleLogs());
    }
}
