package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.webkit.WebView;
import androidx.activity.ComponentActivity;
import com.getcapacitor.JSObject;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
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
    private Options options;
    private Object javascriptInterface;

    @Before
    public void setUp() throws Exception {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();

        options = new Options();
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

    @Test
    public void handleBlobDownloadRequiresToken() throws Exception {
        RecordingExecutorService executor = new RecordingExecutorService();
        setField("executorService", executor);

        callBridge("handleBlobDownload", legacyPayload(null));
        callBridge("handleBlobDownload", legacyPayload("not-the-token"));
        assertEquals(0, executor.submitted);

        callBridge("handleBlobDownload", legacyPayload(blobDownloadToken()));
        assertEquals(1, executor.submitted);
    }

    @Test
    public void appendBlobDownloadChunkRequiresToken() throws Exception {
        String token = blobDownloadToken();
        callBridge("startBlobDownload", startPayload(token));

        callBridge("appendBlobDownloadChunk", chunkPayload(null));
        callBridge("appendBlobDownloadChunk", chunkPayload("not-the-token"));
        assertEquals(0L, sessionBytesWritten("session-1"));

        callBridge("appendBlobDownloadChunk", chunkPayload(token));
        assertEquals(2L, sessionBytesWritten("session-1"));
    }

    @Test
    public void finishBlobDownloadRequiresToken() throws Exception {
        DownloadFailureTracker failureTracker = new DownloadFailureTracker();
        options.setCallbacks(failureTracker);

        String token = blobDownloadToken();
        callBridge("startBlobDownload", startPayload(token));
        callBridge("appendBlobDownloadChunk", chunkPayload(token));

        File outputFile = sessionOutputFile("session-1");

        callBridge("finishBlobDownload", new JSONObject().put("sessionId", "session-1"));
        callBridge("finishBlobDownload", new JSONObject().put("token", "not-the-token").put("sessionId", "session-1"));
        assertTrue("finish without a valid token must not touch the session", blobDownloadSessions().containsKey("session-1"));
        assertEquals(0, failureTracker.failedCount);

        callBridge("finishBlobDownload", new JSONObject().put("token", token).put("sessionId", "session-1"));
        ShadowLooper.idleMainLooper();
        assertEquals(0, blobDownloadSessions().size());
        assertEquals(0, failureTracker.failedCount);
        assertTrue(outputFile.exists());
        assertEquals("hi", new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8));
    }

    private JSONObject legacyPayload(String token) throws Exception {
        JSONObject payload = new JSONObject().put("base64", "aGk=").put("mimeType", "text/plain").put("fileName", "a.txt");
        if (token != null) {
            payload.put("token", token);
        }
        return payload;
    }

    private JSONObject chunkPayload(String token) throws Exception {
        JSONObject payload = new JSONObject().put("sessionId", "session-1").put("base64", "aGk=");
        if (token != null) {
            payload.put("token", token);
        }
        return payload;
    }

    private long sessionBytesWritten(String sessionId) throws Exception {
        Object session = blobDownloadSessions().get(sessionId);
        Field field = session.getClass().getDeclaredField("bytesWritten");
        field.setAccessible(true);
        return field.getLong(session);
    }

    private File sessionOutputFile(String sessionId) throws Exception {
        Object session = blobDownloadSessions().get(sessionId);
        Field field = session.getClass().getDeclaredField("outputFile");
        field.setAccessible(true);
        return (File) field.get(session);
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

    private static final class DownloadFailureTracker implements WebViewCallbacks {

        int failedCount;

        @Override
        public void urlChangeEvent(String url) {}

        @Override
        public void closeEvent(String url) {}

        @Override
        public void hideEvent(String url, JSObject screenshot) {}

        @Override
        public void pageLoaded() {}

        @Override
        public void pageLoadStart() {}

        @Override
        public void pageLoadProgress(double progress) {}

        @Override
        public void pageLoadError() {}

        @Override
        public void customSchemeIntercepted(String url, boolean opened) {}

        @Override
        public void deepLinkPrevented(String url) {}

        @Override
        public void javascriptCallback(String message) {}

        @Override
        public void consoleMessage(String level, String message, String source, Integer line, Integer column) {}

        @Override
        public void buttonNearDoneClicked() {}

        @Override
        public void confirmBtnClicked(String url) {}

        @Override
        public void screenshotTaken(JSObject screenshot) {}

        @Override
        public void downloadCompleted(String sourceUrl, String fileName, String mimeType, String path, String localUrl, String handledBy) {}

        @Override
        public void downloadFailed(String sourceUrl, String fileName, String mimeType, String error) {
            failedCount++;
        }

        @Override
        public void proxyRequestEvent(
            String requestId,
            String phase,
            String url,
            String method,
            String headersJson,
            String body,
            Integer status,
            String responseHeadersJson,
            String responseBody,
            String webviewId
        ) {}
    }

    // Counts tasks instead of running them, so the legacy download never touches the file system.
    private static final class RecordingExecutorService extends AbstractExecutorService {

        int submitted;

        @Override
        public void execute(Runnable command) {
            submitted++;
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
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
