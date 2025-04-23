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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
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
import android.webkit.WebSettings;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.getcapacitor.JSObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
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
import androidx.activity.result.ActivityResultLauncher;
import android.os.Handler;
import android.os.Looper;
import android.widget.RelativeLayout;

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
  private boolean datePickerInjected = false; // Track if we've injected date picker fixes
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

  // Temporary URI for storing camera capture
  public Uri tempCameraUri;

  public interface PermissionHandler {
    void handleCameraPermissionRequest(PermissionRequest request);

    void handleMicrophonePermissionRequest(PermissionRequest request);
  }

  private final PermissionHandler permissionHandler;

  private final ActivityResultLauncher<Intent> fileChooserLauncher;
  private final ActivityResultLauncher<Intent> cameraLauncher;

  public WebViewDialog(
    Context context,
    int theme,
    Options options,
    PermissionHandler permissionHandler,
    WebView capacitorWebView,
    ActivityResultLauncher<Intent> fileChooserLauncher,
    ActivityResultLauncher<Intent> cameraLauncher
  ) {
    // Use Material theme only if materialPicker is enabled
    super(
      context,
      options.getMaterialPicker() ? R.style.InAppBrowserMaterialTheme : theme
    );
    this._options = options;
    this._context = context;
    this.permissionHandler = permissionHandler;
    this.isInitialized = false;
    this.capacitorWebView = capacitorWebView;
    this.fileChooserLauncher = fileChooserLauncher;
    this.cameraLauncher = cameraLauncher;
  }

  // Add this class to provide safer JavaScript interface
  private class JavaScriptInterface {

    @JavascriptInterface
    public void postMessage(String message) {
      // Handle postMessage
    }

    @JavascriptInterface
    public void close() {
      if (activity != null) {
        activity.runOnUiThread(() -> {
          dismiss();
        });
      }
    }

    @JavascriptInterface
    public void share(String title, String text, String url, String fileData, String fileName, String fileType) {
      Log.d("InAppBrowser", "Native share method called with params: " +
        "title=" + title + ", " +
        "text=" + text + ", " +
        "url=" + url + ", " +
        "fileData=" + (fileData != null ? "present" : "null") + ", " +
        "fileName=" + fileName + ", " +
        "fileType=" + fileType);

      if (activity == null) {
        Log.e("InAppBrowser", "Activity is null, cannot share");
        return;
      }

      activity.runOnUiThread(() -> {
        try {
          // Create the sharing intent
          Intent shareIntent = new Intent();
          shareIntent.setAction(Intent.ACTION_SEND);

          // Handle file sharing
          if (fileData != null && !fileData.isEmpty()) {
            try {
              Log.d("InAppBrowser", "Processing file share");
              // Decode base64 data
              byte[] fileBytes = Base64.decode(fileData, Base64.DEFAULT);

              // Create temporary file
              File tempFile = new File(activity.getCacheDir(), fileName);
              FileOutputStream fos = new FileOutputStream(tempFile);
              fos.write(fileBytes);
              fos.close();

              // Get content URI
              Uri fileUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                tempFile
              );

              // Set up share intent for file
              shareIntent.setType(fileType);
              shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
              shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
              Log.d("InAppBrowser", "File share intent prepared");
            } catch (Exception e) {
              Log.e("InAppBrowser", "Error handling file share: " + e.getMessage());
              e.printStackTrace();
              return;
            }
          } else {
            Log.d("InAppBrowser", "Processing text share");
            // Handle text sharing
            shareIntent.setType("text/plain");

            // Combine title, text and url
            StringBuilder shareText = new StringBuilder();
            if (title != null && !title.isEmpty()) {
              shareText.append(title).append("\n");
            }
            if (text != null && !text.isEmpty()) {
              shareText.append(text).append("\n");
            }
            if (url != null && !url.isEmpty()) {
              shareText.append(url);
            }

            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
            Log.d("InAppBrowser", "Text share intent prepared");
          }

          // Verify that we can resolve the intent
          if (shareIntent.resolveActivity(activity.getPackageManager()) != null) {
            Log.d("InAppBrowser", "Found activity to handle share intent");
            // Create chooser dialog
            Intent chooser = Intent.createChooser(shareIntent, "Share with");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Start the activity
            activity.startActivity(chooser);
            Log.d("InAppBrowser", "Share intent started successfully");
          } else {
            Log.e("InAppBrowser", "No activity found to handle share intent");
          }
        } catch (Exception e) {
          Log.e("InAppBrowser", "Error sharing content: " + e.getMessage());
          e.printStackTrace();
        }
      });
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
//////ююююююююююююююююююю
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

  // ... [КОД УСТАНОВКИ STATUS BAR И ПРОЧЕГО] ...

  this._webView = findViewById(R.id.browser_view);

  applyInsets();

  // Add JavaScript interfaces
  _webView.addJavascriptInterface(new JavaScriptInterface(), "mobileApp");
  _webView.addJavascriptInterface(new PreShowScriptInterface(), "PreShowScriptInterface");

  // WebView settings
  WebSettings webSettings = _webView.getSettings();
  webSettings.setJavaScriptEnabled(true);
  webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
  webSettings.setDatabaseEnabled(true);
  webSettings.setDomStorageEnabled(true);
  webSettings.setAllowFileAccess(true);
  webSettings.setLoadWithOverviewMode(true);
  webSettings.setUseWideViewPort(true);
  webSettings.setAllowFileAccessFromFileURLs(true);
  webSettings.setAllowUniversalAccessFromFileURLs(true);
  webSettings.setMediaPlaybackRequiresUserGesture(false);

  if (_options.getTextZoom() > 0) {
    webSettings.setTextZoom(_options.getTextZoom());
  }

  // Set WebViewClient only AFTER _webView is initialized
  setWebViewClient();

  _webView.setWebChromeClient(new MyWebChromeClient());

  // Load URL and headers
  Map<String, String> requestHeaders = new HashMap<>();
  if (_options.getHeaders() != null) {
    Iterator<String> keys = _options.getHeaders().keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (TextUtils.equals(key.toLowerCase(), "user-agent")) {
        _webView.getSettings().setUserAgentString(_options.getHeaders().getString(key));
      } else {
        requestHeaders.put(key, _options.getHeaders().getString(key));
      }
    }
  }
  _webView.loadUrl(_options.getUrl(), requestHeaders);
  _webView.requestFocus();
  _webView.requestFocusFromTouch();

  setupToolbar();

  if (!_options.isPresentAfterPageLoad()) {
    show();
    _options.getPluginCall().resolve();
  }
}

  private void injectAndroidJavaScriptInterface() {
    if (_webView == null) {
      Log.e("InAppBrowser", "injectJavaScriptInterface called before WebView is ready");
      return;
    }

    _webView.addJavascriptInterface(new JavaScriptInterface(), "mobileApp");
    Log.d("InAppBrowser", "JavaScript interface added");

    new Handler(Looper.getMainLooper()).postDelayed(() -> {
      if (_webView == null) return;
      String verifyScript = """
      (function() {
        console.log('Verifying mobileApp interface...');
        if (typeof window.mobileApp === 'undefined') {
          console.error('mobileApp is undefined');
          return false;
        }
        if (typeof window.mobileApp.share !== 'function') {
          console.error('mobileApp.share is not a function');
          return false;
        }
        console.log('mobileApp interface verified successfully');
        return true;
      })();
    """;

      _webView.evaluateJavascript(verifyScript, value -> {
        Log.d("InAppBrowser", "Interface verification result: " + value);
        if ("true".equals(value)) {
          initializeSharePolyfill();
        } else {
          Log.e("InAppBrowser", "JavaScript interface verification failed");
          _webView.addJavascriptInterface(new JavaScriptInterface(), "mobileApp");
          Log.d("InAppBrowser", "Re-added JavaScript interface");

          new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (_webView == null) return;
            _webView.evaluateJavascript(verifyScript, retryValue -> {
              if ("true".equals(retryValue)) {
                initializeSharePolyfill();
              } else {
                Log.e("InAppBrowser", "JavaScript interface still not available after retry");
              }
            });
          }, 1000);
        }
      });
    }, 1000);
  }
