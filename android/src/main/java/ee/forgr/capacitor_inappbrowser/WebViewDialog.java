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
    private String instanceId = "";
    private final Map<String, ProxiedRequest> proxiedRequestsHashmap = new HashMap<>();
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

    public void setInstanceId(String id) {
        this.instanceId = id != null ? id : "";
    }

    public String getInstanceId() {
        return instanceId;
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
    }

    private boolean isJavaScriptControlAllowed() {
        return _options != null && _options.getAllowWebViewJsVisibilityControl();
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
                            WebView.HitTestResult result = view.getHitTestResult();
                            String data = result.getExtra();
                            if (data != null && !data.isEmpty()) {
                                Log.d("InAppBrowser", "Opening target=_blank link externally: " + data);
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(data));
                                browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                _webView.getContext().startActivity(browserIntent);
                                return false;
                            }
                        } catch (Exception e) {
                            Log.e("InAppBrowser", "Error opening external link: " + e.getMessage());
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

        if (this._options.isHidden()) {
            if (_options.getInvisibilityMode() == Options.InvisibilityMode.FAKE_VISIBLE) {
                show();
                applyHiddenMode();
            }
            _options.getPluginCall().resolve();
        } else if (!this._options.isPresentAfterPageLoad()) {
            show();
            _options.getPluginCall().resolve();
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

            String script = String.format(
                """
                (function() {
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
                      }%s
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
                mobileAppExtras
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
                buttonNearDoneView.setOnClickListener((view) -> _options.getCallbacks().buttonNearDoneClicked());
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
            for (Iterator<String> it = headers.keys(); it.hasNext(); ) {
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
                    Log.d("InAppBrowser", "shouldOverrideUrlLoading: " + url);

                    boolean isNotHttpOrHttps = !url.startsWith("https://") && !url.startsWith("http://");

                    // Check if URL is an internal WebView scheme (data:, blob:, about:, javascript:)
                    // These should always be allowed to load in the WebView
                    boolean isInternalScheme =
                        url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("about:") || url.startsWith("javascript:");

                    // If preventDeeplink is true, don't handle any non-http(s) URLs except internal schemes
                    if (_options.getPreventDeeplink()) {
                        Log.d("InAppBrowser", "preventDeeplink is true");
                        if (isNotHttpOrHttps && !isInternalScheme) {
                            return true;
                        }
                    }

                    // Allow internal schemes to load without further processing
                    if (isInternalScheme) {
                        // Extract scheme for logging (avoid logging full data URLs which may contain sensitive content)
                        int colonIndex = url.indexOf(':');
                        String scheme = (colonIndex > 0) ? url.substring(0, Math.min(colonIndex + 1, 20)) : "unknown:";
                        Log.d("InAppBrowser", "Internal scheme detected, allowing WebView to handle: " + scheme);
                        return false;
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
                                String jsTemplate = """
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
                                          id: '%s',
                                          webviewId: '%s'
                                        });
                                      }).catch((e) => {
                                        Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({
                                          ok: false,
                                          result: e.toString(),
                                          id: '%s',
                                          webviewId: '%s'
                                        });
                                      });
                                    } catch (e) {
                                      Capacitor.Plugins.InAppBrowser.lsuakdchgbbaHandleProxiedRequest({
                                        ok: false,
                                        result: e.toString(),
                                        id: '%s',
                                        webviewId: '%s'
                                      });
                                    }
                                    """;
                                String dialogId = instanceId != null ? instanceId : "";
                                String s = String.format(
                                    jsTemplate,
                                    headers,
                                    toBase64(request.getUrl().toString()),
                                    request.getMethod(),
                                    requestId,
                                    dialogId,
                                    requestId,
                                    dialogId,
                                    requestId,
                                    dialogId
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

    /**
     * Convert density-independent pixels (dp) to actual pixels
     */
    private float getPixels(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, _context.getResources().getDisplayMetrics());
    }
}
