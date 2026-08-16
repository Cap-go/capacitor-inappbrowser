package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.Test;

public class FileChooserAcceptSupportTest {

    @Test
    public void normalizeAcceptTypesDedupesAndLowercasesMimeTypes() {
        LinkedHashSet<String> mimeTypes = FileChooserAcceptSupport.normalizeAcceptTypes(
            new String[] { "image/png,IMAGE/PNG,application/pdf" }
        );

        assertEquals(new LinkedHashSet<>(Arrays.asList("image/png", "application/pdf")), mimeTypes);
    }

    @Test
    public void normalizeAcceptTypesClearsWhenWildcardPresent() {
        LinkedHashSet<String> mimeTypes = FileChooserAcceptSupport.normalizeAcceptTypes(new String[] { "image/png", "*/*" });

        assertTrue(mimeTypes.isEmpty());
    }

    @Test
    public void acceptEntryToMimeTypeIgnoresUnknownTokens() {
        assertNull(FileChooserAcceptSupport.acceptEntryToMimeType("bogus"));
        assertNull(FileChooserAcceptSupport.acceptEntryToMimeType(null));
        assertEquals("image/png", FileChooserAcceptSupport.acceptEntryToMimeType("IMAGE/PNG"));
    }

    @Test
    public void isImageOnlyAcceptTypesRequiresAllImageMimeTypes() {
        assertTrue(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "image/*" }));
        assertTrue(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "image/png", "image/jpeg" }));
        assertFalse(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "image/png", "application/pdf" }));
        assertFalse(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "*/*" }));
    }

    @Test
    public void unresolvedExtensionPreventsImageOnlyCaptureRouting() {
        assertFalse(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { ".custom,image/*" }));
        assertFalse(FileChooserAcceptSupport.isMediaOnlyAcceptTypes(new String[] { ".custom,image/*" }));
    }

    @Test
    public void isMediaOnlyAcceptTypesDetectsMediaBucketsFromRawEntries() {
        assertTrue(FileChooserAcceptSupport.isMediaOnlyAcceptTypes(new String[] { "image/png", "video/mp4", "audio/mpeg" }));
        assertFalse(FileChooserAcceptSupport.isMediaOnlyAcceptTypes(new String[] { "image/png", "application/pdf" }));
        assertFalse(FileChooserAcceptSupport.isMediaOnlyAcceptTypes(new String[] { ".custom", "image/*" }));
        assertFalse(FileChooserAcceptSupport.isMediaOnlyAcceptTypes(new String[] { "*/*" }));
    }

    @Test
    public void isMediaOnlyMimeSetDetectsMediaBuckets() {
        assertTrue(FileChooserAcceptSupport.isMediaOnlyMimeSet(new LinkedHashSet<>(Arrays.asList("image/png", "video/mp4", "audio/mpeg"))));
        assertFalse(FileChooserAcceptSupport.isMediaOnlyMimeSet(new LinkedHashSet<>(Arrays.asList("image/png", "application/pdf"))));
        assertFalse(FileChooserAcceptSupport.isMediaOnlyMimeSet(new LinkedHashSet<>()));
    }

    @Test
    public void useGetContentActionOnlyForSingleTypeMediaLists() {
        assertTrue(FileChooserAcceptSupport.useGetContentAction(new String[] { "image/*" }));
        assertFalse(FileChooserAcceptSupport.useGetContentAction(new String[] { "image/png", "image/jpeg" }));
        assertFalse(FileChooserAcceptSupport.useGetContentAction(new String[] { "image/*", "video/*" }));
        assertFalse(FileChooserAcceptSupport.useGetContentAction(new String[] { "image/png", "application/pdf" }));
        assertFalse(FileChooserAcceptSupport.useGetContentAction(new String[] { ".custom", "image/*" }));
    }
}
