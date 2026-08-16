package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.Test;

public class FileChooserAcceptSupportTest {

    @Test
    public void normalizeAcceptTypesConvertsExtensionsAndDedupes() {
        LinkedHashSet<String> mimeTypes = FileChooserAcceptSupport.normalizeAcceptTypes(new String[] { "image/png,.PDF,application/pdf" });

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
        assertEquals("image/png", FileChooserAcceptSupport.acceptEntryToMimeType("IMAGE/PNG"));
        assertEquals("application/pdf", FileChooserAcceptSupport.acceptEntryToMimeType(".pdf"));
    }

    @Test
    public void isImageOnlyAcceptTypesRequiresAllImageMimeTypes() {
        assertTrue(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "image/*" }));
        assertTrue(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "image/png", "image/jpeg" }));
        assertFalse(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "image/png", "application/pdf" }));
        assertFalse(FileChooserAcceptSupport.isImageOnlyAcceptTypes(new String[] { "*/*" }));
    }

    @Test
    public void createFileChooserIntentUsesGetContentForMediaOnly() {
        LinkedHashSet<String> mimeTypes = FileChooserAcceptSupport.normalizeAcceptTypes(new String[] { "image/png", "image/jpeg" });
        Intent intent = FileChooserAcceptSupport.createFileChooserIntent(mimeTypes, true);

        assertEquals(Intent.ACTION_GET_CONTENT, intent.getAction());
        assertEquals("*/*", intent.getType());
        assertEquals(Arrays.asList("image/png", "image/jpeg"), Arrays.asList(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)));
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
    }

    @Test
    public void createFileChooserIntentUsesOpenDocumentForMixedTypes() {
        LinkedHashSet<String> mimeTypes = FileChooserAcceptSupport.normalizeAcceptTypes(new String[] { "image/png,application/pdf" });
        Intent intent = FileChooserAcceptSupport.createFileChooserIntent(mimeTypes, false);

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
        assertEquals("*/*", intent.getType());
        assertEquals(Arrays.asList("image/png", "application/pdf"), Arrays.asList(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)));
        assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true));
    }

    @Test
    public void createFileChooserIntentUsesOpenDocumentForEmptyAcceptList() {
        Intent intent = FileChooserAcceptSupport.createFileChooserIntent(new LinkedHashSet<>(), false);

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
        assertEquals("*/*", intent.getType());
        assertNull(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
    }
}
