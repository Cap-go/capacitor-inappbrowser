package ee.forgr.capacitor_inappbrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.annotation.RequiresApi;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.getcapacitor.JSObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

public class WebViewDialog extends Dialog {

  private class ProxiedRequest {

    private WebResourceResponse response;
    private Semaphore semaphore;

    public WebResourceResponse getResponse() {
      return response;
    }

    public ProxiedRequest() {
      this.semaphore = new Semaphore(0);
      this.response = null;
    }
  }

  private WebView _webView;
  private Toolbar _toolbar;
  private Options _options;
  private Context _context;
  public Activity activity;
  private boolean isInitialized = false;
  private WebView capacitorWebView;
  private HashMap<String, ProxiedRequest> proxiedRequestsHashmap;

  Semaphore preShowSemaphore = null;
  String preshowError = null;

  public PermissionRequest currentPermissionRequest;
  public static final int FILE_CHOOSER_REQUEST_CODE = 1000;
  public ValueCallback<Uri> mUploadMessage;
  public ValueCallback<Uri[]> mFilePathCallback;
  ExecutorService executorService = Executors.newFixedThreadPool(1);

  public interface PermissionHandler {
    void handleCameraPermissionRequest(PermissionRequest request);

    void handleMicrophonePermissionRequest(PermissionRequest request);
  }

  private PermissionHandler permissionHandler;

  public WebViewDialog(
    Context context,
    int theme,
    Options options,
    PermissionHandler permissionHandler,
    WebView capacitorWebView
  ) {
    super(context, theme);
    this._options = options;
    this._context = context;
    this.permissionHandler = permissionHandler;
    this.isInitialized = false;
    this.capacitorWebView = capacitorWebView;
    this.proxiedRequestsHashmap = new HashMap<>();
  }

  public class JavaScriptInterface {

    @JavascriptInterface
    public void postMessage(String message) {
      // Handle message from JavaScript
      _options.getCallbacks().javascriptCallback(message);
    }

