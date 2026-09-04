package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Message;
import android.view.View;
import android.webkit.PermissionRequest;
import android.widget.FrameLayout;
import androidx.activity.ComponentActivity;
import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

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
    public void activityBackHandlerKeepsDisableGoBackOverlayOpenWhenDialogIsShowing() {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();
        activity.setContentView(new FrameLayout(activity));
        Options options = new Options();
        options.setDisableGoBackOnNativeApplication(true);

        WebViewDialog dialog = createBoundDialog(activity, options);
        showDialog(dialog);

        assertTrue(dialog.isActivityBackHandlerEnabled());
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(dialog.isShowing());
    }

    @Test
    public void activityBackHandlerKeepsBackLayerOverlayOpenWhenDisableGoBackEnabled() {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();
        activity.setContentView(new FrameLayout(activity));

        WebViewDialog dialog = createBoundDialog(activity, optionsWithDisableGoBack(true));
        showDialog(dialog);

        View overlayContent = requireOverlayContent(dialog);
        assertTrue(dialog.sendToBack(false));
        idleMainLooper();
        assertFalse(dialog.isShowing());
        assertTrue(dialog.isBackLayerActive());
        assertTrue(dialog.isActivityBackHandlerEnabled());
        assertNotNull(overlayContent.getParent());

        activity.getOnBackPressedDispatcher().onBackPressed();

        assertTrue(dialog.isBackLayerActive());
        assertNotNull(overlayContent.getParent());
    }

    @Test
    public void activityBackHandlerDismissesBackLayerOverlayWhenDisableGoBackDisabled() {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();
        activity.setContentView(new FrameLayout(activity));

        WebViewDialog dialog = createBoundDialog(activity, optionsWithDisableGoBack(false));
        showDialog(dialog);

        View overlayContent = requireOverlayContent(dialog);
        assertTrue(dialog.sendToBack(false));
        idleMainLooper();
        assertFalse(dialog.isShowing());
        assertTrue(dialog.isBackLayerActive());
        assertTrue(dialog.isActivityBackHandlerEnabled());

        activity.getOnBackPressedDispatcher().onBackPressed();

        assertFalse(dialog.isBackLayerActive());
        assertNull(overlayContent.getParent());
    }

    @Test
    public void onlyActiveDialogRegistersActivityBackHandlerAmongMultipleDialogs() {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();
        activity.setContentView(new FrameLayout(activity));

        WebViewDialog activeDialog = createBoundDialog(activity, optionsWithDisableGoBack(true));
        showDialog(activeDialog);

        WebViewDialog inactiveDialog = createBoundDialog(activity, optionsWithDisableGoBack(true));
        showDialog(inactiveDialog);

        activeDialog.setActiveForBackNavigation(true);
        inactiveDialog.setActiveForBackNavigation(false);
        idleMainLooper();

        assertTrue(activeDialog.isActivityBackHandlerEnabled());
        assertFalse(inactiveDialog.isActivityBackHandlerEnabled());

        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(activeDialog.isShowing());
        assertTrue(inactiveDialog.isShowing());

        activeDialog.setActiveForBackNavigation(false);
        inactiveDialog.setActiveForBackNavigation(true);
        idleMainLooper();

        assertFalse(activeDialog.isActivityBackHandlerEnabled());
        assertTrue(inactiveDialog.isActivityBackHandlerEnabled());
    }

    @Test
    public void bringingOlderDialogForwardRoutesActivityBackToIt() {
        ActivityController<ComponentActivity> controller = Robolectric.buildActivity(ComponentActivity.class).setup();
        ComponentActivity activity = controller.get();
        activity.setContentView(new FrameLayout(activity));

        WebViewDialog olderDialog = createBoundDialog(activity, optionsWithDisableGoBack(true));
        showDialog(olderDialog);

        WebViewDialog newerDialog = createBoundDialog(activity, optionsWithDisableGoBack(true));
        showDialog(newerDialog);

        newerDialog.setActiveForBackNavigation(true);
        olderDialog.setActiveForBackNavigation(false);
        idleMainLooper();
        assertTrue(newerDialog.isActivityBackHandlerEnabled());
        assertFalse(olderDialog.isActivityBackHandlerEnabled());

        olderDialog.setActiveForBackNavigation(true);
        newerDialog.setActiveForBackNavigation(false);
        idleMainLooper();
        assertTrue(olderDialog.isActivityBackHandlerEnabled());
        assertFalse(newerDialog.isActivityBackHandlerEnabled());

        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(olderDialog.isShowing());
        assertTrue(newerDialog.isShowing());
    }

    private static void showDialog(WebViewDialog dialog) {
        dialog.show();
        idleMainLooper();
        dialog.applyBackNavigationPolicy();
    }

    private static void idleMainLooper() {
        ShadowLooper.shadowMainLooper().idle();
    }

    private static Options optionsWithDisableGoBack(boolean disableGoBack) {
        Options options = new Options();
        options.setDisableGoBackOnNativeApplication(disableGoBack);
        return options;
    }

    private static WebViewDialog createBoundDialog(ComponentActivity activity, Options options) {
        WebViewDialog dialog = new WebViewDialog(activity, android.R.style.Theme_NoTitleBar, options, noopPermissionHandler(), null);
        dialog.activity = activity;
        dialog.bindToHostActivity();
        dialog.applyBackNavigationPolicy();
        setBrowserContent(dialog, activity);
        return dialog;
    }

    private static void setBrowserContent(WebViewDialog dialog, ComponentActivity activity) {
        CoordinatorLayout coordinatorLayout = new CoordinatorLayout(activity);
        coordinatorLayout.setId(R.id.coordinator_layout);
        dialog.setContentView(coordinatorLayout);
    }

    private static View requireOverlayContent(WebViewDialog dialog) {
        View overlayContent = dialog.findViewById(R.id.coordinator_layout);
        assertNotNull(overlayContent);
        return overlayContent;
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
