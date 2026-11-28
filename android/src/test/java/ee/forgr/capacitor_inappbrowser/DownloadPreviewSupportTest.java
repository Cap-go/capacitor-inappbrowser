package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadPreviewSupportTest {

    @Test
    public void normalizeSupportedModes() {
        assertEquals("inAppBrowser", DownloadPreviewSupport.normalize("inAppBrowser"));
        assertEquals("systemPreview", DownloadPreviewSupport.normalize("SYSTEMPREVIEW"));
        assertEquals("none", DownloadPreviewSupport.normalize("NONE"));
    }

    @Test
    public void normalizeRejectsUnknownModes() {
        assertNull(DownloadPreviewSupport.normalize("external"));
    }

    @Test
    public void skipsPreviewOnlyForNone() {
        assertTrue(DownloadPreviewSupport.skipsPreview("none"));
        assertFalse(DownloadPreviewSupport.skipsPreview("inAppBrowser"));
        assertFalse(DownloadPreviewSupport.skipsPreview("systemPreview"));
    }

    @Test
    public void usesInAppBrowserPreview() {
        assertTrue(DownloadPreviewSupport.usesInAppBrowserPreview("inAppBrowser"));
        assertFalse(DownloadPreviewSupport.usesInAppBrowserPreview("systemPreview"));
        assertFalse(DownloadPreviewSupport.usesInAppBrowserPreview("none"));
    }
}
