package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Message;
import android.webkit.PermissionRequest;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WebViewDialogBackNavigationRobolectricTest {

    @Test
    public void cancelableDialogDismissesOnBackWhenStayInWebViewDisabled() {
        Context context = RuntimeEnvironment.getApplication();
        ComponentDialog dialog = new ComponentDialog(context);
        dialog.setCancelable(WebViewBackNavigationSupport.isCancelableOnBack(false));
        dialog.setContentView(new FrameLayout(context));
        dialog.show();

        assertTrue(dialog.isShowing());
        dialog.getOnBackPressedDispatcher().onBackPressed();
        assertFalse(dialog.isShowing());
    }

    @Test
    public void nonCancelableDialogStaysOpenOnBackWithoutDismissHandler() {
        Context context = RuntimeEnvironment.getApplication();
        ComponentDialog dialog = new ComponentDialog(context);
        dialog.setCancelable(WebViewBackNavigationSupport.isCancelableOnBack(true));
        dialog.setContentView(new FrameLayout(context));
        dialog.show();

        assertTrue(dialog.isShowing());
        dialog.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(dialog.isShowing());
    }

    @Test
    public void enabledBackCallbackConsumesBackWithoutDismissingWhenStayInWebViewEnabled() {
        Context context = RuntimeEnvironment.getApplication();
        ComponentDialog dialog = new ComponentDialog(context);
        dialog.setCancelable(WebViewBackNavigationSupport.isCancelableOnBack(true));
        dialog.setContentView(new FrameLayout(context));

        OnBackPressedDispatcher dispatcher = dialog.getOnBackPressedDispatcher();
        dispatcher.addCallback(
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // Mirrors WebViewDialog IGNORE path for disableGoBackOnNativeApplication.
                }
            }
        );

        dialog.show();
        assertTrue(dialog.isShowing());
        dispatcher.onBackPressed();
        assertTrue(dialog.isShowing());
    }

    @Test
    public void webViewDialogWithDisableGoBackStaysOpenOnBack() {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setDisableGoBackOnNativeApplication(true);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.bindToHostActivity();
        dialog.applyBackNavigationPolicy();
        dialog.setContentView(new FrameLayout(context));
        dialog.show();

        assertTrue(dialog.isShowing());
        dialog.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(dialog.isShowing());
    }

    @Test
    public void webViewDialogWithoutDisableGoBackDismissesOnBack() {
        Context context = RuntimeEnvironment.getApplication();
        Options options = new Options();
        options.setDisableGoBackOnNativeApplication(false);

        WebViewDialog dialog = new WebViewDialog(context, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.bindToHostActivity();
        dialog.applyBackNavigationPolicy();
        dialog.setContentView(new FrameLayout(context));
        dialog.show();

        assertTrue(dialog.isShowing());
        dialog.getOnBackPressedDispatcher().onBackPressed();
        assertFalse(dialog.isShowing());
    }

    @Test
    public void activityBackHandlerKeepsDisableGoBackOverlayOpenOnBackLayer() {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();
        Options options = new Options();
        options.setDisableGoBackOnNativeApplication(true);

        WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.activity = activity;
        dialog.bindToHostActivity();
        dialog.applyBackNavigationPolicy();
        dialog.setContentView(new FrameLayout(activity));
        dialog.show();

        assertTrue(WebViewBackNavigationSupport.shouldRegisterActivityBackHandler(true, false, false, true));
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(dialog.isShowing());
    }

    private static WebViewDialog.PermissionHandler noopPermissionHandler() {
        return new WebViewDialog.PermissionHandler() {
            @Override
            public void handleCameraPermissionRequest(PermissionRequest request) {}

            @Override
            public void handleMicrophonePermissionRequest(PermissionRequest request) {}

            @Override
            public void clearPendingPermissionRequest(PermissionRequest request) {}

            @Override
            public boolean createManagedPopupWindow(WebViewDialog parentDialog, Message resultMsg, boolean isUserGesture, String popupUrl) {
                return false;
            }
        };
    }
}
