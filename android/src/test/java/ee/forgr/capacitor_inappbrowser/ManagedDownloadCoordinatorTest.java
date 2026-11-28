package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ManagedDownloadCoordinatorTest {

    @Test
    public void progressThrottleAllowsFourPerSecond() {
        long base = 1_000L;
        assertTrue(ManagedDownloadCoordinator.shouldEmitProgress(0L, base));
        assertFalse(ManagedDownloadCoordinator.shouldEmitProgress(base, base + 100L));
        assertTrue(ManagedDownloadCoordinator.shouldEmitProgress(base, base + ManagedDownloadCoordinator.PROGRESS_MIN_INTERVAL_MS));
    }

    @Test
    public void cancelEmitsCancelledOnce() {
        List<String> cancelled = new ArrayList<>();
        ManagedDownloadCoordinator coordinator = new ManagedDownloadCoordinator(
            new ManagedDownloadCoordinator.Callbacks() {
                @Override
                public void downloadStarted(String downloadId, String sourceUrl) {}

                @Override
                public void downloadProgress(String downloadId, long bytesReceived, long bytesExpected, String fileName) {}

                @Override
                public void downloadCancelled(String downloadId, String sourceUrl) {
                    cancelled.add(downloadId);
                }
            }
        );

        String downloadId = coordinator.begin("https://example.com/file.bin");
        assertTrue(coordinator.requestCancel(downloadId));
        assertFalse(coordinator.requestCancel(downloadId));
        assertEquals(1, cancelled.size());
        assertEquals(downloadId, cancelled.get(0));
    }

    @Test
    public void resolveBytesExpectedTreatsUnknownAsMinusOne() {
        assertEquals(-1L, ManagedDownloadCoordinator.resolveBytesExpected(-1L));
        assertEquals(42L, ManagedDownloadCoordinator.resolveBytesExpected(42L));
    }
}
