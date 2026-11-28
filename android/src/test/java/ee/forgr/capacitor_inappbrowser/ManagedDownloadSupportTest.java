package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagedDownloadSupportTest {

    @Test
    public void throttlesProgressToAtMostFourPerSecond() {
        assertTrue(ManagedDownloadSupport.shouldEmitProgress(0L, 1000L));
        assertFalse(ManagedDownloadSupport.shouldEmitProgress(1000L, 1000L + ManagedDownloadSupport.PROGRESS_MIN_INTERVAL_MS - 1L));
        assertTrue(ManagedDownloadSupport.shouldEmitProgress(0L, 0L + ManagedDownloadSupport.PROGRESS_MIN_INTERVAL_MS));
    }

    @Test
    public void endingGuardAllowsSingleTerminalEvent() {
        ManagedDownloadSupport.EndingGuard guard = new ManagedDownloadSupport.EndingGuard();
        assertTrue(guard.markStarted());
        assertTrue(guard.tryComplete());
        assertFalse(guard.tryFail());
        assertFalse(guard.tryCancel());
        assertTrue(guard.hasEnded());
    }
}
