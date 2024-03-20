package ee.forgr.capacitor_inappbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import java.net.URI;
import java.net.URISyntaxException;
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
    getWindow()
      .setFlags(
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

    _webView.setWebChromeClient(
      new WebChromeClient() {
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
          Toast
            .makeText(
              WebViewDialog.this.activity,
              "Permission Denied",
              Toast.LENGTH_SHORT
            )
            .show();
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

          if (!url.startsWith("https://") && !url.startsWith("http://")) {
            try {
              Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              context.startActivity(intent);
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
          try {
            URI uri = new URI(url);
            setTitle(uri.getHost());
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
      }
    );
  }

  @Override
  public void onBackPressed() {
    if (
      _webView.canGoBack() &&
      (
        TextUtils.equals(_options.getToolbarType(), "navigation") ||
        _options.getActiveNativeNavigationForWebview()
      )
    ) {
      _webView.goBack();
    } else if (!_options.getDisableGoBackOnNativeApplication()) {
      super.onBackPressed();
    }
  }
}
