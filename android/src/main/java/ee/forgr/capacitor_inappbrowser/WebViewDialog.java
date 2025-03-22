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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.core.view.WindowInsetsControllerCompat;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.getcapacitor.JSObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

public class WebViewDialog extends Dialog {

  private static class ProxiedRequest {

    private WebResourceResponse response;
    private final Semaphore semaphore;

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
  private Options _options = null;
  private final Context _context;
  public Activity activity;
  private boolean isInitialized = false;
  private final WebView capacitorWebView;
  private final Map<String, ProxiedRequest> proxiedRequestsHashmap =
    new HashMap<>();
  private final ExecutorService executorService =
    Executors.newCachedThreadPool();
  private int iconColor = Color.BLACK; // Default icon color

  Semaphore preShowSemaphore = null;
  String preShowError = null;

  public PermissionRequest currentPermissionRequest;
  public static final int FILE_CHOOSER_REQUEST_CODE = 1000;
  public ValueCallback<Uri> mUploadMessage;
  public ValueCallback<Uri[]> mFilePathCallback;

  public interface PermissionHandler {
    void handleCameraPermissionRequest(PermissionRequest request);

    void handleMicrophonePermissionRequest(PermissionRequest request);
  }

  private final PermissionHandler permissionHandler;

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
  }

  // Add this class to provide safer JavaScript interface
  private class JavaScriptInterface {

    @JavascriptInterface
    public void postMessage(String message) {
      try {
        // Handle message from JavaScript safely
        if (message == null || message.isEmpty()) {
          Log.e("InAppBrowser", "Received empty message from WebView");
          return;
        }
        _options.getCallbacks().javascriptCallback(message);
      } catch (Exception e) {
        Log.e("InAppBrowser", "Error in postMessage: " + e.getMessage());
      }
    }

    @JavascriptInterface
    public void close() {
      try {
        // close webview safely
        if (activity != null) {
          activity.runOnUiThread(() -> {
            try {
              String currentUrl = _webView != null ? _webView.getUrl() : "";
              dismiss();
              if (_options != null && _options.getCallbacks() != null) {
                _options.getCallbacks().closeEvent(currentUrl);
              }
            } catch (Exception e) {
              Log.e("InAppBrowser", "Error closing WebView: " + e.getMessage());
            }
          });
        }
      } catch (Exception e) {
        Log.e("InAppBrowser", "Error in close: " + e.getMessage());
      }
    }
  }

  public class PreShowScriptInterface {

    @JavascriptInterface
    public void error(String error) {
      try {
        // Handle message from JavaScript
        if (preShowSemaphore != null) {
          preShowError = error;
          preShowSemaphore.release();
        }
      } catch (Exception e) {
        Log.e("InAppBrowser", "Error in error callback: " + e.getMessage());
      }
    }

    @JavascriptInterface
    public void success() {
      try {
        // Handle message from JavaScript
        if (preShowSemaphore != null) {
          preShowSemaphore.release();
        }
      } catch (Exception e) {
        Log.e("InAppBrowser", "Error in success callback: " + e.getMessage());
      }
    }
  }

  @SuppressLint({ "SetJavaScriptEnabled", "AddJavascriptInterface" })
  public void presentWebView() {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setCancelable(true);
    Objects.requireNonNull(getWindow()).setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    );
    setContentView(R.layout.activity_browser);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    // Make status bar transparent
    if (getWindow() != null) {
      getWindow().setStatusBarColor(Color.TRANSPARENT);
      
      // Add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
      
      // On Android 30+ clear FLAG_TRANSLUCENT_STATUS flag
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    }

    WindowInsetsControllerCompat insetsController =
      new WindowInsetsControllerCompat(getWindow(), getWindow() != null ? getWindow().getDecorView() : null);
    
