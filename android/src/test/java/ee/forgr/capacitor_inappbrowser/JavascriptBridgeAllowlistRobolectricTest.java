package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.webkit.WebView;
import androidx.activity.ComponentActivity;
import androidx.test.core.app.ActivityScenario;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class JavascriptBridgeAllowlistRobolectricTest {

    @Test
    public void allowlistCheckUsesMainThreadUrlFromJavaBridgeThread() throws Exception {
        ActivityScenario<ComponentActivity> scenario = ActivityScenario.launch(ComponentActivity.class);
        scenario.onActivity((activity) -> {
            try {
                Options options = new Options();
                options.setJavascriptBridgeOriginPolicy(
                    JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("https://login.example.com"))
                );
                WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
                dialog.activity = activity;

                WebView webView = new WebView(activity);
                webView.loadUrl("https://login.example.com/oauth");
                setWebView(dialog, webView);

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
                assertTrue("bridge allowlist check timed out", latch.await(5, TimeUnit.SECONDS));
                assertFalse("bridge allowlist check threw on JavaBridge thread", threw.get());
                assertTrue(allowed.get());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void allowlistDeniesDisallowedOriginFromJavaBridgeThread() throws Exception {
        ActivityScenario<ComponentActivity> scenario = ActivityScenario.launch(ComponentActivity.class);
        scenario.onActivity((activity) -> {
            try {
                Options options = new Options();
                options.setJavascriptBridgeOriginPolicy(
                    JavascriptBridgeOriginPolicy.fromAllowlist(Collections.singletonList("https://login.example.com"))
                );
                WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
                dialog.activity = activity;

                WebView webView = new WebView(activity);
                webView.loadUrl("https://evil.example.com/phish");
                setWebView(dialog, webView);

                Method method = WebViewDialog.class.getDeclaredMethod("isJavascriptBridgeAllowedForMainFrame");
                method.setAccessible(true);

                CountDownLatch latch = new CountDownLatch(1);
                AtomicBoolean allowed = new AtomicBoolean(true);
                Thread javaBridgeThread = new Thread(() -> {
                    try {
                        allowed.set((Boolean) method.invoke(dialog));
                    } catch (Exception e) {
                        allowed.set(false);
                    } finally {
                        latch.countDown();
                    }
                }, "JavaBridge");
                javaBridgeThread.start();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertFalse(allowed.get());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static void setWebView(WebViewDialog dialog, WebView webView) throws Exception {
        java.lang.reflect.Field field = WebViewDialog.class.getDeclaredField("_webView");
        field.setAccessible(true);
        field.set(dialog, webView);
    }
}
