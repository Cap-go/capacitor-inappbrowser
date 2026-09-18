package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.Base64;
import android.webkit.WebView;
import com.getcapacitor.JSObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InitialProxyPresentationRobolectricTest {

    @Test
    public void deferredSyntheticPageLoadsBeforeWindowAttachment() throws Exception {
        assertInitialLoadRunsDetached(false, "text/html");
    }

    @Test
    public void hiddenSyntheticPageLoadsBeforeWindowAttachment() throws Exception {
        assertInitialLoadRunsDetached(true, "text/html");
    }

    @Test
    public void deferredNonHtmlFallbackLoadsBeforeWindowAttachment() throws Exception {
        assertInitialLoadRunsDetached(false, "image/png");
    }

    @Test
    public void initialProxyFailurePageLoadsBeforeWindowAttachment() throws Exception {
        assertInitialLoadRunsDetached(false, null);
    }

    private void assertInitialLoadRunsDetached(boolean hidden, String contentType) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setUrl("https://fullscreen.capgo.test/demo");
        options.setHidden(hidden);
        options.setPresentAfterPageLoad(true);
        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, null, null);
        TrackingWebView webView = new TrackingWebView(context);
        Field viewField = WebViewDialog.class.getDeclaredField("_webView");
        viewField.setAccessible(true);
        viewField.set(dialog, webView);
        options.setCallbacks(
            (WebViewCallbacks) Proxy.newProxyInstance(
                WebViewCallbacks.class.getClassLoader(),
                new Class<?>[] { WebViewCallbacks.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("proxyRequestEvent")) {
                        if (contentType == null) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        dialog.handleProxyResponse(
                            (String) args[0],
                            new JSObject()
                                .put("status", 200)
                                .put("headers", new JSObject().put("Content-Type", contentType))
                                .put("body", Base64.encodeToString("<html>loaded</html>".getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP))
                        );
                    }
                    return null;
                }
            )
        );
        Method bootstrap = WebViewDialog.class.getDeclaredMethod(
            "loadInitialLegacyProxyContent",
            Map.class,
            Map.class,
            String.class,
            String.class
        );
        bootstrap.setAccessible(true);
        bootstrap.invoke(dialog, Collections.emptyMap(), Collections.emptyMap(), "GET", null);
        Field executorField = WebViewDialog.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(dialog);
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        ShadowLooper.shadowMainLooper().idle();
        assertFalse(webView.isAttachedToWindow());
        assertFalse(dialog.isShowing());
        assertEquals(options.getUrl(), webView.loadedUrl);
        if (hidden) {
            Method client = WebViewDialog.class.getDeclaredMethod("setWebViewClient");
            client.setAccessible(true);
            client.invoke(dialog);
            webView.getWebViewClient().onPageFinished(webView, options.getUrl());
            assertFalse("Deferred page completion must preserve hidden startup", dialog.isShowing());
        }
        webView.destroy();
    }

    @Test
    public void hideBeforeQueuedPreShowPresentationKeepsDialogHidden() throws Exception {
        assertQueuedPreShowPresentationCanceled(false);
    }

    @Test
    public void closeBeforeQueuedPreShowPresentationDoesNotReopenDialog() throws Exception {
        assertQueuedPreShowPresentationCanceled(true);
    }

    private void assertQueuedPreShowPresentationCanceled(boolean close) throws Exception {
        androidx.activity.ComponentActivity activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity.class)
            .setup()
            .get();
        Options options = new Options();
        options.setUrl("https://fullscreen.capgo.test/demo");
        options.setPresentAfterPageLoad(true);
        options.setPreShowScript("void 0");
        options.setCallbacks(
            (WebViewCallbacks) Proxy.newProxyInstance(
                WebViewCallbacks.class.getClassLoader(),
                new Class<?>[] { WebViewCallbacks.class },
                (proxy, method, args) -> null
            )
        );
        TrackingDialog dialog = new TrackingDialog(activity, options);
        dialog.activity = activity;
        TrackingWebView webView = new TrackingWebView(activity);
        Field viewField = WebViewDialog.class.getDeclaredField("_webView");
        viewField.setAccessible(true);
        viewField.set(dialog, webView);
        // An existing script skips duplicate injection; the real worker still queues its presentation callback.
        dialog.preShowSemaphore = new java.util.concurrent.Semaphore(0);
        Method client = WebViewDialog.class.getDeclaredMethod("setWebViewClient");
        client.setAccessible(true);
        client.invoke(dialog);
        webView.getWebViewClient().onPageFinished(webView, options.getUrl());
        Field executorField = WebViewDialog.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(dialog);
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        if (close) dialog.dismiss();
        else dialog.setHidden(true);
        ShadowLooper.shadowMainLooper().idle();
        assertEquals("A stale pre-show callback must not invoke presentation", 0, dialog.showCount);
        assertFalse("A stale pre-show callback must not present the dialog", dialog.isShowing());
    }

    private static class TrackingDialog extends WebViewDialog {

        int showCount;

        TrackingDialog(Context context, Options options) {
            super(context, android.R.style.Theme_NoTitleBar, options, null, null);
        }

        @Override
        public void show() {
            showCount++;
            super.show();
        }
    }

    private static class TrackingWebView extends WebView {

        String loadedUrl;

        TrackingWebView(Context context) {
            super(context);
        }

        @Override
        public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
            loadedUrl = baseUrl;
        }

        @Override
        public void loadUrl(String url, Map<String, String> headers) {
            loadedUrl = url;
        }
    }
}
