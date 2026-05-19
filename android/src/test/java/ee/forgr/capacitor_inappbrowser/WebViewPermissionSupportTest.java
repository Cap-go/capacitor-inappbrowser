package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.webkit.PermissionRequest;
import org.junit.Test;

public class WebViewPermissionSupportTest {

    @Test
    public void cameraLaunchRequiresOnlyCameraPermission() {
        assertArrayEquals(
            new String[] { Manifest.permission.CAMERA },
            WebViewPermissionSupport.missingAndroidPermissions(true, false, false, false)
        );
        assertArrayEquals(new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE }, WebViewPermissionSupport.grantResources(true, false));
    }

    @Test
    public void cameraAndMicrophoneRequestRequiresBothPermissions() {
        assertArrayEquals(
            new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO },
            WebViewPermissionSupport.missingAndroidPermissions(true, true, false, false)
        );
        assertArrayEquals(
            new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE },
            WebViewPermissionSupport.grantResources(true, true)
        );
    }

    @Test
    public void detectsRequestedResources() {
        String[] resources = new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE };

        assertTrue(WebViewPermissionSupport.hasResource(resources, PermissionRequest.RESOURCE_VIDEO_CAPTURE));
        assertFalse(WebViewPermissionSupport.hasResource(resources, PermissionRequest.RESOURCE_AUDIO_CAPTURE));
    }
}
