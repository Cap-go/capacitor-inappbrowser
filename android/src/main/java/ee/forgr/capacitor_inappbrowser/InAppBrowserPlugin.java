package ee.forgr.capacitor_inappbrowser;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Iterator;

@CapacitorPlugin(name = "InAppBrowser")
public class InAppBrowserPlugin extends Plugin {

  public static final String CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome"; // Change when in stable
  private CustomTabsClient customTabsClient;
  private CustomTabsSession currentSession;
  private WebViewDialog webViewDialog = null;
  private String currentUrl = "";

  CustomTabsServiceConnection connection = new CustomTabsServiceConnection() {
    @Override
    public void onCustomTabsServiceConnected(
      ComponentName name,
      CustomTabsClient client
    ) {
      customTabsClient = client;
      client.warmup(0);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {}
  };

  @PluginMethod
  public void setUrl(PluginCall call) {
    String url = call.getString("url");
    if (url == null || TextUtils.isEmpty(url)) {
      call.reject("Invalid URL");
    }
    currentUrl = url;
    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            webViewDialog.setUrl(url);
          }
        }
      );
    call.resolve();
  }

  @PluginMethod
  public void open(PluginCall call) {
    String url = call.getString("url");

    // get the deeplink prevention, if provided
    Boolean preventDeeplink = call.getBoolean("preventDeeplink", null);

    if (url == null || TextUtils.isEmpty(url)) {
      call.reject("Invalid URL");
    }
    currentUrl = url;
    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(
      getCustomTabsSession()
    );
    CustomTabsIntent tabsIntent = builder.build();
    tabsIntent.intent.putExtra(
      Intent.EXTRA_REFERRER,
      Uri.parse(
        Intent.URI_ANDROID_APP_SCHEME + "//" + getContext().getPackageName()
      )
    );
    tabsIntent.intent.putExtra(
      android.provider.Browser.EXTRA_HEADERS,
      this.getHeaders(call)
    );

    if (preventDeeplink != null) {
      String browserPackageName = "";
      Intent browserIntent = new Intent(
        Intent.ACTION_VIEW,
        Uri.parse("http://")
      );
      ResolveInfo resolveInfo = getContext()
        .getPackageManager()
        .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);

      if (resolveInfo != null) {
        browserPackageName = resolveInfo.activityInfo.packageName;

        if (!browserPackageName.isEmpty()) {
          tabsIntent.intent.setPackage(browserPackageName);
        }
      }
    }

    tabsIntent.launchUrl(getContext(), Uri.parse(url));

    call.resolve();
  }

  @PluginMethod
  public void clearCookies(PluginCall call) {
    if (webViewDialog == null) {
      call.reject("WebView is not open");
    } else {
      String url = currentUrl;
      if (url == null || TextUtils.isEmpty(url)) {
        call.reject("Invalid URL");
      } else {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookie = cookieManager.getCookie(url);
        if (cookie != null) {
          String[] cookies = cookie.split(";");
          for (String c : cookies) {
            String cookieName = c.substring(0, c.indexOf("="));
            cookieManager.setCookie(
              url,
              cookieName + "=; Expires=Thu, 01 Jan 1970 00:00:01 GMT"
            );
          }
        }
        call.resolve();
      }
    }
  }

  @PluginMethod
  public void openWebView(PluginCall call) {
    String url = call.getString("url");
    if (url == null || TextUtils.isEmpty(url)) {
      call.reject("Invalid URL");
    }
    currentUrl = url;
    final Options options = new Options();
    options.setUrl(url);
    options.setHeaders(call.getObject("headers"));
    options.setShowReloadButton(call.getBoolean("showReloadButton", false));
    if (Boolean.TRUE.equals(call.getBoolean("visibleTitle", true))) {
      options.setTitle(call.getString("title", "New Window"));
    } else {
      options.setTitle(call.getString("title", ""));
    }
    options.setToolbarColor(call.getString("toolbarColor", "#ffffff"));
    options.setArrow(Boolean.TRUE.equals(call.getBoolean("showArrow", false)));

    options.setShareDisclaimer(call.getObject("shareDisclaimer", null));
    options.setShareSubject(call.getString("shareSubject", null));
    options.setToolbarType(call.getString("toolbarType", ""));
    options.setPresentAfterPageLoad(
      call.getBoolean("isPresentAfterPageLoad", false)
    );
    if (call.getBoolean("closeModal", false)) {
      options.setCloseModal(true);
      options.setCloseModalTitle(call.getString("closeModalTitle", "Close"));
      options.setCloseModalDescription(
        call.getString("closeModalDescription", "Are you sure ?")
      );
      options.setCloseModalOk(call.getString("closeModalOk", "Ok"));
      options.setCloseModalCancel(call.getString("closeModalCancel", "Cancel"));
    } else {
      options.setCloseModal(false);
    }
    options.setPluginCall(call);
    options.setCallbacks(
      new WebViewCallbacks() {
        @Override
        public void urlChangeEvent(String url) {
          notifyListeners("urlChangeEvent", new JSObject().put("url", url));
        }

        @Override
        public void closeEvent(String url) {
          notifyListeners("closeEvent", new JSObject().put("url", url));
        }

        @Override
        public void pageLoaded() {
          notifyListeners("browserPageLoaded", new JSObject());
        }

        @Override
        public void pageLoadError() {
          notifyListeners("pageLoadError", new JSObject());
        }
      }
    );
    getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            webViewDialog =
              new WebViewDialog(
                getContext(),
                android.R.style.Theme_NoTitleBar,
                options
              );
            webViewDialog.presentWebView();
          }
        }
      );
  }

  @PluginMethod
  public void close(PluginCall call) {
    if (webViewDialog != null) {
      webViewDialog.dismiss();
      webViewDialog = null;
    } else {
      Intent intent = new Intent(
        getContext(),
        getBridge().getActivity().getClass()
      );
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      getContext().startActivity(intent);
    }
    call.resolve();
  }

  private Bundle getHeaders(PluginCall pluginCall) {
    JSObject headersProvided = pluginCall.getObject("headers");
    Bundle headers = new Bundle();
    if (headersProvided != null) {
      Iterator<String> keys = headersProvided.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        headers.putString(key, headersProvided.getString(key));
      }
    }
    return headers;
  }

  protected void handleOnResume() {
    boolean ok = CustomTabsClient.bindCustomTabsService(
      getContext(),
      CUSTOM_TAB_PACKAGE_NAME,
      connection
    );
    if (!ok) {
      Log.e(getLogTag(), "Error binding to custom tabs service");
    }
  }

  protected void handleOnPause() {
    getContext().unbindService(connection);
  }

  public CustomTabsSession getCustomTabsSession() {
    if (customTabsClient == null) {
      return null;
    }

    if (currentSession == null) {
      currentSession =
        customTabsClient.newSession(
          new CustomTabsCallback() {
            @Override
            public void onNavigationEvent(int navigationEvent, Bundle extras) {
              switch (navigationEvent) {
                case NAVIGATION_FINISHED:
                  notifyListeners("browserPageLoaded", new JSObject());
                  break;
              }
            }
          }
        );
    }
    return currentSession;
  }
}
