package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;

public interface WebViewCallbacks {
    public void urlChangeEvent(String url);

    public void closeEvent(String url);

    public void hideEvent(String url, JSObject screenshot);

    public void pageLoaded();

    public void pageLoadStart();

    public void pageLoadProgress(double progress);

    public void pageLoadError();

    public void customSchemeIntercepted(String url, boolean opened);

    public void deepLinkPrevented(String url);

    public void javascriptCallback(String message);

    public void consoleMessage(String level, String message, String source, Integer line, Integer column);

    public void buttonNearDoneClicked();

    public void confirmBtnClicked(String url);

    public void screenshotTaken(JSObject screenshot);

    public void downloadStarted(String downloadId, String sourceUrl);

    public void downloadProgress(String downloadId, long bytesReceived, long bytesExpected, String fileName);

    public void downloadCancelled(String downloadId, String sourceUrl);

    public void downloadCompleted(
        String downloadId,
        String sourceUrl,
        String fileName,
        String mimeType,
        String path,
        String localUrl,
        String handledBy
    );

    public void downloadFailed(String downloadId, String sourceUrl, String fileName, String mimeType, String error);

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
