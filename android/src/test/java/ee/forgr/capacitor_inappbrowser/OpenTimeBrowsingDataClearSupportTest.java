package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class OpenTimeBrowsingDataClearSupportTest {

    @Test
    public void clearFlagsDefaultFalseInOptions() {
        Options options = new Options();
        assertFalse(options.getClearCookiesOnOpen());
        assertFalse(options.getClearCacheOnOpen());
    }

    @Test
    public void clearFlagsCanBeEnabledInOptions() {
        Options options = new Options();
        options.setClearCookiesOnOpen(true);
        options.setClearCacheOnOpen(true);
        assertTrue(options.getClearCookiesOnOpen());
        assertTrue(options.getClearCacheOnOpen());
    }

    @Test
    public void cookieClearDoesNotBlockWhenCallbackHasNotRun() {
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Runnable> captured = new AtomicReference<>();

        OpenTimeBrowsingDataClearSupport.applyBeforeFirstNavigation(captured::set, null, true, false, () -> completed.set(true));

        assertFalse("must return before cookie callback", completed.get());
        captured.get().run();
        assertTrue(completed.get());
    }

    @Test
    public void skipsCookieClearAndCompletesSynchronously() {
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean cookieClearStarted = new AtomicBoolean(false);

        OpenTimeBrowsingDataClearSupport.applyBeforeFirstNavigation(
            (onCleared) -> cookieClearStarted.set(true),
            null,
            false,
            false,
            () -> completed.set(true)
        );

        assertFalse(cookieClearStarted.get());
        assertTrue(completed.get());
    }
}
