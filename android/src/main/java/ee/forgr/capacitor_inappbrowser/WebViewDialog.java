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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import android.webkit.ConsoleMessage;
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
import android.widget.RelativeLayout;
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
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

public class WebViewDialog extends Dialog {

    private static class ProxiedRequest {

        private WebResourceResponse response;
        private final Semaphore semaphore;
        private NativeRequestContext requestContext;
        private NativeResponseData nativeResponse;
        private boolean canceled;

        public WebResourceResponse getResponse() {
            return response;
        }

        public ProxiedRequest() {
            this.semaphore = new Semaphore(0);
            this.response = null;
        }
    }

    private static class NativeRequestContext {

        private String url;
        private String method;
        private Map<String, String> headers;
        private String base64Body;
        private boolean mainFrame;

        NativeRequestContext(String url, String method, Map<String, String> headers, String base64Body, boolean mainFrame) {
            this.url = url;
            this.method = method;
            this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
            this.base64Body = base64Body != null ? base64Body : "";
            this.mainFrame = mainFrame;
        }
    }

    private static class NativeResponseData {

        private int statusCode;
        private String contentType;
        private Map<String, String> headers;
        private byte[] bodyBytes;

        NativeResponseData(int statusCode, String contentType, Map<String, String> headers, byte[] bodyBytes) {
            this.statusCode = statusCode;
            this.contentType = contentType != null ? contentType : "application/octet-stream";
            this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
            this.bodyBytes = bodyBytes != null ? bodyBytes : new byte[0];
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
    private String instanceId = "";
    private final Map<String, ProxiedRequest> proxiedRequestsHashmap = new HashMap<>();
    private ProxyBridge proxyBridge;
    private String proxyBridgeScript;
    private String proxyAccessToken;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private int iconColor = Color.BLACK; // Default icon color
    private boolean isHiddenModeActive = false;
    private WindowManager.LayoutParams previousWindowAttributes;
    private Drawable previousWindowBackground;
    private ViewGroup.LayoutParams previousWebViewLayoutParams;
    private float previousDecorAlpha = 1f;
    private int previousDecorVisibility = View.VISIBLE;
    private float previousWebViewAlpha = 1f;
    private int previousWebViewVisibility = View.VISIBLE;

    Semaphore preShowSemaphore = null;
    String preShowError = null;

    public PermissionRequest currentPermissionRequest;
    public static final int FILE_CHOOSER_REQUEST_CODE = 1000;
    public ValueCallback<Uri> mUploadMessage;
    public ValueCallback<Uri[]> mFilePathCallback;
    private boolean openWebViewResolved;
    private boolean isDismissing = false;
    private PermissionRequest pendingCameraLaunchPermissionRequest;

    // Temporary URI for storing camera capture
    public Uri tempCameraUri;

    public interface PermissionHandler {
        void handleCameraPermissionRequest(PermissionRequest request);

        void handleMicrophonePermissionRequest(PermissionRequest request);

        void clearPendingPermissionRequest(PermissionRequest request);

        boolean createManagedPopupWindow(WebViewDialog parentDialog, android.os.Message resultMsg, boolean isUserGesture, String popupUrl);
    }

    private final PermissionHandler permissionHandler;

    public WebViewDialog(Context context, int theme, Options options, PermissionHandler permissionHandler, WebView capacitorWebView) {
        // Use Material theme only if materialPicker is enabled
        super(context, options.getMaterialPicker() ? R.style.InAppBrowserMaterialTheme : theme);
        this._options = options;
        this._context = context;
        this.permissionHandler = permissionHandler;
        this.isInitialized = false;
        this.openWebViewResolved = false;
        this.capacitorWebView = capacitorWebView;
    }

    public void setInstanceId(String id) {
        this.instanceId = id != null ? id : "";
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Options getOptions() {
        return _options;
    }

    public WebView getManagedWebView() {
        return _webView;
    }

    private void resolveOpenWebViewIfNeeded() {
        if (openWebViewResolved || _options == null) {
            return;
        }
        PluginCall call = _options.getPluginCall();
        if (call == null) {
            Log.e("InAppBrowser", "Cannot resolve openWebView: plugin call is null");
            openWebViewResolved = true;
            return;
        }
        if (instanceId == null || instanceId.isEmpty()) {
            call.reject("Cannot resolve openWebView: missing webview id");
            openWebViewResolved = true;
            return;
        }
        JSObject result = new JSObject();
        result.put("id", instanceId);
        call.resolve(result);
        openWebViewResolved = true;
    }

    private void rejectOpenWebViewIfNeeded(String message) {
        if (openWebViewResolved || _options == null) {
            return;
        }
        PluginCall call = _options.getPluginCall();
        if (call == null) {
            Log.e("InAppBrowser", "Cannot reject openWebView: plugin call is null");
            openWebViewResolved = true;
            return;
        }
        call.reject(message);
        openWebViewResolved = true;
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

                if (_options == null || _options.getCallbacks() == null) {
                    Log.e("InAppBrowser", "Cannot handle postMessage - options or callbacks are null");
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

        @JavascriptInterface
        public void hide() {
            if (!isJavaScriptControlAllowed()) {
                Log.w("InAppBrowser", "hide() blocked: allowWebViewJsVisibilityControl is false");
                return;
            }
            if (activity == null) {
                Log.e("InAppBrowser", "Cannot hide - activity is null");
                return;
            }
            activity.runOnUiThread(() -> setHidden(true));
        }

        @JavascriptInterface
        public void show() {
            if (!isJavaScriptControlAllowed()) {
                Log.w("InAppBrowser", "show() blocked: allowWebViewJsVisibilityControl is false");
                return;
            }
            if (activity == null) {
                Log.e("InAppBrowser", "Cannot show - activity is null");
                return;
            }
            activity.runOnUiThread(() -> {
                if (!isShowing()) {
                    WebViewDialog.this.show();
                }
                setHidden(false);
            });
        }

        @JavascriptInterface
        public void takeScreenshot(String requestId) {
            if (requestId == null || requestId.isEmpty()) {
                Log.e("InAppBrowser", "Cannot take screenshot - requestId is empty");
                return;
            }

            if (_options == null || !_options.getAllowScreenshotsFromWebPage()) {
                rejectJavaScriptScreenshot(requestId, "Screenshot bridge is not enabled for this page");
                return;
            }

            WebViewDialog.this.takeScreenshot(
                new ScreenshotResultCallback() {
                    @Override
                    public void onSuccess(JSObject screenshot) {
                        resolveJavaScriptScreenshot(requestId, screenshot);
                    }

                    @Override
                    public void onError(String message) {
                        rejectJavaScriptScreenshot(requestId, message);
                    }
                }
            );
        }
    }

    public interface ScreenshotResultCallback {
        void onSuccess(JSObject screenshot);

        void onError(String message);
    }

    private boolean isJavaScriptControlAllowed() {
        return _options != null && _options.getAllowWebViewJsVisibilityControl();
    }

    /**
     * Checks if the given HTTP method supports a request body.
     * @param method The HTTP method to check
     * @return true if the method supports a body (POST, PUT, PATCH), false otherwise
     */
    private boolean supportsRequestBody(String method) {
        if (method == null) {
            return false;
        }
        String upperMethod = method.toUpperCase();
        return upperMethod.equals("POST") || upperMethod.equals("PUT") || upperMethod.equals("PATCH");
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
        // Provide window.mobileApp at document start via native interface
        _webView.addJavascriptInterface(new JavaScriptInterface(), "mobileApp");
        _webView.addJavascriptInterface(new PreShowScriptInterface(), "PreShowScriptInterface");
        _webView.addJavascriptInterface(new PrintInterface(this._context, _webView), "PrintInterface");
        if (_options.shouldEnableNativeProxy()) {
            proxyAccessToken = UUID.randomUUID().toString();
            proxyBridge = new ProxyBridge(proxyAccessToken);
            _webView.addJavascriptInterface(proxyBridge, "__capgoProxy");
            proxyBridgeScript = loadProxyBridgeScript();
        }
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

        _webView.getSettings().setSupportMultipleWindows(true);

        // Enhanced settings for Google Pay and Payment Request API support (only when enabled)
        if (_options.getEnableGooglePaySupport()) {
            Log.d("InAppBrowser", "Enabling Google Pay support features");
            _webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            _webView.getSettings().setSupportMultipleWindows(true);
            _webView.getSettings().setGeolocationEnabled(true);

            // Ensure secure context for Payment Request API
            _webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

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

        _webView.setWebChromeClient(
            new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    if (consoleMessage != null && _options != null && _options.getCaptureConsoleLogs() && _options.getCallbacks() != null) {
                        _options
                            .getCallbacks()
                            .consoleMessage(
                                consoleMessage.messageLevel().name(),
                                consoleMessage.message(),
                                consoleMessage.sourceId(),
                                consoleMessage.lineNumber(),
                                null
                            );
                    }
                    return super.onConsoleMessage(consoleMessage);
                }

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
                        webView.evaluateJavascript(
                            "document.querySelector('input[type=\"file\"][capture]') !== null",
                            (hasCaptureValue) -> {
                                Log.d("InAppBrowser", "Quick capture check: " + hasCaptureValue);
                                if (Boolean.parseBoolean(hasCaptureValue.replace("\"", ""))) {
                                    Log.d("InAppBrowser", "Found capture attribute in quick check");
                                }
                            }
                        );

                        // Fixed JavaScript with proper error handling
                        String js = """
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

                        webView.evaluateJavascript(js, (value) -> {
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
                            webView.evaluateJavascript("(function() { return document.documentElement.innerHTML; })()", (htmlSource) -> {
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
                                pendingCameraLaunchPermissionRequest = null;
                                if (isDismissing) {
                                    Log.d("InAppBrowser", "Ignoring delayed camera permission grant during dismiss");
                                    return;
                                }
                                // Permission granted, now launch the camera
                                launchCameraWithPermission(useFrontCamera);
                            }

                            @Override
                            public void deny() {
                                pendingCameraLaunchPermissionRequest = null;
                                if (isDismissing) {
                                    Log.d("InAppBrowser", "Ignoring delayed camera permission denial during dismiss");
                                    return;
                                }
                                // Permission denied, fall back to file picker
                                Log.e("InAppBrowser", "Camera permission denied, falling back to file picker");
                                fallbackToFilePicker();
                            }
                        };

                        pendingCameraLaunchPermissionRequest = tempRequest;

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
                                                (result) -> {
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

                    String popupUrl = null;
                    try {
                        WebView.HitTestResult hitTestResult = view.getHitTestResult();
                        popupUrl = hitTestResult != null ? hitTestResult.getExtra() : null;
                    } catch (Exception ignored) {}

                    if (
                        permissionHandler != null &&
                        permissionHandler.createManagedPopupWindow(WebViewDialog.this, resultMsg, isUserGesture, popupUrl)
                    ) {
                        Log.d("InAppBrowser", "Created managed popup window");
                        return true;
                    }

                    if (!_options.getPreventDeeplink() && isUserGesture) {
                        try {
                            WebView.HitTestResult result = view.getHitTestResult();
                            String data = result.getExtra();
                            if (data != null && !data.isEmpty()) {
                                Log.d("InAppBrowser", "Falling back to external browser for popup URL: " + data);
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(data));
                                browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                _webView.getContext().startActivity(browserIntent);
                                return false;
                            }
                        } catch (Exception e) {
                            Log.e("InAppBrowser", "Error opening external popup fallback: " + e.getMessage());
                        }
                    }

                    return false;
                }

                @Override
                public void onCloseWindow(WebView window) {
                    Log.d("InAppBrowser", "onCloseWindow called");
                    if (window == _webView) {
                        String currentUrl = getUrl();
                        dismiss();
                        if (_options != null && _options.getCallbacks() != null) {
                            _options.getCallbacks().closeEvent(currentUrl);
                        }
                    } else {
                        super.onCloseWindow(window);
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
                    _webView.getSettings().setUserAgentString(_options.getHeaders().getString(key));
                } else {
                    requestHeaders.put(key, _options.getHeaders().getString(key));
                }
            }
        }

        // Load URL with optional HTTP method and body
        String httpMethod = _options.getHttpMethod();
        String httpBody = _options.getHttpBody();

        if (!_options.isPopupWindowMode()) {
            if (supportsRequestBody(httpMethod) && httpBody != null) {
                // For POST/PUT/PATCH requests with body
                // Note: Android WebView has limitations with custom headers on POST
                // Headers may not be sent with the initial request when using postUrl
                byte[] postData = httpBody.getBytes(StandardCharsets.UTF_8);
                _webView.postUrl(this._options.getUrl(), postData);

                // Log a warning if headers were provided, as they won't be sent with postUrl
                if (!requestHeaders.isEmpty()) {
                    Log.w(
                        "InAppBrowser",
                        "Custom headers were provided but may not be sent with POST request. " +
                            "Android WebView's postUrl method has limited header support."
                    );
                }
            } else {
                // For GET and other methods, use loadUrl with headers
                _webView.loadUrl(this._options.getUrl(), requestHeaders);
            }

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
        }

        setupToolbar();
        setWebViewClient();

        if (_options.isPopupWindowMode()) {
            show();
        } else if (this._options.isHidden()) {
            if (_options.getInvisibilityMode() == Options.InvisibilityMode.FAKE_VISIBLE) {
                show();
                applyHiddenMode();
            }
            resolveOpenWebViewIfNeeded();
        } else if (!this._options.isPresentAfterPageLoad()) {
            show();
            resolveOpenWebViewIfNeeded();
        }
    }

    private void applyHiddenMode() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        previousWindowAttributes = new WindowManager.LayoutParams();
        previousWindowAttributes.copyFrom(window.getAttributes());
        previousWindowBackground = window.getDecorView().getBackground();

        View decorView = window.getDecorView();
        if (decorView != null) {
            previousDecorAlpha = decorView.getAlpha();
            previousDecorVisibility = decorView.getVisibility();
        }

        if (_webView != null) {
            previousWebViewAlpha = _webView.getAlpha();
            previousWebViewVisibility = _webView.getVisibility();
            previousWebViewLayoutParams = _webView.getLayoutParams();
        }

        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        if (decorView != null) {
            decorView.setAlpha(0f);
            if (_options.getInvisibilityMode() == Options.InvisibilityMode.AWARE) {
                decorView.setVisibility(View.GONE);
            } else {
                decorView.setVisibility(View.INVISIBLE);
            }
        }

        if (_webView != null) {
            if (_options.getInvisibilityMode() == Options.InvisibilityMode.AWARE) {
                window.setLayout(1, 1);
                _webView.setAlpha(0f);
                _webView.setVisibility(View.INVISIBLE);
                _webView.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));
            } else {
                _webView.setAlpha(0f);
                _webView.setVisibility(View.INVISIBLE);
            }
        }

        isHiddenModeActive = true;
    }

    private void restoreVisibleMode() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        if (previousWindowAttributes != null) {
            window.setAttributes(previousWindowAttributes);
        }
        if (previousWindowBackground != null) {
            window.setBackgroundDrawable(previousWindowBackground);
        }

        View decorView = window.getDecorView();
        if (decorView != null) {
            decorView.setAlpha(previousDecorAlpha);
            decorView.setVisibility(previousDecorVisibility);
        }

        if (_webView != null) {
            if (previousWebViewLayoutParams != null) {
                _webView.setLayoutParams(previousWebViewLayoutParams);
            }
            _webView.setAlpha(previousWebViewAlpha);
            _webView.setVisibility(previousWebViewVisibility);
        }

        previousWindowAttributes = null;
        previousWindowBackground = null;
        previousWebViewLayoutParams = null;
        previousDecorAlpha = 1f;
        previousDecorVisibility = View.VISIBLE;
        previousWebViewAlpha = 1f;
        previousWebViewVisibility = View.VISIBLE;
        isHiddenModeActive = false;
    }

    public void setHidden(boolean hidden) {
        if (hidden) {
            if (!isHiddenModeActive) {
                if (getWindow() == null) {
                    try {
                        show();
                        Window window = getWindow();
                        if (window == null) {
                            Log.w("InAppBrowser", "Unable to apply hidden mode: window is null after show()");
                            return;
                        }
                        View decorView = window.getDecorView();
                        if (decorView == null) {
                            Log.w("InAppBrowser", "Unable to apply hidden mode: decorView is null after show()");
                            return;
                        }
                        // Set flag immediately to prevent race condition if setHidden(false)
                        // is called before the posted runnable executes
                        isHiddenModeActive = true;
                        decorView.post(this::applyHiddenMode);
                    } catch (Exception e) {
                        Log.w("InAppBrowser", "Unable to show dialog before hiding", e);
                    }
                } else {
                    applyHiddenMode();
                }
            }
        } else {
            if (isHiddenModeActive) {
                restoreVisibleMode();
            }
        }
        if (_options != null) {
            _options.setHidden(hidden);
        }
    }

    public boolean isHiddenModeActive() {
        return isHiddenModeActive;
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

        // Fix content browser layout height for all Android versions to allow proper scrolling
        // This fixes landscape scrolling issues where bottom content is unreachable
        View contentBrowserLayout = findViewById(R.id.content_browser_layout);
        if (contentBrowserLayout != null) {
            ViewGroup.LayoutParams layoutParams = contentBrowserLayout.getLayoutParams();
            if (layoutParams != null) {
                // Use MATCH_PARENT for height to allow proper scrolling in all orientations
                // The AppBarLayout's layout_behavior will handle positioning automatically
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                contentBrowserLayout.setLayoutParams(layoutParams);
            }
        }

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
                });
            }
        }

        // Apply system insets to WebView content view (compatible with all Android versions)
        ViewCompat.setOnApplyWindowInsetsListener(_webView, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets systemGestures = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures());
            Insets mandatoryGestures = windowInsets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Boolean keyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());

            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

            // Apply safe margin inset to bottom margin if enabled in options or fallback to 0px
            int safeBottomInset = Math.max(
                bars.bottom,
                Math.max(navigationBars.bottom, Math.max(systemGestures.bottom, mandatoryGestures.bottom))
            );
            int navBottom = _options.getEnabledSafeMargin() ? safeBottomInset : 0;

            // Apply top inset based on enabledSafeTopMargin and useTopInset options
            // If enabledSafeTopMargin is false, force full screen (no top margin)
            // Otherwise, use useTopInset to determine if system inset should be applied
            int navTop = _options.getEnabledSafeTopMargin() && _options.getUseTopInset() ? bars.top : 0;

            // Avoid double-applying top inset; AppBar/status bar handled above on Android 15+
            mlp.topMargin = isAndroid15Plus ? 0 : navTop;

            // Apply larger of navigation bar or keyboard inset to bottom margin
            mlp.bottomMargin = Math.max(navBottom, ime.bottom);

            mlp.leftMargin = bars.left;
            mlp.rightMargin = bars.right;
            v.setLayoutParams(mlp);

            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(_webView);

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

    private String toJsonString(JSObject object) {
        return object == null ? null : object.toString();
    }

    private void resolveJavaScriptScreenshot(String requestId, JSObject screenshot) {
        if (_webView == null) {
            return;
        }
        JSObject payload = new JSObject();
        payload.put("requestId", requestId);
        payload.put("result", screenshot);
        String jsonPayload = toJsonString(payload);
        if (jsonPayload == null) {
            return;
        }
        String script = "window.__capgoInAppBrowserResolveScreenshot(" + jsonPayload + ");";
        _webView.post(() -> {
            if (_webView != null) {
                _webView.evaluateJavascript(script, null);
            }
        });
    }

    private void rejectJavaScriptScreenshot(String requestId, String message) {
        if (_webView == null) {
            return;
        }
        JSObject payload = new JSObject();
        payload.put("requestId", requestId);
        payload.put("message", message);
        String jsonPayload = toJsonString(payload);
        if (jsonPayload == null) {
            return;
        }
        String script = "window.__capgoInAppBrowserRejectScreenshot(" + jsonPayload + ");";
        _webView.post(() -> {
            if (_webView != null) {
                _webView.evaluateJavascript(script, null);
            }
        });
    }

    public void takeScreenshot(ScreenshotResultCallback callback) {
        if (_webView == null) {
            callback.onError("WebView is not initialized");
            return;
        }

        _webView.post(() -> {
            if (_webView == null) {
                callback.onError("WebView is not initialized");
                return;
            }

            int width = _webView.getWidth() > 0 ? _webView.getWidth() : _webView.getMeasuredWidth();
            int height = _webView.getHeight() > 0 ? _webView.getHeight() : _webView.getMeasuredHeight();
            if (width <= 0 || height <= 0) {
                callback.onError("WebView is not ready to capture a screenshot");
                return;
            }

            final Bitmap bitmap;
            try {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError error) {
                callback.onError("Not enough memory to allocate screenshot buffer");
                return;
            }
            Canvas canvas = new Canvas(bitmap);
            _webView.draw(canvas);

            executorService.execute(() -> {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        postScreenshotError(callback, "Failed to encode screenshot");
                        return;
                    }

                    String base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
                    JSObject result = new JSObject();
                    result.put("format", "png");
                    result.put("mimeType", "image/png");
                    result.put("base64", base64);
                    result.put("dataUrl", "data:image/png;base64," + base64);
                    result.put("width", width);
                    result.put("height", height);

                    postScreenshotSuccess(callback, result);
                } catch (IOException e) {
                    postScreenshotError(callback, "Failed to encode screenshot: " + e.getMessage());
                } finally {
                    bitmap.recycle();
                }
            });
        });
    }

    private void postScreenshotSuccess(ScreenshotResultCallback callback, JSObject result) {
        if (_webView == null) {
            callback.onError("WebView is not initialized");
            return;
        }
        _webView.post(() -> {
            if (_options != null && _options.getCallbacks() != null) {
                _options.getCallbacks().screenshotTaken(result);
            }
            callback.onSuccess(result);
        });
    }

    private void postScreenshotError(ScreenshotResultCallback callback, String message) {
        if (_webView == null) {
            callback.onError(message);
            return;
        }
        _webView.post(() -> callback.onError(message));
    }

    private void injectJavaScriptInterface() {
        if (_webView == null) {
            Log.w("InAppBrowser", "Cannot inject JavaScript interface - WebView is null");
            return;
        }

        try {
            String mobileAppExtras = "";
            if (isJavaScriptControlAllowed()) {
                mobileAppExtras = """
                            , hide: function() {
                              try {
                                window.AndroidInterface.hide();
                              } catch(e) {
                                console.error('Error in mobileApp.hide:', e);
                              }
                            },
                            show: function() {
                              try {
                                window.AndroidInterface.show();
                              } catch(e) {
                                console.error('Error in mobileApp.show:', e);
                              }
                            }
                    """;
            }

            String screenshotBridge =
                _options != null && _options.getAllowScreenshotsFromWebPage()
                    ? """
                          ,
                          takeScreenshot: function() {
                            return new Promise(function(resolve, reject) {
                              try {
                                if (!nativeBridge.takeScreenshot) {
                                  reject(new Error('Screenshot bridge is not available'));
                                  return;
                                }
                                var requestId = 'screenshot_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                                window.__capgoInAppBrowserPendingScreenshots[requestId] = { resolve: resolve, reject: reject };
                                nativeBridge.takeScreenshot(requestId);
                              } catch(e) {
                                reject(e);
                              }
                            });
                          }
                      """
                    : "";

            String script = String.format(
                """
                (function() {
                  window.__capgoInAppBrowserPendingScreenshots = window.__capgoInAppBrowserPendingScreenshots || {};
                  window.__capgoInAppBrowserResolveScreenshot = window.__capgoInAppBrowserResolveScreenshot || function(payload) {
                    var pending = window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                    if (!pending) {
                      return;
                    }
                    delete window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                    pending.resolve(payload.result);
                  };
                  window.__capgoInAppBrowserRejectScreenshot = window.__capgoInAppBrowserRejectScreenshot || function(payload) {
                    var pending = window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                    if (!pending) {
                      return;
                    }
                    delete window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                    pending.reject(new Error(payload.message));
                  };
                  // Prefer AndroidInterface when available, otherwise fall back to native window.mobileApp
                  var nativeBridge = window.AndroidInterface || window.mobileApp;
                  if (nativeBridge) {
                    // Wrap native bridge to normalize behavior (stringify objects, expose close/hide/show)
                    window.mobileApp = {
                      postMessage: function(message) {
                        try {
                          var msg = typeof message === 'string' ? message : JSON.stringify(message);
                          nativeBridge.postMessage(msg);
                        } catch(e) {
                          console.error('Error in mobileApp.postMessage:', e);
                        }
                      },
                      close: function() {
                        try {
                          nativeBridge.close();
                        } catch(e) {
                          console.error('Error in mobileApp.close:', e);
                        }
                      }%s%s
                    };
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
                """,
                mobileAppExtras,
                screenshotBridge
            );

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
            String googlePayScript = """
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
                        _webView.evaluateJavascript(googlePayScript, (result) -> {
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
            if (activity instanceof androidx.activity.ComponentActivity) {
                androidx.activity.ComponentActivity componentActivity = (androidx.activity.ComponentActivity) activity;
                componentActivity
                    .getActivityResultRegistry()
                    .register(
                        "file_chooser",
                        new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                        (result) -> {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();
                                if (data != null) {
                                    if (data.getClipData() != null) {
                                        // Handle multiple files
                                        int count = data.getClipData().getItemCount();
                                        Uri[] results = new Uri[count];
                                        for (int i = 0; i < count; i++) {
                                            results[i] = data.getClipData().getItemAt(i).getUri();
                                        }
                                        mFilePathCallback.onReceiveValue(results);
                                    } else if (data.getData() != null) {
                                        // Handle single file
                                        mFilePathCallback.onReceiveValue(new Uri[] { data.getData() });
                                    }
                                }
                            } else {
                                mFilePathCallback.onReceiveValue(null);
                            }
                            mFilePathCallback = null;
                        }
                    )
                    .launch(Intent.createChooser(intent, "Select File"));
            } else {
                // Fallback for non-ComponentActivity
                activity.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST_CODE);
            }
        } catch (ActivityNotFoundException e) {
            // If no app can handle the specific MIME type, try with a more generic one
            Log.e("InAppBrowser", "No app available for type: " + acceptType + ", trying with */*");
            intent.setType("*/*");
            try {
                if (activity instanceof androidx.activity.ComponentActivity) {
                    androidx.activity.ComponentActivity componentActivity = (androidx.activity.ComponentActivity) activity;
                    componentActivity
                        .getActivityResultRegistry()
                        .register(
                            "file_chooser",
                            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                            (result) -> {
                                if (result.getResultCode() == Activity.RESULT_OK) {
                                    Intent data = result.getData();
                                    if (data != null) {
                                        if (data.getClipData() != null) {
                                            // Handle multiple files
                                            int count = data.getClipData().getItemCount();
                                            Uri[] results = new Uri[count];
                                            for (int i = 0; i < count; i++) {
                                                results[i] = data.getClipData().getItemAt(i).getUri();
                                            }
                                            mFilePathCallback.onReceiveValue(results);
                                        } else if (data.getData() != null) {
                                            // Handle single file
                                            mFilePathCallback.onReceiveValue(new Uri[] { data.getData() });
                                        }
                                    }
                                } else {
                                    mFilePathCallback.onReceiveValue(null);
                                }
                                mFilePathCallback = null;
                            }
                        )
                        .launch(Intent.createChooser(intent, "Select File"));
                } else {
                    // Fallback for non-ComponentActivity
                    activity.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST_CODE);
                }
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
                        Pattern urlPattern = _options.getCloseModalURLPattern();
                        final String currentUrl = getUrl();
                        boolean shouldShowModal = urlPattern == null || urlPattern.matcher(currentUrl).find();
                        if (shouldShowModal) {
                            new AlertDialog.Builder(_context)
                                .setTitle(_options.getCloseModalTitle())
                                .setMessage(_options.getCloseModalDescription())
                                .setPositiveButton(
                                    _options.getCloseModalOk(),
                                    new OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // Close button clicked, do something
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
                            dismiss();
                            if (_options != null && _options.getCallbacks() != null) {
                                _options.getCallbacks().closeEvent(currentUrl);
                            }
                        }
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
            if (buttonNearDone != null || _options.getShowScreenshotButton()) {
                ImageButton buttonNearDoneView = _toolbar.findViewById(R.id.buttonNearDone);
                buttonNearDoneView.setVisibility(View.VISIBLE);

                if (_options.getShowScreenshotButton()) {
                    buttonNearDoneView.setImageResource(android.R.drawable.ic_menu_camera);
                    buttonNearDoneView.setColorFilter(iconColor);
                    buttonNearDoneView.setOnClickListener((view) ->
                        takeScreenshot(
                            new ScreenshotResultCallback() {
                                @Override
                                public void onSuccess(JSObject screenshot) {}

                                @Override
                                public void onError(String message) {
                                    Log.e("InAppBrowser", "Failed to capture screenshot from toolbar: " + message);
                                }
                            }
                        )
                    );
                } else {
                    // Handle different icon types
                    String iconType = buttonNearDone.getIconType();
                    if ("vector".equals(iconType)) {
                        // Use native Android vector drawable
                        try {
                            String iconName = buttonNearDone.getIcon();
                            if (iconName.endsWith(".xml")) {
                                iconName = iconName.substring(0, iconName.length() - 4);
                            }

                            int resourceId = _context.getResources().getIdentifier(iconName, "drawable", _context.getPackageName());

                            if (resourceId != 0) {
                                buttonNearDoneView.setImageResource(resourceId);
                                buttonNearDoneView.setColorFilter(iconColor);
                                Log.d("InAppBrowser", "Successfully loaded vector drawable: " + iconName);
                            } else {
                                Log.e("InAppBrowser", "Vector drawable not found: " + iconName + ", using fallback");
                                buttonNearDoneView.setImageResource(android.R.drawable.ic_menu_info_details);
                                buttonNearDoneView.setColorFilter(iconColor);
                            }
                        } catch (Exception e) {
                            Log.e("InAppBrowser", "Error loading vector drawable: " + e.getMessage());
                            buttonNearDoneView.setImageResource(android.R.drawable.ic_menu_info_details);
                            buttonNearDoneView.setColorFilter(iconColor);
                        }
                    } else if ("asset".equals(iconType)) {
                        // Handle SVG from assets
                        AssetManager assetManager = _context.getAssets();
                        InputStream inputStream = null;
                        try {
                            String iconPath = "public/" + buttonNearDone.getIcon();
                            try {
                                inputStream = assetManager.open(iconPath);
                            } catch (IOException e) {
                                try {
                                    inputStream = assetManager.open(buttonNearDone.getIcon());
                                } catch (IOException e2) {
                                    Log.e("InAppBrowser", "SVG file not found in assets: " + buttonNearDone.getIcon());
                                    buttonNearDoneView.setVisibility(View.GONE);
                                    return;
                                }
                            }

                            SVG svg = SVG.getFromInputStream(inputStream);
                            if (svg == null) {
                                Log.e("InAppBrowser", "Failed to parse SVG icon: " + buttonNearDone.getIcon());
                                buttonNearDoneView.setVisibility(View.GONE);
                                return;
                            }

                            float width = buttonNearDone.getWidth() > 0 ? buttonNearDone.getWidth() : 24;
                            float height = buttonNearDone.getHeight() > 0 ? buttonNearDone.getHeight() : 24;
                            float density = _context.getResources().getDisplayMetrics().density;
                            int targetWidth = Math.round(width * density);
                            int targetHeight = Math.round(height * density);

                            svg.setDocumentWidth(targetWidth);
                            svg.setDocumentHeight(targetHeight);

                            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(bitmap);
                            svg.renderToCanvas(canvas);

                            Paint paint = new Paint();
                            paint.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
                            Canvas colorFilterCanvas = new Canvas(bitmap);
                            colorFilterCanvas.drawBitmap(bitmap, 0, 0, paint);

                            buttonNearDoneView.setImageBitmap(bitmap);
                            buttonNearDoneView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            buttonNearDoneView.setPadding(12, 12, 12, 12);
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

                    buttonNearDoneView.setOnClickListener((view) -> _options.getCallbacks().buttonNearDoneClicked());
                }
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
            shareButton.setOnClickListener((view) -> {
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
        handleProxyResponse(id, null);
    }

    public void handleProxyResultOk(JSONObject result, String id) {
        Log.i("InAppBrowserProxy", String.format("handleProxyResultOk: %s, ok: %s, id: %s", result, true, id));
        JSObject response = new JSObject();
        response.put("response", result);
        handleProxyResponse(id, response);
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
                            if (url.startsWith("intent:")) {
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
                                rejectOpenWebViewIfNeeded("No handler available for external URL: " + url);
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

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (view == null || _webView == null) {
                        return null;
                    }
                    if (!shouldUseNativeProxy()) {
                        return null;
                    }

                    String requestUrl = request.getUrl().toString();
                    String originalUrl;
                    String method;
                    Map<String, String> requestHeaders = new HashMap<>();
                    String base64Body = "";

                    if (requestUrl.contains("/_capgo_proxy_?")) {
                        Uri uri = request.getUrl();
                        originalUrl = uri.getQueryParameter("u");
                        String requestId = uri.getQueryParameter("rid");
                        if (originalUrl == null || requestId == null) {
                            return null;
                        }

                        ProxyBridge.StoredRequest stored = proxyBridge != null ? proxyBridge.getAndRemove(requestId) : null;
                        if (stored != null) {
                            method = stored.method;
                            base64Body = stored.base64Body;
                            try {
                                JSONObject headers = new JSONObject(stored.headersJson);
                                for (Iterator<String> iterator = headers.keys(); iterator.hasNext(); ) {
                                    String key = iterator.next();
                                    requestHeaders.put(key, headers.getString(key));
                                }
                            } catch (JSONException error) {
                                Log.e("InAppBrowserProxy", "Failed to parse stored proxy headers", error);
                            }
                        } else {
                            method = request.getMethod();
                        }
                    } else {
                        Pattern pattern = _options.getProxyRequestsPattern();
                        if (pattern != null && !pattern.matcher(requestUrl).find()) {
                            return null;
                        }
                        if (
                            !_options.getProxyRequests() &&
                            pattern == null &&
                            _options.getOutboundProxyRules().isEmpty() &&
                            _options.getInboundProxyRules().isEmpty()
                        ) {
                            return null;
                        }

                        originalUrl = requestUrl;
                        method = request.getMethod();
                        if (request.getRequestHeaders() != null) {
                            requestHeaders.putAll(request.getRequestHeaders());
                        }
                    }

                    NativeRequestContext requestContext = new NativeRequestContext(
                        originalUrl,
                        method,
                        requestHeaders,
                        base64Body,
                        request.isForMainFrame()
                    );

                    boolean legacyProxyMode =
                        _options.getProxyRequests() &&
                        _options.getProxyRequestsPattern() == null &&
                        _options.getOutboundProxyRules().isEmpty() &&
                        _options.getInboundProxyRules().isEmpty();

                    NativeProxyRule outboundRule = legacyProxyMode
                        ? new NativeProxyRule(null, null, null, null, null, null, null, null, false, NativeProxyRule.Action.DELEGATE_TO_JS)
                        : findMatchingRule(_options.getOutboundProxyRules(), requestContext, null);

                    if (outboundRule != null && outboundRule.getAction() == NativeProxyRule.Action.CANCEL) {
                        return createCanceledResponse();
                    }

                    if (outboundRule != null && outboundRule.getAction() == NativeProxyRule.Action.DELEGATE_TO_JS) {
                        String proxyId = UUID.randomUUID().toString();
                        ProxiedRequest proxiedRequest = new ProxiedRequest();
                        proxiedRequest.requestContext = requestContext;
                        addProxiedRequest(proxyId, proxiedRequest);

                        String dialogId = instanceId != null ? instanceId : "";
                        _options
                            .getCallbacks()
                            .proxyRequestEvent(
                                proxyId,
                                "outbound",
                                requestContext.url,
                                requestContext.method,
                                serializeHeaders(requestContext.headers),
                                requestContext.base64Body.isEmpty() ? null : requestContext.base64Body,
                                null,
                                null,
                                null,
                                dialogId
                            );

                        try {
                            if (proxiedRequest.semaphore.tryAcquire(1, 10, TimeUnit.SECONDS)) {
                                if (proxiedRequest.canceled) {
                                    return createCanceledResponse();
                                }
                                if (proxiedRequest.response != null) {
                                    return proxiedRequest.response;
                                }
                                requestContext = proxiedRequest.requestContext != null ? proxiedRequest.requestContext : requestContext;
                            } else {
                                removeProxiedRequest(proxyId);
                                return null;
                            }
                        } catch (InterruptedException error) {
                            Log.e("InAppBrowserProxy", "Semaphore wait error", error);
                            return null;
                        }
                    }

                    NativeResponseData nativeResponse;
                    try {
                        nativeResponse = performNativeRequest(requestContext);
                    } catch (IOException error) {
                        Log.e("InAppBrowserProxy", "Native request failed for: " + requestContext.url, error);
                        return null;
                    }

                    NativeProxyRule inboundRule = findMatchingRule(_options.getInboundProxyRules(), requestContext, nativeResponse);
                    if (inboundRule == null) {
                        return buildWebResourceResponse(nativeResponse);
                    }
                    if (inboundRule.getAction() == NativeProxyRule.Action.CANCEL) {
                        return createCanceledResponse();
                    }
                    if (inboundRule.getAction() == NativeProxyRule.Action.CONTINUE) {
                        return buildWebResourceResponse(nativeResponse);
                    }

                    String proxyId = UUID.randomUUID().toString();
                    ProxiedRequest proxiedRequest = new ProxiedRequest();
                    proxiedRequest.requestContext = requestContext;
                    proxiedRequest.nativeResponse = nativeResponse;
                    addProxiedRequest(proxyId, proxiedRequest);

                    String dialogId = instanceId != null ? instanceId : "";
                    _options
                        .getCallbacks()
                        .proxyRequestEvent(
                            proxyId,
                            "inbound",
                            requestContext.url,
                            requestContext.method,
                            serializeHeaders(requestContext.headers),
                            requestContext.base64Body.isEmpty() ? null : requestContext.base64Body,
                            nativeResponse.statusCode,
                            serializeHeaders(nativeResponse.headers),
                            Base64.encodeToString(nativeResponse.bodyBytes, Base64.NO_WRAP),
                            dialogId
                        );

                    try {
                        if (proxiedRequest.semaphore.tryAcquire(1, 10, TimeUnit.SECONDS)) {
                            if (proxiedRequest.canceled) {
                                return createCanceledResponse();
                            }
                            if (proxiedRequest.response != null) {
                                return proxiedRequest.response;
                            }
                            return buildWebResourceResponse(nativeResponse);
                        }
                        removeProxiedRequest(proxyId);
                    } catch (InterruptedException error) {
                        Log.e("InAppBrowserProxy", "Semaphore wait error", error);
                    }
                    return buildWebResourceResponse(nativeResponse);
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
                    if (_options.getProxyRequests() && proxyBridgeScript != null && proxyAccessToken != null) {
                        String scriptWithToken = proxyBridgeScript.replace("___CAPGO_PROXY_TOKEN___", proxyAccessToken);
                        view.evaluateJavascript(scriptWithToken, null);
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
                                resolveOpenWebViewIfNeeded();
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
                                                        resolveOpenWebViewIfNeeded();
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
                    if (request != null && request.isForMainFrame() && !isInitialized) {
                        CharSequence description = error != null ? error.getDescription() : null;
                        String message = description != null ? "Initial page load failed: " + description : "Initial page load failed";
                        rejectOpenWebViewIfNeeded(message);
                    }
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
        // First, stop any ongoing operations and disable further interactions
        if (_webView != null) {
            try {
                isDismissing = true;

                // Stop loading first to prevent any ongoing operations
                _webView.stopLoading();

                if (pendingCameraLaunchPermissionRequest != null) {
                    permissionHandler.clearPendingPermissionRequest(pendingCameraLaunchPermissionRequest);
                    pendingCameraLaunchPermissionRequest = null;
                }

                if (currentPermissionRequest != null) {
                    try {
                        permissionHandler.clearPendingPermissionRequest(currentPermissionRequest);
                        currentPermissionRequest.deny();
                    } catch (Exception e) {
                        Log.w("InAppBrowser", "Could not deny pending media permission request: " + e.getMessage());
                    } finally {
                        currentPermissionRequest = null;
                    }
                }

                // Clear any pending callbacks to prevent memory leaks
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                    mFilePathCallback = null;
                }
                tempCameraUri = null;

                // Clear file inputs for security/privacy before destroying WebView
                try {
                    String clearInputsScript = """
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

                forceStopMediaCapture(_webView);

                // Remove JavaScript interfaces before destroying
                _webView.removeJavascriptInterface("AndroidInterface");
                _webView.removeJavascriptInterface("PreShowScriptInterface");
                _webView.removeJavascriptInterface("PrintInterface");

                _webView.removeAllViews();

                final WebView webViewToDestroy = _webView;
                _webView = null;
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                final AtomicBoolean destroyCalled = new AtomicBoolean(false);

                final Runnable doDestroy = () -> {
                    if (!destroyCalled.getAndSet(true)) {
                        try {
                            webViewToDestroy.onPause();
                            webViewToDestroy.destroy();
                        } catch (Exception e) {
                            Log.e("InAppBrowser", "Error destroying WebView: " + e.getMessage());
                        }
                    }
                };

                // Schedule the fallback before the navigation handoff so destroy still
                // runs if setWebViewClient/loadUrl throws.
                mainHandler.postDelayed(doDestroy, 3000);

                // Let Chromium finish unloading the page before pausing and destroying
                // the renderer so active media capture can be released cleanly.
                try {
                    webViewToDestroy.setWebViewClient(
                        new WebViewClient() {
                            @Override
                            public void onPageFinished(WebView view, String url) {
                                if ("about:blank".equals(url)) {
                                    mainHandler.postDelayed(doDestroy, 200);
                                }
                            }
                        }
                    );
                    webViewToDestroy.loadUrl("about:blank");
                } catch (Exception e) {
                    Log.e("InAppBrowser", "Falling back to immediate WebView destroy: " + e.getMessage());
                    doDestroy.run();
                }
            } catch (Exception e) {
                Log.e("InAppBrowser", "Error during WebView cleanup: " + e.getMessage());
                _webView = null;
            }
        }

        // Shutdown executor service asynchronously to avoid blocking UI thread
        shutdownExecutorServiceAsync();

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

    private void forceStopMediaCapture(WebView webView) {
        try {
            String stopMediaCaptureScript = """
                (function() {
                  var stoppedTracks = 0;
                  function stopStream(value) {
                    try {
                      if (!value || typeof value.getTracks !== 'function') {
                        return;
                      }
                      var tracks = value.getTracks();
                      for (var i = 0; i < tracks.length; i++) {
                        try {
                          tracks[i].stop();
                          stoppedTracks++;
                        } catch (e) {}
                      }
                    } catch (e) {}
                  }

                  try {
                    var mediaElements = document.querySelectorAll('audio,video');
                    for (var i = 0; i < mediaElements.length; i++) {
                      var element = mediaElements[i];
                      try { stopStream(element.srcObject); } catch (e) {}
                      try { element.pause(); } catch (e) {}
                      try { element.srcObject = null; } catch (e) {}
                    }
                  } catch (e) {}

                  try {
                    var windowKeys = Object.keys(window);
                    for (var j = 0; j < windowKeys.length; j++) {
                      var value = window[windowKeys[j]];
                      stopStream(value);
                      if (Array.isArray(value)) {
                        for (var k = 0; k < value.length; k++) {
                          stopStream(value[k]);
                        }
                      }
                    }
                  } catch (e) {}

                  return stoppedTracks;
                })();
                """;
            webView.evaluateJavascript(stopMediaCaptureScript, (result) ->
                Log.d("InAppBrowser", "Stopped active media tracks before dismiss: " + result)
            );
        } catch (Exception e) {
            Log.w("InAppBrowser", "Could not force-stop media capture before dismiss: " + e.getMessage());
        }
    }

    private void shutdownExecutorServiceAsync() {
        if (executorService.isShutdown()) {
            return;
        }
        Thread shutdownThread = new Thread(
            () -> {
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
            },
            "InAppBrowser-ExecutorShutdown"
        );
        shutdownThread.setDaemon(true);
        shutdownThread.start();
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

    private String loadProxyBridgeScript() {
        try (InputStream is = _context.getAssets().open("proxy-bridge.js")) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
            return result.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            Log.e("InAppBrowserProxy", "Failed to load proxy-bridge.js", e);
            return null;
        }
    }

    private boolean shouldUseNativeProxy() {
        return _options != null && _options.shouldEnableNativeProxy();
    }

    private String decodeBase64Body(String base64Body) {
        if (base64Body == null || base64Body.isEmpty()) {
            return null;
        }
        try {
            return new String(Base64.decode(base64Body, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String serializeHeaders(Map<String, String> headers) {
        JSONObject jsonObject = new JSONObject();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                try {
                    jsonObject.put(entry.getKey(), entry.getValue());
                } catch (JSONException ignored) {}
            }
        }
        return jsonObject.toString();
    }

    private NativeProxyRule findMatchingRule(
        List<NativeProxyRule> rules,
        NativeRequestContext requestContext,
        NativeResponseData responseData
    ) {
        if (rules == null) {
            return null;
        }
        String decodedRequestBody = decodeBase64Body(requestContext.base64Body);
        String serializedRequestHeaders = serializeHeaders(requestContext.headers);
        String serializedResponseHeaders = responseData != null ? serializeHeaders(responseData.headers) : null;
        String decodedResponseBody = responseData != null ? new String(responseData.bodyBytes, StandardCharsets.UTF_8) : null;
        Integer statusCode = responseData != null ? responseData.statusCode : null;

        for (NativeProxyRule rule : rules) {
            if (
                rule.matches(
                    requestContext.url,
                    requestContext.method,
                    serializedRequestHeaders,
                    decodedRequestBody,
                    requestContext.mainFrame,
                    statusCode,
                    serializedResponseHeaders,
                    decodedResponseBody
                )
            ) {
                return rule;
            }
        }
        return null;
    }

    private WebResourceResponse createCanceledResponse() {
        WebResourceResponse response = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        response.setStatusCodeAndReasonPhrase(204, "No Content");
        response.setResponseHeaders(new HashMap<>());
        return response;
    }

    private WebResourceResponse buildWebResourceResponse(NativeResponseData responseData) {
        WebResourceResponse webResourceResponse = new WebResourceResponse(
            responseData.contentType,
            "utf-8",
            new ByteArrayInputStream(responseData.bodyBytes)
        );
        String reasonPhrase = getReasonPhrase(responseData.statusCode);
        if (reasonPhrase.isEmpty()) {
            reasonPhrase = "Unknown";
        }
        webResourceResponse.setStatusCodeAndReasonPhrase(responseData.statusCode, reasonPhrase);
        webResourceResponse.setResponseHeaders(responseData.headers);
        return webResourceResponse;
    }

    private NativeResponseData performNativeRequest(NativeRequestContext requestContext) throws IOException {
        URL url = new URL(requestContext.url);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(requestContext.method != null ? requestContext.method : "GET");
        conn.setInstanceFollowRedirects(true);

        for (Map.Entry<String, String> entry : requestContext.headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        if (requestContext.base64Body != null && !requestContext.base64Body.isEmpty()) {
            byte[] bodyBytes = Base64.decode(requestContext.base64Body, Base64.DEFAULT);
            conn.setDoOutput(true);
            conn.getOutputStream().write(bodyBytes);
        }

        int status = conn.getResponseCode();
        InputStream inputStream;
        try {
            inputStream = conn.getInputStream();
        } catch (IOException e) {
            inputStream = conn.getErrorStream();
        }

        byte[] bodyBytes = new byte[0];
        if (inputStream != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = inputStream.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            inputStream.close();
            bodyBytes = baos.toByteArray();
        }

        Map<String, String> responseHeaders = new HashMap<>();
        for (Map.Entry<String, java.util.List<String>> entry : conn.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                responseHeaders.put(entry.getKey(), entry.getValue().get(0));
            }
        }

        String contentType = responseHeaders.get("Content-Type");
        if (contentType == null) {
            contentType = responseHeaders.get("content-type");
        }
        if (contentType == null) {
            contentType = conn.getContentType();
        }

        conn.disconnect();
        return new NativeResponseData(status, contentType, responseHeaders, bodyBytes);
    }

    public void handleProxyResponse(String requestId, JSObject response) {
        ProxiedRequest proxiedRequest;
        synchronized (proxiedRequestsHashmap) {
            proxiedRequest = proxiedRequestsHashmap.get(requestId);
        }
        if (proxiedRequest == null) {
            Log.e("InAppBrowserProxy", "No pending request for id: " + requestId);
            return;
        }

        if (response == null) {
            synchronized (proxiedRequestsHashmap) {
                proxiedRequestsHashmap.remove(requestId);
            }
            proxiedRequest.semaphore.release();
            return;
        }

        try {
            Boolean canceled = response.getBool("cancel");
            proxiedRequest.canceled = Boolean.TRUE.equals(canceled);

            JSObject requestOverride = response.getJSObject("request");
            if (requestOverride != null && proxiedRequest.requestContext != null) {
                String url = requestOverride.getString("url", proxiedRequest.requestContext.url);
                String method = requestOverride.getString("method", proxiedRequest.requestContext.method);
                JSObject headersObject = requestOverride.getJSObject("headers");
                Map<String, String> headers = proxiedRequest.requestContext.headers;
                if (headersObject != null) {
                    headers = new HashMap<>();
                    Iterator<String> keys = headersObject.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        headers.put(key, headersObject.getString(key));
                    }
                }
                String body = requestOverride.getString("body", proxiedRequest.requestContext.base64Body);
                proxiedRequest.requestContext = new NativeRequestContext(
                    url,
                    method,
                    headers,
                    body,
                    proxiedRequest.requestContext.mainFrame
                );
            }

            JSObject responseOverride = response.getJSObject("response");
            if (responseOverride == null && response.get("status") != null) {
                responseOverride = response;
            }

            if (responseOverride != null) {
                String base64Body = responseOverride.getString("body");
                int status = responseOverride.getInteger("status", 200);
                JSObject headers = responseOverride.getJSObject("headers");

                Map<String, String> responseHeaders = new HashMap<>();
                if (headers != null) {
                    Iterator<String> keys = headers.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        responseHeaders.put(key, headers.getString(key));
                    }
                }

                byte[] bodyBytes = (base64Body != null && !base64Body.isEmpty()) ? Base64.decode(base64Body, Base64.DEFAULT) : new byte[0];

                String contentType = responseHeaders.get("content-type");
                if (contentType == null) {
                    contentType = responseHeaders.get("Content-Type");
                }
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                if (status < 100 || status > 599) {
                    status = 200;
                }
                proxiedRequest.nativeResponse = new NativeResponseData(status, contentType, responseHeaders, bodyBytes);
                proxiedRequest.response = buildWebResourceResponse(proxiedRequest.nativeResponse);
            }
        } catch (Exception e) {
            Log.e("InAppBrowserProxy", "Error building proxy response", e);
        }

        synchronized (proxiedRequestsHashmap) {
            proxiedRequestsHashmap.remove(requestId);
        }
        proxiedRequest.semaphore.release();
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
        String script = """
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
        File image = File.createTempFile(imageFileName /* prefix */, ".jpg" /* suffix */, storageDir /* directory */);
        return image;
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

    public void setEnabledSafeTopMargin(boolean enabled) {
        if (_options.getEnabledSafeTopMargin() == enabled) return;
        _options.setEnabledSafeTopMargin(enabled);
        if (_webView != null) {
            ViewCompat.requestApplyInsets(_webView);
        }
    }

    public void setEnabledSafeBottomMargin(boolean enabled) {
        if (_options.getEnabledSafeMargin() == enabled) return;
        _options.setEnabledSafeMargin(enabled);
        if (_webView != null) {
            ViewCompat.requestApplyInsets(_webView);
        }
    }

    /**
     * Convert density-independent pixels (dp) to actual pixels
     */
    private float getPixels(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, _context.getResources().getDisplayMetrics());
    }
}
