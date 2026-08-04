package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class BrowsingDataClearSupportTest {

    @Test
    public void clearAllBrowsingDataDoesNotWipeProcessGlobalStores() {
        assertFalse(BrowsingDataClearSupport.shouldClearProcessGlobalBrowsingData());
    }
}
