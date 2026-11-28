package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BlobDownloadFilenameCaptureSupportTest {

    @Test
    public void captureScriptWrapsAnchorClickAndDispatchEvent() {
        String script = BlobDownloadFilenameCaptureSupport.createBlobDownloadFilenameCaptureScript();

        assertTrue(script.contains("HTMLAnchorElement.prototype"));
        assertTrue(script.contains("originalClick"));
        assertTrue(script.contains("originalDispatchEvent"));
        assertTrue(script.contains("event.type === 'click'"));
        assertTrue(script.contains("closest('a[download]')"));
        assertTrue(script.contains("__capgoBlobDownloadFilenameCapture"));
    }

    @Test
    public void captureScriptRecordsBlobDownloadNames() {
        String script = BlobDownloadFilenameCaptureSupport.createBlobDownloadFilenameCaptureScript();

        assertTrue(script.contains(BlobDownloadFilenameCaptureSupport.BLOB_DOWNLOAD_NAMES_GLOBAL));
        assertTrue(script.contains("indexOf('blob:')"));
        assertTrue(script.contains("__capgoBlobDownloadFilenameCaptureRegistered"));
    }

    @Test
    public void fileNameResolutionPrefersRecordedNames() {
        String resolver = BlobDownloadFilenameCaptureSupport.FILE_NAME_RESOLUTION_JS;

        assertTrue(resolver.contains(BlobDownloadFilenameCaptureSupport.BLOB_DOWNLOAD_NAMES_GLOBAL));
        assertTrue(resolver.contains("recorded"));
        assertTrue(resolver.contains("getAttribute('download')"));
        assertTrue(resolver.contains("hasAttribute('download')"));
        assertTrue(resolver.contains("fallbackFileName"));
    }

    @Test
    public void captureScriptRefreshesNamesAfterClickAndClearsEmptyDownload() {
        String script = BlobDownloadFilenameCaptureSupport.createBlobDownloadFilenameCaptureScript();

        assertTrue(script.contains("recordBlobDownloadName(this)"));
        assertTrue(script.contains("delete window." + BlobDownloadFilenameCaptureSupport.BLOB_DOWNLOAD_NAMES_GLOBAL));
        assertTrue(script.contains("}, false)"));
    }

    @Test
    public void mainSourcesDoNotUseStringFormattedOnBlobSupport() {
        assertFalse(BlobDownloadFilenameCaptureSupport.createBlobDownloadFilenameCaptureScript().contains(".formatted("));
    }
}
