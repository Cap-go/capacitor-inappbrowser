package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void endingGuardRejectsTerminalEventBeforeStart() {
        ManagedDownloadSupport.EndingGuard guard = new ManagedDownloadSupport.EndingGuard();
        assertFalse(guard.tryComplete());
        assertFalse(guard.tryCancel());
        assertFalse(guard.hasEnded());
    }

    @Test
    public void cancelWinsWhenItLandsFirst() {
        ManagedDownloadSupport.EndingGuard guard = new ManagedDownloadSupport.EndingGuard();
        assertTrue(guard.markStarted());
        assertFalse(guard.markStarted());
        assertTrue(guard.tryCancel());
        assertFalse(guard.tryComplete());
        assertFalse(guard.tryFail());
    }

    @Test
    public void unknownContentLengthBecomesMinusOne() {
        assertEquals(-1L, ManagedDownloadSupport.resolveBytesExpected(-1L));
        assertEquals(-1L, ManagedDownloadSupport.resolveBytesExpected(-42L));
        assertEquals(0L, ManagedDownloadSupport.resolveBytesExpected(0L));
        assertEquals(4096L, ManagedDownloadSupport.resolveBytesExpected(4096L));
    }

    @Test
    public void detectsTruncatedTransfers() {
        assertTrue(ManagedDownloadSupport.isTruncated(10L, 100L));
        assertFalse(ManagedDownloadSupport.isTruncated(100L, 100L));
        assertFalse(ManagedDownloadSupport.isTruncated(10L, -1L));
    }
}
