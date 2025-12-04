package ee.forgr.capacitor_inappbrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.getcapacitor.JSObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
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
    private boolean datePickerInjected = false; // Track if we've injected date picker fixes
    private final WebView capacitorWebView;
    private final Map<String, ProxiedRequest> proxiedRequestsHashmap = new HashMap<>();
    // Allow-Download bridge map: URL -> expiryTimestamp (ms)
    private final Map<String, Long> allowDownloadExpiryMap = new HashMap<>();
    private static final long ALLOW_DOWNLOAD_TTL_MS = 5_000L; // 5s TTL
    // track active downloads so we can query status when completion arrives
    private final Map<Long, String> activeDownloadFileNames = new HashMap<>(); // downloadId -> filename

    // Receiver reference so we can unregister later (single instance)
    private android.content.BroadcastReceiver downloadCompleteReceiver = null;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
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

    public WebViewDialog(Context context, int theme, Options options, PermissionHandler permissionHandler, WebView capacitorWebView) {
        // Use Material theme only if materialPicker is enabled
        super(context, options.getMaterialPicker() ? R.style.InAppBrowserMaterialTheme : theme);
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
                if (message == null || message.isEmpty()) {
                    Log.e("InAppBrowser", "Received empty message from WebView");
                    return;
                }

                // Try to parse JSON message to handle structured messages from the injected JS bridge
                try {
                    String trimmed = message.trim();
                    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                        JSONObject obj = new JSONObject(message);
                        JSONObject detail = obj.has("detail") ? obj.optJSONObject("detail") : null;
                        if (detail != null) {
                            String type = detail.optString("type", null);
                            String url = detail.optString("url", null);
                            if ("allowDownload".equals(type) && url != null && !url.isEmpty()) {
                                long expiry = System.currentTimeMillis() + ALLOW_DOWNLOAD_TTL_MS;
                                synchronized (allowDownloadExpiryMap) {
                                    allowDownloadExpiryMap.put(url, expiry);
                                }
                                Log.i("InAppBrowser", "Native: allowDownload registered for url=" + url + " until=" + expiry);
                                // forward to plugin callback as well if set
                                if (_options != null && _options.getCallbacks() != null) {
                                    _options.getCallbacks().javascriptCallback(message);
                                }
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Not JSON or parsing failed — fallthrough to forward raw string
                    Log.d("InAppBrowser", "postMessage: not JSON or parse error: " + e.getMessage());
                }

                // Default: forward raw string to plugin callback if present
                if (_options != null && _options.getCallbacks() != null) {
                    _options.getCallbacks().javascriptCallback(message);
                } else {
                    Log.w("InAppBrowser", "postMessage: no callbacks available to forward message");
                }
            } catch (Exception e) {
                Log.e("InAppBrowser", "Error in postMessage: " + e.getMessage(), e);
            }
        }

        @JavascriptInterface
        public void close() {
            try {
                // close webview safely
                if (activity == null) {
                    Log.e("InAppBrowser", "Cannot close - activity is null");
                    return;
                }

                activity.runOnUiThread(() -> {
                    try {
                        String currentUrl = getUrl();
                        dismiss();

                        if (_options != null && _options.getCallbacks() != null) {
                            _options.getCallbacks().closeEvent(currentUrl);
                        }
                    } catch (Exception e) {
                        Log.e("InAppBrowser", "Error closing WebView: " + e.getMessage());
                    }
                });
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

    public class PrintInterface {

        private Context context;
        private WebView webView;

        public PrintInterface(Context context, WebView webView) {
            this.context = context;
            this.webView = webView;
        }

        @JavascriptInterface
        public void print() {
            // Run on UI thread since printing requires UI operations
            ((Activity) context).runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            // Create a print job from the WebView content
                            PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
                            String jobName = "Document_" + System.currentTimeMillis();

                            PrintDocumentAdapter printAdapter;

                            // For API 21+ (Lollipop and above)
                            printAdapter = webView.createPrintDocumentAdapter(jobName);

                            printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
                        }
                    }
                );
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
        registerDownloadCompleteReceiver();

        // If custom dimensions are set, configure for touch passthrough
        if (_options != null && (_options.getWidth() != null || _options.getHeight() != null)) {
            Window window = getWindow();
            if (window != null) {
                // Make the dialog background transparent
                window.setBackgroundDrawableResource(android.R.color.transparent);
                // Don't dim the background
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                // Allow touches outside to pass through
                window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            }
        }

        // Set fitsSystemWindows only for Android 10 (API 29)
        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
            View coordinator = findViewById(R.id.coordinator_layout);
            if (coordinator != null) coordinator.setFitsSystemWindows(true);
            View appBar = findViewById(R.id.app_bar_layout);
            if (appBar != null) appBar.setFitsSystemWindows(true);
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Make status bar transparent
        if (getWindow() != null) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);

            // Add FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS flag to the window
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            // On Android 30+ clear FLAG_TRANSLUCENT_STATUS flag
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(
            getWindow(),
            getWindow() != null ? getWindow().getDecorView() : null
        );

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

        // Set dimensions if specified, otherwise fullscreen
        applyDimensions();

        this._webView = findViewById(R.id.browser_view);

        // Apply insets to fix edge-to-edge issues on Android 15+
        applyInsets();

        _webView.addJavascriptInterface(new JavaScriptInterface(), "AndroidInterface");
        _webView.addJavascriptInterface(new PreShowScriptInterface(), "PreShowScriptInterface");
        _webView.addJavascriptInterface(new PrintInterface(this._context, _webView), "PrintInterface");
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

        // Ensure cookies accepted and third-party cookies allowed for main webview
        try {
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cm.setAcceptThirdPartyCookies(_webView, true);
            }
        } catch (Throwable t) {
            Log.w("InAppBrowser", "Could not configure CookieManager: " + t.getMessage());
        }

        // Open links in external browser for target="_blank" if preventDeepLink is false
        if (!_options.getPreventDeeplink()) {
            _webView.getSettings().setSupportMultipleWindows(true);
        }

        // Enhanced settings for Google Pay and Payment Request API support (only when enabled)
        if (_options.getEnableGooglePaySupport()) {
            Log.d("InAppBrowser", "Enabling Google Pay support features");
            _webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            _webView.getSettings().setSupportMultipleWindows(true);
            _webView.getSettings().setGeolocationEnabled(true);

            // Ensure secure context for Payment Request API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                _webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            }

            // Enable Payment Request API only if feature is supported
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) {
                WebSettingsCompat.setPaymentRequestEnabled(_webView.getSettings(), true);
                Log.d("InAppBrowser", "Payment Request API enabled");
            } else {
                Log.d("InAppBrowser", "Payment Request API not supported on this device");
            }
        }

        // Set web view background color
        int backgroundColor = _options.getBackgroundColor().equals("white") ? Color.WHITE : Color.BLACK;
        _webView.setBackgroundColor(backgroundColor);

        // Set text zoom if specified in options
        if (_options.getTextZoom() > 0) {
            _webView.getSettings().setTextZoom(_options.getTextZoom());
        }

        _webView.setWebViewClient(new WebViewClient());
        // Attach DownloadListener to main WebView so attachments are captured centrally
        _webView.setDownloadListener(
            new android.webkit.DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                    Log.d(
                        "InAppBrowser",
                        "Main WebView DownloadListener -> url=" +
                        url +
                        " mimeType=" +
                        mimeType +
                        " contentDisposition=" +
                        contentDisposition
                    );
                    try {
                        startDownloadFromUrl(url, userAgent, contentDisposition, mimeType);
                    } catch (Throwable t) {
                        Log.e("InAppBrowser", "Main download start failed: " + t.getMessage(), t);
                    }
                }
            }
        );
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
                    String acceptType;
                    if (
                        fileChooserParams.getAcceptTypes() != null &&
                        fileChooserParams.getAcceptTypes().length > 0 &&
                        !TextUtils.isEmpty(fileChooserParams.getAcceptTypes()[0])
                    ) {
                        acceptType = fileChooserParams.getAcceptTypes()[0];
                    } else {
                        acceptType = "*/*";
                    }

                    // DEBUG: Log details about the file chooser request
                    Log.d("InAppBrowser", "onShowFileChooser called");
                    Log.d("InAppBrowser", "Accept type: " + acceptType);
                    Log.d("InAppBrowser", "Current URL: " + getUrl());
                    Log.d("InAppBrowser", "Original URL: " + (webView.getOriginalUrl() != null ? webView.getOriginalUrl() : "null"));
                    Log.d(
                        "InAppBrowser",
                        "Has camera permission: " +
                        (activity != null &&
                            activity.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED)
                    );

                    // Check if the file chooser is already open
                    if (mFilePathCallback != null) {
                        mFilePathCallback.onReceiveValue(null);
                        mFilePathCallback = null;
                    }

                    mFilePathCallback = filePathCallback;

                    // Direct check for capture attribute in URL (fallback method)
                    boolean isCaptureInUrl;
                    String captureMode;
                    String currentUrl = getUrl();

                    // Look for capture in URL parameters - sometimes the attribute shows up in URL
                    if (currentUrl != null && currentUrl.contains("capture=")) {
                        isCaptureInUrl = true;
                        captureMode = currentUrl.contains("capture=user") ? "user" : "environment";
                        Log.d("InAppBrowser", "Found capture in URL: " + captureMode);
                    } else {
                        captureMode = null;
                        isCaptureInUrl = false;
                    }

                    // For image inputs, try to detect capture attribute using JavaScript
                    if (acceptType.equals("image/*")) {
                        // Check if HTML content contains capture attribute on file inputs (synchronous check)
                        webView.evaluateJavascript("document.querySelector('input[type=\"file\"][capture]') !== null", hasCaptureValue -> {
                            Log.d("InAppBrowser", "Quick capture check: " + hasCaptureValue);
                            if (Boolean.parseBoolean(hasCaptureValue.replace("\"", ""))) {
                                Log.d("InAppBrowser", "Found capture attribute in quick check");
                            }
                        });

                        // Fixed JavaScript with proper error handling
                        String js =
                            """
                            try {
                              (function() {
                                var captureAttr = null;
                                // Check active element first
                                if (document.activeElement &&
                                    document.activeElement.tagName === 'INPUT' &&
                                    document.activeElement.type === 'file') {
                                  if (document.activeElement.hasAttribute('capture')) {
                                    captureAttr = document.activeElement.getAttribute('capture') || 'environment';
                                    return captureAttr;
                                  }
                                }
                                // Try to find any input with capture attribute
                                var inputs = document.querySelectorAll('input[type="file"][capture]');
                                if (inputs && inputs.length > 0) {
                                  captureAttr = inputs[0].getAttribute('capture') || 'environment';
                                  return captureAttr;
                                }
                                // Try to extract from HTML attributes
                                var allInputs = document.getElementsByTagName('input');
                                for (var i = 0; i < allInputs.length; i++) {
                                  var input = allInputs[i];
                                  if (input.type === 'file') {
                                    if (input.hasAttribute('capture')) {
                                      captureAttr = input.getAttribute('capture') || 'environment';
                                      return captureAttr;
                                    }
                                    // Look for the accept attribute containing image/* as this might be a camera input
                                    var acceptAttr = input.getAttribute('accept');
                                    if (acceptAttr && acceptAttr.indexOf('image/*') >= 0) {
                                      console.log('Found input with image/* accept');
                                    }
                                  }
                                }
                                return '';
                              })();
                            } catch(e) {
                              console.error('Capture detection error:', e);
                              return '';
                            }
                            """;

                        webView.evaluateJavascript(js, value -> {
                            Log.d("InAppBrowser", "Capture attribute JS result: " + value);

                            // If we already found capture in URL, use that directly
                            if (isCaptureInUrl) {
                                Log.d("InAppBrowser", "Using capture from URL: " + captureMode);
                                launchCamera(captureMode.equals("user"));
                                return;
                            }

                            // Process JavaScript result
                            if (value != null && value.length() > 2) {
                                // Clean up the value (remove quotes)
                                String captureValue = value.replace("\"", "");
                                Log.d("InAppBrowser", "Found capture attribute: " + captureValue);

                                if (!captureValue.isEmpty()) {
                                    activity.runOnUiThread(() -> launchCamera(captureValue.equals("user")));
                                    return;
                                }
                            }

                            // Look for hints in the web page source
                            Log.d("InAppBrowser", "Looking for camera hints in page content");
                            webView.evaluateJavascript("(function() { return document.documentElement.innerHTML; })()", htmlSource -> {
                                if (htmlSource != null && htmlSource.length() > 10) {
                                    boolean hasCameraOrSelfieKeyword =
                                        htmlSource.contains("capture=") || htmlSource.contains("camera") || htmlSource.contains("selfie");

                                    Log.d("InAppBrowser", "Page contains camera keywords: " + hasCameraOrSelfieKeyword);

                                    if (
                                        hasCameraOrSelfieKeyword &&
                                        currentUrl != null &&
                                        (currentUrl.contains("selfie") || currentUrl.contains("camera") || currentUrl.contains("photo"))
                                    ) {
                                        Log.d("InAppBrowser", "URL suggests camera usage, launching camera");
                                        activity.runOnUiThread(() -> launchCamera(currentUrl.contains("selfie")));
                                        return;
                                    }
                                }

                                // If all detection methods fail, fall back to regular file picker
                                Log.d("InAppBrowser", "No capture attribute detected, using file picker");
                                openFileChooser(
                                    filePathCallback,
                                    acceptType,
                                    fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE
                                );
                            });
                        });
                        return true;
                    }

                    // For non-image types, use regular file picker
                    openFileChooser(filePathCallback, acceptType, fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    return true;
                }

                /**
                 * Launch the camera app for capturing images
                 * @param useFrontCamera true to use front camera, false for back camera
                 */
                private void launchCamera(boolean useFrontCamera) {
                    Log.d("InAppBrowser", "Launching camera, front camera: " + useFrontCamera);

                    // First check if we have camera permission
                    if (activity != null && permissionHandler != null) {
                        // Create a temporary permission request to check camera permission
                        android.webkit.PermissionRequest tempRequest = new android.webkit.PermissionRequest() {
                            @Override
                            public Uri getOrigin() {
                                return Uri.parse("file:///android_asset/");
                            }

                            @Override
                            public String[] getResources() {
                                return new String[] { PermissionRequest.RESOURCE_VIDEO_CAPTURE };
                            }

                            @Override
                            public void grant(String[] resources) {
                                // Permission granted, now launch the camera
                                launchCameraWithPermission(useFrontCamera);
                            }

                            @Override
                            public void deny() {
                                // Permission denied, fall back to file picker
                                Log.e("InAppBrowser", "Camera permission denied, falling back to file picker");
                                fallbackToFilePicker();
                            }
                        };

                        // Request camera permission through the plugin
                        permissionHandler.handleCameraPermissionRequest(tempRequest);
                        return;
                    }

                    // If we can't request permission, try launching directly
                    launchCameraWithPermission(useFrontCamera);
                }

                /**
                 * Launch camera after permission is granted
                 */
                private void launchCameraWithPermission(boolean useFrontCamera) {
                    try {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                            File photoFile = null;
                            try {
                                photoFile = createImageFile();
                            } catch (IOException ex) {
                                Log.e("InAppBrowser", "Error creating image file", ex);
                                fallbackToFilePicker();
                                return;
                            }

                            if (photoFile != null) {
                                tempCameraUri = FileProvider.getUriForFile(
                                    activity,
                                    activity.getPackageName() + ".fileprovider",
                                    photoFile
                                );
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri);

                                if (useFrontCamera) {
                                    takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                                }

                                try {
                                    if (activity instanceof androidx.activity.ComponentActivity) {
                                        androidx.activity.ComponentActivity componentActivity =
                                            (androidx.activity.ComponentActivity) activity;
                                        componentActivity
                                            .getActivityResultRegistry()
                                            .register(
                                                "camera_capture",
                                                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                                                result -> {
                                                    if (result.getResultCode() == Activity.RESULT_OK) {
                                                        if (tempCameraUri != null) {
                                                            mFilePathCallback.onReceiveValue(new Uri[] { tempCameraUri });
                                                        }
                                                    } else {
                                                        mFilePathCallback.onReceiveValue(null);
                                                    }
                                                    mFilePathCallback = null;
                                                    tempCameraUri = null;
                                                }
                                            )
                                            .launch(takePictureIntent);
                                    } else {
                                        // Fallback for non-ComponentActivity
                                        activity.startActivityForResult(takePictureIntent, FILE_CHOOSER_REQUEST_CODE);
                                    }
                                } catch (SecurityException e) {
                                    Log.e("InAppBrowser", "Security exception launching camera: " + e.getMessage(), e);
                                    fallbackToFilePicker();
                                }
                            } else {
                                Log.e("InAppBrowser", "Failed to create photo URI, falling back to file picker");
                                fallbackToFilePicker();
                            }
                        }
                    } catch (Exception e) {
                        Log.e("InAppBrowser", "Camera launch failed: " + e.getMessage(), e);
                        fallbackToFilePicker();
                    }
                }

                /**
                 * Fall back to file picker when camera launch fails
                 */
                private void fallbackToFilePicker() {
                    if (mFilePathCallback != null) {
                        openFileChooser(mFilePathCallback, "image/*", false);
                    }
                }

                // Grant permissions for cam
                @Override
                public void onPermissionRequest(final PermissionRequest request) {
                    Log.i("INAPPBROWSER", "onPermissionRequest " + Arrays.toString(request.getResources()));
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
                    Toast.makeText(WebViewDialog.this.activity, "Permission Denied", Toast.LENGTH_SHORT).show();
                    // Handle the denied permission
                    if (currentPermissionRequest != null) {
                        currentPermissionRequest.deny();
                        currentPermissionRequest = null;
                    }
                }

                // Handle geolocation permission requests
                @Override
                public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                    Log.i("INAPPBROWSER", "onGeolocationPermissionsShowPrompt for origin: " + origin);
                    // Grant geolocation permission automatically for openWebView
                    // This allows websites to access location when opened with openWebView
                    callback.invoke(origin, true, false);
                }

                // This method will be called at page load, a good place to inject customizations
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);

                    // When the page is almost loaded, inject our date picker customization
                    // Only if materialPicker option is enabled
                    if (newProgress > 75 && !datePickerInjected && _options.getMaterialPicker()) {
                        injectDatePickerFixes();
                    }
                }

                // Support for Google Pay and popup windows (critical for OR_BIBED_15 fix)
                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                    Log.d(
                        "InAppBrowser",
                        "onCreateWindow called - isUserGesture: " +
                        isUserGesture +
                        ", GooglePaySupport: " +
                        _options.getEnableGooglePaySupport() +
                        ", preventDeeplink: " +
                        _options.getPreventDeeplink()
                    );

                    // When preventDeeplink is false, open target="_blank" links externally
                    if (!_options.getPreventDeeplink() && isUserGesture) {
                        try {
                            Log.d(
                                "InAppBrowser",
                                "onCreateWindow: creating temporary popup WebView to capture target URL for external open"
                            );

                            // Create a temporary WebView to capture the URL the page is trying to open in the new window
                            final WebView popupWebView = new WebView(activity);
                            popupWebView.getSettings().setJavaScriptEnabled(true);
                            popupWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                            popupWebView.getSettings().setSupportMultipleWindows(true);

                            // Intercept the first load attempt and open it externally.
                            popupWebView.setWebViewClient(
                                new WebViewClient() {
                                    private boolean handled = false;

                                    @Override
                                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                        if (handled) return true;
                                        handled = true;
                                        try {
                                            Log.d("InAppBrowser", "Popup WebView intercepted URL for external open: " + url);
                                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                            browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            _webView.getContext().startActivity(browserIntent);
                                        } catch (ActivityNotFoundException e) {
                                            Log.e("InAppBrowser", "No app found to handle this popup URL: " + e.getMessage(), e);
                                        } catch (Exception ex) {
                                            Log.e("InAppBrowser", "Error launching external intent for popup URL: " + ex.getMessage(), ex);
                                        } finally {
                                            // Cleanup the temporary WebView on the UI thread
                                            try {
                                                activity.runOnUiThread(() -> {
                                                    try {
                                                        if (popupWebView.getParent() != null) {
                                                            ((ViewGroup) popupWebView.getParent()).removeView(popupWebView);
                                                        }
                                                    } catch (Throwable ignore) {}
                                                    try {
                                                        popupWebView.destroy();
                                                    } catch (Throwable ignore) {}
                                                });
                                            } catch (Throwable ignore) {}
                                        }
                                        return true;
                                    }

                                    @Override
                                    public void onPageFinished(WebView view, String url) {
                                        super.onPageFinished(view, url);
                                        // In some cases the URL may arrive here if shouldOverrideUrlLoading was not called;
                                        // as a defensive measure, also open externally from here the first time the page finishes.
                                        if (!handled) {
                                            handled = true;
                                            try {
                                                Log.d("InAppBrowser", "Popup WebView onPageFinished intercept: " + url);
                                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                                browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                _webView.getContext().startActivity(browserIntent);
                                            } catch (Exception e) {
                                                Log.e(
                                                    "InAppBrowser",
                                                    "Error opening popup URL externally from onPageFinished: " + e.getMessage(),
                                                    e
                                                );
                                            } finally {
                                                try {
                                                    activity.runOnUiThread(() -> {
                                                        try {
                                                            if (popupWebView.getParent() != null) {
                                                                ((ViewGroup) popupWebView.getParent()).removeView(popupWebView);
                                                            }
                                                        } catch (Throwable ignore) {}
                                                        try {
                                                            popupWebView.destroy();
                                                        } catch (Throwable ignore) {}
                                                    });
                                                } catch (Throwable ignore) {}
                                            }
                                        }
                                    }
                                }
                            );

                            // Supply the temporary WebView to the transport and return true to indicate we handled the window
                            try {
                                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                                transport.setWebView(popupWebView);
                                resultMsg.sendToTarget();
                                Log.d("InAppBrowser", "Temporary popup WebView installed to capture the target URL");
                                return true;
                            } catch (ClassCastException ccEx) {
                                Log.w("InAppBrowser", "Transport object not WebViewTransport: " + ccEx.getMessage());
                                // Fallback: cannot install transport, return false to allow default behavior
                                return false;
                            }
                        } catch (Exception e) {
                            Log.e("InAppBrowser", "onCreateWindow external-open fallback error: " + e.getMessage(), e);
                            // In case of error, allow default behavior (do not block)
                            return false;
                        }
                    }

                    // Only handle popup windows if Google Pay support is enabled
                    if (_options.getEnableGooglePaySupport() && isUserGesture) {
                        // Create a new WebView for the popup
                        WebView popupWebView = new WebView(activity);
                        popupWebView.getSettings().setJavaScriptEnabled(true);
                        popupWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
                        popupWebView.getSettings().setSupportMultipleWindows(true);

                        // Set WebViewClient to handle URL loading and closing
                        popupWebView.setWebViewClient(
                            new WebViewClient() {
                                @Override
                                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                    Log.d("InAppBrowser", "Popup WebView loading URL: " + url);

                                    // Handle Google Pay result URLs or close conditions
                                    if (url.contains("google.com/pay") || url.contains("close") || url.contains("cancel")) {
                                        Log.d("InAppBrowser", "Closing popup for Google Pay result");
                                        // Notify the parent WebView and close popup
                                        activity.runOnUiThread(() -> {
                                            try {
                                                if (popupWebView.getParent() != null) {
                                                    ((ViewGroup) popupWebView.getParent()).removeView(popupWebView);
                                                }
                                                popupWebView.destroy();
                                            } catch (Exception e) {
                                                Log.e("InAppBrowser", "Error closing popup: " + e.getMessage());
                                            }
                                        });
                                        return true;
                                    }
                                    return false;
                                }
                            }
                        );

                        // Set up the popup WebView transport
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        transport.setWebView(popupWebView);
                        resultMsg.sendToTarget();

                        Log.d("InAppBrowser", "Created popup window for Google Pay");
                        return true;
                    }

                    return false;
                }
            }
        );

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

        _webView.loadUrl(this._options.getUrl(), requestHeaders);
        _webView.requestFocus();
        _webView.requestFocusFromTouch();

        // Inject JavaScript interface early to ensure it's available immediately
        // This complements the injection in onPageFinished and doUpdateVisitedHistory
        _webView.post(() -> {
            if (_webView != null) {
                injectJavaScriptInterface();

                // Inject Google Pay support enhancements if enabled
                if (_options.getEnableGooglePaySupport()) {
                    injectGooglePayPolyfills();
                }

                Log.d("InAppBrowser", "JavaScript interface injected early after URL load");
            }
        });

        setupToolbar();
        setWebViewClient();

        if (!this._options.isPresentAfterPageLoad()) {
            show();
            _options.getPluginCall().resolve();
        }
    }

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
            if (toolbarView != null && toolbarView.getParent() instanceof com.google.android.material.appbar.AppBarLayout appBarLayout) {
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
                    int resourceId = getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
                    if (resourceId > 0) {
                        statusBarHeight = getContext().getResources().getDimensionPixelSize(resourceId);
                    }

                    // Fix status bar view
                    if (statusBarColorView != null) {
                        ViewGroup.LayoutParams params = statusBarColorView.getLayoutParams();
                        params.height = statusBarHeight;
                        statusBarColorView.setLayoutParams(params);
                        statusBarColorView.setBackgroundColor(finalBgColor);
                        statusBarColorView.setVisibility(View.VISIBLE);
                    }

                    // Fix AppBarLayout position
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) appBarLayout.getLayoutParams();
                    params.topMargin = statusBarHeight;
                    appBarLayout.setLayoutParams(params);
                    appBarLayout.setBackgroundColor(finalBgColor);
                    View contentBrowserLayout = findViewById(R.id.content_browser_layout);
                    View parentContainer = findViewById(android.R.id.content);
                    if (contentBrowserLayout == null || parentContainer == null) {
                        Log.w("InAppBrowser", "Required views not found for height calculation");
                        return;
                    }

                    ViewGroup.LayoutParams layoutParams = contentBrowserLayout.getLayoutParams();
                    if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
                        Log.w("InAppBrowser", "Content browser layout does not support margins");
                        return;
                    }
                    ViewGroup.MarginLayoutParams mlpContentBrowserLayout = (ViewGroup.MarginLayoutParams) layoutParams;

                    int parentHeight = parentContainer.getHeight();
                    int appBarHeight = appBarLayout.getHeight(); // can be 0 if not visible with the toolbar type BLANK

                    if (parentHeight <= 0) {
                        Log.w("InAppBrowser", "Parent dimensions not yet available");
                        return;
                    }

                    // Recompute the height of the content browser to be able to set margin bottom as we want to
                    mlpContentBrowserLayout.height = parentHeight - (statusBarHeight + appBarHeight);
                    contentBrowserLayout.setLayoutParams(mlpContentBrowserLayout);
                });
            }
        }

        // Apply system insets to WebView content view (compatible with all Android versions)
        ViewCompat.setOnApplyWindowInsetsListener(_webView, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Boolean keyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());

            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

            // Apply safe margin inset to bottom margin if enabled in options or fallback to 0px
            int navBottom = _options.getEnabledSafeMargin() ? bars.bottom : 0;

            // Apply top inset only if useTopInset option is enabled or fallback to 0px
            int navTop = _options.getUseTopInset() ? bars.top : 0;

            // Avoid double-applying top inset; AppBar/status bar handled above on Android 15+
            mlp.topMargin = isAndroid15Plus ? 0 : navTop;

            // Apply larger of navigation bar or keyboard inset to bottom margin
            mlp.bottomMargin = Math.max(navBottom, ime.bottom);

            mlp.leftMargin = bars.left;
            mlp.rightMargin = bars.right;
            v.setLayoutParams(mlp);

            return WindowInsetsCompat.CONSUMED;
        });

        // Handle window decoration - version-specific handling
        if (getWindow() != null) {
            if (isAndroid15Plus) {
                // Android 15+: Use edge-to-edge with proper insets handling
                getWindow().setDecorFitsSystemWindows(false);
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);

                WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

                // Set status bar text color
                if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
                    try {
                        int backgroundColor = Color.parseColor(_options.getToolbarColor());
                        boolean isDarkBackground = isDarkColor(backgroundColor);
                        controller.setAppearanceLightStatusBars(!isDarkBackground);
                    } catch (IllegalArgumentException e) {
                        // Ignore color parsing errors
                    }
                }
            } else if (Build.VERSION.SDK_INT >= 30) {
                // Android 11-14: Keep navigation bar transparent but respect status bar
                getWindow().setNavigationBarColor(Color.TRANSPARENT);

                WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

                // Set status bar color to match toolbar or use system default
                if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
                    try {
                        int toolbarColor = Color.parseColor(_options.getToolbarColor());
                        getWindow().setStatusBarColor(toolbarColor);
                        boolean isDarkBackground = isDarkColor(toolbarColor);
                        controller.setAppearanceLightStatusBars(!isDarkBackground);
                    } catch (IllegalArgumentException e) {
                        // Follow system theme if color parsing fails
                        boolean isDarkTheme = isDarkThemeEnabled();
                        int statusBarColor = isDarkTheme ? Color.BLACK : Color.WHITE;
                        getWindow().setStatusBarColor(statusBarColor);
                        controller.setAppearanceLightStatusBars(!isDarkTheme);
                    }
                } else {
                    // Follow system theme if no toolbar color provided
                    boolean isDarkTheme = isDarkThemeEnabled();
                    int statusBarColor = isDarkTheme ? Color.BLACK : Color.WHITE;
                    getWindow().setStatusBarColor(statusBarColor);
                    controller.setAppearanceLightStatusBars(!isDarkTheme);
                }
            } else {
                // Pre-Android 11: Use deprecated flags for edge-to-edge navigation bar only
                getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

                getWindow().setNavigationBarColor(Color.TRANSPARENT);

                // Set status bar color to match toolbar
                if (_options.getToolbarColor() != null && !_options.getToolbarColor().isEmpty()) {
                    try {
                        int toolbarColor = Color.parseColor(_options.getToolbarColor());
                        getWindow().setStatusBarColor(toolbarColor);
                    } catch (IllegalArgumentException e) {
                        // Use system default
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
                String script = String.format("window.dispatchEvent(new CustomEvent('messageFromNative', %s));", jsonDetail);
                _webView.post(() -> {
                    if (_webView != null) {
                        _webView.evaluateJavascript(script, null);
                    }
                });
            } catch (Exception e) {
                Log.e("postMessageToJS", "Error sending message to JS: " + e.getMessage());
            }
        }
    }

    private void injectJavaScriptInterface() {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot inject JavaScript interface - WebView is null");
            return;
        }

        try {
            String script =
                """
                (function() {
                  if (window.AndroidInterface) {
                    // Create mobileApp object for backward compatibility
                    if (!window.mobileApp) {
                      window.mobileApp = {
                        postMessage: function(message) {
                          try {
                            var msg = typeof message === 'string' ? message : JSON.stringify(message);
                            window.AndroidInterface.postMessage(msg);
                          } catch(e) {
                            console.error('Error in mobileApp.postMessage:', e);
                          }
                        },
                        close: function() {
                          try {
                            window.AndroidInterface.close();
                          } catch(e) {
                            console.error('Error in mobileApp.close:', e);
                          }
                        }
                      };
                    }
                  }
                  // Override window.print function to use our PrintInterface
                  if (window.PrintInterface) {
                    window.print = function() {
                      try {
                        window.PrintInterface.print();
                      } catch(e) {
                        console.error('Error in print:', e);
                      }
                    };
                  }
                })();
                """;

            _webView.post(() -> {
                if (_webView != null) {
                    try {
                        _webView.evaluateJavascript(script, null);
                    } catch (Exception e) {
                        Log.e("InAppBrowser", "Error injecting JavaScript interface: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error preparing JavaScript interface: " + e.getMessage());
        }
    }

    /**
     * Injects JavaScript polyfills and enhancements for Google Pay support
     * Helps resolve OR_BIBED_15 errors by ensuring proper cross-origin handling
     */
    private void injectGooglePayPolyfills() {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot inject Google Pay polyfills - WebView is null");
            return;
        }

        try {
            String googlePayScript =
                """
                (function() {
                  console.log('[InAppBrowser] Injecting Google Pay support enhancements');

                  // Enhance window.open to work better with Google Pay popups
                  const originalWindowOpen = window.open;
                  window.open = function(url, target, features) {
                    console.log('[InAppBrowser] Enhanced window.open called:', url, target, features);

                    // For Google Pay URLs, ensure they open in a new context
                    if (url && (url.includes('google.com/pay') || url.includes('accounts.google.com'))) {
                      console.log('[InAppBrowser] Google Pay popup detected, using enhanced handling');
                      // Let the native WebView handle this via onCreateWindow
                      return originalWindowOpen.call(window, url, '_blank', features);
                    }

                    return originalWindowOpen.call(window, url, target, features);
                  };

                  // Ensure proper Payment Request API context
                  if (window.PaymentRequest) {
                    console.log('[InAppBrowser] Payment Request API available');

                    // Wrap PaymentRequest constructor to add better error handling
                    const OriginalPaymentRequest = window.PaymentRequest;
                    window.PaymentRequest = function(methodData, details, options) {
                      console.log('[InAppBrowser] PaymentRequest created with enhanced error handling');
                      const request = new OriginalPaymentRequest(methodData, details, options);

                      // Override show method to handle popup blocking issues
                      const originalShow = request.show;
                      request.show = function() {
                        console.log('[InAppBrowser] PaymentRequest.show() called');
                        return originalShow.call(this).catch((error) => {
                          console.error('[InAppBrowser] PaymentRequest error:', error);
                          if (error.name === 'SecurityError' || error.message.includes('popup')) {
                            console.log('[InAppBrowser] Attempting to handle popup blocking issue');
                          }
                          throw error;
                        });
                      };

                      return request;
                    };

                    // Copy static methods
                    Object.setPrototypeOf(window.PaymentRequest, OriginalPaymentRequest);
                    Object.defineProperty(window.PaymentRequest, 'prototype', {
                      value: OriginalPaymentRequest.prototype
                    });
                  }

                  // Add meta tag to ensure proper cross-origin handling if not present
                  if (!document.querySelector('meta[http-equiv="Cross-Origin-Opener-Policy"]')) {
                    const meta = document.createElement('meta');
                    meta.setAttribute('http-equiv', 'Cross-Origin-Opener-Policy');
                    meta.setAttribute('content', 'same-origin-allow-popups');
                    if (document.head) {
                      document.head.appendChild(meta);
                      console.log('[InAppBrowser] Added Cross-Origin-Opener-Policy meta tag');
                    }
                  }

                  console.log('[InAppBrowser] Google Pay support enhancements complete');
                })();
                """;

            _webView.post(() -> {
                if (_webView != null) {
                    try {
                        _webView.evaluateJavascript(googlePayScript, result -> {
                            Log.d("InAppBrowser", "Google Pay polyfills injected successfully");
                        });
                    } catch (Exception e) {
                        Log.e("InAppBrowser", "Error injecting Google Pay polyfills: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error preparing Google Pay polyfills: " + e.getMessage());
        }
    }

    private void injectPreShowScript() {
        //    String script =
        //        "import('https://unpkg.com/darkreader@4.9.89/darkreader.js').then(() => {DarkReader.enable({ brightness: 100, contrast: 90, sepia: 10 });window.PreLoadScriptInterface.finished()})";

        if (preShowSemaphore != null) {
            return;
        }

        String script = String.format(
            """
            async function preShowFunction() {
              %s
            }
            preShowFunction()
              .then(() => window.PreShowScriptInterface.success())
              .catch(err => {
                console.error('Pre show error', err);
                window.PreShowScriptInterface.error(JSON.stringify(err, Object.getOwnPropertyNames(err)));
              });
            """,
            _options.getPreShowScript()
        );

        Log.i("InjectPreShowScript", String.format("PreShowScript script:\n%s", script));

        preShowSemaphore = new Semaphore(0);
        activity.runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    if (_webView != null) {
                        _webView.evaluateJavascript(script, null);
                    } else {
                        // If WebView is null, release semaphore to prevent deadlock
                        if (preShowSemaphore != null) {
                            preShowSemaphore.release();
                        }
                    }
                }
            }
        );

        try {
            if (!preShowSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                Log.e("InjectPreShowScript", "PreShowScript running for over 10 seconds. The plugin will not wait any longer!");
                return;
            }
            if (preShowError != null && !preShowError.isEmpty()) {
                Log.e("InjectPreShowScript", "Error within the user-provided preShowFunction: " + preShowError);
            }
        } catch (InterruptedException e) {
            Log.e("InjectPreShowScript", "Error when calling InjectPreShowScript: " + e.getMessage());
        } finally {
            preShowSemaphore = null;
            preShowError = null;
        }
    }

    private void openFileChooser(ValueCallback<Uri[]> filePathCallback, String acceptType, boolean isMultiple) {
        mFilePathCallback = filePathCallback;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Normalize acceptType
        if (acceptType == null || acceptType.isEmpty() || acceptType.equals("undefined")) {
            acceptType = "*/*";
        }

        // Build mimeTypes array to pass as EXTRA_MIME_TYPES (better chooser behavior)
        String[] mimeTypes = null;
        try {
            if (acceptType.contains(",")) {
                // comma separated list
                String[] parts = acceptType.split(",");
                for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
                mimeTypes = parts;
            } else if (acceptType.startsWith(".")) {
                // single extension like ".pdf"
                String ext = acceptType.replaceFirst("^\\.", "").toLowerCase();
                String mt = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                mimeTypes = new String[] { mt != null ? mt : "*/*" };
            } else if (acceptType.contains("/")) {
                mimeTypes = new String[] { acceptType };
            } else if (acceptType.equals("*/*")) {
                mimeTypes = null; // allow all
            } else {
                // fallback try to interpret as extension list
                mimeTypes = new String[] { acceptType };
            }
        } catch (Exception e) {
            Log.w("InAppBrowser", "Error parsing acceptType: " + e.getMessage());
            mimeTypes = null;
        }

        // If we have explicit mime types, set both type and EXTRA_MIME_TYPES
        if (mimeTypes != null && mimeTypes.length == 1) {
            intent.setType(mimeTypes[0]);
        } else {
            // generic
            intent.setType("*/*");
            if (mimeTypes != null && mimeTypes.length > 0) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
        }

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple);
        Log.d(
            "InAppBrowser",
            "File picker intent type=" +
            intent.getType() +
            " extraMimeTypes=" +
            (mimeTypes != null ? java.util.Arrays.toString(mimeTypes) : "null")
        );

        try {
            if (activity instanceof androidx.activity.ComponentActivity) {
                androidx.activity.ComponentActivity componentActivity = (androidx.activity.ComponentActivity) activity;
                componentActivity
                    .getActivityResultRegistry()
                    .register(
                        "file_chooser",
                        new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            try {
                                if (result.getResultCode() == Activity.RESULT_OK) {
                                    Intent data = result.getData();
                                    if (data != null) {
                                        if (data.getClipData() != null) {
                                            int count = data.getClipData().getItemCount();
                                            Uri[] results = new Uri[count];
                                            for (int i = 0; i < count; i++) {
                                                Uri uri = data.getClipData().getItemAt(i).getUri();
                                                // Detect and log MIME + filename
                                                String detectedMime = detectMimeTypeFromUri(getContext(), uri);
                                                String name = getFileNameFromUri(getContext(), uri);
                                                Log.i(
                                                    "InAppBrowser",
                                                    "Selected file [" + i + "]: uri=" + uri + " name=" + name + " mime=" + detectedMime
                                                );
                                                results[i] = uri;
                                            }
                                            mFilePathCallback.onReceiveValue(results);
                                        } else if (data.getData() != null) {
                                            Uri uri = data.getData();
                                            String detectedMime = detectMimeTypeFromUri(getContext(), uri);
                                            String name = getFileNameFromUri(getContext(), uri);
                                            Log.i("InAppBrowser", "Selected file: uri=" + uri + " name=" + name + " mime=" + detectedMime);
                                            mFilePathCallback.onReceiveValue(new Uri[] { uri });
                                        } else {
                                            mFilePathCallback.onReceiveValue(null);
                                        }
                                    } else {
                                        mFilePathCallback.onReceiveValue(null);
                                    }
                                } else {
                                    mFilePathCallback.onReceiveValue(null);
                                }
                            } catch (Throwable t) {
                                Log.e("InAppBrowser", "file chooser result handling error: " + t.getMessage(), t);
                                if (mFilePathCallback != null) {
                                    mFilePathCallback.onReceiveValue(null);
                                }
                            } finally {
                                mFilePathCallback = null;
                            }
                        }
                    )
                    .launch(Intent.createChooser(intent, getStringResourceOrDefault("select_file", "Select file")));
            } else {
                activity.startActivityForResult(
                    Intent.createChooser(intent, getStringResourceOrDefault("select_file", "Select file")),
                    FILE_CHOOSER_REQUEST_CODE
                );
            }
        } catch (ActivityNotFoundException e) {
            Log.e("InAppBrowser", "No app available for type: " + intent.getType() + ", trying with */*", e);
            intent.setType("*/*");
            try {
                if (activity instanceof androidx.activity.ComponentActivity) {
                    androidx.activity.ComponentActivity componentActivity = (androidx.activity.ComponentActivity) activity;
                    componentActivity
                        .getActivityResultRegistry()
                        .register(
                            "file_chooser",
                            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                            result -> {
                                try {
                                    if (result.getResultCode() == Activity.RESULT_OK) {
                                        Intent data = result.getData();
                                        if (data != null) {
                                            if (data.getClipData() != null) {
                                                int count = data.getClipData().getItemCount();
                                                Uri[] results = new Uri[count];
                                                for (int i = 0; i < count; i++) {
                                                    Uri uri = data.getClipData().getItemAt(i).getUri();
                                                    String detectedMime = detectMimeTypeFromUri(getContext(), uri);
                                                    String name = getFileNameFromUri(getContext(), uri);
                                                    Log.i(
                                                        "InAppBrowser",
                                                        "Selected file [" + i + "]: uri=" + uri + " name=" + name + " mime=" + detectedMime
                                                    );
                                                    results[i] = uri;
                                                }
                                                mFilePathCallback.onReceiveValue(results);
                                            } else if (data.getData() != null) {
                                                Uri uri = data.getData();
                                                String detectedMime = detectMimeTypeFromUri(getContext(), uri);
                                                String name = getFileNameFromUri(getContext(), uri);
                                                Log.i(
                                                    "InAppBrowser",
                                                    "Selected file: uri=" + uri + " name=" + name + " mime=" + detectedMime
                                                );
                                                mFilePathCallback.onReceiveValue(new Uri[] { uri });
                                            } else {
                                                mFilePathCallback.onReceiveValue(null);
                                            }
                                        } else {
                                            mFilePathCallback.onReceiveValue(null);
                                        }
                                    } else {
                                        mFilePathCallback.onReceiveValue(null);
                                    }
                                } catch (Throwable t) {
                                    Log.e("InAppBrowser", "file chooser fallback result handling error: " + t.getMessage(), t);
                                    if (mFilePathCallback != null) mFilePathCallback.onReceiveValue(null);
                                } finally {
                                    mFilePathCallback = null;
                                }
                            }
                        )
                        .launch(Intent.createChooser(intent, "Select File"));
                } else {
                    activity.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST_CODE);
                }
            } catch (ActivityNotFoundException ex) {
                Log.e("InAppBrowser", "No app can handle file picker", ex);
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                    mFilePathCallback = null;
                }
            }
        }
    }

    // Helper: get display name for a content Uri
    private String getFileNameFromUri(Context ctx, Uri uri) {
        if (uri == null || ctx == null) return null;
        String name = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            android.database.Cursor cursor = null;
            try {
                cursor = ctx
                    .getContentResolver()
                    .query(uri, new String[] { android.provider.OpenableColumns.DISPLAY_NAME }, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) name = cursor.getString(idx);
                }
            } catch (Exception e) {
                Log.w("InAppBrowser", "getFileNameFromUri query failed: " + e.getMessage());
            } finally {
                try {
                    if (cursor != null) cursor.close();
                } catch (Exception ignore) {}
            }
        }
        if (name == null) {
            String path = uri.getPath();
            if (path != null) {
                int last = path.lastIndexOf('/');
                if (last != -1) name = path.substring(last + 1);
                else name = path;
            }
        }
        return name;
    }

    // Helper: detect mime type reliably (ContentResolver -> extension -> magic bytes)
    private String detectMimeTypeFromUri(Context ctx, Uri uri) {
        if (uri == null || ctx == null) return null;
        String mime = null;
        try {
            // 1) ContentResolver
            mime = ctx.getContentResolver().getType(uri);
        } catch (Exception ignore) {}

        // 2) Try extension from display name or path
        if ((mime == null || mime.isEmpty())) {
            String name = getFileNameFromUri(ctx, uri);
            if (name != null) {
                int dot = name.lastIndexOf('.');
                if (dot > 0 && dot < name.length() - 1) {
                    String ext = name.substring(dot + 1).toLowerCase();
                    mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                }
            }
        }

        // 3) Inspect magic bytes for common types if still unknown or generic
        if (mime == null || mime.equals("*/*")) {
            java.io.InputStream is = null;
            try {
                is = ctx.getContentResolver().openInputStream(uri);
                if (is != null) {
                    byte[] header = new byte[16];
                    int read = is.read(header);
                    if (read > 0) {
                        // PDF: %PDF-
                        if (read >= 4 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
                            mime = "application/pdf";
                        }
                        // PNG
                        else if (read >= 4 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                            mime = "image/png";
                        }
                        // JPG
                        else if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                            mime = "image/jpeg";
                        }
                        // ZIP / Office OOXML (docx/xlsx/pptx)
                        else if (read >= 2 && header[0] == 'P' && header[1] == 'K') {
                            // Could be zip or office
                            mime = "application/zip";
                        }
                        // plain text heuristic: many bytes printable
                        else {
                            boolean printable = true;
                            int printableCount = 0;
                            int total = Math.min(read, 64);
                            for (int i = 0; i < total; i++) {
                                int b = header[i] & 0xFF;
                                if (b == 9 || b == 10 || b == 13 || (b >= 32 && b <= 126)) printableCount++;
                            }
                            if (total > 0 && printableCount >= (total * 0.9)) {
                                mime = "text/plain";
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("InAppBrowser", "Error probing mime by magic bytes: " + e.getMessage());
            } finally {
                try {
                    if (is != null) is.close();
                } catch (Exception ignore) {}
            }
        }

        if (mime == null) mime = "*/*";
        return mime;
    }

    public void reload() {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot reload - WebView is null");
            return;
        }

        try {
            // First stop any ongoing loading
            _webView.stopLoading();

            // Check if there's a URL to reload
            String currentUrl = getUrl();
            if (currentUrl != null && !currentUrl.equals("about:blank")) {
                // Reload the current page
                _webView.reload();
                Log.d("InAppBrowser", "Reloading page: " + currentUrl);
            } else if (_options != null && _options.getUrl() != null) {
                // If webView URL is null but we have an initial URL, load that
                setUrl(_options.getUrl());
                Log.d("InAppBrowser", "Loading initial URL: " + _options.getUrl());
            } else {
                Log.w("InAppBrowser", "Cannot reload - no valid URL available");
            }
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error during reload: " + e.getMessage());
        }
    }

    public void destroy() {
        if (_webView != null) {
            _webView.destroy();
        }
    }

    public String getUrl() {
        try {
            WebView webView = _webView;
            if (webView != null) {
                String url = webView.getUrl();
                return url != null ? url : "";
            }
        } catch (Exception e) {
            Log.w("InAppBrowser", "Error getting URL: " + e.getMessage());
        }
        return "";
    }

    public void executeScript(String script) {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot execute script - WebView is null");
            return;
        }

        if (script == null || script.trim().isEmpty()) {
            Log.w("InAppBrowser", "Cannot execute empty script");
            return;
        }

        try {
            _webView.evaluateJavascript(script, null);
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error executing script: " + e.getMessage());
        }
    }

    public void setUrl(String url) {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot set URL - WebView is null");
            return;
        }

        if (url == null || url.trim().isEmpty()) {
            Log.w("InAppBrowser", "Cannot set empty URL");
            return;
        }

        try {
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
            _webView.loadUrl(url, requestHeaders);
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error setting URL: " + e.getMessage());
        }
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
                                        String currentUrl = getUrl();
                                        dismiss();
                                        if (_options != null && _options.getCallbacks() != null) {
                                            // Notify that confirm was clicked
                                            _options.getCallbacks().confirmBtnClicked(currentUrl);
                                            _options.getCallbacks().closeEvent(currentUrl);
                                        }
                                    }
                                }
                            )
                            .setNegativeButton(_options.getCloseModalCancel(), null)
                            .show();
                    } else {
                        String currentUrl = getUrl();
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
                            String currentUrl = getUrl();
                            if (currentUrl != null) {
                                // Reload the current page
                                _webView.reload();
                                Log.d("InAppBrowser", "Reloading page: " + currentUrl);
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
            ImageButton buttonNearDoneView = _toolbar.findViewById(R.id.buttonNearDone);
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
                        int resourceId = _context.getResources().getIdentifier(iconName, "drawable", _context.getPackageName());

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
                buttonNearDoneView.setOnClickListener(view -> _options.getCallbacks().buttonNearDoneClicked());
            } else {
                ImageButton buttonNearDoneView = _toolbar.findViewById(R.id.buttonNearDone);
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
                        .setPositiveButton(shareDisclaimer.getString("confirmBtn", "Confirm"), (dialog, which) -> {
                            // Notify that confirm was clicked
                            String currentUrl = getUrl();
                            _options.getCallbacks().confirmBtnClicked(currentUrl);
                            shareUrl();
                        })
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
        Log.i("InAppBrowserProxy", String.format("handleProxyResultError: %s, ok: %s id: %s", result, false, id));
        ProxiedRequest proxiedRequest = proxiedRequestsHashmap.get(id);
        if (proxiedRequest == null) {
            Log.e("InAppBrowserProxy", "proxiedRequest is null");
            return;
        }
        proxiedRequestsHashmap.remove(id);
        proxiedRequest.semaphore.release();
    }

    public void handleProxyResultOk(JSONObject result, String id) {
        Log.i("InAppBrowserProxy", String.format("handleProxyResultOk: %s, ok: %s, id: %s", result, true, id));
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
            Log.e("InAppBrowserProxy", String.format("Status code %s outside of the allowed range", code));
            return;
        }

        WebResourceResponse webResourceResponse = new WebResourceResponse(
            contentType,
            "utf-8",
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
        );

        webResourceResponse.setStatusCodeAndReasonPhrase(code, getReasonPhrase(code));
        proxiedRequest.response = webResourceResponse;
        proxiedRequest.semaphore.release();
    }

    private void setWebViewClient() {
        _webView.setWebViewClient(
            new WebViewClient() {
                /**
                 * Checks whether the given URL is authorized based on the provided list of authorized links.
                 * <p>
                 * For http(s) URLs, compares only the host (ignoring "www." prefix and case).
                 * Each entry in authorizedLinks should be a base URL (e.g., "https://example.com").
                 * If the host of the input URL matches (case-insensitive) the host of any authorized link, returns true.
                 * <p>
                 * This method is intended to limit which external links can be handled as authorized app links.
                 *
                 * @param url             The URL to check. Can be any valid absolute URL.
                 * @param authorizedLinks List of authorized base URLs (e.g., "https://mydomain.com", "myapp://").
                 * @return true if the URL is authorized (host matches one of the authorizedLinks); false otherwise.
                 */
                private boolean isUrlAuthorized(String url, List<String> authorizedLinks) {
                    if (authorizedLinks == null || authorizedLinks.isEmpty() || url == null) {
                        return false;
                    }
                    try {
                        URI uri = new URI(url);
                        String urlHost = uri.getHost();
                        if (urlHost == null) return false;
                        if (urlHost.startsWith("www.")) urlHost = urlHost.substring(4);
                        for (String authorized : authorizedLinks) {
                            URI authUri = new URI(authorized);
                            String authHost = authUri.getHost();
                            if (authHost == null) continue;
                            if (authHost.startsWith("www.")) authHost = authHost.substring(4);
                            if (urlHost.equalsIgnoreCase(authHost)) {
                                return true;
                            }
                        }
                    } catch (URISyntaxException e) {
                        Log.e("InAppBrowser", "Invalid URI in isUrlAuthorized: " + url, e);
                    }
                    return false;
                }

                /**
                 * Checks if a host should be blocked based on the configured blocked hosts patterns
                 * @param url The URL to check
                 * @param blockedHosts The list of blocked hosts patterns
                 * @return true if the host should be blocked, false otherwise
                 */
                private boolean shouldBlockHost(String url, List<String> blockedHosts) {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost();

                    if (host == null || host.isEmpty()) {
                        return false;
                    }

                    if (blockedHosts == null || blockedHosts.isEmpty()) {
                        return false;
                    }

                    String normalizedHost = host.toLowerCase();

                    for (String blockPattern : blockedHosts) {
                        if (blockPattern != null && matchesBlockPattern(normalizedHost, blockPattern.toLowerCase())) {
                            Log.d("InAppBrowser", "Blocked host detected: " + host);
                            return true;
                        }
                    }

                    return false;
                }

                /**
                 * Matches a host against a blocking pattern (supports wildcards)
                 * @param host The normalized host to check
                 * @param pattern The normalized blocking pattern
                 * @return true if the host matches the pattern
                 */
                private boolean matchesBlockPattern(String host, String pattern) {
                    if (pattern == null || pattern.isEmpty()) {
                        return false;
                    }

                    // Exact match - fastest check first
                    if (host.equals(pattern)) {
                        return true;
                    }

                    // No wildcards - already checked exact match above
                    if (!pattern.contains("*")) {
                        return false;
                    }

                    // Handle wildcard patterns
                    if (pattern.startsWith("*.")) {
                        return matchesWildcardDomain(host, pattern);
                    } else if (pattern.contains("*")) {
                        return matchesRegexPattern(host, pattern);
                    }

                    return false;
                }

                /**
                 * Handles simple subdomain wildcard patterns like "*.example.com"
                 * @param host The host to check
                 * @param pattern The wildcard pattern starting with "*."
                 * @return true if the host matches the wildcard domain
                 */
                private boolean matchesWildcardDomain(String host, String pattern) {
                    String domain = pattern.substring(2); // Remove "*."

                    if (domain.isEmpty()) {
                        return false;
                    }

                    // Match exact domain or any subdomain
                    return host.equals(domain) || host.endsWith("." + domain);
                }

                /**
                 * Handles complex regex patterns with multiple wildcards
                 * @param host The host to check
                 * @param pattern The pattern with wildcards to convert to regex
                 * @return true if the host matches the regex pattern
                 */
                private boolean matchesRegexPattern(String host, String pattern) {
                    try {
                        // Escape special regex characters except *
                        String escapedPattern = pattern
                            .replace("\\", "\\\\") // Must escape backslashes first
                            .replace(".", "\\.")
                            .replace("+", "\\+")
                            .replace("?", "\\?")
                            .replace("^", "\\^")
                            .replace("$", "\\$")
                            .replace("(", "\\(")
                            .replace(")", "\\)")
                            .replace("[", "\\[")
                            .replace("]", "\\]")
                            .replace("{", "\\{")
                            .replace("}", "\\}")
                            .replace("|", "\\|");

                        // Convert wildcards to regex
                        String regexPattern = "^" + escapedPattern.replace("*", ".*") + "$";

                        return Pattern.matches(regexPattern, host);
                    } catch (Exception e) {
                        Log.e("InAppBrowser", "Invalid regex pattern '" + pattern + "': " + e.getMessage());
                        return false;
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (view == null || _webView == null) {
                        return false;
                    }
                    Context context = view.getContext();
                    String url = request.getUrl().toString();

                    try {
                        synchronized (allowDownloadExpiryMap) {
                            Long expiry = allowDownloadExpiryMap.get(url);
                            if (expiry != null) {
                                if (System.currentTimeMillis() <= expiry) {
                                    Log.i("InAppBrowser", "Allowed download URL matched (bridge) -> starting in-app download: " + url);
                                    // Start native download; prevent normal navigation to avoid duplicate handling
                                    startDownloadFromUrl(url, null, null, null);
                                    allowDownloadExpiryMap.remove(url);
                                    return true;
                                } else {
                                    // expired
                                    allowDownloadExpiryMap.remove(url);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w("InAppBrowser", "Error checking allowDownload map: " + e.getMessage());
                    }

                    Log.d("InAppBrowser", "shouldOverrideUrlLoading: " + url);

                    boolean isNotHttpOrHttps = !url.startsWith("https://") && !url.startsWith("http://");

                    // If preventDeeplink is true, don't handle any non-http(s) URLs
                    if (_options.getPreventDeeplink()) {
                        Log.d("InAppBrowser", "preventDeeplink is true");
                        if (isNotHttpOrHttps) {
                            return true;
                        }
                    }

                    // Handle authorized app links
                    List<String> authorizedLinks = _options.getAuthorizedAppLinks();
                    boolean urlAuthorized = isUrlAuthorized(url, authorizedLinks);

                    Log.d("InAppBrowser", "authorizedLinks: " + authorizedLinks);
                    Log.d("InAppBrowser", "urlAuthorized: " + urlAuthorized);

                    if (urlAuthorized) {
                        try {
                            Log.d("InAppBrowser", "Launching intent for authorized link: " + url);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                            Log.i("InAppBrowser", "Intent started for authorized link: " + url);
                            return true;
                        } catch (ActivityNotFoundException e) {
                            Log.e("InAppBrowser", "No app found to handle this authorized link", e);
                            return false;
                        }
                    }

                    if (isNotHttpOrHttps) {
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
                            Log.w("InAppBrowser", "No handler for external URL: " + url, e);
                            // Notify that a page load error occurred
                            if (_options.getCallbacks() != null && request.isForMainFrame()) {
                                _options.getCallbacks().pageLoadError();
                            }
                            return true; // prevent WebView from attempting to load the custom scheme
                        }
                    }

                    // Check for blocked hosts (main-frame only) using the extracted function
                    List<String> blockedHosts = _options.getBlockedHosts();
                    if (blockedHosts != null && !blockedHosts.isEmpty() && request.isForMainFrame()) {
                        Log.d("InAppBrowser", "Checking for blocked hosts (on main frame)");
                        if (shouldBlockHost(url, blockedHosts)) {
                            // Make sure to notify that a URL has changed even when it was blocked
                            if (_options.getCallbacks() != null) {
                                _options.getCallbacks().urlChangeEvent(url);
                            }
                            Log.d("InAppBrowser", "Navigation blocked for URL: " + url);
                            return true; // Block the navigation
                        }
                    }

                    return false;
                }

                @Override
                public void onReceivedClientCertRequest(WebView view, android.webkit.ClientCertRequest request) {
                    Log.i("InAppBrowser", "onReceivedClientCertRequest CALLED");

                    if (request == null) {
                        Log.e("InAppBrowser", "ClientCertRequest is null");
                        return;
                    }

                    if (activity == null) {
                        Log.e("InAppBrowser", "Activity is null, canceling request");
                        try {
                            request.cancel();
                        } catch (Exception e) {
                            Log.e("InAppBrowser", "Error canceling request: " + e.getMessage());
                        }
                        return;
                    }

                    try {
                        Log.i("InAppBrowser", "Host: " + request.getHost());
                        Log.i("InAppBrowser", "Port: " + request.getPort());
                        Log.i("InAppBrowser", "Principals: " + java.util.Arrays.toString(request.getPrincipals()));
                        Log.i("InAppBrowser", "KeyTypes: " + java.util.Arrays.toString(request.getKeyTypes()));

                        KeyChain.choosePrivateKeyAlias(
                            activity,
                            new KeyChainAliasCallback() {
                                @Override
                                public void alias(String alias) {
                                    if (alias != null) {
                                        try {
                                            PrivateKey privateKey = KeyChain.getPrivateKey(activity, alias);
                                            X509Certificate[] certChain = KeyChain.getCertificateChain(activity, alias);
                                            request.proceed(privateKey, certChain);
                                            Log.i("InAppBrowser", "Selected certificate: " + alias);
                                        } catch (Exception e) {
                                            try {
                                                request.cancel();
                                            } catch (Exception cancelEx) {
                                                Log.e("InAppBrowser", "Error canceling request: " + cancelEx.getMessage());
                                            }
                                            Log.e("InAppBrowser", "Error selecting certificate: " + e.getMessage());
                                        }
                                    } else {
                                        try {
                                            request.cancel();
                                        } catch (Exception e) {
                                            Log.e("InAppBrowser", "Error canceling request: " + e.getMessage());
                                        }
                                        Log.i("InAppBrowser", "No certificate found");
                                    }
                                }
                            },
                            null, // keyTypes
                            null, // issuers
                            request.getHost(),
                            request.getPort(),
                            null // alias (null = system asks user to choose)
                        );
                    } catch (Exception e) {
                        Log.e("InAppBrowser", "Error in onReceivedClientCertRequest: " + e.getMessage());
                        try {
                            request.cancel();
                        } catch (Exception cancelEx) {
                            Log.e("InAppBrowser", "Error canceling request after exception: " + cancelEx.getMessage());
                        }
                    }
                }

                private String randomRequestId() {
                    return UUID.randomUUID().toString();
                }

                private String toBase64(String raw) {
                    String s = Base64.encodeToString(raw.getBytes(), Base64.NO_WRAP);
                    // Remove any padding '=' characters
                    while (s.endsWith("=")) {
                        s = s.substring(0, s.length() - 1);
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
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (view == null || _webView == null) {
                        return null;
                    }
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

                    Log.i("InAppBrowserProxy", String.format("Proxying request: %s", request.getUrl().toString()));

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
                                Map<String, String> requestHeaders = request.getRequestHeaders();
                                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                                    headers.append(
                                        String.format("h[atob('%s')]=atob('%s');", toBase64(header.getKey()), toBase64(header.getValue()))
                                    );
                                }
                                String jsTemplate =
                                    """
                                    try {
                                      function getHeaders() {
                                        const h = {};
                                        %s
                                        return h;
                                      }
                                      window.InAppBrowserProxyRequest(new Request(atob('%s'), {
                                        headers: getHeaders(),
                                        method: '%s'
                                      })).then(async (res) => {
                                        Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({
                                          ok: true,
                                          result: (!!res ? {
                                            headers: Object.fromEntries(res.headers.entries()),
                                            code: res.status,
                                            body: (await res.text())
                                          } : null),
                                          id: '%s'
                                        });
                                      }).catch((e) => {
                                        Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({
                                          ok: false,
                                          result: e.toString(),
                                          id: '%s'
                                        });
                                      });
                                    } catch (e) {
                                      Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({
                                        ok: false,
                                        result: e.toString(),
                                        id: '%s'
                                      });
                                    }
                                    """;
                                String s = String.format(
                                    jsTemplate,
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
                public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                    if (view == null || _webView == null) {
                        if (handler != null) {
                            handler.cancel();
                        }
                        return;
                    }
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
                            if (sourcePort == -1 && Objects.equals(sourceProtocol, "https")) sourcePort = 443;
                            else if (sourcePort == -1 && Objects.equals(sourceProtocol, "http")) sourcePort = 80;
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
                            else if (port == -1 && Objects.equals(protocol, "http")) port = 80;
                        } catch (URISyntaxException e) {
                            e.printStackTrace();
                        }

                        if (Objects.equals(sourceHost, host) && Objects.equals(sourceProtocol, protocol) && sourcePort == port) {
                            final String username = Objects.requireNonNull(credentials.getString("username"));
                            final String password = Objects.requireNonNull(credentials.getString("password"));
                            handler.proceed(username, password);
                            return;
                        }
                    }

                    super.onReceivedHttpAuthRequest(view, handler, host, realm);
                }

                @Override
                public void onLoadResource(WebView view, String url) {
                    if (view == null || _webView == null) {
                        return;
                    }
                    super.onLoadResource(view, url);
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    if (view == null || _webView == null) {
                        return;
                    }
                    try {
                        URI uri = new URI(url);
                        if (TextUtils.isEmpty(_options.getTitle())) {
                            setTitle(uri.getHost());
                        }
                    } catch (URISyntaxException e) {
                        // Do nothing
                    }
                }

                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    if (view == null || _webView == null) {
                        return;
                    }
                    if (!isReload) {
                        _options.getCallbacks().urlChangeEvent(url);
                    }
                    super.doUpdateVisitedHistory(view, url, isReload);
                    injectJavaScriptInterface();

                    // Inject Google Pay polyfills if enabled
                    if (_options.getEnableGooglePaySupport()) {
                        injectGooglePayPolyfills();
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (view == null || _webView == null) {
                        return;
                    }
                    if (!isInitialized) {
                        isInitialized = true;
                        _webView.clearHistory();
                        if (_options.isPresentAfterPageLoad()) {
                            boolean usePreShowScript = _options.getPreShowScript() != null && !_options.getPreShowScript().isEmpty();
                            if (!usePreShowScript) {
                                show();
                                _options.getPluginCall().resolve();
                            } else {
                                executorService.execute(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (_options.getPreShowScript() != null && !_options.getPreShowScript().isEmpty()) {
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
                    } else if (_options.getPreShowScript() != null && !_options.getPreShowScript().isEmpty()) {
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
                    if (_webView != null && _webView.canGoBack()) {
                        backButton.setImageResource(R.drawable.arrow_back_enabled);
                        backButton.setEnabled(true);
                        backButton.setColorFilter(iconColor);
                        backButton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (_webView != null && _webView.canGoBack()) {
                                        _webView.goBack();
                                    }
                                }
                            }
                        );
                    } else {
                        backButton.setImageResource(R.drawable.arrow_back_disabled);
                        backButton.setEnabled(false);
                        backButton.setColorFilter(Color.argb(128, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
                    }

                    ImageButton forwardButton = _toolbar.findViewById(R.id.forwardButton);
                    if (_webView != null && _webView.canGoForward()) {
                        forwardButton.setImageResource(R.drawable.arrow_forward_enabled);
                        forwardButton.setEnabled(true);
                        forwardButton.setColorFilter(iconColor);
                        forwardButton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (_webView != null && _webView.canGoForward()) {
                                        _webView.goForward();
                                    }
                                }
                            }
                        );
                    } else {
                        forwardButton.setImageResource(R.drawable.arrow_forward_disabled);
                        forwardButton.setEnabled(false);
                        forwardButton.setColorFilter(Color.argb(128, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
                    }

                    _options.getCallbacks().pageLoaded();
                    injectJavaScriptInterface();

                    // Inject Google Pay polyfills if enabled
                    if (_options.getEnableGooglePaySupport()) {
                        injectGooglePayPolyfills();
                    }
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    if (view == null || _webView == null) {
                        return;
                    }
                    _options.getCallbacks().pageLoadError();
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    if (view == null || _webView == null) {
                        if (handler != null) {
                            handler.cancel();
                        }
                        return;
                    }
                    boolean ignoreSSLUntrustedError = _options.ignoreUntrustedSSLError();
                    if (ignoreSSLUntrustedError && error.getPrimaryError() == SslError.SSL_UNTRUSTED) handler.proceed();
                    else {
                        super.onReceivedSslError(view, handler, error);
                    }
                }
            }
        );
    }

    /**
     * Navigates back in the WebView history if possible
     * @return true if navigation was successful, false otherwise
     */
    public boolean goBack() {
        if (_webView != null && _webView.canGoBack()) {
            _webView.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (
            _webView != null &&
            _webView.canGoBack() &&
            (TextUtils.equals(_options.getToolbarType(), "navigation") || _options.getActiveNativeNavigationForWebview())
        ) {
            _webView.goBack();
        } else if (!_options.getDisableGoBackOnNativeApplication()) {
            String currentUrl = getUrl();
            _options.getCallbacks().closeEvent(currentUrl);
            if (_webView != null) {
                _webView.destroy();
            }
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Forward volume key events to the MainActivity
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return activity.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Forward volume key events to the MainActivity
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return activity.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
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
            case (300) -> "Multiple Choices";
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
        unregisterDownloadCompleteReceiver();
        // First, stop any ongoing operations and disable further interactions
        if (_webView != null) {
            try {
                // Stop loading first to prevent any ongoing operations
                _webView.stopLoading();

                // Clear any pending callbacks to prevent memory leaks
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                    mFilePathCallback = null;
                }
                tempCameraUri = null;

                // Clear file inputs for security/privacy before destroying WebView
                try {
                    String clearInputsScript =
                        """
                        (function() {
                          try {
                            var inputs = document.querySelectorAll('input[type="file"]');
                            for (var i = 0; i < inputs.length; i++) {
                              inputs[i].value = '';
                            }
                            return true;
                          } catch(e) {
                            console.log('Error clearing file inputs:', e);
                            return false;
                          }
                        })();
                        """;
                    _webView.evaluateJavascript(clearInputsScript, null);
                } catch (Exception e) {
                    Log.w("InAppBrowser", "Could not clear file inputs (WebView may be in invalid state): " + e.getMessage());
                }

                // Remove JavaScript interfaces before destroying
                _webView.removeJavascriptInterface("AndroidInterface");
                _webView.removeJavascriptInterface("PreShowScriptInterface");
                _webView.removeJavascriptInterface("PrintInterface");

                // Load blank page and cleanup
                _webView.loadUrl("about:blank");
                _webView.onPause();
                _webView.removeAllViews();
                _webView.destroy();
                _webView = null;
            } catch (Exception e) {
                Log.e("InAppBrowser", "Error during WebView cleanup: " + e.getMessage());
                // Force set to null even if cleanup failed
                _webView = null;
            }
        }

        activeDownloadFileNames.clear();
        allowDownloadExpiryMap.clear();

        // Shutdown executor service safely
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            } catch (Exception e) {
                Log.e("InAppBrowser", "Error shutting down executor: " + e.getMessage());
            }
        }

        try {
            if (downloadCompleteReceiver != null) {
                try {
                    getContext().unregisterReceiver(downloadCompleteReceiver);
                } catch (Exception ignore) {}
                downloadCompleteReceiver = null;
            }
        } catch (Exception e) {
            Log.w("InAppBrowser", "Error unregistering download receiver: " + e.getMessage());
        }
        synchronized (activeDownloadFileNames) {
            activeDownloadFileNames.clear();
        }

        try {
            super.dismiss();
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error dismissing dialog: " + e.getMessage());
        }

        // Clear any remaining proxied requests
        synchronized (proxiedRequestsHashmap) {
            proxiedRequestsHashmap.clear();
        }

        try {
            super.dismiss();
        } catch (Exception e) {
            Log.e("InAppBrowser", "Error dismissing dialog: " + e.getMessage());
        }
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

    private void injectDatePickerFixes() {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot inject date picker fixes - WebView is null");
            return;
        }

        if (datePickerInjected) {
            return;
        }

        datePickerInjected = true;

        // This script adds minimal fixes for date inputs to use Material Design
        String script =
            """
            (function() {
              try {
                // Find all date inputs
                const dateInputs = document.querySelectorAll('input[type="date"]');
                dateInputs.forEach(input => {
                  // Ensure change events propagate correctly
                  let lastValue = input.value;
                  input.addEventListener('change', () => {
                    try {
                      if (input.value !== lastValue) {
                        lastValue = input.value;
                        // Dispatch an input event to ensure frameworks detect the change
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                      }
                    } catch(e) {
                      console.error('Error in date input change handler:', e);
                    }
                  });
                });
              } catch(e) {
                console.error('Error applying date picker fixes:', e);
              }
            })();""";

        // Execute the script in the WebView
        _webView.post(() -> {
            if (_webView != null) {
                try {
                    _webView.evaluateJavascript(script, null);
                    Log.d("InAppBrowser", "Applied minimal date picker fixes");
                } catch (Exception e) {
                    Log.e("InAppBrowser", "Error injecting date picker fixes: " + e.getMessage());
                }
            }
        });
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
                return androidx.core.content.FileProvider.getUriForFile(_context, _context.getPackageName() + ".fileprovider", tempFile);
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
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName/* prefix */, ".jpg"/* suffix */, storageDir/* directory */);
        return image;
    }

    private String downloadReasonToString(int reason) {
        return switch (reason) {
            case android.app.DownloadManager.ERROR_CANNOT_RESUME -> "ERROR_CANNOT_RESUME";
            case android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "ERROR_DEVICE_NOT_FOUND";
            case android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "ERROR_FILE_ALREADY_EXISTS";
            case android.app.DownloadManager.ERROR_FILE_ERROR -> "ERROR_FILE_ERROR";
            case android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> "ERROR_HTTP_DATA_ERROR";
            case android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "ERROR_INSUFFICIENT_SPACE";
            case android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "ERROR_TOO_MANY_REDIRECTS";
            case android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "ERROR_UNHANDLED_HTTP_CODE";
            case android.app.DownloadManager.ERROR_UNKNOWN -> "ERROR_UNKNOWN";
            default -> String.valueOf(reason);
        };
    }

    /**
     * Start a native download via DownloadManager, preserving cookies and UA when possible.
     * Improved filename extraction from Content-Disposition and mimeType.
     */
    private void startDownloadFromUrl(final String url, final String userAgent, final String contentDisposition, final String mimeType) {
        if (url == null || url.isEmpty()) {
            Log.w("InAppBrowser", "startDownloadFromUrl: url is empty");
            return;
        }

        final String ua = (userAgent != null && !userAgent.isEmpty())
            ? userAgent
            : (_webView != null ? _webView.getSettings().getUserAgentString() : System.getProperty("http.agent"));

        final String cookies = android.webkit.CookieManager.getInstance().getCookie(url);

        // Try to extract filename from contentDisposition, fallback to URLUtil
        String filename = null;
        try {
            if (contentDisposition != null) {
                java.util.regex.Matcher mStar = java.util.regex.Pattern.compile(
                    "filename\\*=(?:UTF-8'')?([^;\\r\\n]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                ).matcher(contentDisposition);
                if (mStar.find()) {
                    try {
                        String raw = mStar.group(1).trim();
                        filename = java.net.URLDecoder.decode(raw.replaceAll("^\"|\"$", ""), "UTF-8");
                    } catch (Exception ignore) {}
                }
                if (filename == null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "filename=\"?([^\";]+)\"?",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                    ).matcher(contentDisposition);
                    if (m.find()) {
                        filename = m.group(1).trim();
                    }
                }
            }
        } catch (Throwable t) {
            Log.w("InAppBrowser", "Error parsing contentDisposition: " + t.getMessage());
        }

        if (filename == null || filename.isEmpty()) {
            try {
                filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            } catch (Throwable t) {
                filename = "file.bin";
            }
        }

        // sanitize and ensure ext
        String namePart = filename;
        String extPart = "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            namePart = filename.substring(0, lastDot);
            extPart = filename.substring(lastDot + 1);
        } else {
            try {
                if (mimeType != null && !mimeType.isEmpty()) {
                    String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if (ext != null && !ext.isEmpty()) extPart = ext;
                }
            } catch (Throwable ignore) {}
        }

        try {
            namePart = namePart.replaceAll("[\\\\/:*?\"<>|]+", "_");
            namePart = namePart.replaceAll("\\s+", "_");
            namePart = namePart.replaceAll("[\\p{Cntrl}]", "");
            if (namePart.length() > 120) namePart = namePart.substring(0, 120);
            extPart = extPart.replaceAll("[^A-Za-z0-9]", "");
            if (extPart.length() > 10) extPart = extPart.substring(0, 10);
        } catch (Throwable t) {
            Log.w("InAppBrowser", "Error sanitizing filename parts: " + t.getMessage());
        }

        final String finalFileName = (extPart != null && !extPart.isEmpty()) ? (namePart + "." + extPart) : (namePart + ".bin");

        Log.d(
            "InAppBrowser",
            "startDownloadFromUrl -> url=" +
            url +
            " filename=" +
            finalFileName +
            " mimeType=" +
            mimeType +
            " contentDisposition=" +
            contentDisposition
        );

        activity.runOnUiThread(() -> {
            try {
                Uri downloadUri;
                try {
                    downloadUri = Uri.parse(url);
                } catch (Exception e) {
                    String safeUrl = url.replace(" ", "%20");
                    downloadUri = Uri.parse(safeUrl);
                }

                android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(downloadUri);
                if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
                if (ua != null && !ua.isEmpty()) request.addRequestHeader("User-Agent", ua);
                if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);

                request.setTitle(finalFileName);
                request.setDescription("Downloading file...");
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                // Set destination in a way that avoids WRITE_EXTERNAL_STORAGE requirement when possible
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // On Android 10+ we rely on system handling (DM/MediaStore); keep public Downloads target
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName);
                } else {
                    // Pre-Q devices: if we have WRITE_EXTERNAL_STORAGE permission, write to public Downloads
                    if (
                        activity != null &&
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName);
                    } else {
                        // No permission -> write into app-specific external files dir to avoid permission requirement
                        // This will place the file under Android/data/<package>/files/Download/
                        request.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_DOWNLOADS, finalFileName);
                        Log.d("InAppBrowser", "No WRITE_EXTERNAL_STORAGE permission - using app-specific external files dir for download");
                    }
                }

                // Allow downloads over metered networks and roaming to avoid "queued" policies
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);

                android.app.DownloadManager dm = (android.app.DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    long id = dm.enqueue(request);
                    synchronized (activeDownloadFileNames) {
                        activeDownloadFileNames.put(id, finalFileName);
                    }
                    Log.d("InAppBrowser", "DownloadManager enqueued id=" + id + " file=" + finalFileName + " url=" + url);

                    // Post-enqueue check: wait 5s, then check status; if still PENDING, wait another 5s; if still not running/successful -> fallback
                    // IMPORTANT: capture UA and cookies here so the background thread doesn't access WebView methods.
                    final String capturedUA = ua;
                    final String capturedCookies = cookies;
                    executorService.execute(() -> {
                        try {
                            // Primera espera
                            Thread.sleep(5000);

                            android.app.DownloadManager.Query q1 = new android.app.DownloadManager.Query();
                            q1.setFilterById(id);
                            android.database.Cursor cursor1 = dm.query(q1);
                            int status1 = -1;
                            int reason1 = -1;
                            String sourceUri1 = null;
                            if (cursor1 != null) {
                                try {
                                    if (cursor1.moveToFirst()) {
                                        int statusIdx = cursor1.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                                        int reasonIdx = cursor1.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                                        int uriIdx = cursor1.getColumnIndex(android.app.DownloadManager.COLUMN_URI);
                                        status1 = statusIdx != -1 ? cursor1.getInt(statusIdx) : -1;
                                        reason1 = reasonIdx != -1 ? cursor1.getInt(reasonIdx) : -1;
                                        sourceUri1 = (uriIdx != -1) ? cursor1.getString(uriIdx) : null;
                                    }
                                } finally {
                                    try {
                                        cursor1.close();
                                    } catch (Exception ignore) {}
                                }
                            }
                            Log.d("InAppBrowser", "Post-enqueue check1 id=" + id + " status=" + status1 + " reason=" + reason1);

                            // If PENDING, give it one more chance
                            if (status1 == android.app.DownloadManager.STATUS_PENDING) {
                                Thread.sleep(5000);

                                android.app.DownloadManager.Query q2 = new android.app.DownloadManager.Query();
                                q2.setFilterById(id);
                                android.database.Cursor cursor2 = dm.query(q2);
                                int status2 = -1;
                                int reason2 = -1;
                                String sourceUri2 = null;
                                if (cursor2 != null) {
                                    try {
                                        if (cursor2.moveToFirst()) {
                                            int statusIdx = cursor2.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                                            int reasonIdx = cursor2.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                                            int uriIdx = cursor2.getColumnIndex(android.app.DownloadManager.COLUMN_URI);
                                            status2 = statusIdx != -1 ? cursor2.getInt(statusIdx) : -1;
                                            reason2 = reasonIdx != -1 ? cursor2.getInt(reasonIdx) : -1;
                                            sourceUri2 = (uriIdx != -1) ? cursor2.getString(uriIdx) : null;
                                        }
                                    } finally {
                                        try {
                                            cursor2.close();
                                        } catch (Exception ignore) {}
                                    }
                                }
                                Log.d("InAppBrowser", "Post-enqueue check2 id=" + id + " status=" + status2 + " reason=" + reason2);

                                // Si sigue PENDING después de 10s, considerarlo atascado y fallback
                                if (status2 == android.app.DownloadManager.STATUS_PENDING) {
                                    Log.w(
                                        "InAppBrowser",
                                        "DownloadManager still pending after 10s (id=" + id + "). Falling back to manual download."
                                    );
                                    String attemptedFileName;
                                    synchronized (activeDownloadFileNames) {
                                        attemptedFileName = activeDownloadFileNames.remove(id);
                                    }
                                    // intenta obtener URL desde sourceUri2, si null usa sourceUri1
                                    String fallbackUrl = sourceUri2 != null ? sourceUri2 : sourceUri1;
                                    String fallbackCookies = (capturedCookies != null && !capturedCookies.isEmpty()) ? capturedCookies : "";
                                    String fallbackUA = (capturedUA != null && !capturedUA.isEmpty())
                                        ? capturedUA
                                        : System.getProperty("http.agent");

                                    // elimina la tarea DM para limpiar la cola
                                    try {
                                        dm.remove(id);
                                    } catch (Exception ignore) {}

                                    // Ejecuta fallback (está bien llamar esto desde background)
                                    downloadWithHttpURLConnection(fallbackUrl, attemptedFileName, mimeType, fallbackCookies, fallbackUA);
                                } else if (
                                    status2 != android.app.DownloadManager.STATUS_RUNNING &&
                                    status2 != android.app.DownloadManager.STATUS_SUCCESSFUL
                                ) {
                                    // No arrancó correctamente -> fallback
                                    Log.w(
                                        "InAppBrowser",
                                        "DownloadManager status after 10s not running/successful (id=" + id + "), doing fallback."
                                    );
                                    String attemptedFileName;
                                    synchronized (activeDownloadFileNames) {
                                        attemptedFileName = activeDownloadFileNames.remove(id);
                                    }
                                    String fallbackUrl = sourceUri2 != null ? sourceUri2 : sourceUri1;
                                    String fallbackCookies = (capturedCookies != null && !capturedCookies.isEmpty()) ? capturedCookies : "";
                                    String fallbackUA = (capturedUA != null && !capturedUA.isEmpty())
                                        ? capturedUA
                                        : System.getProperty("http.agent");
                                    try {
                                        dm.remove(id);
                                    } catch (Exception ignore) {}
                                    downloadWithHttpURLConnection(fallbackUrl, attemptedFileName, mimeType, fallbackCookies, fallbackUA);
                                }
                            } else if (
                                status1 != android.app.DownloadManager.STATUS_RUNNING &&
                                status1 != android.app.DownloadManager.STATUS_SUCCESSFUL
                            ) {
                                // Si no estaba pending pero tampoco corriendo, fallback ahora
                                Log.w("InAppBrowser", "DownloadManager did not run after enqueue (id=" + id + "), performing fallback.");
                                String attemptedFileName;
                                synchronized (activeDownloadFileNames) {
                                    attemptedFileName = activeDownloadFileNames.remove(id);
                                }
                                String fallbackUrl = sourceUri1;
                                String fallbackCookies = (capturedCookies != null && !capturedCookies.isEmpty()) ? capturedCookies : "";
                                String fallbackUA = (capturedUA != null && !capturedUA.isEmpty())
                                    ? capturedUA
                                    : System.getProperty("http.agent");
                                try {
                                    dm.remove(id);
                                } catch (Exception ignore) {}
                                downloadWithHttpURLConnection(fallbackUrl, attemptedFileName, mimeType, fallbackCookies, fallbackUA);
                            } // else status running or successful -> leave it to DownloadManager and BroadcastReceiver
                        } catch (Throwable t) {
                            Log.e("InAppBrowser", "post-enqueue status check error: " + t.getMessage(), t);
                        }
                    });

                    // register receiver once (lazy)
                    if (downloadCompleteReceiver == null) {
                        downloadCompleteReceiver = new android.content.BroadcastReceiver() {
                            @Override
                            public void onReceive(Context ctx, Intent intent) {
                                try {
                                    long completedId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                                    if (completedId == -1L) return;
                                    handleDownloadCompletion(completedId);
                                } catch (Throwable ex) {
                                    Log.e("InAppBrowser", "Download complete receiver error: " + ex.getMessage(), ex);
                                }
                            }
                        };
                        try {
                            IntentFilter filter = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                // Android 12+ requires explicit exported flag for non-system receivers
                                getContext().registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                            } else {
                                getContext().registerReceiver(downloadCompleteReceiver, filter);
                            }
                            Log.d("InAppBrowser", "Download complete receiver registered");
                        } catch (Exception e) {
                            Log.w("InAppBrowser", "Could not register download receiver: " + e.getMessage());
                        }
                    }
                } else {
                    Log.e("InAppBrowser", "DownloadManager not available");
                    // fallback immediately
                    executorService.execute(() -> downloadWithHttpURLConnection(url, finalFileName, mimeType, cookies, ua));
                }
            } catch (Throwable e) {
                Log.e("InAppBrowser", "startDownloadFromUrl error: " + e.getMessage(), e);
                // fallback
                executorService.execute(() -> downloadWithHttpURLConnection(url, finalFileName, mimeType, cookies, ua));
            }
        });
    }

    private void handleDownloadCompletion(long downloadId) {
        final WebView webView = _webView; // capture reference
        try {
            android.app.DownloadManager dm = (android.app.DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Log.w("InAppBrowser", "DownloadManager not available in handleDownloadCompletion");
                return;
            }

            final Context ctx = getContext();

            android.app.DownloadManager.Query q = new android.app.DownloadManager.Query();
            q.setFilterById(downloadId);
            android.database.Cursor c = dm.query(q);
            if (c == null) {
                Log.w("InAppBrowser", "DownloadManager query returned null cursor for id=" + downloadId);
                return;
            }

            try {
                if (!c.moveToFirst()) {
                    Log.w("InAppBrowser", "DownloadManager cursor empty for id=" + downloadId);
                    return;
                }

                int statusIdx = c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int reasonIdx = c.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                int localUriIdx = c.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI);
                int uriIdx = c.getColumnIndex(android.app.DownloadManager.COLUMN_URI);

                int status = statusIdx != -1 ? c.getInt(statusIdx) : -1;
                int reason = reasonIdx != -1 ? c.getInt(reasonIdx) : -1;
                String localUri = localUriIdx != -1 ? c.getString(localUriIdx) : null;
                String sourceUri = uriIdx != -1 ? c.getString(uriIdx) : null;

                // Extract additional fields that some providers fill in.
                int localFilenameIdx = c.getColumnIndex("local_filename");
                int mediaTypeIdx = c.getColumnIndex(android.app.DownloadManager.COLUMN_MEDIA_TYPE);
                String localFilename = localFilenameIdx != -1 ? c.getString(localFilenameIdx) : null;
                String cursorMediaType = mediaTypeIdx != -1 ? c.getString(mediaTypeIdx) : null;
                Log.d("InAppBrowser", "download cursor extras: localFilename=" + localFilename + " mediaType=" + cursorMediaType);

                String fileName;
                synchronized (activeDownloadFileNames) {
                    fileName = activeDownloadFileNames.remove(downloadId);
                }

                Log.d(
                    "InAppBrowser",
                    "Download complete id=" +
                    downloadId +
                    " status=" +
                    status +
                    " reason=" +
                    reason +
                    " localUri=" +
                    localUri +
                    " sourceUri=" +
                    sourceUri +
                    " filename=" +
                    fileName
                );

                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    String delivered = fileName != null ? fileName : (localUri != null ? localUri : ("download_" + downloadId));
                    Log.i("InAppBrowser", "Download successful: " + delivered);

                    // Determine MIME type (try ContentResolver first, then extension)
                    String mimeType = null;
                    try {
                        if (ctx != null && localUri != null) {
                            try {
                                Uri parsed = Uri.parse(localUri);
                                mimeType = ctx.getContentResolver().getType(parsed);
                            } catch (Exception ignore) {}
                        }
                        if ((mimeType == null || mimeType.isEmpty()) && delivered != null) {
                            int dot = delivered.lastIndexOf('.');
                            if (dot > 0 && dot < delivered.length() - 1) {
                                String ext = delivered.substring(dot + 1).toLowerCase();
                                mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                            }
                        }
                    } catch (Throwable t) {
                        Log.w("InAppBrowser", "Could not determine mimeType: " + t.getMessage());
                    }

                    // Try to construct a URI we can share/open:
                    Uri notifyUri = null;
                    try {
                        if (localUri != null) {
                            try {
                                Uri parsed = Uri.parse(localUri);
                                String scheme = parsed.getScheme();
                                if ("content".equalsIgnoreCase(scheme)) {
                                    notifyUri = parsed;
                                } else if ("file".equalsIgnoreCase(scheme) || parsed.getPath() != null) {
                                    // convert file:// URI or plain path to a File and use FileProvider
                                    File f = new File(parsed.getPath());
                                    if (f.exists() && ctx != null) {
                                        try {
                                            notifyUri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
                                        } catch (Exception ex) {
                                            // fallback to the original parsed URI if anything fails
                                            notifyUri = parsed;
                                        }
                                    } else {
                                        // file doesn't exist; leave notifyUri null and fall back to searching by filename
                                        notifyUri = null;
                                    }
                                } else {
                                    notifyUri = parsed;
                                }
                            } catch (Exception e) {
                                Log.w("InAppBrowser", "Could not parse localUri: " + e.getMessage());
                            }
                        }

                        if (notifyUri == null && localFilename != null && ctx != null) {
                            File possibleLocal = new File(localFilename);
                            if (possibleLocal.exists()) {
                                try {
                                    notifyUri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", possibleLocal);
                                } catch (Exception ex) {
                                    Log.w("InAppBrowser", "FileProvider failed for localFilename: " + ex.getMessage());
                                    notifyUri = null;
                                }
                            }
                        }
                    } catch (Throwable tx) {
                        Log.w("InAppBrowser", "Error building notifyUri: " + tx.getMessage());
                        notifyUri = null;
                    }

                    // Notify user: show system notification and allow opening the file
                    try {
                        notifyDownloadSaved(notifyUri, delivered, mimeType);
                    } catch (Throwable nt) {
                        Log.w("InAppBrowser", "notifyDownloadSaved failed: " + nt.getMessage());
                    }

                    // Try to notify plugin callbacks.downloadFinished(String) if available (via reflection).
                    boolean notified = false;
                    try {
                        if (_options != null && _options.getCallbacks() != null) {
                            Object callbacks = _options.getCallbacks();
                            try {
                                java.lang.reflect.Method m = callbacks.getClass().getMethod("downloadFinished", String.class);
                                if (m != null) {
                                    m.invoke(callbacks, delivered);
                                    notified = true;
                                }
                            } catch (NoSuchMethodException ns) {
                                // Method not present - fallthrough to JS notification
                            } catch (Exception invokeEx) {
                                Log.w("InAppBrowser", "Error invoking downloadFinished via reflection: " + invokeEx.getMessage());
                            }
                        }
                    } catch (Throwable t) {
                        Log.w("InAppBrowser", "Reflection attempt for downloadFinished failed: " + t.getMessage());
                    }

                    if (!notified) {
                        // Fallback: notify the WebView JS and, if possible, the generic javascriptCallback
                        try {
                            JSONObject payload = new JSONObject();
                            payload.put("type", "downloadFinished");
                            payload.put("file", delivered);
                            postMessageToJS(payload);
                        } catch (Exception je) {
                            Log.w("InAppBrowser", "Could not post downloadFinished to JS: " + je.getMessage());
                        }

                        try {
                            if (_options != null && _options.getCallbacks() != null) {
                                Object callbacks = _options.getCallbacks();
                                try {
                                    java.lang.reflect.Method jscb = callbacks.getClass().getMethod("javascriptCallback", String.class);
                                    if (jscb != null) {
                                        String msg = String.format(
                                            "{\"detail\":{\"type\":\"downloadFinished\",\"file\":\"%s\"}}",
                                            delivered.replace("\"", "\\\"")
                                        );
                                        jscb.invoke(callbacks, msg);
                                    }
                                } catch (NoSuchMethodException ignore) {
                                    // nothing to call
                                } catch (Exception e) {
                                    Log.w("InAppBrowser", "Error invoking javascriptCallback for downloadFinished: " + e.getMessage());
                                }
                            }
                        } catch (Throwable t) {
                            Log.w("InAppBrowser", "Error while attempting to call javascriptCallback: " + t.getMessage());
                        }
                    }

                    return;
                }

                // Non-successful status -> attempt fallback manual download
                Log.w(
                    "InAppBrowser",
                    "DownloadManager reported failure for id=" + downloadId + " reason=" + reason + ". Falling back to manual download."
                );
                String attemptedFileName = (fileName != null) ? fileName : ("download_" + downloadId);

                final String fallbackUrl = (sourceUri != null && !sourceUri.isEmpty()) ? sourceUri : null;
                final String fallbackCookies = (fallbackUrl != null)
                    ? android.webkit.CookieManager.getInstance().getCookie(fallbackUrl)
                    : android.webkit.CookieManager.getInstance().getCookie("");
                final String fallbackUA = (webView != null) ? webView.getSettings().getUserAgentString() : System.getProperty("http.agent");

                // Run fallback download on executor
                executorService.execute(() -> {
                    try {
                        downloadWithHttpURLConnection(fallbackUrl, attemptedFileName, null, fallbackCookies, fallbackUA);
                    } catch (Throwable t) {
                        Log.e("InAppBrowser", "Fallback download failed for id=" + downloadId + ": " + t.getMessage(), t);
                    }
                });
            } finally {
                try {
                    c.close();
                } catch (Exception ignore) {}
            }
        } catch (Throwable t) {
            Log.e("InAppBrowser", "handleDownloadCompletion error: " + t.getMessage(), t);
        }
    }

    private void notifyDownloadSaved(@Nullable Uri fileUri, @Nullable String fileName, @Nullable String mimeType) {
        Log.i("InAppBrowser", "notifyDownloadSaved: name=" + fileName + " uri=" + fileUri + " mime=" + mimeType);
        try {
            Context ctx = getContext();
            if (ctx == null) return;

            String channelId = "inappbrowser_downloads";
            String channelName = getStringResourceOrDefault("download_channel_name", "App Downloads");
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

            // Create channel if needed (Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = nm.getNotificationChannel(channelId);
                if (ch == null) {
                    ch = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
                    ch.setDescription(getStringResourceOrDefault("download_channel_description", "Download completion notifications"));
                    nm.createNotificationChannel(ch);
                }
            }

            // Intent to open the file (or the Downloads app if there is no URI)
            Intent openIntent;
            if (fileUri != null) {
                openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(fileUri, (mimeType != null && !mimeType.isEmpty()) ? mimeType : "*/*");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                // Use the DownloadManager constant to open the system Downloads UI
                openIntent = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
                openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            Intent chooser = Intent.createChooser(openIntent, "Open file");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pi = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), chooser, flags);

            NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download saved")
                .setContentText(fileName != null ? fileName : "File downloaded")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            int notifId = Math.abs(
                ("dl_" + (fileName != null ? fileName : String.valueOf(System.currentTimeMillis()))).hashCode() % Integer.MAX_VALUE
            );
            nm.notify(notifId, nb.build());
        } catch (Throwable t) {
            Log.w("InAppBrowser", "notifyDownloadSaved failed: " + t.getMessage());
        }
    }

    private void downloadWithHttpURLConnection(String url, String fileName, String mimeType, String cookies, String userAgent) {
        if (url == null || url.isEmpty()) {
            Log.e("InAppBrowser", "fallback download: url null or empty");
            return;
        }

        InputStream input = null;
        java.io.OutputStream output = null;
        java.net.HttpURLConnection conn = null;
        Uri savedUri = null;
        File outFile = null;

        try {
            java.net.URL u = new java.net.URL(url);
            conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            if (userAgent != null && !userAgent.isEmpty()) conn.setRequestProperty("User-Agent", userAgent);
            if (cookies != null && !cookies.isEmpty()) conn.setRequestProperty("Cookie", cookies);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            int response = conn.getResponseCode();
            Log.d("InAppBrowser", "fallback download response code: " + response + " for url: " + url);
            if (response / 100 != 2) {
                Log.w("InAppBrowser", "fallback download non-2xx response: " + response);
                return;
            }

            input = conn.getInputStream();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore to write to Downloads
                android.content.ContentResolver cr = getContext().getContentResolver();
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                if (mimeType != null && !mimeType.isEmpty()) values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                savedUri = cr.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (savedUri == null) {
                    Log.e("InAppBrowser", "fallback download: cannot create MediaStore entry");
                    return;
                }
                output = cr.openOutputStream(savedUri);
                if (output == null) {
                    Log.e("InAppBrowser", "fallback download: cannot open output stream for MediaStore uri");
                    return;
                }
            } else {
                // Legacy: prefer public Downloads if app has permission, otherwise use app-specific external files dir
                File downloadsDir = null;
                try {
                    if (
                        activity != null &&
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    } else {
                        // use app-specific external files directory to avoid permission requirement
                        downloadsDir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        Log.d("InAppBrowser", "No WRITE_EXTERNAL_STORAGE permission - saving fallback file to app external files dir");
                    }
                } catch (Exception e) {
                    Log.w("InAppBrowser", "Could not determine downloads dir, falling back to app files dir: " + e.getMessage());
                    downloadsDir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                }

                if (downloadsDir == null) {
                    Log.e("InAppBrowser", "Unable to access any downloads directory");
                    return;
                }
                outFile = new File(downloadsDir, fileName);
                output = new java.io.FileOutputStream(outFile);
            }

            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
            Log.i("InAppBrowser", "fallback download saved file: " + fileName);

            // Notify plugin callback / JS if present (existing logic)
            boolean notified = false;
            try {
                if (_options != null && _options.getCallbacks() != null) {
                    Object callbacks = _options.getCallbacks();
                    try {
                        java.lang.reflect.Method m = callbacks.getClass().getMethod("downloadFinished", String.class);
                        if (m != null) {
                            m.invoke(callbacks, fileName);
                            notified = true;
                        }
                    } catch (NoSuchMethodException ns) {
                        Log.d("InAppBrowser", "callbacks.downloadFinished not present, will fallback to JS event");
                    } catch (Exception invokeEx) {
                        Log.w("InAppBrowser", "Error invoking downloadFinished via reflection: " + invokeEx.getMessage());
                    }
                }
            } catch (Throwable t) {
                Log.w("InAppBrowser", "Reflection attempt for downloadFinished failed: " + t.getMessage());
            }

            if (!notified) {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("type", "downloadFinished");
                    payload.put("file", fileName);
                    postMessageToJS(payload);
                } catch (Exception je) {
                    Log.w("InAppBrowser", "Could not post downloadFinished to JS: " + je.getMessage());
                }
            }

            // Notify user via notification + open file intent
            if (savedUri != null) {
                // MediaStore path (Android Q+)
                notifyDownloadSaved(savedUri, fileName, mimeType);
            } else if (outFile != null) {
                // Legacy path: create FileProvider Uri
                try {
                    Context ctx = getContext();
                    if (ctx != null) {
                        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", outFile);
                        // Make visible in media provider / Downloads app
                        try {
                            android.media.MediaScannerConnection.scanFile(ctx, new String[] { outFile.getAbsolutePath() }, null, null);
                        } catch (Exception ignore) {}
                        notifyDownloadSaved(uri, fileName, mimeType);
                    }
                } catch (Throwable ex) {
                    Log.w("InAppBrowser", "Could not create FileProvider uri: " + ex.getMessage());
                    // fallback: notify by name only (no URI)
                    notifyDownloadSaved(null, fileName, mimeType);
                }
            } else {
                // Unknown case, notify by name only
                notifyDownloadSaved(null, fileName, mimeType);
            }
        } catch (Throwable t) {
            Log.e("InAppBrowser", "fallback download error: " + t.getMessage(), t);
        } finally {
            try {
                if (input != null) input.close();
            } catch (Exception ignored) {}
            try {
                if (output != null) output.close();
            } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Register the BroadcastReceiver to detect when downloads are complete.
     */
    private void registerDownloadCompleteReceiver() {
        if (downloadCompleteReceiver != null) {
            return; // It was already
        }

        downloadCompleteReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId != -1 && activeDownloadFileNames.containsKey(downloadId)) {
                    handleDownloadCompletion(downloadId);
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            _context.registerReceiver(downloadCompleteReceiver, filter);
        }
    }

    /**
     * Unregister the Download BroadcastReceiver
     */
    private void unregisterDownloadCompleteReceiver() {
        if (downloadCompleteReceiver != null) {
            try {
                _context.unregisterReceiver(downloadCompleteReceiver);
            } catch (IllegalArgumentException e) {
                // It was already unregistered
            }
            downloadCompleteReceiver = null;
        }
    }

    /**
     * Apply dimensions to the webview window
     */
    private void applyDimensions() {
        Integer width = _options.getWidth();
        Integer height = _options.getHeight();
        Integer x = _options.getX();
        Integer y = _options.getY();

        WindowManager.LayoutParams params = getWindow().getAttributes();

        // If both width and height are specified, use custom dimensions
        if (width != null && height != null) {
            params.width = (int) getPixels(width);
            params.height = (int) getPixels(height);
            params.x = (x != null) ? (int) getPixels(x) : 0;
            params.y = (y != null) ? (int) getPixels(y) : 0;
        } else if (height != null && width == null) {
            // If only height is specified, use custom height with fullscreen width
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = (int) getPixels(height);
            params.x = 0;
            params.y = (y != null) ? (int) getPixels(y) : 0;
        } else {
            // Default to fullscreen
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.x = 0;
            params.y = 0;
        }

        getWindow().setAttributes(params);
    }

    /**
     * Update dimensions at runtime
     */
    public void updateDimensions(Integer width, Integer height, Integer x, Integer y) {
        // Update options
        if (width != null) {
            _options.setWidth(width);
        }
        if (height != null) {
            _options.setHeight(height);
        }
        if (x != null) {
            _options.setX(x);
        }
        if (y != null) {
            _options.setY(y);
        }

        // Apply new dimensions
        applyDimensions();
    }

    /**
     * Convert density-independent pixels (dp) to actual pixels
     */
    private float getPixels(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, _context.getResources().getDisplayMetrics());
    }

    private String getStringResourceOrDefault(String name, String defaultValue) {
        try {
            Context ctx = getContext();
            if (ctx == null) return defaultValue;
            int resId = ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
            if (resId != 0) {
                // Resource exists -> return localized string
                return ctx.getString(resId);
            }
        } catch (Exception e) {
            Log.w("InAppBrowser", "getStringResourceOrDefault failed for '" + name + "': " + e.getMessage());
        }
        // Fallback to provided default (English)
        return defaultValue;
    }
}
