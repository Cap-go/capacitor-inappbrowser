package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.webkit.WebView;
import androidx.activity.ComponentActivity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BlobDownloadBridgeTokenRobolectricTest {

    private WebViewDialog dialog;
    private Object javascriptInterface;

    @Before
    public void setUp() throws Exception {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();

        Options options = new Options();
        options.setHandleDownloads(true);
        options.setJavascriptBridgeOriginPolicy(
            JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("https://app.example.com"))
        );
        dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
        dialog.activity = activity;
        setField("_webView", new UrlTrackingWebView(activity, "https://app.example.com/"));
        ShadowLooper.idleMainLooper();

        Class<?> interfaceClass = Class.forName(WebViewDialog.class.getName() + "$JavaScriptInterface");
        Constructor<?> constructor = interfaceClass.getDeclaredConstructor(WebViewDialog.class);
        constructor.setAccessible(true);
        javascriptInterface = constructor.newInstance(dialog);
    }

    @Test
    public void startBlobDownloadWithoutTokenIsIgnored() throws Exception {
        callBridge("startBlobDownload", startPayload(null));

        assertEquals(0, blobDownloadSessions().size());
    }

    @Test
    public void startBlobDownloadWithWrongTokenIsIgnored() throws Exception {
        callBridge("startBlobDownload", startPayload("not-the-token"));

        assertEquals(0, blobDownloadSessions().size());
    }

    @Test
    public void startBlobDownloadWithoutSizeIsRejected() throws Exception {
        JSONObject payload = startPayload(blobDownloadToken());
        payload.remove("size");
        callBridge("startBlobDownload", payload);

        assertEquals(0, blobDownloadSessions().size());
    }

    @Test
    public void injectedScriptTokenStartsAndAbortsSession() throws Exception {
        String token = blobDownloadToken();
        callBridge("startBlobDownload", startPayload(token));
        assertTrue(blobDownloadSessions().containsKey("session-1"));

        callBridge("abortBlobDownload", new JSONObject().put("sessionId", "session-1"));
        assertTrue("abort without token must not cancel the session", blobDownloadSessions().containsKey("session-1"));

        callBridge("abortBlobDownload", new JSONObject().put("token", token).put("sessionId", "session-1"));
        assertEquals(0, blobDownloadSessions().size());
    }

    private JSONObject startPayload(String token) throws Exception {
        JSONObject payload = new JSONObject()
            .put("sessionId", "session-1")
            .put("fileName", "a.txt")
            .put("mimeType", "text/plain")
            .put("size", 2);
        if (token != null) {
            payload.put("token", token);
        }
        return payload;
    }

    private void callBridge(String methodName, JSONObject payload) throws Exception {
        Method method = javascriptInterface.getClass().getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        method.invoke(javascriptInterface, payload.toString());
    }

    private String blobDownloadToken() throws Exception {
        Field field = WebViewDialog.class.getDeclaredField("blobDownloadToken");
        field.setAccessible(true);
        return (String) field.get(dialog);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> blobDownloadSessions() throws Exception {
        Field field = WebViewDialog.class.getDeclaredField("blobDownloadSessions");
        field.setAccessible(true);
        return (Map<String, ?>) field.get(dialog);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = WebViewDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(dialog, value);
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
