package ee.forgr.capacitor_inappbrowser;

import android.Manifest;
import android.webkit.PermissionRequest;
import java.util.ArrayList;
import java.util.List;

final class WebViewPermissionSupport {

    private WebViewPermissionSupport() {}

    static boolean hasResource(String[] resources, String resource) {
        if (resources == null || resource == null) {
            return false;
        }

        for (String requestedResource : resources) {
            if (resource.equals(requestedResource)) {
                return true;
            }
        }

        return false;
    }

    static String[] missingAndroidPermissions(
        boolean needsCamera,
        boolean needsMicrophone,
        boolean hasCameraPermission,
        boolean hasMicrophonePermission
    ) {
        List<String> permissions = new ArrayList<>();

        if (needsCamera && !hasCameraPermission) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (needsMicrophone && !hasMicrophonePermission) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        return permissions.toArray(new String[0]);
    }

    static String[] grantResources(boolean needsCamera, boolean needsMicrophone) {
        List<String> resources = new ArrayList<>();

        if (needsCamera) {
            resources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        }

        if (needsMicrophone) {
            resources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        }

        return resources.toArray(new String[0]);
    }
}
