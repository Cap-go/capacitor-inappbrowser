package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class ProxyBridgeTest {

    @Test
    public void storeRequestKeepsLivePayloadsBeyondExpectedQueueSize() {
        ProxyBridge bridge = new ProxyBridge("token");

        for (int index = 0; index < ProxyBridge.MAX_EXPECTED_STORED_REQUESTS + 32; index += 1) {
            bridge.storeRequest("token", "request-" + index, "GET", "{}", "", "same-origin");
        }

        assertNotNull(bridge.getAndRemove("request-0"));
        assertNotNull(bridge.getAndRemove("request-" + (ProxyBridge.MAX_EXPECTED_STORED_REQUESTS + 31)));
    }

    @Test
    public void storeRequestDropsExpiredPayloadsBeforeAddingNewOnes() {
        AtomicLong now = new AtomicLong(1_000L);
        ProxyBridge bridge = new ProxyBridge("token", now::get);

        bridge.storeRequest("token", "expired-request", "GET", "{}", "", "same-origin");

        now.addAndGet(ProxyBridge.STORED_REQUEST_TTL_MS + 1L);
        bridge.storeRequest("token", "fresh-request", "GET", "{}", "", "same-origin");

        assertNull(bridge.getAndRemove("expired-request"));
        assertNotNull(bridge.getAndRemove("fresh-request"));
    }
}
