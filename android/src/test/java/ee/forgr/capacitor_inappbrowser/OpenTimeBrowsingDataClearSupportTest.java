package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
