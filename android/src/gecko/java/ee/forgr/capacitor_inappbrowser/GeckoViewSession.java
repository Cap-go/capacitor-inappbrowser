package ee.forgr.capacitor_inappbrowser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.JSObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class GeckoViewSession implements BrowserSession {

    private static final String LOG_TAG = "GeckoViewSession";
    private static final String BRIDGE_SCHEME = "capgoiab";
    private static final String BRIDGE_HOST = "bridge";
    private static final Object RUNTIME_LOCK = new Object();

    private static GeckoRuntime sharedRuntime;

    private final Context context;
    private final Activity activity;
    private final Options options;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Dialog dialog;
    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private Toolbar toolbar;
    private TextView titleView;
    private TextView subtitleView;
    private ImageButton backButton;
    private boolean canGoBack;
    private boolean canGoForward;
    private boolean pageFinished;
    private boolean dismissed;
    private boolean hidden;
    private String instanceId;
    private String currentUrl = "";
    private String currentTitle = "";

    public GeckoViewSession(
        Context context,
        int theme,
        Options options,
        WebViewDialog.PermissionHandler permissionHandler,
        android.webkit.WebView capacitorWebView,
        Activity activity
    ) {
        this.context = context;
        this.activity = activity;
        this.options = options;
        this.hidden = options.isHidden();
        createDialog(theme);
    }

    @Override
    public void setInstanceId(String id) {
        instanceId = id;
    }

    @Override
    public void present() {
        runOnMainThread(() -> {
            if (dismissed) {
                return;
            }
            ensureSession();
            loadCurrentUrl();
            if (!options.isPresentAfterPageLoad() && !hidden) {
                showInternal();
            }
        });
    }

    @Override
    public String getUrl() {
        return currentUrl;
    }

    @Override
    public void setUrl(String url) {
        runOnMainThread(() -> {
            if (url == null || url.trim().isEmpty()) {
                return;
            }
            currentUrl = url;
            options.setUrl(url);
            updateToolbarText();
            if (geckoSession != null) {
                loadUri(url);
            }
        });
    }

    @Override
    public void postMessageToJS(Object detail) {
        try {
            JSObject payload = new JSObject();
            payload.put("detail", detail);
            executeScript("window.dispatchEvent(new CustomEvent('messageFromNative', " + payload + "));\n");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to send message to Gecko page", e);
        }
    }

    @Override
    public void setHidden(boolean hidden) {
        runOnMainThread(() -> {
            this.hidden = hidden;
            options.setHidden(hidden);
            if (hidden) {
                hideInternal();
            } else if (!options.isPresentAfterPageLoad() || pageFinished) {
                showInternal();
            }
        });
    }

    @Override
    public boolean isShowing() {
        return dialog != null && dialog.isShowing() && !hidden;
    }

    @Override
    public void show() {
        runOnMainThread(() -> {
            hidden = false;
            options.setHidden(false);
            if (!options.isPresentAfterPageLoad() || pageFinished) {
                showInternal();
            }
        });
    }

    @Override
    public void executeScript(String script) {
        runOnMainThread(() -> {
            if (geckoSession == null || script == null || script.trim().isEmpty()) {
                return;
            }
            loadJavascript(script);
        });
    }

    @Override
    public void takeScreenshot(ScreenshotResultCallback callback) {
        runOnMainThread(() -> {
            if (geckoView == null) {
                callback.onError("GeckoView is not initialized");
                return;
            }

            geckoView.capturePixels().accept(
                bitmap -> {
                    if (bitmap == null) {
                        callback.onError("Failed to capture GeckoView pixels");
                        return;
                    }
                    encodeBitmap(bitmap, callback);
                },
                throwable -> {
                    callback.onError("Failed to capture GeckoView screenshot: " + throwable.getMessage());
                }
            );
        });
    }

    @Override
    public boolean goBack() {
        if (geckoSession == null || !canGoBack) {
            return false;
        }
        runOnMainThread(() -> geckoSession.goBack());
        return true;
    }

    @Override
    public void reload() {
        runOnMainThread(() -> {
            if (geckoSession != null) {
                geckoSession.reload();
            }
        });
    }

    @Override
    public void dismiss() {
        runOnMainThread(this::dismissInternal);
    }

    @Override
    public void updateDimensions(Integer width, Integer height, Integer x, Integer y) {
        runOnMainThread(() -> {
            if (width != null) {
                options.setWidth(width);
            }
            if (height != null) {
                options.setHeight(height);
            }
            if (x != null) {
                options.setX(x);
            }
            if (y != null) {
                options.setY(y);
            }
            applyDimensions();
        });
    }

    @Override
    public void setEnabledSafeTopMargin(boolean enabled) {
        runOnMainThread(() -> {
            options.setEnabledSafeTopMargin(enabled);
            if (geckoView != null) {
                ViewCompat.requestApplyInsets(geckoView);
            }
        });
    }

    @Override
    public void setEnabledSafeBottomMargin(boolean enabled) {
        runOnMainThread(() -> {
            options.setEnabledSafeMargin(enabled);
            if (geckoView != null) {
                ViewCompat.requestApplyInsets(geckoView);
            }
        });
    }

    private void createDialog(int theme) {
        dialog = new Dialog(context, theme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(parseColor(options.getBackgroundColor(), Color.WHITE));

        boolean showToolbar = shouldShowToolbar();
        if (showToolbar) {
            toolbar = createToolbar();
            root.addView(toolbar);
        }

        geckoView = new GeckoView(context);
        geckoView.setId(View.generateViewId());
        geckoView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        geckoView.setBackgroundColor(parseColor(options.getBackgroundColor(), Color.WHITE));
        geckoView.setFocusable(true);
        geckoView.setFocusableInTouchMode(true);
        configureInsets();
        root.addView(geckoView);

        dialog.setContentView(root);
        applyDimensions();
    }

    private Toolbar createToolbar() {
        Toolbar toolbarView = new Toolbar(context);
        toolbarView.setLayoutParams(
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) getPixels(56))
        );
        int toolbarColor = parseColor(options.getToolbarColor(), Color.WHITE);
        int toolbarTextColor = parseColor(options.getToolbarTextColor(), isDarkColor(toolbarColor) ? Color.WHITE : Color.BLACK);
        toolbarView.setBackgroundColor(toolbarColor);
        toolbarView.setPopupTheme(androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light);
        toolbarView.setContentInsetsRelative((int) getPixels(8), (int) getPixels(8));

        backButton = createToolbarButton(android.R.drawable.ic_media_previous, toolbarTextColor, view -> {
            if (canGoBack && geckoSession != null) {
                geckoSession.goBack();
            }
        });
        toolbarView.addView(backButton);

        LinearLayout center = new LinearLayout(context);
        Toolbar.LayoutParams centerParams = new Toolbar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerParams.gravity = Gravity.CENTER_VERTICAL;
        center.setLayoutParams(centerParams);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setTextColor(toolbarTextColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setSingleLine(true);
        center.addView(titleView);

        subtitleView = new TextView(context);
        subtitleView.setTextColor(adjustAlpha(toolbarTextColor, 0.72f));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitleView.setSingleLine(true);
        center.addView(subtitleView);
        toolbarView.addView(center);

        ImageButton reloadButton = createToolbarButton(android.R.drawable.ic_popup_sync, toolbarTextColor, view -> reload());
        toolbarView.addView(reloadButton);

        ImageButton auxButton = createAuxiliaryButton(toolbarTextColor);
        if (auxButton != null) {
            toolbarView.addView(auxButton);
        }

        ImageButton closeButton = createToolbarButton(android.R.drawable.ic_menu_close_clear_cancel, toolbarTextColor, view -> emitCloseAndDismiss());
        toolbarView.addView(closeButton);

        updateToolbarText();
        updateBackButton();
        return toolbarView;
    }

    private ImageButton createAuxiliaryButton(int tint) {
        if (options.getShowScreenshotButton()) {
            return createToolbarButton(android.R.drawable.ic_menu_camera, tint, view -> {
                takeScreenshot(
                    new ScreenshotResultCallback() {
                        @Override
                        public void onSuccess(JSObject screenshot) {}

                        @Override
                        public void onError(String message) {
                            Log.e(LOG_TAG, message);
                        }
                    }
                );
            });
        }

        Options.ButtonNearDone buttonNearDone = options.getButtonNearDone();
        if (buttonNearDone == null) {
            return null;
        }

        ImageButton button = new ImageButton(context);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(view -> {
            if (options.getCallbacks() != null) {
                options.getCallbacks().buttonNearDoneClicked();
            }
        });
        Drawable drawable = resolveButtonNearDoneDrawable(buttonNearDone);
        if (drawable != null) {
            button.setImageDrawable(drawable);
            button.setColorFilter(tint);
        }
        Toolbar.LayoutParams params = new Toolbar.LayoutParams((int) getPixels(40), (int) getPixels(40));
        button.setLayoutParams(params);
        return button;
    }

    private Drawable resolveButtonNearDoneDrawable(Options.ButtonNearDone buttonNearDone) {
        try {
            if (buttonNearDone.getIconTypeEnum() == Options.ButtonNearDone.AllIconTypes.VECTOR) {
                int resourceId = context.getResources().getIdentifier(buttonNearDone.getIcon(), "drawable", context.getPackageName());
                if (resourceId != 0) {
                    return AppCompatResources.getDrawable(context, resourceId);
                }
                return null;
            }

            InputStream inputStream;
            try {
                inputStream = context.getAssets().open("public/" + buttonNearDone.getIcon());
            } catch (IOException ignored) {
                inputStream = context.getAssets().open(buttonNearDone.getIcon());
            }
            try (InputStream closable = inputStream) {
                return Drawable.createFromStream(closable, buttonNearDone.getIcon());
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "Unable to load Gecko toolbar asset icon", e);
            return null;
        }
    }

    private ImageButton createToolbarButton(int iconRes, int tint, View.OnClickListener onClickListener) {
        ImageButton button = new ImageButton(context);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setImageResource(iconRes);
        button.setColorFilter(tint);
        button.setOnClickListener(onClickListener);
        Toolbar.LayoutParams params = new Toolbar.LayoutParams((int) getPixels(40), (int) getPixels(40));
        button.setLayoutParams(params);
        return button;
    }

    private void configureInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
            geckoView,
            (view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                int topPadding = options.getEnabledSafeTopMargin() ? insets.top : 0;
                int bottomPadding = options.getEnabledSafeMargin() ? insets.bottom : 0;
                view.setPadding(view.getPaddingLeft(), topPadding, view.getPaddingRight(), bottomPadding);
                geckoView.setVerticalClipping(bottomPadding);
                return windowInsets;
            }
        );
    }

    private void ensureSession() {
        if (geckoSession != null) {
            return;
        }

        geckoSession = new GeckoSession();
        geckoSession.setNavigationDelegate(createNavigationDelegate());
        geckoSession.setProgressDelegate(createProgressDelegate());
        geckoSession.setContentDelegate(createContentDelegate());
        geckoSession.open(getOrCreateRuntime(context));
        geckoSession.setActive(!hidden);
        geckoSession.setFocused(!hidden);
        geckoView.setSession(geckoSession);
        geckoView.setActivityContextDelegate(() -> activity);
        geckoView.coverUntilFirstPaint(parseColor(options.getBackgroundColor(), Color.WHITE));
    }

    private GeckoSession.NavigationDelegate createNavigationDelegate() {
        return new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession session, boolean canNavigateBack) {
                canGoBack = canNavigateBack;
                updateBackButton();
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean canNavigateForward) {
                canGoForward = canNavigateForward;
            }

            @Override
            public void onLocationChange(
                GeckoSession session,
                @Nullable String url,
                List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                Boolean hasUserGesture
            ) {
                currentUrl = url != null ? url : "";
                updateToolbarText();
                if (options.getCallbacks() != null) {
                    options.getCallbacks().urlChangeEvent(currentUrl);
                }
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, GeckoSession.NavigationDelegate.LoadRequest request) {
                Uri uri = Uri.parse(request.uri);
                if (BRIDGE_SCHEME.equals(uri.getScheme()) && BRIDGE_HOST.equals(uri.getHost())) {
                    handleBridgeUri(uri);
                    return GeckoResult.deny();
                }
                return GeckoResult.allow();
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession session, @Nullable String uri, org.mozilla.geckoview.WebRequestError error) {
                if (options.getCallbacks() != null) {
                    options.getCallbacks().pageLoadError();
                }
                return null;
            }
        };
    }

    private GeckoSession.ProgressDelegate createProgressDelegate() {
        return new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                currentUrl = url;
                updateToolbarText();
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                pageFinished = success;
                if (!success) {
                    if (options.getCallbacks() != null) {
                        options.getCallbacks().pageLoadError();
                    }
                    return;
                }

                injectBridgeScript();
                if (options.getPreShowScript() != null && !options.getPreShowScript().isBlank()) {
                    loadJavascript(options.getPreShowScript());
                }
                if (options.getCallbacks() != null) {
                    options.getCallbacks().pageLoaded();
                }
                if (options.isPresentAfterPageLoad() && !hidden) {
                    showInternal();
                }
            }
        };
    }

    private GeckoSession.ContentDelegate createContentDelegate() {
        return new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, @Nullable String title) {
                currentTitle = title != null ? title : "";
                updateToolbarText();
            }

            @Override
            public void onCloseRequest(GeckoSession session) {
                emitCloseAndDismiss();
            }
        };
    }

    private void handleBridgeUri(Uri uri) {
        String action = uri.getLastPathSegment();
        if (action == null) {
            return;
        }

        switch (action) {
            case "message" -> {
                String payload = decode(uri.getQueryParameter("payload"));
                if (payload != null && options.getCallbacks() != null) {
                    options.getCallbacks().javascriptCallback(payload);
                }
            }
            case "close" -> emitCloseAndDismiss();
            case "hide" -> {
                if (options.getAllowWebViewJsVisibilityControl()) {
                    setHidden(true);
                }
            }
            case "show" -> {
                if (options.getAllowWebViewJsVisibilityControl()) {
                    setHidden(false);
                }
            }
            case "screenshot" -> {
                if (!options.getAllowScreenshotsFromWebPage()) {
                    return;
                }
                String requestId = uri.getQueryParameter("requestId");
                if (requestId == null || requestId.isBlank()) {
                    return;
                }
                takeScreenshot(
                    new ScreenshotResultCallback() {
                        @Override
                        public void onSuccess(JSObject screenshot) {
                            resolveScreenshotPromise(requestId, screenshot);
                        }

                        @Override
                        public void onError(String message) {
                            rejectScreenshotPromise(requestId, message);
                        }
                    }
                );
            }
            default -> {
                Log.d(LOG_TAG, "Ignoring unknown Gecko bridge action: " + action);
            }
        }
    }

    private void emitCloseAndDismiss() {
        if (dismissed) {
            return;
        }
        if (options.getCallbacks() != null) {
            options.getCallbacks().closeEvent(currentUrl);
        }
        dismissInternal();
    }

    private void dismissInternal() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        hidden = true;

        if (geckoView != null) {
            try {
                geckoView.releaseSession();
            } catch (IllegalStateException ignored) {}
        }
        if (geckoSession != null) {
            try {
                if (geckoSession.isOpen()) {
                    geckoSession.close();
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to close GeckoSession cleanly", e);
            }
            geckoSession = null;
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private void showInternal() {
        if (dialog == null) {
            return;
        }
        hidden = false;
        if (!dialog.isShowing()) {
            dialog.show();
            applyDimensions();
        }
        View decorView = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (decorView != null) {
            decorView.setAlpha(1f);
            decorView.setVisibility(View.VISIBLE);
        }
        if (geckoView != null) {
            geckoView.setVisibility(View.VISIBLE);
            geckoView.setAlpha(1f);
            ViewCompat.requestApplyInsets(geckoView);
        }
        if (geckoSession != null) {
            geckoSession.setActive(true);
            geckoSession.setFocused(true);
        }
    }

    private void hideInternal() {
        View decorView = dialog != null && dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (decorView != null) {
            decorView.setAlpha(0f);
            decorView.setVisibility(View.INVISIBLE);
        }
        if (geckoView != null) {
            geckoView.setAlpha(0f);
            geckoView.setVisibility(View.INVISIBLE);
        }
        if (geckoSession != null) {
            geckoSession.setFocused(false);
            geckoSession.setActive(false);
        }
    }

    private void loadCurrentUrl() {
        String url = options.getUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        currentUrl = url;
        updateToolbarText();
        loadUri(url);
        warnAboutUnsupportedRequestCustomization();
    }

    private void warnAboutUnsupportedRequestCustomization() {
        String httpMethod = options.getHttpMethod();
        if (httpMethod != null && !httpMethod.isBlank() && !"GET".equalsIgnoreCase(httpMethod)) {
            Log.w(LOG_TAG, "GeckoViewSession does not yet support non-GET initial loads");
        }
        if (options.getHttpBody() != null) {
            Log.w(LOG_TAG, "GeckoViewSession does not yet support initial request bodies");
        }
        if (options.getProxyRequestsPattern() != null) {
            Log.w(LOG_TAG, "GeckoViewSession does not yet support proxyRequests on Android");
        }
        if (options.getEnableGooglePaySupport()) {
            Log.w(LOG_TAG, "GeckoViewSession does not yet include the Android WebView Google Pay compatibility polyfill");
        }
    }

    private void loadUri(String url) {
        if (geckoSession == null) {
            return;
        }

        Map<String, String> additionalHeaders = extractHeaders(options.getHeaders());
        if (additionalHeaders.isEmpty()) {
            geckoSession.loadUri(url);
            return;
        }

        geckoSession.load(
            new GeckoSession.Loader()
                .uri(url)
                .additionalHeaders(additionalHeaders)
                .headerFilter(GeckoSession.HEADER_FILTER_UNRESTRICTED_UNSAFE)
        );
    }

    private Map<String, String> extractHeaders(@Nullable JSObject source) {
        Map<String, String> headers = new HashMap<>();
        if (source == null) {
            return headers;
        }

        for (String key : source.keys()) {
            Object value = source.opt(key);
            if (value == null) {
                continue;
            }
            headers.put(key, String.valueOf(value));
        }

        return headers;
    }

    private void loadJavascript(String script) {
        String encoded = Base64.encodeToString(script.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String javascriptUri =
            "javascript:(function(){try{eval(atob('" + encoded + "'));}catch(e){console.error('Capgo Gecko eval failed',e);}})()";
        geckoSession.loadUri(javascriptUri);
    }

    private void injectBridgeScript() {
        String visibilityExtras = options.getAllowWebViewJsVisibilityControl()
            ? "hide:function(){location.href='" + bridgeBaseUri("hide") + "';},show:function(){location.href='" + bridgeBaseUri("show") + "';},"
            : "";
        String screenshotBridge = options.getAllowScreenshotsFromWebPage()
            ? "takeScreenshot:function(){return new Promise(function(resolve,reject){try{var requestId='screenshot_'+Date.now()+'_'+Math.random().toString(36).slice(2);window.__capgoInAppBrowserPendingScreenshots[requestId]={resolve:resolve,reject:reject};location.href='" +
              bridgeBaseUri("screenshot") +
              "?requestId='+encodeURIComponent(requestId);}catch(e){reject(e);}});},"
            : "";
        String script =
            "(function(){" +
            "window.__capgoInAppBrowserPendingScreenshots=window.__capgoInAppBrowserPendingScreenshots||{};" +
            "window.__capgoInAppBrowserResolveScreenshot=window.__capgoInAppBrowserResolveScreenshot||function(payload){var pending=window.__capgoInAppBrowserPendingScreenshots[payload.requestId];if(!pending){return;}delete window.__capgoInAppBrowserPendingScreenshots[payload.requestId];pending.resolve(payload.result);};" +
            "window.__capgoInAppBrowserRejectScreenshot=window.__capgoInAppBrowserRejectScreenshot||function(payload){var pending=window.__capgoInAppBrowserPendingScreenshots[payload.requestId];if(!pending){return;}delete window.__capgoInAppBrowserPendingScreenshots[payload.requestId];pending.reject(new Error(payload.message));};" +
            "window.mobileApp={" +
            "postMessage:function(message){try{var msg=typeof message==='string'?message:JSON.stringify(message);location.href='" + bridgeBaseUri("message") + "?payload='+encodeURIComponent(msg);}catch(e){console.error('Error in mobileApp.postMessage',e);}}," +
            "close:function(){location.href='" + bridgeBaseUri("close") + "';}," +
            visibilityExtras +
            screenshotBridge +
            "};" +
            "})();";
        loadJavascript(script);
    }

    private void resolveScreenshotPromise(String requestId, JSObject screenshot) {
        JSObject payload = new JSObject();
        payload.put("requestId", requestId);
        payload.put("result", screenshot);
        executeScript("window.__capgoInAppBrowserResolveScreenshot(" + payload + ");");
    }

    private void rejectScreenshotPromise(String requestId, String message) {
        JSObject payload = new JSObject();
        payload.put("requestId", requestId);
        payload.put("message", message);
        executeScript("window.__capgoInAppBrowserRejectScreenshot(" + payload + ");");
    }

    private void encodeBitmap(Bitmap bitmap, ScreenshotResultCallback callback) {
        new Thread(
            () -> {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        postError(callback, "Failed to encode Gecko screenshot");
                        return;
                    }

                    String base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
                    JSObject result = new JSObject();
                    result.put("format", "png");
                    result.put("mimeType", "image/png");
                    result.put("base64", base64);
                    result.put("dataUrl", "data:image/png;base64," + base64);
                    result.put("width", bitmap.getWidth());
                    result.put("height", bitmap.getHeight());
                    mainHandler.post(() -> {
                        if (options.getCallbacks() != null) {
                            options.getCallbacks().screenshotTaken(result);
                        }
                        callback.onSuccess(result);
                    });
                } catch (IOException e) {
                    postError(callback, "Failed to encode Gecko screenshot: " + e.getMessage());
                } finally {
                    bitmap.recycle();
                }
            },
            "Capgo-GeckoScreenshot"
        ).start();
    }

    private void postError(ScreenshotResultCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void applyDimensions() {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }

        Window window = dialog.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        Integer width = options.getWidth();
        Integer height = options.getHeight();
        Integer x = options.getX();
        Integer y = options.getY();

        if (width != null && height != null) {
            params.width = (int) getPixels(width);
            params.height = (int) getPixels(height);
            params.x = x != null ? (int) getPixels(x) : 0;
            params.y = y != null ? (int) getPixels(y) : 0;
        } else if (height != null) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = (int) getPixels(height);
            params.x = 0;
            params.y = y != null ? (int) getPixels(y) : 0;
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.x = 0;
            params.y = 0;
        }

        window.setAttributes(params);
    }

    private boolean shouldShowToolbar() {
        String toolbarType = options.getToolbarType();
        return toolbarType == null || (!"blank".equals(toolbarType) && !"activity".equals(toolbarType) && !"navigation".equals(toolbarType));
    }

    private void updateToolbarText() {
        runOnMainThread(() -> {
            if (titleView == null || subtitleView == null) {
                return;
            }
            String title = options.getTitle();
            if (title == null || title.isBlank()) {
                title = currentTitle;
            }
            if (title == null || title.isBlank()) {
                title = currentUrl;
            }
            titleView.setText(title != null ? title : "");
            subtitleView.setText(currentUrl != null ? currentUrl : "");
            subtitleView.setVisibility(options.getVisibleTitle() ? View.VISIBLE : View.GONE);
        });
    }

    private void updateBackButton() {
        runOnMainThread(() -> {
            if (backButton != null) {
                backButton.setEnabled(canGoBack);
                backButton.setAlpha(canGoBack ? 1f : 0.4f);
            }
        });
    }

    private static GeckoRuntime getOrCreateRuntime(Context context) {
        synchronized (RUNTIME_LOCK) {
            if (sharedRuntime == null) {
                sharedRuntime = GeckoRuntime.create(context.getApplicationContext());
            }
            return sharedRuntime;
        }
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private String bridgeBaseUri(String action) {
        return BRIDGE_SCHEME + "://" + BRIDGE_HOST + "/" + action;
    }

    private String decode(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return Uri.decode(value);
    }

    private int parseColor(@Nullable String color, int fallback) {
        if (color == null || color.isBlank()) {
            return fallback;
        }
        try {
            return Color.parseColor(color);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private boolean isDarkColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance < 0.5;
    }

    private int adjustAlpha(int color, float factor) {
        return Color.argb(Math.round(Color.alpha(color) * factor), Color.red(color), Color.green(color), Color.blue(color));
    }

    private float getPixels(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