// /////
  /**
   * Apply window insets to the WebView to properly handle edge-to-edge display
   * and fix status bar overlap issues on Android 15+
   */
  private void applyInsets() {
    if (_webView == null) {
      return;
    }

    // Check if we need Android 15+ specific fixes
    boolean isAndroid15Plus = Build.VERSION.SDK_INT >= 35;

    // Get parent view
    ViewGroup parent = (ViewGroup) _webView.getParent();

    // Find status bar color view and toolbar for Android 15+ specific handling
    View statusBarColorView = findViewById(R.id.status_bar_color_view);
    View toolbarView = findViewById(R.id.tool_bar);

    // Special handling for Android 15+
    if (isAndroid15Plus) {
      // Get AppBarLayout which contains the toolbar
      if (toolbarView != null && toolbarView.getParent() instanceof com.google.android.material.appbar.AppBarLayout) {
        com.google.android.material.appbar.AppBarLayout appBarLayout =
          (com.google.android.material.appbar.AppBarLayout) toolbarView.getParent();
        // Remove elevation to eliminate shadows (only on Android 15+)
        appBarLayout.setElevation(0);
        appBarLayout.setStateListAnimator(null);
        appBarLayout.setOutlineProvider(null);

        // Determine background color to use
        int backgroundColor = Color.BLACK; // Default fallback
        if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
          try {
            backgroundColor = Color.parseColor(_options.getToolbarColor());
          } catch (IllegalArgumentException e) {
            Log.e("InAppBrowser", "Invalid toolbar color, using black: " + e.getMessage());
          }
        } else {
          // Follow system theme if no color specified
          boolean isDarkTheme = isDarkThemeEnabled();
          backgroundColor = isDarkTheme ? Color.BLACK : Color.WHITE;
        }

        // Apply fixes for Android 15+ using a delayed post
        final int finalBgColor = backgroundColor;
        _webView.post(() -> {
          // Get status bar height
          int statusBarHeight = 0;
          int resourceId = getContext()
            .getResources()
            .getIdentifier("status_bar_height", "dimen", "android");
          if (resourceId > 0) {
            statusBarHeight = getContext()
              .getResources()
              .getDimensionPixelSize(resourceId);
          }

          // Fix status bar view
          if (statusBarColorView != null) {
            ViewGroup.LayoutParams params =
              statusBarColorView.getLayoutParams();
            params.height = statusBarHeight;
            statusBarColorView.setLayoutParams(params);
            statusBarColorView.setBackgroundColor(finalBgColor);
            statusBarColorView.setVisibility(View.VISIBLE);
          }

          // Fix AppBarLayout position
          ViewGroup.MarginLayoutParams params =
            (ViewGroup.MarginLayoutParams) appBarLayout.getLayoutParams();
          params.topMargin = statusBarHeight;
          appBarLayout.setLayoutParams(params);
          appBarLayout.setBackgroundColor(finalBgColor);
        });
      }
    }

    // Apply system insets to WebView (compatible with all Android versions)
    ViewCompat.setOnApplyWindowInsetsListener(_webView, (v, windowInsets) -> {
      Insets insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars()
      );
      Boolean keyboardVisible = windowInsets.isVisible(
        WindowInsetsCompat.Type.ime()
      );

      ViewGroup.MarginLayoutParams mlp =
        (ViewGroup.MarginLayoutParams) v.getLayoutParams();

      // Apply margins based on Android version
      if (isAndroid15Plus) {
        // Android 15+ specific handling
        if (keyboardVisible) {
          mlp.bottomMargin = 0;
        } else {
          mlp.bottomMargin = insets.bottom;
        }
        // On Android 15+, don't add top margin as it's handled by AppBarLayout
        mlp.topMargin = 0;
      } else {
        // Original behavior for older Android versions
        mlp.topMargin = insets.top;
        mlp.bottomMargin = insets.bottom;
      }

      // These stay the same for all Android versions
      mlp.leftMargin = insets.left;
      mlp.rightMargin = insets.right;
      v.setLayoutParams(mlp);

      return WindowInsetsCompat.CONSUMED;
    });

    // Handle window decoration - version-specific window settings
    if (getWindow() != null) {
      if (isAndroid15Plus) {
        // Only for Android 15+: Set window to draw behind status bar
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        // Set status bar text color
        int backgroundColor;
        if (
          _options.getToolbarColor() != null &&
            !_options.getToolbarColor().isEmpty()
        ) {
          try {
            backgroundColor = Color.parseColor(_options.getToolbarColor());
            boolean isDarkBackground = isDarkColor(backgroundColor);
            WindowInsetsControllerCompat controller =
              new WindowInsetsControllerCompat(
                getWindow(),
                getWindow().getDecorView()
              );
            controller.setAppearanceLightStatusBars(!isDarkBackground);
          } catch (IllegalArgumentException e) {
            // Ignore color parsing errors
          }
        }
      } else if (Build.VERSION.SDK_INT >= 30) {
        // Android 11-14: Use original behavior
        WindowInsetsControllerCompat controller =
          new WindowInsetsControllerCompat(
            getWindow(),
            getWindow().getDecorView()
          );

        // Original behavior for status bar color
        if (
          _options.getToolbarColor() != null &&
            !_options.getToolbarColor().isEmpty()
        ) {
          try {
            int toolbarColor = Color.parseColor(_options.getToolbarColor());
            getWindow().setStatusBarColor(toolbarColor);

            boolean isDarkBackground = isDarkColor(toolbarColor);
            controller.setAppearanceLightStatusBars(!isDarkBackground);
          } catch (IllegalArgumentException e) {
            // Ignore color parsing errors
          }
        }
      } else {
        // Pre-Android 11: Original behavior with deprecated flags
        getWindow()
          .getDecorView()
          .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
              View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          );

        // Apply original status bar color logic
        if (
          _options.getToolbarColor() != null &&
            !_options.getToolbarColor().isEmpty()
        ) {
          try {
            int toolbarColor = Color.parseColor(_options.getToolbarColor());
            getWindow().setStatusBarColor(toolbarColor);
          } catch (IllegalArgumentException e) {
            // Ignore color parsing errors
          }
        }
      }
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

  private void initializeSharePolyfill() {
    String initScript = """
      (function() {
        console.log('Initializing Web Share API polyfill...');
        
        // Function to check if mobileApp is available
        function isMobileAppAvailable() {
          return typeof window.mobileApp !== 'undefined' && 
                 typeof window.mobileApp.share === 'function';
        }
        
        // Function to wait for mobileApp to be available
        function waitForMobileApp(callback, maxAttempts = 20) {
          let attempts = 0;
          const checkInterval = setInterval(() => {
            attempts++;
            if (isMobileAppAvailable()) {
              clearInterval(checkInterval);
              callback();
            } else if (attempts >= maxAttempts) {
              clearInterval(checkInterval);
              console.error('mobileApp interface not available after ' + maxAttempts + ' attempts');
            }
          }, 100);
        }
        
        // Initialize Web Share API polyfill
        if (typeof navigator.share === 'undefined') {
          console.log('navigator.share is undefined, adding polyfill');
          navigator.share = function(shareData) {
            console.log('Share called with data:', shareData);
            return new Promise((resolve, reject) => {
              try {
                const title = shareData.title || '';
                const text = shareData.text || '';
                const url = shareData.url || '';
                
                console.log('Preparing to share:', {title, text, url});
                
                if (shareData.files && shareData.files.length > 0) {
                  console.log('Sharing file:', shareData.files[0]);
                  const file = shareData.files[0];
                  const reader = new FileReader();
                  
                  reader.onload = function() {
                    try {
                      console.log('File loaded, converting to base64');
                      const base64Data = reader.result.split(',')[1];
                      
                      // Wait for mobileApp to be available before sharing
                      waitForMobileApp(() => {
                        console.log('mobileApp available, calling share');
                        window.mobileApp.share(title, text, url, base64Data, file.name, file.type);
                        resolve();
                      }, 20);
                    } catch (error) {
                      console.error('Error in file share:', error);
                      reject(error);
                    }
                  };
                  
                  reader.onerror = function(error) {
                    console.error('Error reading file:', error);
                    reject(error);
                  };
                  
                  reader.readAsDataURL(file);
                } else {
                  console.log('Sharing text/url');
                  // Wait for mobileApp to be available before sharing
                  waitForMobileApp(() => {
                    console.log('mobileApp available, calling share');
                    window.mobileApp.share(title, text, url, '', '', '');
                    resolve();
                  }, 20);
                }
              } catch (error) {
                console.error('Error in share polyfill:', error);
                reject(error);
              }
            });
          };
          console.log('Web Share API polyfill added successfully');
        } else {
          console.log('navigator.share already exists');
        }
      })();
    """;

    _webView.evaluateJavascript(initScript, new ValueCallback<String>() {
      @Override
      public void onReceiveValue(String value) {
        Log.d("InAppBrowser", "Web Share API polyfill initialized with result: " + value);
      }
    });
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
        "preShowFunction().then(() => window.PreShowScriptInterface.success()).catch(err => { console.error('Pre show error', err); window.PreShowScriptInterface.error(JSON.stringify(err, Object.getOwnPropertyNames(err))) })";

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

  private void openFileChooser(ValueCallback<Uri[]> filePathCallback) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");

    if (fileChooserLauncher != null) {
      fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"));
    } else {
      // Fallback for older Android versions
      try {
        activity.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST_CODE);
      } catch (ActivityNotFoundException e) {
        Toast.makeText(activity, "Cannot open file chooser", Toast.LENGTH_LONG).show();
      }
    }
  }

  private void openFileChooser(ValueCallback<Uri[]> filePathCallback, String acceptType, boolean isMultiple) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    if (isMultiple) {
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }
    if (acceptType != null && !acceptType.isEmpty()) {
      intent.setType(acceptType);
    }

    if (fileChooserLauncher != null) {
      fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"));
    } else {
      // Fallback for older Android versions
      try {
        activity.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST_CODE);
      } catch (ActivityNotFoundException e) {
        Toast.makeText(activity, "Cannot open file chooser", Toast.LENGTH_LONG).show();
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
    if (_webView != null) {
      return _webView.getUrl();
    }
    return "";
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
    if (
      _options.getToolbarColor() != null &&
        !_options.getToolbarColor().isEmpty()
    ) {
      try {
        int toolbarColor = Color.parseColor(_options.getToolbarColor());
        _toolbar.setBackgroundColor(toolbarColor);

        // Get toolbar title and ensure it gets the right color
        TextView titleText = _toolbar.findViewById(R.id.titleText);

        // Determine icon and text color
        int iconColor;
        if (
          _options.getToolbarTextColor() != null &&
            !_options.getToolbarTextColor().isEmpty()
        ) {
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
          WindowInsetsControllerCompat insetsController =
            new WindowInsetsControllerCompat(
              getWindow(),
              getWindow().getDecorView()
            );
          insetsController.setAppearanceLightStatusBars(!isDarkBackground);
        }
      } catch (IllegalArgumentException e) {
        Log.e(
          "InAppBrowser",
          "Invalid toolbar color: " + _options.getToolbarColor()
        );
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

    if (TextUtils.equals(_options.getToolbarType(), "activity")) {
      // Activity mode should ONLY have:
      // 1. Close button
      // 2. Share button (if shareSubject is provided)

      // Hide all navigation buttons
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);

      // Hide buttonNearDone
      ImageButton buttonNearDoneView = _toolbar.findViewById(
        R.id.buttonNearDone
      );
      buttonNearDoneView.setVisibility(View.GONE);

      // In activity mode, always make the share button visible by setting a default shareSubject if not provided
      if (
        _options.getShareSubject() == null ||
          _options.getShareSubject().isEmpty()
      ) {
        _options.setShareSubject("Share");
        Log.d("InAppBrowser", "Activity mode: Setting default shareSubject");
      }
      // Status bar color is already set at the top of this method, no need to set again

      // Share button visibility is handled separately later
    } else if (TextUtils.equals(_options.getToolbarType(), "default")) {
      // Default mode should ONLY have:
      // 1. Close button

      // Hide all navigation buttons
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);

      // Hide share button
      _toolbar.findViewById(R.id.shareButton).setVisibility(View.GONE);

      // Hide buttonNearDone
      ImageButton buttonNearDoneView = _toolbar.findViewById(
        R.id.buttonNearDone
      );
      buttonNearDoneView.setVisibility(View.GONE);

      // Hide reload button
      _toolbar.findViewById(R.id.reloadButton).setVisibility(View.GONE);
    } else if (TextUtils.equals(_options.getToolbarType(), "navigation")) {
      ImageButton buttonNearDoneView = _toolbar.findViewById(
        R.id.buttonNearDone
      );
      buttonNearDoneView.setVisibility(View.GONE);
      // Status bar color is already set at the top of this method, no need to set again
    } else if (TextUtils.equals(_options.getToolbarType(), "blank")) {
      // Hide all navigation buttons
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.shareButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.reloadButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.buttonNearDone).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.closeButton).setVisibility(View.GONE);
    } else if (TextUtils.equals(_options.getToolbarType(), "hidden")) {
      // Hide the entire toolbar
      _toolbar.setVisibility(View.GONE);

      // Adjust the WebView layout to take full space
      if (_webView.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
          (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) _webView.getLayoutParams();
        params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        params.rightToRight = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
        _webView.setLayoutParams(params);
      }
    } else {
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);

      // Status bar color is already set at the top of this method, no need to set again

      Options.ButtonNearDone buttonNearDone = _options.getButtonNearDone();
      if (buttonNearDone != null) {
        ImageButton buttonNearDoneView = _toolbar.findViewById(
          R.id.buttonNearDone
        );
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
            int resourceId = _context
              .getResources()
              .getIdentifier(iconName, "drawable", _context.getPackageName());

            if (resourceId != 0) {
              // Set the vector drawable
              buttonNearDoneView.setImageResource(resourceId);
              // Apply color filter
              buttonNearDoneView.setColorFilter(iconColor);
              Log.d(
                "InAppBrowser",
                "Successfully loaded vector drawable: " + iconName
              );
            } else {
              Log.e(
                "InAppBrowser",
                "Vector drawable not found: " + iconName + ", using fallback"
              );
              // Fallback to a common system icon
              buttonNearDoneView.setImageResource(
                android.R.drawable.ic_menu_info_details
              );
              buttonNearDoneView.setColorFilter(iconColor);
            }
          } catch (Exception e) {
            Log.e(
              "InAppBrowser",
              "Error loading vector drawable: " + e.getMessage()
            );
            // Fallback to a common system icon
            buttonNearDoneView.setImageResource(
              android.R.drawable.ic_menu_info_details
            );
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
                Log.e(
                  "InAppBrowser",
                  "SVG file not found in assets: " + buttonNearDone.getIcon()
                );
                buttonNearDoneView.setVisibility(View.GONE);
                return;
              }
            }

            // Parse and render SVG
            SVG svg = SVG.getFromInputStream(inputStream);
            if (svg == null) {
              Log.e(
                "InAppBrowser",
                "Failed to parse SVG icon: " + buttonNearDone.getIcon()
              );
              buttonNearDoneView.setVisibility(View.GONE);
              return;
            }

            // Get the dimensions from options or use SVG's size
            float width = buttonNearDone.getWidth() > 0
              ? buttonNearDone.getWidth()
              : 24;
            float height = buttonNearDone.getHeight() > 0
              ? buttonNearDone.getHeight()
              : 24;

            // Get density for proper scaling
            float density = _context.getResources().getDisplayMetrics().density;
            int targetWidth = Math.round(width * density);
            int targetHeight = Math.round(height * density);

            // Set document size
            svg.setDocumentWidth(targetWidth);
            svg.setDocumentHeight(targetHeight);

            // Create a bitmap and render SVG to it for better quality
            Bitmap bitmap = Bitmap.createBitmap(
              targetWidth,
              targetHeight,
              Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(bitmap);
            svg.renderToCanvas(canvas);

            // Apply color filter to the bitmap
            Paint paint = new Paint();
            paint.setColorFilter(
              new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
            );
            Canvas colorFilterCanvas = new Canvas(bitmap);
            colorFilterCanvas.drawBitmap(bitmap, 0, 0, paint);

            // Set the colored bitmap as image
            buttonNearDoneView.setImageBitmap(bitmap);
            buttonNearDoneView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            buttonNearDoneView.setPadding(12, 12, 12, 12); // Standard button padding
          } catch (SVGParseException e) {
            Log.e(
              "InAppBrowser",
              "Error loading SVG icon: " + e.getMessage(),
              e
            );
            buttonNearDoneView.setVisibility(View.GONE);
          } finally {
            if (inputStream != null) {
              try {
                inputStream.close();
              } catch (IOException e) {
                Log.e(
                  "InAppBrowser",
                  "Error closing input stream: " + e.getMessage()
                );
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
    if (
      _options.getShareSubject() != null &&
        !_options.getShareSubject().isEmpty()
    ) {
      shareButton.setVisibility(View.VISIBLE);
      Log.d(
        "InAppBrowser",
        "Share button should be visible, shareSubject: " +
          _options.getShareSubject()
      );

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
            .setNegativeButton(
              shareDisclaimer.getString("cancelBtn", "Cancel"),
              null
            )
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

  private Uri convertHeicToJpeg(Uri heicUri) {
    try {
      // Get input stream
      InputStream inputStream = activity.getContentResolver().openInputStream(heicUri);
      if (inputStream == null) return heicUri;

      // Create output file
      File jpegFile = new File(activity.getCacheDir(), "converted_" + System.currentTimeMillis() + ".jpg");
      FileOutputStream outputStream = new FileOutputStream(jpegFile);

      // Get image dimensions
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(inputStream, null, options);
      inputStream.close();

      // Calculate sample size to reduce memory usage
      int maxSize = 2048; // Max dimension
      int sampleSize = 1;
      if (options.outHeight > maxSize || options.outWidth > maxSize) {
        sampleSize = Math.round((float) Math.max(options.outHeight, options.outWidth) / maxSize);
      }

      // Read image with calculated sample size
      options.inJustDecodeBounds = false;
      options.inSampleSize = sampleSize;
      inputStream = activity.getContentResolver().openInputStream(heicUri);
      Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
      inputStream.close();

      if (bitmap == null) return heicUri;

      // Compress to JPEG with good quality
      bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
      outputStream.flush();
      outputStream.close();
      bitmap.recycle();

      // Get content URI for the converted file
      return FileProvider.getUriForFile(
        activity,
        activity.getPackageName() + ".fileprovider",
        jpegFile
      );
    } catch (Exception e) {
      Log.e("InAppBrowser", "Error converting HEIC to JPEG: " + e.getMessage());
      return heicUri;
    }
  }

  public void handleFileChooserResult(ActivityResult result) {
    if (mFilePathCallback != null) {
      Uri[] results = null;
      if (result.getResultCode() == Activity.RESULT_OK) {
        Intent data = result.getData();
        if (data != null) {
          String dataString = data.getDataString();
          if (dataString != null) {
            Uri originalUri = Uri.parse(dataString);
            // Check if the file is HEIC format
            String mimeType = activity.getContentResolver().getType(originalUri);
            if (mimeType != null && mimeType.equals("image/heic")) {
              results = new Uri[] { convertHeicToJpeg(originalUri) };
            } else {
              results = new Uri[] { originalUri };
            }
          } else if (data.getClipData() != null) {
            final int numSelectedFiles = data.getClipData().getItemCount();
            results = new Uri[numSelectedFiles];
            for (int i = 0; i < numSelectedFiles; i++) {
              Uri originalUri = data.getClipData().getItemAt(i).getUri();
              // Check if the file is HEIC format
              String mimeType = activity.getContentResolver().getType(originalUri);
              if (mimeType != null && mimeType.equals("image/heic")) {
                results[i] = convertHeicToJpeg(originalUri);
              } else {
                results[i] = originalUri;
              }
            }
          }
        }
      }
      mFilePathCallback.onReceiveValue(results);
      mFilePathCallback = null;
    }
  }

  public void handleCameraResult(ActivityResult result) {
    if (mFilePathCallback != null) {
      Uri[] results = null;
      if (result.getResultCode() == Activity.RESULT_OK && tempCameraUri != null) {
        results = new Uri[] { tempCameraUri };
      }
      mFilePathCallback.onReceiveValue(results);
      mFilePathCallback = null;
      tempCameraUri = null;
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
    _webView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        injectAndroidJavaScriptInterface();
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        injectAndroidJavaScriptInterface();
      }
    });
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
    switch (statusCode) {
      case 200: return "OK";
      case 201: return "Created";
      case 202: return "Accepted";
      case 203: return "Non Authoritative Information";
      case 204: return "No Content";
      case 205: return "Reset Content";
      case 206: return "Partial Content";
      case 207: return "Partial Update OK";
      case 300: return "Mutliple Choices";
      case 301: return "Moved Permanently";
      case 302: return "Moved Temporarily";
      case 303: return "See Other";
      case 304: return "Not Modified";
      case 305: return "Use Proxy";
      case 307: return "Temporary Redirect";
      case 400: return "Bad Request";
      case 401: return "Unauthorized";
      case 402: return "Payment Required";
      case 403: return "Forbidden";
      case 404: return "Not Found";
      case 405: return "Method Not Allowed";
      case 406: return "Not Acceptable";
      case 407: return "Proxy Authentication Required";
      case 408: return "Request Timeout";
      case 409: return "Conflict";
      case 410: return "Gone";
      case 411: return "Length Required";
      case 412: return "Precondition Failed";
      case 413: return "Request Entity Too Large";
      case 414: return "Request-URI Too Long";
      case 415: return "Unsupported Media Type";
      case 416: return "Requested Range Not Satisfiable";
      case 417: return "Expectation Failed";
      case 418: return "Reauthentication Required";
      case 419: return "Proxy Reauthentication Required";
      case 422: return "Unprocessable Entity";
      case 423: return "Locked";
      case 424: return "Failed Dependency";
      case 500: return "Server Error";
      case 501: return "Not Implemented";
      case 502: return "Bad Gateway";
      case 503: return "Service Unavailable";
      case 504: return "Gateway Timeout";
      case 505: return "HTTP Version Not Supported";
      case 507: return "Insufficient Storage";
      default: return "";
    }
  }

  @Override
  public void dismiss() {
    if (_webView != null) {
      // Reset file inputs to prevent WebView from caching them
      _webView.evaluateJavascript(
        "(function() {" +
          "  var inputs = document.querySelectorAll('input[type=\"file\"]');" +
          "  for (var i = 0; i < inputs.length; i++) {" +
          "    inputs[i].value = '';" +
          "  }" +
          "  return true;" +
          "})();",
        null
      );

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

      if (
        theme.resolveAttribute(android.R.attr.isLightTheme, typedValue, true)
      ) {
        // isLightTheme exists - returns true if light, false if dark
        return typedValue.data != 1;
      }

      // Fallback method - check background color of window
      if (
        theme.resolveAttribute(
          android.R.attr.windowBackground,
          typedValue,
          true
        )
      ) {
        int backgroundColor = typedValue.data;
        return isDarkColor(backgroundColor);
      }
    } catch (Exception e) {
      // Ignore and fallback to light theme
    }
    return false;
  }

  private void injectDatePickerFixes() {
    if (_webView == null || datePickerInjected) {
      return;
    }

    datePickerInjected = true;

    // This script adds minimal fixes for date inputs to use Material Design
    String script =
      "(function() {\n" +
      "  // Find all date inputs\n" +
      "  const dateInputs = document.querySelectorAll('input[type=\"date\"]');\n" +
      "  dateInputs.forEach(input => {\n" +
      "    // Ensure change events propagate correctly\n" +
      "    let lastValue = input.value;\n" +
      "    input.addEventListener('change', () => {\n" +
      "      if (input.value !== lastValue) {\n" +
      "        lastValue = input.value;\n" +
      "        // Dispatch an input event to ensure frameworks detect the change\n" +
      "        input.dispatchEvent(new Event('input', { bubbles: true }));\n" +
      "      }\n" +
      "    });\n" +
      "  });\n" +
      "})();";

    // Execute the script in the WebView
    _webView.post(() -> _webView.evaluateJavascript(script, null));

    Log.d("InAppBrowser", "Applied minimal date picker fixes");
  }

  /**
   * Creates a temporary URI for storing camera capture
   * @return URI for the temporary file or null if creation failed
   */
  private Uri createTempImageUri() {
    try {
      String fileName = "capture_" + System.currentTimeMillis() + ".jpg";
      java.io.File cacheDir = _context.getCacheDir();

      // Make sure cache directory exists
      if (!cacheDir.exists() && !cacheDir.mkdirs()) {
        return null;
      }

      // Create temporary file
      java.io.File tempFile = new java.io.File(cacheDir, fileName);
      if (!tempFile.createNewFile()) {
        return null;
      }

      // Get content URI through FileProvider
      try {
        return androidx.core.content.FileProvider.getUriForFile(
          _context,
          _context.getPackageName() + ".fileprovider",
          tempFile
        );
      } catch (IllegalArgumentException e) {
        // Try using external storage as fallback
        java.io.File externalCacheDir = _context.getExternalCacheDir();
        if (externalCacheDir != null) {
          tempFile = new java.io.File(externalCacheDir, fileName);
          final boolean newFile = tempFile.createNewFile();
          if (!newFile) {
            Log.d("InAppBrowser", "Error creating new file");
          }
          return androidx.core.content.FileProvider.getUriForFile(
            _context,
            _context.getPackageName() + ".fileprovider",
            tempFile
          );
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private File createImageFile() throws IOException {
    // Create an image file name
    String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(
      new java.util.Date()
    );
    String imageFileName = "JPEG_" + timeStamp + "_";
    File storageDir = activity.getExternalFilesDir(
      Environment.DIRECTORY_PICTURES
    );
    File image = File.createTempFile(
      imageFileName,/* prefix */
      ".jpg",/* suffix */
      storageDir/* directory */
    );
    return image;
  }

  private class MyWebChromeClient extends WebChromeClient {
    @Override
    public boolean onShowFileChooser(
      WebView webView,
      ValueCallback<Uri[]> filePathCallback,
      FileChooserParams fileChooserParams
    ) {
      mFilePathCallback = filePathCallback;

      // Check if this is a camera capture request
      String[] acceptTypes = fileChooserParams.getAcceptTypes();
      boolean isCameraCapture = false;

      // Check for capture="camera" attribute
      if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN) {
        // Check if it's specifically a camera capture request
        if (acceptTypes != null && acceptTypes.length > 0) {
          for (String acceptType : acceptTypes) {
            // If it's a video capture, it's definitely a camera request
            if (acceptType.contains("video/*")) {
              isCameraCapture = true;
              break;
            }
            // For images, only use camera if explicitly requested
            if (acceptType.contains("image/*") && fileChooserParams.isCaptureEnabled()) {
              isCameraCapture = true;
              break;
            }
          }
        }
      }

      if (isCameraCapture) {
        // Handle camera capture
        try {
          // Create a temporary file to store the camera capture
          tempCameraUri = createTempImageUri();
          if (tempCameraUri != null) {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Check if there's a camera app available
            if (cameraIntent.resolveActivity(activity.getPackageManager()) != null) {
              cameraLauncher.launch(cameraIntent);
            } else {
              Toast.makeText(activity, "No camera app found", Toast.LENGTH_LONG).show();
              mFilePathCallback = null;
              return false;
            }
          } else {
            Toast.makeText(activity, "Cannot create temporary file for camera capture", Toast.LENGTH_LONG).show();
            mFilePathCallback = null;
            return false;
          }
        } catch (Exception e) {
          mFilePathCallback = null;
          Toast.makeText(activity, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
          return false;
        }
      } else {
        // Handle regular file selection
        try {
          Intent intent = fileChooserParams.createIntent();
          if (intent.resolveActivity(activity.getPackageManager()) != null) {
            fileChooserLauncher.launch(intent);
          } else {
            Toast.makeText(activity, "No file chooser app found", Toast.LENGTH_LONG).show();
            mFilePathCallback = null;
            return false;
          }
        } catch (Exception e) {
          mFilePathCallback = null;
          Toast.makeText(activity, "Error opening file chooser: " + e.getMessage(), Toast.LENGTH_LONG).show();
          return false;
        }
      }
      return true;
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
      activity.runOnUiThread(() -> {
        if (request.getResources()[0].equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
          permissionHandler.handleCameraPermissionRequest(request);
        } else if (request.getResources()[0].equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
          permissionHandler.handleMicrophonePermissionRequest(request);
        }
      });
    }
  }
}
