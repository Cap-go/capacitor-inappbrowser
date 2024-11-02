package ee.forgr.capacitor_inappbrowser;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
  name = "InAppBrowser",
  permissions = {
    @Permission(alias = "camera", strings = { Manifest.permission.CAMERA }),
    @Permission(
      alias = "microphone",
      strings = { Manifest.permission.RECORD_AUDIO }
    ),
    @Permission(
      alias = "storage",
      strings = { Manifest.permission.READ_EXTERNAL_STORAGE }
    ),
    @Permission(
      alias = "storage",
      strings = { Manifest.permission.READ_MEDIA_IMAGES }
    ),
  },
  requestCodes = { WebViewDialog.FILE_CHOOSER_REQUEST_CODE }
)
public class InAppBrowserPlugin
  extends Plugin
  implements WebViewDialog.PermissionHandler {

  public static final String CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome"; // Change when in stable
  private CustomTabsClient customTabsClient;
  private CustomTabsSession currentSession;
  private WebViewDialog webViewDialog = null;
  private String currentUrl = "";

  private PermissionRequest currentPermissionRequest;

  public void handleMicrophonePermissionRequest(PermissionRequest request) {
    this.currentPermissionRequest = request;
    if (getPermissionState("microphone") != PermissionState.GRANTED) {
      requestPermissionForAlias(
        "microphone",
        null,
        "microphonePermissionCallback"
      );
    } else {
      grantMicrophonePermission();
    }
  }

  private void grantMicrophonePermission() {
    if (currentPermissionRequest != null) {
      currentPermissionRequest.grant(
        new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE }
      );
      currentPermissionRequest = null;
    }
  }

  @PermissionCallback
  private void microphonePermissionCallback(PluginCall call) {
    if (getPermissionState("microphone") == PermissionState.GRANTED) {
      grantCameraAndMicrophonePermission();
    } else {
      if (currentPermissionRequest != null) {
        currentPermissionRequest.deny();
        currentPermissionRequest = null;
      }
      call.reject("Microphone permission is required");
    }
  }

  private void grantCameraAndMicrophonePermission() {
    if (currentPermissionRequest != null) {
      currentPermissionRequest.grant(
        new String[] {
          PermissionRequest.RESOURCE_VIDEO_CAPTURE,
          PermissionRequest.RESOURCE_AUDIO_CAPTURE,
        }
      );
      currentPermissionRequest = null;
    }
  }

  public void handleCameraPermissionRequest(PermissionRequest request) {
    this.currentPermissionRequest = request;
    if (getPermissionState("camera") != PermissionState.GRANTED) {
      requestPermissionForAlias("camera", null, "cameraPermissionCallback");
    } else if (getPermissionState("microphone") != PermissionState.GRANTED) {
      requestPermissionForAlias(
        "microphone",
        null,
        "microphonePermissionCallback"
      );
    } else {
      grantCameraAndMicrophonePermission();
    }
  }

  @Override
  protected void handleOnActivityResult(
    int requestCode,
    int resultCode,
    Intent data
  ) {
    super.handleOnActivityResult(requestCode, resultCode, data);

    // Check if the request code matches the file chooser request code
    if (requestCode == WebViewDialog.FILE_CHOOSER_REQUEST_CODE) {
      if (webViewDialog != null && webViewDialog.mFilePathCallback != null) {
        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
          if (data != null) {
            String dataString = data.getDataString();
            if (data.getClipData() != null) { // If multiple file selected
              int count = data.getClipData().getItemCount();
              results = new Uri[count];
              for (int i = 0; i < count; i++) {
                results[i] = data.getClipData().getItemAt(i).getUri();
              }
            } else if (dataString != null) { //if single file selected
              results = new Uri[] { Uri.parse(dataString) };
            }
          }
        }

        // Pass the results back to the WebView
        try {
          webViewDialog.mFilePathCallback.onReceiveValue(results);
          webViewDialog.mFilePathCallback = null;
        } catch (Exception e) {
          Log.e("ACTIVITYRESULT", e.getMessage());
        }
      }
    }
  }

  @PermissionCallback
  private void cameraPermissionCallback() {
    if (getPermissionState("camera") == PermissionState.GRANTED) {
      grantCameraPermission();
    } else {
      if (currentPermissionRequest != null) {
        currentPermissionRequest.deny();
        currentPermissionRequest = null;
      }
      // Handle the case where permission was not granted
    }
  }

  @PermissionCallback
  private void cameraPermissionCallback(PluginCall call) {
    if (getPermissionState("camera") == PermissionState.GRANTED) {
      if (getPermissionState("microphone") != PermissionState.GRANTED) {
        requestPermissionForAlias(
          "microphone",
          null,
          "microphonePermissionCallback"
        );
      } else {
        grantCameraAndMicrophonePermission();
      }
    } else {
      if (currentPermissionRequest != null) {
        currentPermissionRequest.deny();
        currentPermissionRequest = null;
      }
      call.reject("Camera permission is required");
    }
  }

  private void grantCameraPermission() {
    if (currentPermissionRequest != null) {
      currentPermissionRequest.grant(
        new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE }
      );
      currentPermissionRequest = null;
    }
  }

  CustomTabsServiceConnection connection = new CustomTabsServiceConnection() {
    @Override
    public void onCustomTabsServiceConnected(
      @NonNull ComponentName name,
      CustomTabsClient client
    ) {
      customTabsClient = client;
      client.warmup(0);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {}
  };

  @PluginMethod
  public void requestCameraPermission(PluginCall call) {
    if (getPermissionState("camera") != PermissionState.GRANTED) {
      requestPermissionForAlias("camera", call, "cameraPermissionCallback");
    } else {
      call.resolve();
    }
  }

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
  public void clearCache(PluginCall call) {
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.removeAllCookies(null);
    cookieManager.flush();
    call.resolve();
  }

  @PluginMethod
  public void clearAllCookies(PluginCall call) {
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.removeAllCookies(null);
    cookieManager.flush();
    call.resolve();
  }

  @PluginMethod
  public void clearCookies(PluginCall call) {
    String url = call.getString("url", currentUrl);
    if (url == null || TextUtils.isEmpty(url)) {
      call.reject("Invalid URL");
      return;
    }

    Uri uri = Uri.parse(url);
    String host = uri.getHost();
    if (host == null || TextUtils.isEmpty(host)) {
      call.reject("Invalid URL (Host is null)");
      return;
    }

    CookieManager cookieManager = CookieManager.getInstance();
    String cookieString = cookieManager.getCookie(url);
    ArrayList<String> cookiesToRemove = new ArrayList<>();

    if (cookieString != null) {
      String[] cookies = cookieString.split("; ");

      String domain = uri.getHost();

      for (String cookie : cookies) {
        String[] parts = cookie.split("=");
        if (parts.length > 0) {
          cookiesToRemove.add(parts[0].trim());
          CookieManager.getInstance()
            .setCookie(url, String.format("%s=del;", parts[0].trim()));
        }
      }
    }

    StringBuilder scriptToRun = new StringBuilder();
    for (String cookieToRemove : cookiesToRemove) {
      scriptToRun.append(
        String.format(
          "window.cookieStore.delete('%s', {name: '%s', domain: '%s'});",
          cookieToRemove,
          cookieToRemove,
          url
        )
      );
    }

    Log.i("DelCookies", String.format("Script to run:\n%s", scriptToRun));

    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            webViewDialog.executeScript(scriptToRun.toString());
          }
        }
      );

    call.resolve();
  }

  @PluginMethod
  public void getCookies(PluginCall call) {
    String url = call.getString("url");
    if (url == null || TextUtils.isEmpty(url)) {
      call.reject("Invalid URL");
    } else {
      CookieManager cookieManager = CookieManager.getInstance();
      String cookieString = cookieManager.getCookie(url);
      if (cookieString != null) {
        String[] cookiePairs = cookieString.split("; ");
        JSObject result = new JSObject();
        for (String cookie : cookiePairs) {
          String[] parts = cookie.split("=", 2);
          if (parts.length == 2) {
            result.put(parts[0], parts[1]);
          }
        }
        call.resolve(result);
      }
      call.resolve(new JSObject());
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
    options.setCredentials(call.getObject("credentials"));
    options.setShowReloadButton(
      Boolean.TRUE.equals(call.getBoolean("showReloadButton", false))
    );
    options.setVisibleTitle(
      Boolean.TRUE.equals(call.getBoolean("visibleTitle", true))
    );
    if (Boolean.TRUE.equals(options.getVisibleTitle())) {
      options.setTitle(call.getString("title", "New Window"));
    } else {
      options.setTitle(call.getString("title", ""));
    }
    options.setToolbarColor(call.getString("toolbarColor", "#ffffff"));
    options.setArrow(Boolean.TRUE.equals(call.getBoolean("showArrow", false)));
    options.setIgnoreUntrustedSSLError(
      Boolean.TRUE.equals(call.getBoolean("ignoreUntrustedSSLError", false))
    );

    String proxyRequestsStr = call.getString("proxyRequests");
    if (proxyRequestsStr != null) {
      try {
        options.setProxyRequestsPattern(Pattern.compile(proxyRequestsStr));
      } catch (PatternSyntaxException e) {
        Log.e(
          "WebViewDialog",
          String.format("Pattern '%s' is not a valid pattern", proxyRequestsStr)
        );
      }
    }

    try {
      Options.ButtonNearDone buttonNearDone =
        Options.ButtonNearDone.generateFromPluginCall(
          call,
          getActivity().getAssets()
        );
      options.setButtonNearDone(buttonNearDone);
    } catch (IllegalArgumentException illegalArgumentException) {
      call.reject(
        String.format(
          "ButtonNearDone rejected: %s",
          illegalArgumentException.getMessage()
        )
      );
    } catch (RuntimeException e) {
      Log.e(
        "WebViewDialog",
        String.format("ButtonNearDone runtime error: %s", e)
      );
      call.reject(String.format("ButtonNearDone RuntimeException: %s", e));
    }

    options.setShareDisclaimer(call.getObject("shareDisclaimer", null));
    options.setPreShowScript(call.getString("preShowScript", null));
    options.setShareSubject(call.getString("shareSubject", null));
    options.setToolbarType(call.getString("toolbarType", ""));
    options.setActiveNativeNavigationForWebview(
      Boolean.TRUE.equals(
        call.getBoolean("activeNativeNavigationForWebview", false)
      )
    );
    options.setDisableGoBackOnNativeApplication(
      Boolean.TRUE.equals(
        call.getBoolean("disableGoBackOnNativeApplication", false)
      )
    );
    options.setPresentAfterPageLoad(
      Boolean.TRUE.equals(call.getBoolean("isPresentAfterPageLoad", false))
    );
    if (Boolean.TRUE.equals(call.getBoolean("closeModal", false))) {
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
    //    options.getToolbarItemTypes().add(ToolbarItemType.RELOAD); TODO: fix this
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

        @Override
        public void buttonNearDoneClicked() {
          notifyListeners("buttonNearDoneClick", new JSObject());
        }

        @Override
        public void javascriptCallback(String message) {
          // Handle the message received from JavaScript
          Log.d(
            "WebViewDialog",
            "Received message from JavaScript: " + message
          );
          // Process the message as needed
          try {
            // Parse the received message as a JSON object
            JSONObject jsonMessage = new JSONObject(message);

            // Create a new JSObject to send to the Capacitor plugin
            JSObject jsObject = new JSObject();

            // Iterate through the keys in the JSON object and add them to the JSObject
            Iterator<String> keys = jsonMessage.keys();
            while (keys.hasNext()) {
              String key = keys.next();
              jsObject.put(key, jsonMessage.get(key));
            }

            // Notify listeners with the parsed message
            notifyListeners("messageFromWebview", jsObject);
          } catch (JSONException e) {
            Log.e(
              "WebViewDialog",
              "Error parsing JSON message: " + e.getMessage()
            );

            // If JSON parsing fails, send the raw message as a string
            JSObject jsObject = new JSObject();
            jsObject.put("rawMessage", message);
            notifyListeners("messageFromWebview", jsObject);
          }
        }
      }
    );
    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            webViewDialog = new WebViewDialog(
              getContext(),
              android.R.style.Theme_NoTitleBar,
              options,
              InAppBrowserPlugin.this,
              getBridge().getWebView()
            );
            webViewDialog.activity = InAppBrowserPlugin.this.getActivity();
            webViewDialog.presentWebView();
          }
        }
      );
  }

  @PluginMethod
  public void postMessage(PluginCall call) {
    if (webViewDialog == null) {
      call.reject("WebView is not initialized");
      return;
    }
    JSObject eventData = call.getObject("detail");
    // Log event data
    if (eventData == null) {
      call.reject("No event data provided");
      return;
    }

    Log.d("InAppBrowserPlugin", "Event data: " + eventData.toString());
    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            webViewDialog.postMessageToJS(eventData);
            call.resolve();
          }
        }
      );
  }

  @PluginMethod
  public void executeScript(PluginCall call) {
    String script = call.getString("code");
    if (script == null || TextUtils.isEmpty(script)) {
      call.reject("No script to run");
    }

    if (webViewDialog != null) {
      this.getActivity()
        .runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              webViewDialog.executeScript(script);
            }
          }
        );
    }

    call.resolve();
  }

  @PluginMethod
  public void reload(PluginCall call) {
    if (webViewDialog != null) {
      this.getActivity()
        .runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              webViewDialog.reload();
            }
          }
        );
    }
    call.resolve();
  }

  @PluginMethod
  public void lsuakdchgbbaHandleProxiedRequest(PluginCall call) {
    if (webViewDialog != null) {
      Boolean ok = call.getBoolean("ok", false);
      String id = call.getString("id");
      if (id == null) {
        Log.e("InAppBrowserProxy", "CRITICAL ERROR, proxy id = null");
        return;
      }
      if (Boolean.FALSE.equals(ok)) {
        String result = call.getString("result", "");
        webViewDialog.handleProxyResultError(result, id);
      } else {
        JSONObject object = call.getObject("result");
        webViewDialog.handleProxyResultOk(object, id);
      }
    }
    call.resolve();
  }

  @PluginMethod
  public void close(PluginCall call) {
    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            if (webViewDialog != null) {
              notifyListeners(
                "closeEvent",
                new JSObject().put("url", webViewDialog.getUrl())
              );
              webViewDialog.dismiss();
              webViewDialog.destroy();
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
        }
      );
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
      currentSession = customTabsClient.newSession(
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
