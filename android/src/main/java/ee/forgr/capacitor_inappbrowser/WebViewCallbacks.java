package ee.forgr.capacitor_inappbrowser;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

public interface WebViewCallbacks {
  public void urlChangeEvent(String url);

  public void closeEvent(String url);

  public void pageLoaded();

  public void pageLoadError();

  public void javascriptCallback(String message);

  public WebResourceResponse shouldInterceptRequest(WebResourceRequest request);
}
