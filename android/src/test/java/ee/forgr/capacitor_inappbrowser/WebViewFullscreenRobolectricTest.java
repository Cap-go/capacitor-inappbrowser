package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.*;

import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WebViewFullscreenRobolectricTest {

    private static class Fixture {

        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        Options options = new Options();
        WebViewDialog dialog;
        WebView webView;
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout container;
        View toolbar;
        List<Boolean> events = new ArrayList<>();

        Fixture(boolean startup, boolean hidden) throws Exception {
            options.setUrl("https://example.com/start");
            options.setTitle("Browser");
            options.setFullscreen(startup);
            options.setHidden(hidden);
            dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, null, null);
            dialog.activity = activity;
            FrameLayout root = new FrameLayout(activity);
            root.setId(R.id.coordinator_layout);
            container = new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity);
            container.setId(R.id.content_browser_layout);
            container.setPadding(1, 20, 3, 40);
            webView = new WebView(activity);
            container.addView(webView);
            toolbar = new View(activity);
            toolbar.setId(R.id.app_bar_layout);
            root.addView(container);
            root.addView(toolbar);
            dialog.setContentView(root);
            Field field = WebViewDialog.class.getDeclaredField("_webView");
            field.setAccessible(true);
            field.set(dialog, webView);
            dialog.setFullscreenChangeListener(events::add);
            dialog.show();
        }
    }

    @Test
    public void transitionsKeepLiveWebViewAndRestoreWindowAndPadding() throws Exception {
        Fixture f = new Fixture(false, false);
        int flags = f.dialog.getWindow().getAttributes().flags;
        int visibility = f.dialog.getWindow().getDecorView().getSystemUiVisibility();
        Object parent = f.webView.getParent();
        f.dialog.setFullscreen(true);
        f.dialog.setFullscreen(true);
        assertTrue(f.dialog.isFullscreen());
        assertEquals(View.GONE, f.toolbar.getVisibility());
        assertEquals(0, f.container.getPaddingTop());
        assertSame(parent, f.webView.getParent());
        f.dialog.setFullscreen(false);
        f.dialog.setFullscreen(false);
        assertEquals(flags, f.dialog.getWindow().getAttributes().flags);
        assertEquals(visibility, f.dialog.getWindow().getDecorView().getSystemUiVisibility());
        assertEquals(View.VISIBLE, f.toolbar.getVisibility());
        assertEquals(20, f.container.getPaddingTop());
        assertEquals(40, f.container.getPaddingBottom());
        assertEquals(List.of(true, false), f.events);
        assertSame(parent, f.webView.getParent());
    }

    @Test
    public void startupEntersAndBackExitsWithoutClosing() throws Exception {
        Fixture f = new Fixture(true, false);
        assertTrue(f.dialog.isFullscreen());
        f.dialog.getOnBackPressedDispatcher().onBackPressed();
        assertFalse(f.dialog.isFullscreen());
        assertTrue(f.dialog.isShowing());
    }

    @Test
    public void hiddenStartupWaitsUntilShownAndBackgroundCancelsPendingEntry() throws Exception {
        Fixture f = new Fixture(true, true);
        assertFalse(f.dialog.isFullscreen());
        assertTrue(f.events.isEmpty());
        f.dialog.setHidden(false);
        assertTrue(f.dialog.isFullscreen());
        f.dialog.clearFullscreen();
        assertFalse(f.options.isFullscreen());
        Fixture pending = new Fixture(true, true);
        pending.dialog.clearFullscreen();
        pending.dialog.setHidden(false);
        assertFalse(pending.dialog.isFullscreen());
    }

    @Test
    public void hideClearsFullscreenWithoutDestroyingWebView() throws Exception {
        Fixture f = new Fixture(true, false);
        Object parent = f.webView.getParent();
        f.dialog.setHidden(true);
        assertFalse(f.dialog.isFullscreen());
        assertSame(parent, f.webView.getParent());
        f.dialog.setHidden(false);
        assertFalse(f.dialog.isFullscreen());
    }

    @Test
    public void exitButtonIsAccessibleAndDoesNotClose() throws Exception {
        Fixture f = new Fixture(true, false);
        Field field = WebViewDialog.class.getDeclaredField("fullscreenExitButton");
        field.setAccessible(true);
        View button = (View) field.get(f.dialog);
        assertEquals("Exit fullscreen", button.getContentDescription());
        assertTrue(button.getLayoutParams().width >= 48);
        button.performClick();
        assertFalse(f.dialog.isFullscreen());
        assertTrue(f.dialog.isShowing());
    }

    @Test
    public void mediaReturnsToNativeFullscreenThenGlobalExitRestoresBaseline() throws Exception {
        Fixture f = new Fixture(false, false);
        int flags = f.dialog.getWindow().getAttributes().flags;
        f.dialog.setFullscreen(true);
        Method show = WebViewDialog.class.getDeclaredMethod(
            "showCustomFullscreenView",
            View.class,
            WebChromeClient.CustomViewCallback.class
        );
        show.setAccessible(true);
        List<Boolean> hidden = new ArrayList<>();
        show.invoke(f.dialog, new View(f.activity), (WebChromeClient.CustomViewCallback) () -> hidden.add(true));
        Method exit = WebViewDialog.class.getDeclaredMethod("exitCustomFullscreenView");
        exit.setAccessible(true);
        exit.invoke(f.dialog);
        assertTrue(f.dialog.isFullscreen());
        f.dialog.clearFullscreen();
        assertEquals(flags, f.dialog.getWindow().getAttributes().flags);
        assertEquals(1, hidden.size());
    }

    @Test
    public void hiddenAndCustomSizedTargetsReject() throws Exception {
        Fixture f = new Fixture(false, true);
        assertThrows(IllegalStateException.class, () -> f.dialog.setFullscreen(true));
        f.options.setHidden(false);
        f.options.setHeight(100);
        assertThrows(IllegalStateException.class, () -> f.dialog.setFullscreen(true));
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void deactivationRestoresOlderDialogAndDoesNotAutoReenter() throws Exception {
        Fixture f = new Fixture(true, false);
        f.dialog.setActiveForBackNavigation(false);
        assertFalse(f.dialog.isFullscreen());
        f.dialog.setActiveForBackNavigation(true);
        assertFalse(f.dialog.isFullscreen());
    }

    @Test
    public void sameOriginNavigationStaysFullscreenAndCrossOriginExits() throws Exception {
        Fixture f = new Fixture(true, false);
        Method setup = WebViewDialog.class.getDeclaredMethod("setWebViewClient");
        setup.setAccessible(true);
        setup.invoke(f.dialog);
        f.webView.getWebViewClient().onPageStarted(f.webView, "https://example.com/next", null);
        assertTrue(f.dialog.isFullscreen());
        f.webView.getWebViewClient().onPageStarted(f.webView, "https://other.example/", null);
        assertFalse(f.dialog.isFullscreen());
        assertTrue(f.dialog.isShowing());
    }

    @Test
    public void rendererTerminationClearsFullscreen() throws Exception {
        Fixture f = new Fixture(true, false);
        Method setup = WebViewDialog.class.getDeclaredMethod("setWebViewClient");
        setup.setAccessible(true);
        setup.invoke(f.dialog);
        assertTrue(f.webView.getWebViewClient().onRenderProcessGone(f.webView, null));
        assertFalse(f.dialog.isFullscreen());
        assertEquals(List.of(true, false), f.events);
    }

    @Test
    public void rotationKeepsFullscreenAndExitInsetsFollowCutout() throws Exception {
        Fixture f = new Fixture(true, false);
        Method rotate = WebViewDialog.class.getDeclaredMethod("refreshLayoutForConfigurationChange");
        rotate.setAccessible(true);
        rotate.invoke(f.dialog);
        assertTrue(f.dialog.isFullscreen());
        assertEquals(View.GONE, f.toolbar.getVisibility());
        Method insets = WebViewDialog.class.getDeclaredMethod("updateFullscreenExitInsets", androidx.core.view.WindowInsetsCompat.class);
        insets.setAccessible(true);
        insets.invoke(
            f.dialog,
            new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsetsIgnoringVisibility(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                    androidx.core.graphics.Insets.of(0, 25, 30, 0)
                )
                .build()
        );
        Field buttonField = WebViewDialog.class.getDeclaredField("fullscreenExitButton");
        buttonField.setAccessible(true);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) ((View) buttonField.get(f.dialog)).getLayoutParams();
        assertEquals(25, params.topMargin);
        assertEquals(30, params.rightMargin);
    }

    @Test
    public void hiddenPendingStartupSurvivesAnotherActiveWebView() throws Exception {
        Fixture f = new Fixture(true, true);
        f.dialog.setActiveForBackNavigation(false);
        assertTrue(f.options.isFullscreen());
        f.dialog.setActiveForBackNavigation(true);
        f.dialog.setHidden(false);
        assertTrue(f.dialog.isFullscreen());
    }

    @Test
    public void dimensionChangesExitBeforeUpdatingBaseline() throws Exception {
        Fixture f = new Fixture(true, false);
        f.dialog.updateDimensions(null, 300, 10, 20);
        assertFalse(f.dialog.isFullscreen());
        assertEquals(Integer.valueOf(300), f.options.getHeight());
        assertEquals(Integer.valueOf(10), f.options.getX());
        assertEquals(300, f.dialog.getWindow().getAttributes().height);
        f.dialog.setFullscreen(false);
        assertEquals(300, f.dialog.getWindow().getAttributes().height);
    }

    @Test
    public void explicitHideCancelsPendingStartup() throws Exception {
        Fixture f = new Fixture(true, true);
        f.dialog.clearFullscreen();
        f.dialog.setHidden(true);
        f.dialog.setHidden(false);
        assertFalse(f.dialog.isFullscreen());
        assertTrue(f.events.isEmpty());
    }

    private static class TestCall extends com.getcapacitor.PluginCall {

        boolean resolved;
        String rejected;
        com.getcapacitor.JSObject result;

        TestCall(com.getcapacitor.JSObject data) {
            super(null, "InAppBrowser", "test", "test", data);
        }

        @Override
        public void resolve() {
            resolved = true;
        }

        @Override
        public void resolve(com.getcapacitor.JSObject data) {
            resolved = true;
            result = data;
        }

        @Override
        public void reject(String message) {
            rejected = message;
        }
    }

    private static class TestPlugin extends CapgoInAppBrowserPlugin {

        androidx.appcompat.app.AppCompatActivity host = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity.class).get();

        @Override
        public androidx.appcompat.app.AppCompatActivity getActivity() {
            return host;
        }
    }

    private static TestPlugin pluginFor(Fixture fixture) throws Exception {
        TestPlugin plugin = new TestPlugin();
        fixture.dialog.setInstanceId("one");
        Method register = CapgoInAppBrowserPlugin.class.getDeclaredMethod(
            "registerWebView",
            String.class,
            WebViewDialog.class,
            boolean.class
        );
        register.setAccessible(true);
        register.invoke(plugin, "one", fixture.dialog, true);
        return plugin;
    }

    @Test
    public void pluginHideCancelsPendingAndShowActivatesBeforeEntry() throws Exception {
        Fixture f = new Fixture(true, true);
        TestPlugin plugin = pluginFor(f);
        TestCall hide = new TestCall(new com.getcapacitor.JSObject());
        plugin.hide(hide);
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
        assertTrue(hide.resolved);
        assertFalse(f.options.isFullscreen());
        TestCall show = new TestCall(new com.getcapacitor.JSObject());
        plugin.show(show);
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
        assertTrue(show.resolved);
        assertFalse(f.dialog.isFullscreen());
    }

    @Test
    public void pluginRejectsMissingTargetsAndNonBooleanEnabled() throws Exception {
        Fixture f = new Fixture(false, false);
        TestPlugin plugin = pluginFor(f);
        TestCall invalid = new TestCall(new com.getcapacitor.JSObject().put("enabled", "true"));
        plugin.setFullscreen(invalid);
        assertNotNull(invalid.rejected);
        TestCall missing = new TestCall(new com.getcapacitor.JSObject().put("id", "missing").put("enabled", true));
        plugin.setFullscreen(missing);
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
        assertNotNull(missing.rejected);
        assertFalse(f.dialog.isFullscreen());
        TestCall enable = new TestCall(new com.getcapacitor.JSObject().put("enabled", true));
        plugin.setFullscreen(enable);
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
        assertTrue(enable.resolved);
        TestCall get = new TestCall(new com.getcapacitor.JSObject());
        plugin.getFullscreen(get);
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();
        assertTrue(get.result.getBool("enabled"));
    }

    @Test
    @Config(sdk = 33)
    public void fullscreenIncludesCutoutAndRestoresOriginalExclusion() throws Exception {
        Fixture f = new Fixture(false, false);
        android.view.WindowManager.LayoutParams baseline = f.dialog.getWindow().getAttributes();
        baseline.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        f.dialog.getWindow().setAttributes(baseline);
        f.dialog.setFullscreen(true);
        assertEquals(
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            f.dialog.getWindow().getAttributes().layoutInDisplayCutoutMode
        );
        f.dialog.setFullscreen(false);
        assertEquals(
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER,
            f.dialog.getWindow().getAttributes().layoutInDisplayCutoutMode
        );
    }

    @Test
    @Config(sdk = 28)
    public void androidPieFullscreenUsesSupportedShortEdgesMode() throws Exception {
        Fixture f = new Fixture(true, false);
        assertEquals(
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
            f.dialog.getWindow().getAttributes().layoutInDisplayCutoutMode
        );
        f.dialog.clearFullscreen();
        assertEquals(
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,
            f.dialog.getWindow().getAttributes().layoutInDisplayCutoutMode
        );
    }
}
