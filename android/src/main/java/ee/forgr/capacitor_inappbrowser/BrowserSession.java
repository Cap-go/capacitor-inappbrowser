package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;

public interface BrowserSession {

    interface ScreenshotResultCallback {
        void onSuccess(JSObject screenshot);

        void onError(String message);
    }

    void setInstanceId(String id);

    void present();

    String getUrl();

    void setUrl(String url);

    void postMessageToJS(Object detail);

    void setHidden(boolean hidden);

    boolean isShowing();

    void show();

    void executeScript(String script);

    void takeScreenshot(ScreenshotResultCallback callback);

    boolean goBack();

    void reload();

    void dismiss();

    void updateDimensions(Integer width, Integer height, Integer x, Integer y);

    void setEnabledSafeTopMargin(boolean enabled);

    void setEnabledSafeBottomMargin(boolean enabled);
}
