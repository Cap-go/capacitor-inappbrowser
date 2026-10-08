package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FileInputCaptureChooserSupportTest {

    @Test
    public void usesCameraCapturePathOnlyForImageOnlyCaptureEnabled() {
        assertTrue(FileInputCaptureChooserSupport.usesCameraCapturePath(true, true));
        assertFalse(FileInputCaptureChooserSupport.usesCameraCapturePath(true, false));
        assertFalse(FileInputCaptureChooserSupport.usesCameraCapturePath(false, true));
        assertFalse(FileInputCaptureChooserSupport.usesCameraCapturePath(false, false));
    }

    @Test
    public void hookScriptUsesComposedPathForShadowDomFileInputs() {
        String script = FileInputCaptureChooserSupport.createFileInputCaptureHookScript();
        assertTrue(script.contains("composedPath"));
        assertTrue(script.contains("capgoFindCaptureFromClickEvent"));
    }

    @Test
    public void chooserFallbackScriptWalksActiveElementShadowRoot() {
        String script = FileInputCaptureChooserSupport.createChooserFallbackCaptureResolutionScript();
        assertTrue(script.contains("shadowRoot"));
        assertTrue(script.contains("capgoFindCaptureFromActiveElementChain"));
    }
}