    if (getWindow() != null) {
      getWindow()
        .getDecorView()
        .post(() -> {
          // Get status bar height
          int statusBarHeight = 0;
          int resourceId = getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
          if (resourceId > 0) {
            statusBarHeight = getContext().getResources().getDimensionPixelSize(resourceId);
          }
          
          // Find the status bar color view
          View statusBarColorView = findViewById(R.id.status_bar_color_view);
          
          // Set the height of the status bar color view
          if (statusBarColorView != null) {
            statusBarColorView.getLayoutParams().height = statusBarHeight;
            statusBarColorView.requestLayout();
            
            // Set color based on toolbar color or dark mode
            if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
              try {
                // Use explicitly provided toolbar color for status bar
                int toolbarColor = Color.parseColor(_options.getToolbarColor());
                statusBarColorView.setBackgroundColor(toolbarColor);
                
                // Set status bar text to white or black based on background
                boolean isDarkBackground = isDarkColor(toolbarColor);
                insetsController.setAppearanceLightStatusBars(!isDarkBackground);
              } catch (IllegalArgumentException e) {
                // Fallback to default black if color parsing fails
                statusBarColorView.setBackgroundColor(Color.BLACK);
                insetsController.setAppearanceLightStatusBars(false);
              }
            } else {
              // Follow system dark mode if no toolbar color provided
              boolean isDarkTheme = isDarkThemeEnabled();
              int statusBarColor = isDarkTheme ? Color.BLACK : Color.WHITE;
              statusBarColorView.setBackgroundColor(statusBarColor);
              insetsController.setAppearanceLightStatusBars(!isDarkTheme);
            }
          }
        });
    }

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
          FileChooserParams fileChooserParams
        ) {
          // Get the accept type safely
          String acceptType = "*/*"; // Default to all file types
          if (fileChooserParams.getAcceptTypes() != null && 
              fileChooserParams.getAcceptTypes().length > 0 &&
              !TextUtils.isEmpty(fileChooserParams.getAcceptTypes()[0])) {
            acceptType = fileChooserParams.getAcceptTypes()[0];
          }
          
          // Check if the file chooser is already open
          if (mFilePathCallback != null) {
            mFilePathCallback.onReceiveValue(null);
            mFilePathCallback = null;
          }
          
          openFileChooser(
            filePathCallback,
            acceptType,
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
      if (preShowError != null && !preShowError.isEmpty()) {
        Log.e(
          "InjectPreShowScript",
          "Error within the user-provided preShowFunction: " + preShowError
        );
      }
    } catch (InterruptedException e) {
      Log.e(
        "InjectPreShowScript",
        "Error when calling InjectPreShowScript: " + e.getMessage()
      );
    } finally {
      preShowSemaphore = null;
      preShowError = null;
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
    
    // Fix MIME type handling
    if (acceptType == null || acceptType.isEmpty() || acceptType.equals("undefined")) {
      acceptType = "*/*";
    } else {
      // Handle common web input accept types
      if (acceptType.equals("image/*")) {
        // Keep as is - image/*
      } else if (acceptType.contains("image/")) {
        // Specific image type requested but keep it general for better compatibility
        acceptType = "image/*";
      } else if (acceptType.equals("audio/*") || acceptType.contains("audio/")) {
        acceptType = "audio/*";
      } else if (acceptType.equals("video/*") || acceptType.contains("video/")) {
        acceptType = "video/*";
      } else if (acceptType.startsWith(".") || acceptType.contains(",")) {
        // Handle file extensions like ".pdf, .docx" by using a general mime type
        if (acceptType.contains(".pdf")) {
          acceptType = "application/pdf";
        } else if (acceptType.contains(".doc") || acceptType.contains(".docx")) {
          acceptType = "application/msword";
        } else if (acceptType.contains(".xls") || acceptType.contains(".xlsx")) {
          acceptType = "application/vnd.ms-excel";
        } else if (acceptType.contains(".txt") || acceptType.contains(".text")) {
          acceptType = "text/plain";
        } else {
          // Default for extension lists
          acceptType = "*/*";
        }
      }
    }
    
    Log.d("InAppBrowser", "File picker using MIME type: " + acceptType);
    intent.setType(acceptType);
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple);
    
    try {
      activity.startActivityForResult(
        Intent.createChooser(intent, "Select File"),
        FILE_CHOOSER_REQUEST_CODE
      );
    } catch (ActivityNotFoundException e) {
      // If no app can handle the specific MIME type, try with a more generic one
      Log.e("InAppBrowser", "No app available for type: " + acceptType + ", trying with */*");
      intent.setType("*/*");
      try {
        activity.startActivityForResult(
          Intent.createChooser(intent, "Select File"),
          FILE_CHOOSER_REQUEST_CODE
        );
      } catch (ActivityNotFoundException ex) {
        // If still failing, report error
        Log.e("InAppBrowser", "No app can handle file picker", ex);
        if (mFilePathCallback != null) {
          mFilePathCallback.onReceiveValue(null);
          mFilePathCallback = null;
        }
      }
    }
  }

  public void reload() {
    if (_webView != null) {
      // First stop any ongoing loading
      _webView.stopLoading();
      
      // Check if there's a URL to reload
      if (_webView.getUrl() != null) {
        // Reload the current page
        _webView.reload();
        Log.d("InAppBrowser", "Reloading page: " + _webView.getUrl());
      } else if (_options != null && _options.getUrl() != null) {
        // If webView URL is null but we have an initial URL, load that
        setUrl(_options.getUrl());
        Log.d("InAppBrowser", "Loading initial URL: " + _options.getUrl());
      }
    }
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
    _toolbar = findViewById(R.id.tool_bar);
    
    // Apply toolbar color early, for ALL toolbar types, before any view configuration
    if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
      try {
        int toolbarColor = Color.parseColor(_options.getToolbarColor());
        _toolbar.setBackgroundColor(toolbarColor);
        
        // Get toolbar title and ensure it gets the right color
        TextView titleText = _toolbar.findViewById(R.id.titleText);
        
        // Determine icon and text color
        int iconColor;
        if (_options.getToolbarTextColor() != null && !_options.getToolbarTextColor().isEmpty()) {
          try {
            iconColor = Color.parseColor(_options.getToolbarTextColor());
          } catch (IllegalArgumentException e) {
            // Fallback to automatic detection if parsing fails
            boolean isDarkBackground = isDarkColor(toolbarColor);
            iconColor = isDarkBackground ? Color.WHITE : Color.BLACK;
          }
        } else {
          // No explicit toolbarTextColor, use automatic detection based on background
          boolean isDarkBackground = isDarkColor(toolbarColor);
          iconColor = isDarkBackground ? Color.WHITE : Color.BLACK;
        }
        
        // Store for later use with navigation buttons
        this.iconColor = iconColor;
        
        // Set title text color directly
        titleText.setTextColor(iconColor);
        
        // Apply colors to all buttons
        applyColorToAllButtons(toolbarColor, iconColor);
        
        // Also ensure status bar gets the color
        if (getWindow() != null) {
          // Set status bar color
          getWindow().setStatusBarColor(toolbarColor);
          
          // Determine proper status bar text color (light or dark icons)
          boolean isDarkBackground = isDarkColor(toolbarColor);
          WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(
            getWindow(), 
            getWindow().getDecorView()
          );
          insetsController.setAppearanceLightStatusBars(!isDarkBackground);
        }
      } catch (IllegalArgumentException e) {
        Log.e("InAppBrowser", "Invalid toolbar color: " + _options.getToolbarColor());
      }
    }
    
    ImageButton closeButtonView = _toolbar.findViewById(R.id.closeButton);
    closeButtonView.setOnClickListener(
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
                new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                    // Close button clicked, do something
                    String currentUrl = _webView != null
                      ? _webView.getUrl()
                      : "";
                    dismiss();
                    if (_options != null && _options.getCallbacks() != null) {
                      _options.getCallbacks().closeEvent(currentUrl);
                    }
                  }
                }
              )
              .setNegativeButton(_options.getCloseModalCancel(), null)
              .show();
          } else {
            String currentUrl = _webView != null ? _webView.getUrl() : "";
            dismiss();
            if (_options != null && _options.getCallbacks() != null) {
              _options.getCallbacks().closeEvent(currentUrl);
            }
          }
        }
      }
    );

    if (_options.showArrow()) {
      closeButtonView.setImageResource(R.drawable.arrow_back_enabled);
    }

    // Handle reload button visibility
    if (_options.getShowReloadButton() && !TextUtils.equals(_options.getToolbarType(), "activity")) {
      View reloadButtonView = _toolbar.findViewById(R.id.reloadButton);
      reloadButtonView.setVisibility(View.VISIBLE);
      reloadButtonView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            if (_webView != null) {
              // First stop any ongoing loading
              _webView.stopLoading();
              
              // Check if there's a URL to reload
              if (_webView.getUrl() != null) {
                // Reload the current page
                _webView.reload();
                Log.d("InAppBrowser", "Reloading page: " + _webView.getUrl());
              } else if (_options.getUrl() != null) {
                // If webView URL is null but we have an initial URL, load that
                setUrl(_options.getUrl());
                Log.d("InAppBrowser", "Loading initial URL: " + _options.getUrl());
              }
            }
          }
        }
      );
    } else {
      View reloadButtonView = _toolbar.findViewById(R.id.reloadButton);
      reloadButtonView.setVisibility(View.GONE);
    }

    if (TextUtils.equals(_options.getToolbarType(), "activity")) {
      // Activity mode should ONLY have:
      // 1. Close button
      // 2. Share button (if shareSubject is provided)
      
      // Hide all navigation buttons
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);
      
      // Hide buttonNearDone
      ImageButton buttonNearDoneView = _toolbar.findViewById(R.id.buttonNearDone);
      buttonNearDoneView.setVisibility(View.GONE);
      
      // In activity mode, always make the share button visible by setting a default shareSubject if not provided
      if (_options.getShareSubject() == null || _options.getShareSubject().isEmpty()) {
        _options.setShareSubject("Share");
        Log.d("InAppBrowser", "Activity mode: Setting default shareSubject");
      }
      
      // Status bar color is already set at the top of this method, no need to set again
      
      // Share button visibility is handled separately later
    } else if (TextUtils.equals(_options.getToolbarType(), "navigation")) {
      ImageButton buttonNearDoneView = _toolbar.findViewById(
        R.id.buttonNearDone
      );
      buttonNearDoneView.setVisibility(View.GONE);
      
      // Status bar color is already set at the top of this method, no need to set again
    } else if (TextUtils.equals(_options.getToolbarType(), "blank")) {
      _toolbar.setVisibility(View.GONE);
      
      // Also set window background color to match status bar for blank toolbar
      View statusBarColorView = findViewById(R.id.status_bar_color_view);
      if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
        try {
          int toolbarColor = Color.parseColor(_options.getToolbarColor());
          if (getWindow() != null) {
            getWindow().getDecorView().setBackgroundColor(toolbarColor);
          }
          // Also set status bar color view background if available
          if (statusBarColorView != null) {
            statusBarColorView.setBackgroundColor(toolbarColor);
          }
        } catch (IllegalArgumentException e) {
          // Fallback to system default if color parsing fails
          boolean isDarkTheme = isDarkThemeEnabled();
          int windowBackgroundColor = isDarkTheme ? Color.BLACK : Color.WHITE;
          if (getWindow() != null) {
            getWindow().getDecorView().setBackgroundColor(windowBackgroundColor);
          }
          // Also set status bar color view background if available
          if (statusBarColorView != null) {
            statusBarColorView.setBackgroundColor(windowBackgroundColor);
          }
        }
      } else {
        // Follow system dark mode
        boolean isDarkTheme = isDarkThemeEnabled();
        int windowBackgroundColor = isDarkTheme ? Color.BLACK : Color.WHITE;
        if (getWindow() != null) {
          getWindow().getDecorView().setBackgroundColor(windowBackgroundColor);
        }
        // Also set status bar color view background if available
        if (statusBarColorView != null) {
          statusBarColorView.setBackgroundColor(windowBackgroundColor);
        }
      }
    } else {
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);
      
      // Status bar color is already set at the top of this method, no need to set again

      Options.ButtonNearDone buttonNearDone = _options.getButtonNearDone();
      if (buttonNearDone != null) {
        ImageButton buttonNearDoneView = _toolbar.findViewById(R.id.buttonNearDone);
        buttonNearDoneView.setVisibility(View.VISIBLE);
        
        // Handle different icon types
        String iconType = buttonNearDone.getIconType();
        if ("vector".equals(iconType)) {
          // Use native Android vector drawable
          try {
            String iconName = buttonNearDone.getIcon();
            // Convert name to Android resource ID (remove file extension if present)
            if (iconName.endsWith(".xml")) {
              iconName = iconName.substring(0, iconName.length() - 4);
            }
            
            // Get resource ID
            int resourceId = _context.getResources().getIdentifier(
              iconName, "drawable", _context.getPackageName());
            
            if (resourceId != 0) {
              // Set the vector drawable
              buttonNearDoneView.setImageResource(resourceId);
              // Apply color filter
              buttonNearDoneView.setColorFilter(iconColor);
              Log.d("InAppBrowser", "Successfully loaded vector drawable: " + iconName);
            } else {
              Log.e("InAppBrowser", "Vector drawable not found: " + iconName + ", using fallback");
              // Fallback to a common system icon
              buttonNearDoneView.setImageResource(android.R.drawable.ic_menu_info_details);
              buttonNearDoneView.setColorFilter(iconColor);
            }
          } catch (Exception e) {
            Log.e("InAppBrowser", "Error loading vector drawable: " + e.getMessage());
            // Fallback to a common system icon
            buttonNearDoneView.setImageResource(android.R.drawable.ic_menu_info_details);
            buttonNearDoneView.setColorFilter(iconColor);
          }
        } else if ("asset".equals(iconType)) {
          // Handle SVG from assets
          AssetManager assetManager = _context.getAssets();
          InputStream inputStream = null;
          try {
            // Try to load from public folder first
            String iconPath = "public/" + buttonNearDone.getIcon();
            try {
              inputStream = assetManager.open(iconPath);
            } catch (IOException e) {
              // If not found in public, try root assets
              try {
                inputStream = assetManager.open(buttonNearDone.getIcon());
              } catch (IOException e2) {
                Log.e("InAppBrowser", "SVG file not found in assets: " + buttonNearDone.getIcon());
                buttonNearDoneView.setVisibility(View.GONE);
                return;
              }
            }

            if (inputStream == null) {
              Log.e("InAppBrowser", "Failed to load SVG icon: " + buttonNearDone.getIcon());
              buttonNearDoneView.setVisibility(View.GONE);
              return;
            }

            // Parse and render SVG
            SVG svg = SVG.getFromInputStream(inputStream);
            if (svg == null) {
              Log.e("InAppBrowser", "Failed to parse SVG icon: " + buttonNearDone.getIcon());
              buttonNearDoneView.setVisibility(View.GONE);
              return;
            }

            // Get the dimensions from options or use SVG's size
            float width = buttonNearDone.getWidth() > 0 ? buttonNearDone.getWidth() : 24;
            float height = buttonNearDone.getHeight() > 0 ? buttonNearDone.getHeight() : 24;

            // Get density for proper scaling
            float density = _context.getResources().getDisplayMetrics().density;
            int targetWidth = Math.round(width * density);
            int targetHeight = Math.round(height * density);
            
            // Set document size
            svg.setDocumentWidth(targetWidth);
            svg.setDocumentHeight(targetHeight);
            
            // Create a bitmap and render SVG to it for better quality
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            svg.renderToCanvas(canvas);
            
            // Apply color filter to the bitmap
            Paint paint = new Paint();
            paint.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            Canvas colorFilterCanvas = new Canvas(bitmap);
            colorFilterCanvas.drawBitmap(bitmap, 0, 0, paint);
            
            // Set the colored bitmap as image
            buttonNearDoneView.setImageBitmap(bitmap);
            buttonNearDoneView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            buttonNearDoneView.setPadding(12, 12, 12, 12); // Standard button padding
          } catch (SVGParseException e) {
            Log.e("InAppBrowser", "Error loading SVG icon: " + e.getMessage(), e);
            buttonNearDoneView.setVisibility(View.GONE);
          } finally {
            if (inputStream != null) {
              try {
                inputStream.close();
              } catch (IOException e) {
                Log.e("InAppBrowser", "Error closing input stream: " + e.getMessage());
              }
            }
          }
        } else {
          // Default fallback or unsupported type
          Log.e("InAppBrowser", "Unsupported icon type: " + iconType);
          buttonNearDoneView.setVisibility(View.GONE);
        }
        
        // Set the click listener
        buttonNearDoneView.setOnClickListener(view ->
          _options.getCallbacks().buttonNearDoneClicked()
        );
      } else {
        ImageButton buttonNearDoneView = _toolbar.findViewById(
          R.id.buttonNearDone
        );
        buttonNearDoneView.setVisibility(View.GONE);
      }
    }

    // Add share button functionality
    ImageButton shareButton = _toolbar.findViewById(R.id.shareButton);
    if (_options.getShareSubject() != null && !_options.getShareSubject().isEmpty()) {
      shareButton.setVisibility(View.VISIBLE);
      Log.d("InAppBrowser", "Share button should be visible, shareSubject: " + _options.getShareSubject());
      
      // Apply the same color filter as other buttons to ensure visibility
      shareButton.setColorFilter(iconColor);
      
      // The color filter is now applied in applyColorToAllButtons
      shareButton.setOnClickListener(view -> {
        JSObject shareDisclaimer = _options.getShareDisclaimer();
        if (shareDisclaimer != null) {
          new AlertDialog.Builder(_context)
            .setTitle(shareDisclaimer.getString("title", "Title"))
            .setMessage(shareDisclaimer.getString("message", "Message"))
            .setPositiveButton(
              shareDisclaimer.getString("confirmBtn", "Confirm"),
              (dialog, which) -> {
                _options.getCallbacks().confirmBtnClicked();
                shareUrl();
              }
            )
            .setNegativeButton(shareDisclaimer.getString("cancelBtn", "Cancel"), null)
            .show();
        } else {
          shareUrl();
        }
      });
    } else {
      shareButton.setVisibility(View.GONE);
    }

    // Also color the title text
    TextView titleText = _toolbar.findViewById(R.id.titleText);
    if (titleText != null) {
      titleText.setTextColor(iconColor);
      
      // Set the title text
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
    }
  }

  /**
   * Applies background and tint colors to all buttons in the toolbar
   */
  private void applyColorToAllButtons(int backgroundColor, int iconColor) {
    // Get all buttons
    ImageButton backButton = _toolbar.findViewById(R.id.backButton);
    ImageButton forwardButton = _toolbar.findViewById(R.id.forwardButton);
    ImageButton closeButton = _toolbar.findViewById(R.id.closeButton);
    ImageButton reloadButton = _toolbar.findViewById(R.id.reloadButton);
    ImageButton shareButton = _toolbar.findViewById(R.id.shareButton);
    ImageButton buttonNearDoneView = _toolbar.findViewById(R.id.buttonNearDone);
    
    // Set button backgrounds
    backButton.setBackgroundColor(backgroundColor);
    forwardButton.setBackgroundColor(backgroundColor);
    closeButton.setBackgroundColor(backgroundColor);
    reloadButton.setBackgroundColor(backgroundColor);
    
    // Apply tint colors to buttons
    backButton.setColorFilter(iconColor);
    forwardButton.setColorFilter(iconColor);
    closeButton.setColorFilter(iconColor);
    reloadButton.setColorFilter(iconColor);
    shareButton.setColorFilter(iconColor);
    buttonNearDoneView.setColorFilter(iconColor);
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
            } catch (ActivityNotFoundException | URISyntaxException e) {
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
          addProxiedRequest(requestId, proxiedRequest);

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
                  "try {function getHeaders() {const h = {}; %s return h}; window.InAppBrowserProxyRequest(new Request(atob('%s'), {headers: getHeaders(), method: '%s'})).then(async (res) => Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({ok: true, result: (!!res ? {headers: Object.fromEntries(res.headers.entries()), code: res.status, body: (await res.text())} : null), id: '%s'})).catch((e) => Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({ok: false, result: e.toString(), id: '%s'})} catch (e) {Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({ok: false, result: e.toString(), id: '%s'})}",
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
              removeProxiedRequest(requestId); // prevent mem leak
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
            backButton.setColorFilter(iconColor);
          } else {
            backButton.setImageResource(R.drawable.arrow_back_disabled);
            backButton.setEnabled(false);
            backButton.setColorFilter(Color.argb(128, Color.red(iconColor), 
                                              Color.green(iconColor), 
                                              Color.blue(iconColor)));
          }

          ImageButton forwardButton = _toolbar.findViewById(R.id.forwardButton);
          if (_webView.canGoForward()) {
            forwardButton.setImageResource(R.drawable.arrow_forward_enabled);
            forwardButton.setEnabled(true);
            forwardButton.setColorFilter(iconColor);
          } else {
            forwardButton.setImageResource(R.drawable.arrow_forward_disabled);
            forwardButton.setEnabled(false);
            forwardButton.setColorFilter(Color.argb(128, Color.red(iconColor), 
                                                  Color.green(iconColor), 
                                                  Color.blue(iconColor)));
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
      _options.getCallbacks().closeEvent(_webView.getUrl());
      _webView.destroy();
      super.onBackPressed();
    }
  }

  public static String getReasonPhrase(int statusCode) {
    return switch (statusCode) {
      case (200) -> "OK";
      case (201) -> "Created";
      case (202) -> "Accepted";
      case (203) -> "Non Authoritative Information";
      case (204) -> "No Content";
      case (205) -> "Reset Content";
      case (206) -> "Partial Content";
      case (207) -> "Partial Update OK";
      case (300) -> "Mutliple Choices";
      case (301) -> "Moved Permanently";
      case (302) -> "Moved Temporarily";
      case (303) -> "See Other";
      case (304) -> "Not Modified";
      case (305) -> "Use Proxy";
      case (307) -> "Temporary Redirect";
      case (400) -> "Bad Request";
      case (401) -> "Unauthorized";
      case (402) -> "Payment Required";
      case (403) -> "Forbidden";
      case (404) -> "Not Found";
      case (405) -> "Method Not Allowed";
      case (406) -> "Not Acceptable";
      case (407) -> "Proxy Authentication Required";
      case (408) -> "Request Timeout";
      case (409) -> "Conflict";
      case (410) -> "Gone";
      case (411) -> "Length Required";
      case (412) -> "Precondition Failed";
      case (413) -> "Request Entity Too Large";
      case (414) -> "Request-URI Too Long";
      case (415) -> "Unsupported Media Type";
      case (416) -> "Requested Range Not Satisfiable";
      case (417) -> "Expectation Failed";
      case (418) -> "Reauthentication Required";
      case (419) -> "Proxy Reauthentication Required";
      case (422) -> "Unprocessable Entity";
      case (423) -> "Locked";
      case (424) -> "Failed Dependency";
      case (500) -> "Server Error";
      case (501) -> "Not Implemented";
      case (502) -> "Bad Gateway";
      case (503) -> "Service Unavailable";
      case (504) -> "Gateway Timeout";
      case (505) -> "HTTP Version Not Supported";
      case (507) -> "Insufficient Storage";
      default -> "";
    };
  }

  @Override
  public void dismiss() {
    if (_webView != null) {
      _webView.loadUrl("about:blank");
      _webView.onPause();
      _webView.removeAllViews();
      _webView.destroy();
      _webView = null;
    }

    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
      }
    }

    super.dismiss();
  }

  public void addProxiedRequest(String key, ProxiedRequest request) {
    synchronized (proxiedRequestsHashmap) {
      proxiedRequestsHashmap.put(key, request);
    }
  }

  public ProxiedRequest getProxiedRequest(String key) {
    synchronized (proxiedRequestsHashmap) {
      ProxiedRequest request = proxiedRequestsHashmap.get(key);
      proxiedRequestsHashmap.remove(key);
      return request;
    }
  }

  public void removeProxiedRequest(String key) {
    synchronized (proxiedRequestsHashmap) {
      proxiedRequestsHashmap.remove(key);
    }
  }

  private void shareUrl() {
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("text/plain");
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, _options.getShareSubject());
    shareIntent.putExtra(Intent.EXTRA_TEXT, _options.getUrl());
    _context.startActivity(Intent.createChooser(shareIntent, "Share"));
  }

  private boolean isDarkColor(int color) {
    int red = Color.red(color);
    int green = Color.green(color);
    int blue = Color.blue(color);
    double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
    return luminance < 0.5;
  }

  private boolean isDarkThemeEnabled() {
    // This method checks if dark theme is currently enabled without using Configuration class
    try {
      // On Android 10+, check via resources for night mode
      Resources.Theme theme = _context.getTheme();
      TypedValue typedValue = new TypedValue();
      
      if (theme.resolveAttribute(android.R.attr.isLightTheme, typedValue, true)) {
        // isLightTheme exists - returns true if light, false if dark
        return typedValue.data != 1;
      }
      
      // Fallback method - check background color of window
      if (theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)) {
        int backgroundColor = typedValue.data;
        return isDarkColor(backgroundColor);
      }
    } catch (Exception e) {
      // Ignore and fallback to light theme
    }
    return false;
  }
}
