package ee.forgr.capacitor_inappbrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import java.io.File;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class WebViewDialog extends Dialog {

  private WebView _webView;
  private Toolbar _toolbar;
  private Options _options;
  private Context _context;
  public Activity activity;
  private boolean isInitialized = false;

  public PermissionRequest currentPermissionRequest;
  public static final int FILE_CHOOSER_REQUEST_CODE = 1000;
  public ValueCallback<Uri> mUploadMessage;
  public ValueCallback<Uri[]> mFilePathCallback;

  public static Uri capturedImageUri = null;
  private String mCameraPhotoPath;

  private static final String TAG = WebViewDialog.class.getSimpleName();

  public interface PermissionHandler {
    void handleCameraPermissionRequest(PermissionRequest request);
  }

  private PermissionHandler permissionHandler;

  public WebViewDialog(
    Context context,
    int theme,
    Options options,
    PermissionHandler permissionHandler
  ) {
    super(context, theme);
    this._options = options;
    this._context = context;
    this.permissionHandler = permissionHandler;
    this.isInitialized = false;
  }

  public void presentWebView() {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setCancelable(true);
    if (!_options.useWhitePanelMode()) {
      getWindow()
        .setFlags(
          WindowManager.LayoutParams.FLAG_FULLSCREEN,
          WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    if (_options.useHardwareAcceleration()) {
      // Enable hardware acceleration for the webdialog window
      getWindow()
        .setFlags(
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
    }

    setContentView(R.layout.activity_browser);
    getWindow()
      .setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT
      );

    this._webView = findViewById(R.id.browser_view);

    if (_options.useHardwareAcceleration()) {
      // Enable hardware acceleration
      _webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    if (_options.useWhitePanelMode()) {
      // Show a white view until the page is loaded
      _webView.setBackgroundColor(Color.WHITE);
    }

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

    _webView.setWebViewClient(new WebViewClient());

    getWindow().getAttributes().windowAnimations =
      com.google.android.material.R.style.Animation_Design_BottomSheetDialog;

    _webView.setWebChromeClient(
      new WebChromeClient() {
        // Enable file open dialog
        @Override
        public boolean onShowFileChooser(
          WebView webView,
          ValueCallback<Uri[]> filePathCallback,
          WebChromeClient.FileChooserParams fileChooserParams
        ) {
          mFilePathCallback = filePathCallback;

          Intent takePictureIntent = new Intent(
            MediaStore.ACTION_IMAGE_CAPTURE
          );
          if (
            takePictureIntent.resolveActivity(_context.getPackageManager()) !=
            null
          ) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
              photoFile = createImageFile();
            } catch (IOException ex) {
              // Error occurred while creating the File
              Log.e(TAG, "Unable to create Image File", ex);
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
              Uri photoURI = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                photoFile
              );
              takePictureIntent.putExtra("PhotoPath", photoURI);
              capturedImageUri = photoURI;
              takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            }
          }

          Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
          contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
          contentSelectionIntent.setType(fileChooserParams.getAcceptTypes()[0]);

          Intent[] intentArray;
          if (takePictureIntent != null) {
            intentArray = new Intent[] { takePictureIntent };
          } else {
            intentArray = new Intent[0];
          }

          Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
          chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
          chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
          chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

          if (
            ContextCompat.checkSelfPermission(
              getContext(),
              Manifest.permission.CAMERA
            ) !=
            PackageManager.PERMISSION_GRANTED
          ) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
              getContext(),
              com.google.android.material.R.style.Theme_AppCompat_Light_Dialog_Alert
            );
            builder.setTitle(R.string.camera_permission_alert_title);
            builder.setMessage(R.string.camera_permission_alert_message);

            builder.setPositiveButton(
              R.string.camera_permission_alert_positive,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  goToSettings();
                }
              }
            );

            builder.setNegativeButton(
              R.string.camera_permission_alert_negative,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  // Cancel
                }
              }
            );

            builder.show();
            return false;
          }

          activity.startActivityForResult(
            chooserIntent,
            FILE_CHOOSER_REQUEST_CODE
          );
          return true;
        }

        // Grant permissions for cam
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
          Log.i(
            "INAPPBROWSER",
            "onPermissionRequest " + request.getResources().toString()
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
              break;
            }
          }
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

  /**
   * More info this method can be found at
   * http://developer.android.com/training/camera/photobasics.html
   *
   * @return
   * @throws IOException
   */
  private File createImageFile() throws IOException {
    // Create an image file name
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(
      new Date()
    );
    String imageFileName = "JPEG_" + timeStamp + "_";
    File storageDir = getContext()
      .getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    File image = File.createTempFile(
      imageFileName,/* prefix */
      ".jpg",/* suffix */
      storageDir/* directory */
    );

    // Save a file: path for use with ACTION_VIEW intents
    String mCurrentPhotoPath = image.getAbsolutePath();
    Log.i("FILEPICKER", mCurrentPhotoPath);
    return image;
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
      //TODO: Add share button functionality
    } else if (TextUtils.equals(_options.getToolbarType(), "navigation")) {
      //TODO: Remove share button when implemented
    } else if (TextUtils.equals(_options.getToolbarType(), "blank")) {
      _toolbar.setVisibility(View.GONE);
    } else {
      _toolbar.findViewById(R.id.forwardButton).setVisibility(View.GONE);
      _toolbar.findViewById(R.id.backButton).setVisibility(View.GONE);
    }
  }

  private void setWebViewClient() {
    _webView.setWebViewClient(
      new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(
          WebView view,
          WebResourceRequest request
        ) {
          Context context = view.getContext();
          String url = request.getUrl().toString();

          try {
            if (
              _options.getOpenSystemBrowserList().length() != 0 &&
              _options.getOpenSystemBrowserList().toList().contains(url)
            ) {
              openSystemBrowser(url, context);
              return true;
            }
          } catch (Exception e) {
            Log.e("SYSTEMBROWSERLINKS", e.getMessage());
          }

          if (!url.startsWith("http") && !url.startsWith("http://")) {
            try {
              openSystemBrowser(url, context);
              return true;
            } catch (ActivityNotFoundException e) {
              // Do nothing
            }
          }
          return false;
        }

        @Override
        public void onLoadResource(WebView view, String url) {
          super.onLoadResource(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
          super.onPageStarted(view, url, favicon);
          if (_options.getAutoClosePatterns().length() != 0) {
            try {
              String path = new URL(url).getPath();
              if (_options.getAutoClosePatterns().toList().contains(path)) {
                dismiss();
                _options.getCallbacks().urlChangeEvent(url);
                _webView.destroy();
              }
            } catch (Exception e) {
              Log.e("AUTOCLOSE", "Unable to cast autoclose params");
            }
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

        public void doUpdateVisitedHistory(
          WebView view,
          String url,
          boolean isReload
        ) {
          if (!isReload) {
            _options.getCallbacks().urlChangeEvent(url);
          }
          super.doUpdateVisitedHistory(view, url, isReload);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
          super.onPageFinished(view, url);
          _options.getCallbacks().pageLoaded();
          if (!isInitialized) {
            isInitialized = true;
            _webView.clearHistory();
            if (_options.isPresentAfterPageLoad()) {
              show();
              _options.getPluginCall().resolve();
            }
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

  private void openSystemBrowser(String url, Context context) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  private void goToSettings() {
    Intent intent = new Intent();
    intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
    intent.setData(uri);
    getContext().startActivity(intent);
  }
}
