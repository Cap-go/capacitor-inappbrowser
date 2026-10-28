package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertTrue;

import android.webkit.WebView;
import androidx.activity.ComponentActivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class JavascriptBridgeWebMessageActionRobolectricTest {

    @Test
    public void webMessageBridgeActionCloseDoesNotHitJavascriptInterfaceRejection() throws Exception {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();

        Options options = new Options();
        options.setJavascriptBridgeOriginPolicy(
            JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("https://login.example.com"))
        );
        options.setAllowWebViewJsVisibilityControl(true);
        WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
        dialog.activity = activity;

        UrlTrackingWebView webView = new UrlTrackingWebView(activity, "https://login.example.com/oauth");
        setWebView(dialog, webView);
        ShadowLooper.idleMainLooper();

        Method handleAction = WebViewDialog.class.getDeclaredMethod("handleWebMessageBridgeAction", String.class);
        handleAction.setAccessible(true);
        handleAction.invoke(dialog, "close");
        ShadowLooper.idleMainLooper();

        assertTrue(readBooleanField(dialog, "isDismissing"));
    }

    private static void setWebView(WebViewDialog dialog, WebView webView) throws Exception {
        Field field = WebViewDialog.class.getDeclaredField("_webView");
        field.setAccessible(true);
        field.set(dialog, webView);
    }

    private static boolean readBooleanField(WebViewDialog dialog, String name) throws Exception {
        Field field = WebViewDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(dialog);
    }

    private static final class UrlTrackingWebView extends WebView {

        private final String currentUrl;

        UrlTrackingWebView(ComponentActivity activity, String currentUrl) {
            super(activity);
            this.currentUrl = currentUrl;
        }

        @Override
        public String getUrl() {
            return currentUrl;
        }
    }
}
