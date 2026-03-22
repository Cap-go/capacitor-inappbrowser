package ee.forgr.capacitor_inappbrowser;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;

public class SystemWebViewSession implements BrowserSession, BrowserSessionFileChooser, BrowserSessionProxy {

    private final WebViewDialog dialog;

    public SystemWebViewSession(
        Context context,
        int theme,
        Options options,
        WebViewDialog.PermissionHandler permissionHandler,
        WebView capacitorWebView,
        Activity activity
    ) {
        dialog = new WebViewDialog(context, theme, options, permissionHandler, capacitorWebView);
        dialog.activity = activity;
    }

    @Override
    public void setInstanceId(String id) {
        dialog.setInstanceId(id);
    }

    @Override
    public void present() {
        dialog.presentWebView();
    }

    @Override
    public String getUrl() {
        return dialog.getUrl();
    }

    @Override
    public void setUrl(String url) {
        dialog.setUrl(url);
    }

    @Override
    public void postMessageToJS(Object detail) {
        dialog.postMessageToJS(detail);
    }

    @Override
    public void setHidden(boolean hidden) {
        dialog.setHidden(hidden);
    }

    @Override
    public boolean isShowing() {
        return dialog.isShowing();
    }

    @Override
    public void show() {
        dialog.show();
    }

    @Override
    public void executeScript(String script) {
        dialog.executeScript(script);
    }

    @Override
    public void takeScreenshot(ScreenshotResultCallback callback) {
        dialog.takeScreenshot(
            new WebViewDialog.ScreenshotResultCallback() {
                @Override
                public void onSuccess(com.getcapacitor.JSObject screenshot) {
                    callback.onSuccess(screenshot);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            }
        );
    }

    @Override
    public boolean goBack() {
        return dialog.goBack();
    }

    @Override
    public void reload() {
        dialog.reload();
    }

    @Override
    public void dismiss() {
        dialog.dismiss();
    }

    @Override
    public void updateDimensions(Integer width, Integer height, Integer x, Integer y) {
        dialog.updateDimensions(width, height, x, y);
    }

    @Override
    public void setEnabledSafeTopMargin(boolean enabled) {
        dialog.setEnabledSafeTopMargin(enabled);
    }

    @Override
    public void setEnabledSafeBottomMargin(boolean enabled) {
        dialog.setEnabledSafeBottomMargin(enabled);
    }

    @Override
    public boolean hasPendingFileChooser() {
        return dialog.mFilePathCallback != null;
    }

    @Override
    public Uri getTempCameraUri() {
        return dialog.tempCameraUri;
    }

    @Override
    public void deliverFileChooserResult(Uri[] results) {
        if (dialog.mFilePathCallback != null) {
            dialog.mFilePathCallback.onReceiveValue(results);
        }
    }

    @Override
    public void clearPendingFileChooser() {
        dialog.mFilePathCallback = null;
        dialog.tempCameraUri = null;
    }

    @Override
    public void handleProxyResultError(String result, String id) {
        dialog.handleProxyResultError(result, id);
    }

    @Override
    public void handleProxyResultOk(org.json.JSONObject result, String id) {
        dialog.handleProxyResultOk(result, id);
    }
}
