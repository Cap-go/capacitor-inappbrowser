package ee.forgr.capacitor_inappbrowser;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class WebViewDialog extends Dialog {

    private WebView _webView;
    private Toolbar _toolbar;
    private Options _options;
    private boolean isInitialized = false;

    public WebViewDialog(Context context, int theme, Options options) {
        super(context, theme);
        this._options = options;
        this.isInitialized = false;
    }

    public void presentWebView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_browser);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

        this._webView = findViewById(R.id.browser_view);

        _webView.getSettings().setJavaScriptEnabled(true);
        _webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        _webView.getSettings().setDatabaseEnabled(true);
        _webView.getSettings().setDomStorageEnabled(true);
        _webView.getSettings().setPluginState(android.webkit.WebSettings.PluginState.ON);
        _webView.getSettings().setLoadWithOverviewMode(true);
        _webView.getSettings().setUseWideViewPort(true);

        Map<String, String> requestHeaders = new HashMap<>();
        if (_options.getHeaders() != null) {
            Iterator<String> keys = _options.getHeaders().keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (TextUtils.equals(key, "User-Agent")) {
                    _webView.getSettings().setUserAgentString(_options.getHeaders().getString(key));
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

    public void setUrl(String url) {
        Map<String, String> requestHeaders = new HashMap<>();
        if (_options.getHeaders() != null) {
            Iterator<String> keys = _options.getHeaders().keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (TextUtils.equals(key, "User-Agent")) {
                    _webView.getSettings().setUserAgentString(_options.getHeaders().getString(key));
                } else {
                    requestHeaders.put(key, _options.getHeaders().getString(key));
                }
            }
        }
        _webView.loadUrl(url, requestHeaders);
    }

    private void setTitle(String newTitleText) {
        TextView textView = (TextView) _toolbar.findViewById(R.id.titleText);
        textView.setText(newTitleText);
    }

    private void setupToolbar() {
        _toolbar = this.findViewById(R.id.tool_bar);
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
                    dismiss();
                    _options.getCallbacks().closeEvent(_webView.getUrl());
                }
            }
        );

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
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
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
                    _options.getCallbacks().urlChangeEvent(url);
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
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    _options.getCallbacks().pageLoadError();
                }
            }
        );
    }

    @Override
    public void onBackPressed() {
        if (_webView.canGoBack() && TextUtils.equals(_options.getToolbarType(), "navigation")) {
            _webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
