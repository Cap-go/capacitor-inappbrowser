package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.webkit.WebView;
import androidx.activity.ComponentActivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class JavascriptBridgeAllowlistRobolectricTest {

    @Test
    public void allowlistCheckUsesMainThreadUrlFromJavaBridgeThread() throws Exception {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();

        Options options = new Options();
        options.setJavascriptBridgeOriginPolicy(
            JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("https://login.example.com"))
        );
        WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
        dialog.activity = activity;

        UrlTrackingWebView webView = new UrlTrackingWebView(activity, "https://login.example.com/oauth");
        setWebView(dialog, webView);
        ShadowLooper.idleMainLooper();

        Method method = WebViewDialog.class.getDeclaredMethod("isJavascriptBridgeAllowedForMainFrame");
        method.setAccessible(true);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean allowed = new AtomicBoolean(false);
        AtomicBoolean threw = new AtomicBoolean(false);
        Thread javaBridgeThread = new Thread(() -> {
            try {
                allowed.set((Boolean) method.invoke(dialog));
            } catch (Exception e) {
                threw.set(true);
            } finally {
                latch.countDown();
            }
        }, "JavaBridge");
        javaBridgeThread.start();
        long deadlineMs = System.currentTimeMillis() + 5000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadlineMs) {
            ShadowLooper.idleMainLooper();
            latch.await(25, TimeUnit.MILLISECONDS);
        }
        assertTrue("bridge allowlist check timed out", latch.getCount() == 0);
        assertFalse("bridge allowlist check threw on JavaBridge thread", threw.get());
        assertTrue(allowed.get());
    }

    @Test
    public void allowlistDeniesDisallowedOriginFromJavaBridgeThread() throws Exception {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();

        Options options = new Options();
        options.setJavascriptBridgeOriginPolicy(
            JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("https://login.example.com"))
        );
        WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
        dialog.activity = activity;

        UrlTrackingWebView webView = new UrlTrackingWebView(activity, "https://evil.example.com/phish");
        setWebView(dialog, webView);
        ShadowLooper.idleMainLooper();

        Method method = WebViewDialog.class.getDeclaredMethod("isJavascriptBridgeAllowedForMainFrame");
        method.setAccessible(true);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean allowed = new AtomicBoolean(true);
        AtomicBoolean threw = new AtomicBoolean(false);
        Thread javaBridgeThread = new Thread(() -> {
            try {
                allowed.set((Boolean) method.invoke(dialog));
            } catch (Exception e) {
                threw.set(true);
                allowed.set(false);
            } finally {
                latch.countDown();
            }
        }, "JavaBridge");
        javaBridgeThread.start();
        long deadlineMs = System.currentTimeMillis() + 5000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadlineMs) {
            ShadowLooper.idleMainLooper();
            latch.await(25, TimeUnit.MILLISECONDS);
        }
        assertTrue(latch.getCount() == 0);
        assertFalse("bridge allowlist check threw on JavaBridge thread", threw.get());
        assertFalse(allowed.get());
    }

    private static void setWebView(WebViewDialog dialog, WebView webView) throws Exception {
        Field field = WebViewDialog.class.getDeclaredField("_webView");
        field.setAccessible(true);
        field.set(dialog, webView);
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
