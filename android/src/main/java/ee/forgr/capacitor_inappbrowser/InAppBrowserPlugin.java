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

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.lang.reflect.Field;
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

  private ActivityResultLauncher<Intent> fileChooserLauncher;
  private ActivityResultLauncher<Intent> cameraLauncher;

  @Override
  public void load() {
    super.load();
    // Register activity result launchers early in the lifecycle
    if (getActivity() instanceof ComponentActivity) {
      ComponentActivity activity = (ComponentActivity) getActivity();

      fileChooserLauncher = activity.registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
          if (webViewDialog != null) {
            webViewDialog.handleFileChooserResult(result);
          }
        }
      );

      cameraLauncher = activity.registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
          if (webViewDialog != null) {
            webViewDialog.handleCameraResult(result);
          }
        }
      );
    }
  }

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
      if (call != null) {
        call.reject("Microphone permission is required");
      }
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

      // Reject only if there's a call - could be null for WebViewDialog flow
      if (call != null) {
        call.reject("Camera permission is required");
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
      ComponentName name,
      CustomTabsClient client
    ) {
      customTabsClient = client;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      customTabsClient = null;
    }
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
    if (url == null) {
      call.reject("URL is required");
      return;
    }

    // get the deeplink prevention, if provided
    Boolean preventDeeplink = call.getBoolean("preventDeeplink", false);
    Boolean isPresentAfterPageLoad = call.getBoolean(
      "isPresentAfterPageLoad",
      false
    );

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

    if (preventDeeplink != false) {
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

    if (isPresentAfterPageLoad) {
      tabsIntent.intent.putExtra("isPresentAfterPageLoad", true);
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
    options.setBackgroundColor(call.getString("backgroundColor", "black"));
    options.setToolbarTextColor(call.getString("toolbarTextColor"));
    options.setArrow(Boolean.TRUE.equals(call.getBoolean("showArrow", false)));
    options.setIgnoreUntrustedSSLError(
      Boolean.TRUE.equals(call.getBoolean("ignoreUntrustedSSLError", false))
    );

    // Set text zoom if specified in options (default is 100)
    Integer textZoom = call.getInt("textZoom");
    if (textZoom != null) {
      options.setTextZoom(textZoom);
    }

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
      // Try to set buttonNearDone if present, with better error handling
      JSObject buttonNearDoneObj = call.getObject("buttonNearDone");
      if (buttonNearDoneObj != null) {
        try {
          // Provide better debugging for buttonNearDone
          JSObject androidObj = buttonNearDoneObj.getJSObject("android");
          if (androidObj != null) {
            String iconType = androidObj.getString("iconType", "asset");
            String icon = androidObj.getString("icon", "");
            Log.d(
              "InAppBrowser",
              "ButtonNearDone config - iconType: " +
              iconType +
              ", icon: " +
              icon
            );

            // For vector type, verify if resource exists
            if ("vector".equals(iconType)) {
              int resourceId = getContext()
                .getResources()
                .getIdentifier(icon, "drawable", getContext().getPackageName());
              if (resourceId == 0) {
                Log.e("InAppBrowser", "Vector resource not found: " + icon);
                // List available drawable resources to help debugging
                try {
                  final StringBuilder availableResources = getStringBuilder();
                  Log.d("InAppBrowser", availableResources.toString());
                } catch (Exception e) {
                  Log.e(
                    "InAppBrowser",
                    "Error listing resources: " + e.getMessage()
                  );
                }
              } else {
                Log.d(
                  "InAppBrowser",
                  "Vector resource found with ID: " + resourceId
                );
              }
            }
          }

          // Try to create the ButtonNearDone object
          Options.ButtonNearDone buttonNearDone =
            Options.ButtonNearDone.generateFromPluginCall(
              call,
              getContext().getAssets()
            );
          options.setButtonNearDone(buttonNearDone);
        } catch (Exception e) {
          Log.e(
            "InAppBrowser",
            "Error setting buttonNearDone: " + e.getMessage(),
            e
          );
        }
      }
    } catch (Exception e) {
      Log.e(
        "InAppBrowser",
        "Error processing buttonNearDone: " + e.getMessage(),
        e
      );
    }

    options.setShareDisclaimer(call.getObject("shareDisclaimer", null));
    options.setPreShowScript(call.getString("preShowScript", null));
    options.setShareSubject(call.getString("shareSubject", null));
    options.setToolbarType(call.getString("toolbarType", ""));
    options.setPreventDeeplink(
      Boolean.TRUE.equals(call.getBoolean("preventDeeplink", false))
    );

    // Validate preShowScript requires isPresentAfterPageLoad
    if (
      call.getData().has("preShowScript") &&
      !Boolean.TRUE.equals(call.getBoolean("isPresentAfterPageLoad", false))
    ) {
      call.reject("preShowScript requires isPresentAfterPageLoad to be true");
      return;
    }

    // Validate closeModal options
    if (Boolean.TRUE.equals(call.getBoolean("closeModal", false))) {
      options.setCloseModal(true);
      options.setCloseModalTitle(call.getString("closeModalTitle", "Close"));
      options.setCloseModalDescription(
        call.getString("closeModalDescription", "Are you sure ?")
      );
      options.setCloseModalOk(call.getString("closeModalOk", "Ok"));
      options.setCloseModalCancel(call.getString("closeModalCancel", "Cancel"));
    } else {
      // Reject if closeModal is false but closeModal options are provided
      if (
        call.getData().has("closeModalTitle") ||
        call.getData().has("closeModalDescription") ||
        call.getData().has("closeModalOk") ||
        call.getData().has("closeModalCancel")
      ) {
        call.reject("closeModal options require closeModal to be true");
        return;
      }
      options.setCloseModal(false);
    }

    // Validate shareDisclaimer requires shareSubject
    if (
      call.getData().has("shareDisclaimer") &&
      !call.getData().has("shareSubject")
    ) {
      call.reject("shareDisclaimer requires shareSubject to be provided");
      return;
    }

    // Validate buttonNearDone compatibility with toolbar type
    if (call.getData().has("buttonNearDone")) {
      String toolbarType = options.getToolbarType();
      if (
        TextUtils.equals(toolbarType, "activity") ||
        TextUtils.equals(toolbarType, "navigation") ||
        TextUtils.equals(toolbarType, "blank")
      ) {
        call.reject(
          "buttonNearDone is not compatible with toolbarType: " + toolbarType
        );
        return;
      }
    }

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
    options.setPluginCall(call);

    // Set Material Design picker option
    options.setMaterialPicker(
      Boolean.TRUE.equals(call.getBoolean("materialPicker", false))
    );

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
        public void confirmBtnClicked() {
          notifyListeners("confirmBtnClicked", new JSObject());
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
              android.R.style.Theme_DeviceDefault_Light_NoActionBar,
              options,
              InAppBrowserPlugin.this,
              getBridge().getWebView(),
              fileChooserLauncher,
              cameraLauncher
            );
            webViewDialog.activity = InAppBrowserPlugin.this.getActivity();
            webViewDialog.presentWebView();
          }
        }
      );
  }

  @NonNull
  private static StringBuilder getStringBuilder() {
    Field[] drawables = R.drawable.class.getFields();
    StringBuilder availableResources = new StringBuilder(
      "Available resources: "
    );
    for (int i = 0; i < Math.min(10, drawables.length); i++) {
      availableResources.append(drawables[i].getName()).append(", ");
    }
    if (drawables.length > 10) {
      availableResources
        .append("... (")
        .append(drawables.length - 10)
        .append(" more)");
    }
    return availableResources;
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
            if (navigationEvent == NAVIGATION_FINISHED) {
              notifyListeners("browserPageLoaded", new JSObject());
            }
          }
        }
      );
    }
    return currentSession;
  }

  @Override
  protected void handleOnDestroy() {
    if (webViewDialog != null) {
      try {
        webViewDialog.dismiss();
      } catch (Exception e) {
        // Ignore, dialog may already be dismissed
      }
      webViewDialog = null;
    }
    currentPermissionRequest = null;
    customTabsClient = null;
    currentSession = null;
    super.handleOnDestroy();
  }
}
