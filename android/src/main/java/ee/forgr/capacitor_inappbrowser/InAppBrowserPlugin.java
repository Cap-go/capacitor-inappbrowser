package ee.forgr.capacitor_inappbrowser;

import android.Manifest;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

@Deprecated
@CapacitorPlugin(
    name = "CapgoInAppBrowser",
    permissions = {
        @Permission(alias = "camera", strings = { Manifest.permission.CAMERA }),
        @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(alias = "storage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    },
    requestCodes = { WebViewDialog.FILE_CHOOSER_REQUEST_CODE }
)
public class InAppBrowserPlugin extends CapgoInAppBrowserPlugin {}