    @JavascriptInterface
    public void close() {
      // close webview
      activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            dismiss();
            _options.getCallbacks().closeEvent(_webView.getUrl());
            _webView.destroy();
          }
        }
      );
    }
  }

  public class PreShowScriptInterface {

    @JavascriptInterface
    public void error(String error) {
      // Handle message from JavaScript
      if (preShowSemaphore != null) {
        preshowError = error;
        preShowSemaphore.release();
      }
    }

    @JavascriptInterface
    public void success() {
      // Handle message from JavaScript
      if (preShowSemaphore != null) {
        preShowSemaphore.release();
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  public void presentWebView() {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setCancelable(true);
    Objects.requireNonNull(getWindow()).setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    );
    setContentView(R.layout.activity_browser);
    getWindow()
      .setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT
      );

    this._webView = findViewById(R.id.browser_view);
    _webView.addJavascriptInterface(
      new JavaScriptInterface(),
      "AndroidInterface"
    );
    _webView.addJavascriptInterface(
      new PreShowScriptInterface(),
      "PreShowScriptInterface"
    );
    _webView.getSettings().setJavaScriptEnabled(true);
    _webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
    _webView.getSettings().setDatabaseEnabled(true);
    _webView.getSettings().setDomStorageEnabled(true);
    _webView.getSettings().setAllowFileAccess(true);
    _webView
      .getSettings()
      .setPluginState(android.webkit.WebSettings.PluginState.ON);
    _webView.getSettings().setLoadWithOverviewMode(true);
    _webView.getSettings().setUseWideViewPort(true);
    _webView.getSettings().setAllowFileAccessFromFileURLs(true);
    _webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
    _webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

    _webView.setWebViewClient(new WebViewClient());

    _webView.setWebChromeClient(
      new WebChromeClient() {
        // Enable file open dialog
        @Override
        public boolean onShowFileChooser(
          WebView webView,
          ValueCallback<Uri[]> filePathCallback,
          WebChromeClient.FileChooserParams fileChooserParams
        ) {
          openFileChooser(
            filePathCallback,
            fileChooserParams.getAcceptTypes()[0],
            fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE
          );
          return true;
        }

        // Grant permissions for cam
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
          Log.i(
            "INAPPBROWSER",
            "onPermissionRequest " + Arrays.toString(request.getResources())
          );
          final String[] requestedResources = request.getResources();
          for (String r : requestedResources) {
            Log.i("INAPPBROWSER", "requestedResources " + r);
            if (r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
              Log.i("INAPPBROWSER", "RESOURCE_VIDEO_CAPTURE req");
              // Store the permission request
              currentPermissionRequest = request;
              // Initiate the permission request through the plugin
              if (permissionHandler != null) {
                permissionHandler.handleCameraPermissionRequest(request);
              }
              return; // Return here to avoid denying the request
            } else if (r.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
              Log.i("INAPPBROWSER", "RESOURCE_AUDIO_CAPTURE req");
              // Store the permission request
              currentPermissionRequest = request;
              // Initiate the permission request through the plugin
              if (permissionHandler != null) {
                permissionHandler.handleMicrophonePermissionRequest(request);
              }
              return; // Return here to avoid denying the request
            }
          }
          // If no matching permission is found, deny the request
          request.deny();
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
          super.onPermissionRequestCanceled(request);
          Toast.makeText(
            WebViewDialog.this.activity,
            "Permission Denied",
            Toast.LENGTH_SHORT
          ).show();
          // Handle the denied permission
          if (currentPermissionRequest != null) {
            currentPermissionRequest.deny();
            currentPermissionRequest = null;
          }
        }
      }
    );

    Map<String, String> requestHeaders = new HashMap<>();
    if (_options.getHeaders() != null) {
      Iterator<String> keys = _options.getHeaders().keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (TextUtils.equals(key.toLowerCase(), "user-agent")) {
          _webView
            .getSettings()
            .setUserAgentString(_options.getHeaders().getString(key));
        } else {
          requestHeaders.put(key, _options.getHeaders().getString(key));
        }
      }
    }

    _webView.loadUrl(this._options.getUrl(), requestHeaders);
    _webView.requestFocus();
    _webView.requestFocusFromTouch();

    setupToolbar();
    setWebViewClient();

    if (!this._options.isPresentAfterPageLoad()) {
      show();
      _options.getPluginCall().resolve();
    }
  }

  public void postMessageToJS(Object detail) {
    if (_webView != null) {
      try {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("detail", detail);
        String jsonDetail = jsonObject.toString();
        String script =
          "window.dispatchEvent(new CustomEvent('messageFromNative', " +
          jsonDetail +
          "));";
        _webView.post(() -> _webView.evaluateJavascript(script, null));
      } catch (Exception e) {
        Log.e(
          "postMessageToJS",
          "Error sending message to JS: " + e.getMessage()
        );
      }
    }
  }

  private void injectJavaScriptInterface() {
    String script =
      "if (!window.mobileApp) { " +
      "    window.mobileApp = { " +
      "        postMessage: function(message) { " +
      "            if (window.AndroidInterface) { " +
      "                window.AndroidInterface.postMessage(JSON.stringify(message)); " +
      "            } " +
      "        }, " +
      "        close: function() { " +
      "            window.AndroidInterface.close(); " +
      "        } " +
      "    }; " +
      "}";
    _webView.evaluateJavascript(script, null);
  }

  private void injectPreShowScript() {
    //    String script =
    //        "import('https://unpkg.com/darkreader@4.9.89/darkreader.js').then(() => {DarkReader.enable({ brightness: 100, contrast: 90, sepia: 10 });window.PreLoadScriptInterface.finished()})";

    if (preShowSemaphore != null) {
      return;
    }

    String script =
      "async function preShowFunction() {\n" +
      _options.getPreShowScript() +
      '\n' +
      "};\n" +
      "preShowFunction().then(() => window.PreShowScriptInterface.success()).catch(err => { console.error('Preshow error', err); window.PreShowScriptInterface.error(JSON.stringify(err, Object.getOwnPropertyNames(err))) })";

    Log.i(
      "InjectPreShowScript",
      String.format("PreShowScript script:\n%s", script)
    );

    preShowSemaphore = new Semaphore(0);
    activity.runOnUiThread(
      new Runnable() {
        @Override
        public void run() {
          _webView.evaluateJavascript(script, null);
        }
      }
    );

    try {
      if (!preShowSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
        Log.e(
          "InjectPreShowScript",
          "PreShowScript running for over 10 seconds. The plugin will not wait any longer!"
        );
        return;
      }
      if (preshowError != null && !preshowError.isEmpty()) {
        Log.e(
          "InjectPreShowScript",
          "Error within the user-provided preShowFunction: " + preshowError
        );
      }
    } catch (InterruptedException e) {
      Log.e(
        "InjectPreShowScript",
        "Error when calling InjectPreShowScript: " + e.getMessage()
      );
    } finally {
      preShowSemaphore = null;
      preshowError = null;
    }
  }

  private void openFileChooser(
    ValueCallback<Uri[]> filePathCallback,
    String acceptType,
    boolean isMultiple
  ) {
    mFilePathCallback = filePathCallback;
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(acceptType); // Default to */*
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple);
    activity.startActivityForResult(
      Intent.createChooser(intent, "Select File"),
      FILE_CHOOSER_REQUEST_CODE
    );
  }

  public void reload() {
    _webView.reload();
  }

  public void destroy() {
    _webView.destroy();
  }

  public String getUrl() {
    return _webView.getUrl();
  }

  public void executeScript(String script) {
    _webView.evaluateJavascript(script, null);
  }

  public void setUrl(String url) {
    Map<String, String> requestHeaders = new HashMap<>();
    if (_options.getHeaders() != null) {
      Iterator<String> keys = _options.getHeaders().keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (TextUtils.equals(key.toLowerCase(), "user-agent")) {
          _webView
            .getSettings()
            .setUserAgentString(_options.getHeaders().getString(key));
        } else {
          requestHeaders.put(key, _options.getHeaders().getString(key));
        }
      }
    }
    _webView.loadUrl(url, requestHeaders);
  }

  private void setTitle(String newTitleText) {
    TextView textView = (TextView) _toolbar.findViewById(R.id.titleText);
    if (_options.getVisibleTitle()) {
      textView.setText(newTitleText);
    } else {
      textView.setText("");
    }
  }

  private void setupToolbar() {
    _toolbar = this.findViewById(R.id.tool_bar);
    int color = Color.parseColor("#ffffff");
    try {
      color = Color.parseColor(_options.getToolbarColor());
    } catch (IllegalArgumentException e) {
      // Do nothing
    }
    _toolbar.setBackgroundColor(color);
    _toolbar.findViewById(R.id.backButton).setBackgroundColor(color);
    _toolbar.findViewById(R.id.forwardButton).setBackgroundColor(color);
    _toolbar.findViewById(R.id.closeButton).setBackgroundColor(color);
    _toolbar.findViewById(R.id.reloadButton).setBackgroundColor(color);

    if (!TextUtils.isEmpty(_options.getTitle())) {
      this.setTitle(_options.getTitle());
    } else {
      try {
        URI uri = new URI(_options.getUrl());
        this.setTitle(uri.getHost());
      } catch (URISyntaxException e) {
        this.setTitle(_options.getTitle());
      }
    }

    View backButton = _toolbar.findViewById(R.id.backButton);
    backButton.setOnClickListener(
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          if (_webView.canGoBack()) {
            _webView.goBack();
          }
        }
      }
    );

    View forwardButton = _toolbar.findViewById(R.id.forwardButton);
    forwardButton.setOnClickListener(
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          if (_webView.canGoForward()) {
            _webView.goForward();
          }
        }
      }
    );

    View closeButton = _toolbar.findViewById(R.id.closeButton);
    closeButton.setOnClickListener(
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          // if closeModal true then display a native modal to check if the user is sure to close the browser
          if (_options.getCloseModal()) {
            new AlertDialog.Builder(_context)
              .setTitle(_options.getCloseModalTitle())
              .setMessage(_options.getCloseModalDescription())
              .setPositiveButton(
                _options.getCloseModalOk(),
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                    // Close button clicked, do something
                    dismiss();
                    _options.getCallbacks().closeEvent(_webView.getUrl());
                    _webView.destroy();
                  }
                }
              )
              .setNegativeButton(_options.getCloseModalCancel(), null)
              .show();
          } else {
            dismiss();
            _options.getCallbacks().closeEvent(_webView.getUrl());
            _webView.destroy();
          }
        }
      }
    );

    if (_options.showArrow()) {
      closeButton.setBackgroundResource(R.drawable.arrow_forward_enabled);
    }

    if (_options.getShowReloadButton()) {
      View reloadButton = _toolbar.findViewById(R.id.reloadButton);
      reloadButton.setVisibility(View.VISIBLE);
      reloadButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            _webView.reload();
          }
        }
      );
    }

    if (TextUtils.equals(_options.getToolbarType(), "activity")) {
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);
      ImageButton buttonNearDoneView = _toolbar.findViewById(
        R.id.buttonNearDone
      );
      buttonNearDoneView.setVisibility(View.GONE);
      //TODO: Add share button functionality
    } else if (TextUtils.equals(_options.getToolbarType(), "navigation")) {
      ImageButton buttonNearDoneView = _toolbar.findViewById(
        R.id.buttonNearDone
      );
      buttonNearDoneView.setVisibility(View.GONE);
      //TODO: Remove share button when implemented
    } else if (TextUtils.equals(_options.getToolbarType(), "blank")) {
      _toolbar.setVisibility(View.GONE);
    } else {
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);

      Options.ButtonNearDone buttonNearDone = _options.getButtonNearDone();
      if (buttonNearDone != null) {
        AssetManager assetManager = _context.getAssets();

        // Open the SVG file from assets
        InputStream inputStream = null;
        try {
          ImageButton buttonNearDoneView = _toolbar.findViewById(
            R.id.buttonNearDone
          );
          buttonNearDoneView.setVisibility(View.VISIBLE);

          inputStream = assetManager.open(buttonNearDone.getIcon());

          SVG svg = SVG.getFromInputStream(inputStream);
          Picture picture = svg.renderToPicture(
            buttonNearDone.getWidth(),
            buttonNearDone.getHeight()
          );
          PictureDrawable pictureDrawable = new PictureDrawable(picture);

          buttonNearDoneView.setImageDrawable(pictureDrawable);
          buttonNearDoneView.setOnClickListener(view ->
            _options.getCallbacks().buttonNearDoneClicked()
          );
        } catch (IOException | SVGParseException e) {
          throw new RuntimeException(e);
        } finally {
          if (inputStream != null) {
            try {
              inputStream.close();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      } else {
        ImageButton buttonNearDoneView = _toolbar.findViewById(
          R.id.buttonNearDone
        );
        buttonNearDoneView.setVisibility(View.GONE);
      }
    }
  }

  public void handleProxyResultError(String result, String id) {
    Log.i(
      "InAppBrowserProxy",
      String.format(
        "handleProxyResultError: %s, ok: %s id: %s",
        result,
        false,
        id
      )
    );
    ProxiedRequest proxiedRequest = proxiedRequestsHashmap.get(id);
    if (proxiedRequest == null) {
      Log.e("InAppBrowserProxy", "proxiedRequest is null");
      return;
    }
    proxiedRequestsHashmap.remove(id);
    proxiedRequest.semaphore.release();
  }

  public void handleProxyResultOk(JSONObject result, String id) {
    Log.i(
      "InAppBrowserProxy",
      String.format("handleProxyResultOk: %s, ok: %s, id: %s", result, true, id)
    );
    ProxiedRequest proxiedRequest = proxiedRequestsHashmap.get(id);
    if (proxiedRequest == null) {
      Log.e("InAppBrowserProxy", "proxiedRequest is null");
      return;
    }
    proxiedRequestsHashmap.remove(id);

    if (result == null) {
      proxiedRequest.semaphore.release();
      return;
    }

    Map<String, String> responseHeaders = new HashMap<>();
    String body;
    int code;

    try {
      body = result.getString("body");
      code = result.getInt("code");
      JSONObject headers = result.getJSONObject("headers");
      for (Iterator<String> it = headers.keys(); it.hasNext();) {
        String headerName = it.next();
        String header = headers.getString(headerName);
        responseHeaders.put(headerName, header);
      }
    } catch (JSONException e) {
      Log.e("InAppBrowserProxy", "Cannot parse OK result", e);
      return;
    }

    String contentType = responseHeaders.get("Content-Type");
    if (contentType == null) {
      contentType = responseHeaders.get("content-type");
    }
    if (contentType == null) {
      Log.e("InAppBrowserProxy", "'Content-Type' header is required");
      return;
    }

    if (!((100 <= code && code <= 299) || (400 <= code && code <= 599))) {
      Log.e(
        "InAppBrowserProxy",
        String.format("Status code %s outside of the allowed range", code)
      );
      return;
    }

    WebResourceResponse webResourceResponse = new WebResourceResponse(
      contentType,
      "utf-8",
      new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
    );

    webResourceResponse.setStatusCodeAndReasonPhrase(
      code,
      getReasonPhrase(code)
    );
    proxiedRequest.response = webResourceResponse;
    proxiedRequest.semaphore.release();
  }

  private void setWebViewClient() {
    _webView.setWebViewClient(
      new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(
          WebView view,
          WebResourceRequest request
        ) {
          //          HashMap<String, String> map = new HashMap<>();
          //          map.put("x-requested-with", null);
          //          view.loadUrl(request.getUrl().toString(), map);
          Context context = view.getContext();
          String url = request.getUrl().toString();

          if (!url.startsWith("https://") && !url.startsWith("http://")) {
            try {
              Intent intent;
              if (url.startsWith("intent://")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
              } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
              }

              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              context.startActivity(intent);
              return true;
            } catch (ActivityNotFoundException e) {
              // Do nothing
            } catch (URISyntaxException e) {
              // Do nothing
            }
          }
          return false;
        }

        private String randomRequestId() {
          return UUID.randomUUID().toString();
        }

        private String toBase64(String raw) {
          String s = Base64.encodeToString(raw.getBytes(), Base64.NO_WRAP);
          if (s.endsWith("=")) {
            s = s.substring(0, s.length() - 2);
          }
          return s;
        }

        //
        //        void handleRedirect(String currentUrl, Response response) {
        //          String loc = response.header("Location");
        //          _webView.evaluateJavascript("");
        //        }
        //
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(
          WebView view,
          WebResourceRequest request
        ) {
          Pattern pattern = _options.getProxyRequestsPattern();
          if (pattern == null) {
            return null;
          }
          Matcher matcher = pattern.matcher(request.getUrl().toString());
          if (!matcher.find()) {
            return null;
          }

          // Requests matches the regex
          if (Objects.equals(request.getMethod(), "POST")) {
            // Log.e("HTTP", String.format("returned null (ok) %s", request.getUrl().toString()));
            return null;
          }

          Log.i(
            "InAppBrowserProxy",
            String.format("Proxying request: %s", request.getUrl().toString())
          );

          // We need to call a JS function
          String requestId = randomRequestId();
          ProxiedRequest proxiedRequest = new ProxiedRequest();
          proxiedRequestsHashmap.put(requestId, proxiedRequest);

          // lsuakdchgbbaHandleProxiedRequest
          activity.runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                StringBuilder headers = new StringBuilder();
                Map<String, String> requestHeaders =
                  request.getRequestHeaders();
                for (Map.Entry<
                  String,
                  String
                > header : requestHeaders.entrySet()) {
                  headers.append(
                    String.format(
                      "h[atob('%s')]=atob('%s');",
                      toBase64(header.getKey()),
                      toBase64(header.getValue())
                    )
                  );
                }
                String s = String.format(
                  "try {function getHeaders() {const h = {}; %s return h}; window.InAppBrowserProxyRequest(new Request(atob('%s'), {headers: getHeaders(), method: '%s'})).then(async (res) => Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({ok: true, result: (!!res ? {headers: Object.fromEntries(res.headers.entries()), code: res.status, body: (await res.text())} : null), id: '%s'})).catch((e) => Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({ok: false, result: e.toString(), id: '%s'}))} catch (e) {Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({ok: false, result: e.toString(), id: '%s'})}",
                  headers,
                  toBase64(request.getUrl().toString()),
                  request.getMethod(),
                  requestId,
                  requestId,
                  requestId
                );
                // Log.i("HTTP", s);
                capacitorWebView.evaluateJavascript(s, null);
              }
            }
          );

          // 10 seconds wait max
          try {
            if (proxiedRequest.semaphore.tryAcquire(1, 10, TimeUnit.SECONDS)) {
              return proxiedRequest.response;
            } else {
              Log.e("InAppBrowserProxy", "Semaphore timed out");
              proxiedRequestsHashmap.remove(requestId); // prevent mem leak
            }
          } catch (InterruptedException e) {
            Log.e("InAppBrowserProxy", "Semaphore wait error", e);
          }
          return null;
        }

        @Override
        public void onReceivedHttpAuthRequest(
          WebView view,
          HttpAuthHandler handler,
          String host,
          String realm
        ) {
          final String sourceUrl = _options.getUrl();
          final String url = view.getUrl();
          final JSObject credentials = _options.getCredentials();

          if (
            credentials != null &&
            credentials.getString("username") != null &&
            credentials.getString("password") != null &&
            sourceUrl != null &&
            url != null
          ) {
            String sourceProtocol = "";
            String sourceHost = "";
            int sourcePort = -1;
            try {
              URI uri = new URI(sourceUrl);
              sourceProtocol = uri.getScheme();
              sourceHost = uri.getHost();
              sourcePort = uri.getPort();
              if (
                sourcePort == -1 && Objects.equals(sourceProtocol, "https")
              ) sourcePort = 443;
              else if (
                sourcePort == -1 && Objects.equals(sourceProtocol, "http")
              ) sourcePort = 80;
            } catch (URISyntaxException e) {
              e.printStackTrace();
            }

            String protocol = "";
            int port = -1;
            try {
              URI uri = new URI(url);
              protocol = uri.getScheme();
              port = uri.getPort();
              if (port == -1 && Objects.equals(protocol, "https")) port = 443;
              else if (port == -1 && Objects.equals(protocol, "http")) port =
                80;
            } catch (URISyntaxException e) {
              e.printStackTrace();
            }

            if (
              Objects.equals(sourceHost, host) &&
              Objects.equals(sourceProtocol, protocol) &&
              sourcePort == port
            ) {
              final String username = Objects.requireNonNull(
                credentials.getString("username")
              );
              final String password = Objects.requireNonNull(
                credentials.getString("password")
              );
              handler.proceed(username, password);
              return;
            }
          }

          super.onReceivedHttpAuthRequest(view, handler, host, realm);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
          super.onLoadResource(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
          super.onPageStarted(view, url, favicon);
          try {
            URI uri = new URI(url);
            if (TextUtils.isEmpty(_options.getTitle())) {
              setTitle(uri.getHost());
            }
          } catch (URISyntaxException e) {
            // Do nothing
          }
        }

        public void doUpdateVisitedHistory(
          WebView view,
          String url,
          boolean isReload
        ) {
          if (!isReload) {
            _options.getCallbacks().urlChangeEvent(url);
          }
          super.doUpdateVisitedHistory(view, url, isReload);
          injectJavaScriptInterface();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
          super.onPageFinished(view, url);
          if (!isInitialized) {
            isInitialized = true;
            _webView.clearHistory();
            if (_options.isPresentAfterPageLoad()) {
              boolean usePreShowScript =
                _options.getPreShowScript() != null &&
                !_options.getPreShowScript().isEmpty();
              if (!usePreShowScript) {
                show();
                _options.getPluginCall().resolve();
              } else {
                executorService.execute(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (
                        _options.getPreShowScript() != null &&
                        !_options.getPreShowScript().isEmpty()
                      ) {
                        injectPreShowScript();
                      }

                      activity.runOnUiThread(
                        new Runnable() {
                          @Override
                          public void run() {
                            show();
                            _options.getPluginCall().resolve();
                          }
                        }
                      );
                    }
                  }
                );
              }
            }
          } else if (
            _options.getPreShowScript() != null &&
            !_options.getPreShowScript().isEmpty()
          ) {
            executorService.execute(
              new Runnable() {
                @Override
                public void run() {
                  injectPreShowScript();
                }
              }
            );
          }

          ImageButton backButton = _toolbar.findViewById(R.id.backButton);
          if (_webView.canGoBack()) {
            backButton.setImageResource(R.drawable.arrow_back_enabled);
            backButton.setEnabled(true);
          } else {
            backButton.setImageResource(R.drawable.arrow_back_disabled);
            backButton.setEnabled(false);
          }

          ImageButton forwardButton = _toolbar.findViewById(R.id.forwardButton);
          if (_webView.canGoForward()) {
            forwardButton.setImageResource(R.drawable.arrow_forward_enabled);
            forwardButton.setEnabled(true);
          } else {
            forwardButton.setImageResource(R.drawable.arrow_forward_disabled);
            forwardButton.setEnabled(false);
          }

          _options.getCallbacks().pageLoaded();
          injectJavaScriptInterface();
        }

        @Override
        public void onReceivedError(
          WebView view,
          WebResourceRequest request,
          WebResourceError error
        ) {
          super.onReceivedError(view, request, error);
          _options.getCallbacks().pageLoadError();
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(
          WebView view,
          SslErrorHandler handler,
          SslError error
        ) {
          boolean ignoreSSLUntrustedError = _options.ignoreUntrustedSSLError();
          if (
            ignoreSSLUntrustedError &&
            error.getPrimaryError() == SslError.SSL_UNTRUSTED
          ) handler.proceed();
          else {
            super.onReceivedSslError(view, handler, error);
          }
        }
      }
    );
  }

  @Override
  public void onBackPressed() {
    if (
      _webView.canGoBack() &&
      (TextUtils.equals(_options.getToolbarType(), "navigation") ||
        _options.getActiveNativeNavigationForWebview())
    ) {
      _webView.goBack();
    } else if (!_options.getDisableGoBackOnNativeApplication()) {
      super.onBackPressed();
    }
  }

  public static String getReasonPhrase(int statusCode) {
    switch (statusCode) {
      case (200):
        return "OK";
      case (201):
        return "Created";
      case (202):
        return "Accepted";
      case (203):
        return "Non Authoritative Information";
      case (204):
        return "No Content";
      case (205):
        return "Reset Content";
      case (206):
        return "Partial Content";
      case (207):
        return "Partial Update OK";
      case (300):
        return "Mutliple Choices";
      case (301):
        return "Moved Permanently";
      case (302):
        return "Moved Temporarily";
      case (303):
        return "See Other";
      case (304):
        return "Not Modified";
      case (305):
        return "Use Proxy";
      case (307):
        return "Temporary Redirect";
      case (400):
        return "Bad Request";
      case (401):
        return "Unauthorized";
      case (402):
        return "Payment Required";
      case (403):
        return "Forbidden";
      case (404):
        return "Not Found";
      case (405):
        return "Method Not Allowed";
      case (406):
        return "Not Acceptable";
      case (407):
        return "Proxy Authentication Required";
      case (408):
        return "Request Timeout";
      case (409):
        return "Conflict";
      case (410):
        return "Gone";
      case (411):
        return "Length Required";
      case (412):
        return "Precondition Failed";
      case (413):
        return "Request Entity Too Large";
      case (414):
        return "Request-URI Too Long";
      case (415):
        return "Unsupported Media Type";
      case (416):
        return "Requested Range Not Satisfiable";
      case (417):
        return "Expectation Failed";
      case (418):
        return "Reauthentication Required";
      case (419):
        return "Proxy Reauthentication Required";
      case (422):
        return "Unprocessable Entity";
      case (423):
        return "Locked";
      case (424):
        return "Failed Dependency";
      case (500):
        return "Server Error";
      case (501):
        return "Not Implemented";
      case (502):
        return "Bad Gateway";
      case (503):
        return "Service Unavailable";
      case (504):
        return "Gateway Timeout";
      case (505):
        return "HTTP Version Not Supported";
      case (507):
        return "Insufficient Storage";
      default:
        return "";
    }
  }
}
