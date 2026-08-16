package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.webkit.ValueCallback;
import org.junit.Test;

public class FileChooserRequestSupportTest {

    private static final class RecordingCallback implements ValueCallback<Uri[]> {

        Uri[] lastValue;
        int callCount;

        @Override
        public void onReceiveValue(Uri[] value) {
            callCount++;
            lastValue = value;
        }
    }

    @Test
    public void activeRequestIdentityMatchesOnlyCurrentRequest() {
        RecordingCallback firstCallback = new RecordingCallback();
        RecordingCallback secondCallback = new RecordingCallback();
        FileChooserRequestSupport.FileChooserRequest first = new FileChooserRequestSupport.FileChooserRequest(
            firstCallback,
            "image/*",
            false
        );
        FileChooserRequestSupport.FileChooserRequest second = new FileChooserRequestSupport.FileChooserRequest(
            secondCallback,
            "application/pdf",
            true
        );

        assertTrue(FileChooserRequestSupport.isActive(first, first));
        assertFalse(FileChooserRequestSupport.isActive(first, second));
    }

    @Test
    public void supersededRequestDoesNotReceiveCompletion() {
        RecordingCallback firstCallback = new RecordingCallback();
        RecordingCallback secondCallback = new RecordingCallback();
        FileChooserRequestSupport.FileChooserRequest first = new FileChooserRequestSupport.FileChooserRequest(
            firstCallback,
            "image/*",
            false
        );
        FileChooserRequestSupport.FileChooserRequest second = new FileChooserRequestSupport.FileChooserRequest(
            secondCallback,
            "image/*",
            false
        );

        Uri[] cameraResult = new Uri[1];
        assertFalse(FileChooserRequestSupport.completeIfActive(first, second, cameraResult));

        assertEquals(0, firstCallback.callCount);
        assertEquals(0, secondCallback.callCount);
    }

    @Test
    public void activeRequestReceivesCompletion() {
        RecordingCallback callback = new RecordingCallback();
        FileChooserRequestSupport.FileChooserRequest request = new FileChooserRequestSupport.FileChooserRequest(callback, "image/*", false);
        Uri[] cameraResult = new Uri[1];

        assertTrue(FileChooserRequestSupport.completeIfActive(request, request, cameraResult));
        assertEquals(1, callback.callCount);
        assertEquals(cameraResult, callback.lastValue);
    }

    @Test
    public void cancelNotifiesCallbackWithNull() {
        RecordingCallback callback = new RecordingCallback();
        FileChooserRequestSupport.FileChooserRequest request = new FileChooserRequestSupport.FileChooserRequest(callback, "*/*", false);

        FileChooserRequestSupport.cancel(request);

        assertEquals(1, callback.callCount);
        assertEquals(null, callback.lastValue);
    }

    @Test
    public void cancelIfActiveOnlyCancelsCurrentRequest() {
        RecordingCallback firstCallback = new RecordingCallback();
        RecordingCallback secondCallback = new RecordingCallback();
        FileChooserRequestSupport.FileChooserRequest first = new FileChooserRequestSupport.FileChooserRequest(
            firstCallback,
            "image/*",
            false
        );
        FileChooserRequestSupport.FileChooserRequest second = new FileChooserRequestSupport.FileChooserRequest(
            secondCallback,
            "image/*",
            false
        );

        assertFalse(FileChooserRequestSupport.cancelIfActive(first, second));
        assertEquals(0, firstCallback.callCount);

        assertTrue(FileChooserRequestSupport.cancelIfActive(second, second));
        assertEquals(1, secondCallback.callCount);
        assertEquals(null, secondCallback.lastValue);
    }
}
