package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;

public interface WebViewCallbacks {
    public void urlChangeEvent(String url);

    public void closeEvent(String url);

    public void pageLoaded();

    public void pageLoadError();

    public void javascriptCallback(String message);

    public void consoleMessage(String level, String message, String source, Integer line, Integer column);

    public void buttonNearDoneClicked();

    public void confirmBtnClicked(String url);

    public void screenshotTaken(JSObject screenshot);

    public void downloadCompleted(String sourceUrl, String fileName, String mimeType, String path, String localUrl, String handledBy);

    public void downloadFailed(String sourceUrl, String fileName, String mimeType, String error);

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
