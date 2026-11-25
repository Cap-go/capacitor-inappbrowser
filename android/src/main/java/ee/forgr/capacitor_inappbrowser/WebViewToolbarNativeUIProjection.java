package ee.forgr.capacitor_inappbrowser;

import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import io.github.webview_native_ui.engine.NativeUIProjection;
import org.json.JSONObject;

/**
 * Retains one Engine registration for an openWebView custom toolbar.
 */
public final class WebViewToolbarNativeUIProjection {

    private static final String TAG = "InAppBrowserNativeUI";

    private final NativeUIProjection projection;

    private WebViewToolbarNativeUIProjection(NativeUIProjection projection) {
        this.projection = projection;
    }

    public static WebViewToolbarNativeUIProjection tryCreate(Plugin plugin, Options options, View toolbarView, WebViewDialog dialog) {
        if (!ToolbarNativeUIEngineSupport.shouldRegister(options.isEnableNativeUIEngine(), options.getToolbarType())) {
            return null;
        }
        if (toolbarView == null) {
            return null;
        }
        Window window = dialog != null ? dialog.getWindow() : null;
        if (window == null && plugin.getActivity() != null) {
            window = plugin.getActivity().getWindow();
        }
        WebView bridgeWebView = plugin.getBridge().getWebView();
        String selector = options.getNativeUIProjectionSelector();
        try {
            NativeUIProjection projection = new NativeUIProjection(
                window,
                bridgeWebView,
                plugin.getPluginHandle().getId(),
                selector,
                ToolbarNativeUIEngineSupport.receiveListeners(dialog != null ? dialog.getInstanceId() : ""),
                () -> toolbarView
            );
            return new WebViewToolbarNativeUIProjection(projection);
        } catch (Exception error) {
            Log.e(TAG, "Failed to register toolbar projection", error);
            return null;
        }
    }

    public void receive(String eventName, JSObject data) {
        if (projection == null) {
            return;
        }
        try {
            JSONObject payload = data != null ? new JSONObject(data.toString()) : null;
            projection.receive(eventName, payload);
        } catch (Exception error) {
            Log.w(TAG, "Engine receive skipped for " + eventName, error);
        }
    }

    public void close() {
        if (projection == null) {
            return;
        }
        try {
            projection.close();
        } catch (Exception error) {
            Log.w(TAG, "Engine close failed", error);
        }
    }
}
