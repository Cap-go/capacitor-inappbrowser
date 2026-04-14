package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ProxyResponseRoutingTest {

    @Test
    public void resolveTargetDialogUsesExplicitWebviewIdWhenProvided() {
        FakeLocator dialogA = new FakeLocator(true);
        FakeLocator dialogB = new FakeLocator(false);
        Map<String, FakeLocator> dialogs = new LinkedHashMap<>();
        dialogs.put("a", dialogA);
        dialogs.put("b", dialogB);

        FakeLocator resolved = ProxyResponseRouting.resolveTargetDialog("b", "req-1", dialogs);

        assertSame(dialogB, resolved);
    }

    @Test
    public void resolveTargetDialogFindsUniquePendingRequestWhenWebviewIdMissing() {
        FakeLocator dialogA = new FakeLocator(false);
        FakeLocator dialogB = new FakeLocator(true);
        Map<String, FakeLocator> dialogs = new LinkedHashMap<>();
        dialogs.put("a", dialogA);
        dialogs.put("b", dialogB);

        FakeLocator resolved = ProxyResponseRouting.resolveTargetDialog(null, "req-1", dialogs);

        assertSame(dialogB, resolved);
    }

    @Test
    public void resolveTargetDialogRejectsAmbiguousPendingRequestsWhenWebviewIdMissing() {
        FakeLocator dialogA = new FakeLocator(true);
        FakeLocator dialogB = new FakeLocator(true);
        Map<String, FakeLocator> dialogs = new LinkedHashMap<>();
        dialogs.put("a", dialogA);
        dialogs.put("b", dialogB);

        FakeLocator resolved = ProxyResponseRouting.resolveTargetDialog(null, "req-1", dialogs);

        assertNull(resolved);
    }

    @Test
    public void resolveTargetDialogRejectsMissingPendingRequestsWhenWebviewIdMissing() {
        FakeLocator dialogA = new FakeLocator(false);
        FakeLocator dialogB = new FakeLocator(false);
        Map<String, FakeLocator> dialogs = new LinkedHashMap<>();
        dialogs.put("a", dialogA);
        dialogs.put("b", dialogB);

        FakeLocator resolved = ProxyResponseRouting.resolveTargetDialog(null, "req-1", dialogs);

        assertNull(resolved);
    }

    private static final class FakeLocator implements ProxyResponseRouting.ProxyRequestLocator {

        private final boolean pending;

        private FakeLocator(boolean pending) {
            this.pending = pending;
        }

        @Override
        public boolean hasPendingProxyRequest(String requestId) {
            return pending;
        }
    }
}
