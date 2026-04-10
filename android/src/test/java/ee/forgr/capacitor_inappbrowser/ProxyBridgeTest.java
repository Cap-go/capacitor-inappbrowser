package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ProxyBridgeTest {

    @Test
    public void storeRequestEvictsOldestPendingRequestFirst() {
        ProxyBridge bridge = new ProxyBridge("token");

        for (int index = 0; index <= 256; index++) {
            bridge.storeRequest("token", "req-" + index, "GET", "{}", "", "same-origin");
        }

        assertNull(bridge.getAndRemove("req-0"));
        assertNotNull(bridge.getAndRemove("req-1"));
        assertNotNull(bridge.getAndRemove("req-256"));
    }

    @Test
    public void storeRequestRejectsInvalidToken() {
        ProxyBridge bridge = new ProxyBridge("token");

        bridge.storeRequest("wrong-token", "req-1", "GET", "{}", "", "same-origin");

        assertNull(bridge.getAndRemove("req-1"));
    }
}
