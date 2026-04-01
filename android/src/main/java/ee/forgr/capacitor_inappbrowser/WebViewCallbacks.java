package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;

public interface WebViewCallbacks {
    public void urlChangeEvent(String url);

    public void closeEvent(String url);

    public void pageLoaded();

    public void pageLoadError();

    public void javascriptCallback(String message);

    public void buttonNearDoneClicked();

    public void confirmBtnClicked(String url);

    public void screenshotTaken(JSObject screenshot);

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
    );
}
