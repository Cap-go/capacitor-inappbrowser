package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.util.Queue;
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

    @Test
    public void getAndRemoveAlsoRemovesRequestIdFromEvictionQueue() throws Exception {
        ProxyBridge bridge = new ProxyBridge("token");

        bridge.storeRequest("token", "req-1", "GET", "{}", "", "same-origin");

        assertNotNull(bridge.getAndRemove("req-1"));
        assertEquals(0, getStoredRequestOrderSize(bridge));
    }

    private static int getStoredRequestOrderSize(ProxyBridge bridge) throws Exception {
        Field field = ProxyBridge.class.getDeclaredField("storedRequestOrder");
        field.setAccessible(true);
        Queue<?> storedRequestOrder = (Queue<?>) field.get(bridge);
        return storedRequestOrder.size();
    }
}
