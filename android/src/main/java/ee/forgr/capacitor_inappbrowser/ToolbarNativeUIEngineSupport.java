package ee.forgr.capacitor_inappbrowser;

import android.text.TextUtils;
import io.github.webview_native_ui.engine.NativeUIProjection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates openWebView toolbar registration for the optional WebView Native UI Engine.
 * Custom toolbar only (not blank). System Custom Tabs ({@code open()}) never use this path.
 */
public final class ToolbarNativeUIEngineSupport {

    public static final String EVENT_TOOLBAR_APPEARANCE_CHANGED = "inAppBrowserToolbarAppearanceChanged";
    public static final String EVENT_TOOLBAR_NATIVE_SHOWN = "inAppBrowserToolbarNativeShown";
    public static final String EVENT_TOOLBAR_WEB_PROJECTION = "inAppBrowserToolbarWebProjectionStarted";
    public static final String EVENT_TOOLBAR_HIDDEN = "inAppBrowserToolbarHidden";

    private ToolbarNativeUIEngineSupport() {}

    public static boolean shouldRegister(boolean enabled, String toolbarType) {
        if (!enabled) {
            return false;
        }
        if (toolbarType == null || toolbarType.trim().isEmpty()) {
            return true;
        }
        return !"blank".equalsIgnoreCase(toolbarType.trim());
    }

    public static String validateOptions(boolean enabled, String projectionSelector) {
        if (!enabled) {
            return null;
        }
        if (TextUtils.isEmpty(projectionSelector) || projectionSelector.trim().isEmpty()) {
            return "nativeUIProjectionSelector is required when enableNativeUIEngine is true";
        }
        return null;
    }

    public static NativeUIProjection.ReceiveListeners receiveListeners(String instanceId) {
        List<String> update = Collections.singletonList(EVENT_TOOLBAR_APPEARANCE_CHANGED);
        NativeUIProjection.ReceiveListeners.Projection projection = new NativeUIProjection.ReceiveListeners.Projection(
            Collections.singletonList(EVENT_TOOLBAR_NATIVE_SHOWN),
            Collections.singletonList(EVENT_TOOLBAR_WEB_PROJECTION),
            Collections.singletonList(EVENT_TOOLBAR_HIDDEN)
        );
        Map<String, Object> match = new HashMap<>();
        if (!TextUtils.isEmpty(instanceId)) {
            match.put("id", instanceId);
        }
        return new NativeUIProjection.ReceiveListeners(update, projection, match);
    }
}
