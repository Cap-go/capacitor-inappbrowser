package ee.forgr.capacitor_inappbrowser;

import android.net.Uri;

public interface BrowserSessionFileChooser {
    boolean hasPendingFileChooser();

    Uri getTempCameraUri();

    void deliverFileChooserResult(Uri[] results);

    void clearPendingFileChooser();
}
