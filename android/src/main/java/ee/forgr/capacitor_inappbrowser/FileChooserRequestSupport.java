package ee.forgr.capacitor_inappbrowser;

import android.net.Uri;
import android.webkit.ValueCallback;

/**
 * Request-scoped state for Android WebView file-chooser flows.
 * Prevents overlapping chooser/camera requests from cross-delivering results.
 */
final class FileChooserRequestSupport {

    static final class FileChooserRequest {

        final ValueCallback<Uri[]> callback;
        final String[] acceptTypes;
        final boolean multiple;
        Uri tempCameraUri;

        FileChooserRequest(ValueCallback<Uri[]> callback, String[] acceptTypes, boolean multiple) {
            this.callback = callback;
            this.acceptTypes = acceptTypes;
            this.multiple = multiple;
        }
    }

    private FileChooserRequestSupport() {}

    static boolean isActive(FileChooserRequest request, FileChooserRequest active) {
        return request != null && request == active;
    }

    static void cancel(FileChooserRequest request) {
        if (request != null && request.callback != null) {
            request.callback.onReceiveValue(null);
        }
    }

    static boolean completeIfActive(FileChooserRequest request, FileChooserRequest active, Uri[] results) {
        if (!isActive(request, active)) {
            return false;
        }
        request.callback.onReceiveValue(results);
        return true;
    }

    static boolean cancelIfActive(FileChooserRequest request, FileChooserRequest active) {
        if (!isActive(request, active)) {
            return false;
        }
        request.callback.onReceiveValue(null);
        return true;
    }
}
