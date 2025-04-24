package ee.forgr.capacitor_inappbrowser;

import android.webkit.PermissionRequest;

public interface PermissionHandler {
    void handleCameraPermissionRequest(PermissionRequest request);
    void handleMicrophonePermissionRequest(PermissionRequest request);
} 