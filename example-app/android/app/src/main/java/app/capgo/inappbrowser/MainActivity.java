package app.capgo.inappbrowser;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MaestroHarness";
    private static final int MAESTRO_HARNESS_MAX_RETRIES = 1200;
    private static final long MAESTRO_HARNESS_RETRY_DELAY_MS = 250L;
    private static final long MAESTRO_AUTORUN_RETRY_DELAY_MS = 250L;
    private static final String MAESTRO_BOOTING_TEXT = "Maestro Booting";
    private static final String MAESTRO_READY_TEXT = "Maestro Ready";
    private static final String MAESTRO_RUN_PROXY_TEXT = "Run Proxy Regression (Maestro)";
    private static final String EXTRA_MAESTRO_AUTORUN_PROXY_REGRESSION = "capgo_maestro_autorun_proxy_regression";

    private LinearLayout maestroOverlay;
    private Button maestroRunButton;
    private TextView maestroReadyBanner;
    private TextView maestroProxyStatus;
    private TextView maestroProxyDetails;
    private boolean maestroReady;
    private boolean maestroRunning;
    private boolean maestroPendingRun;
    private boolean maestroHarnessInstalled;
    private boolean maestroHarnessBridgeInjected;
    private int maestroHarnessRetryCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleMaestroLaunchIntent(getIntent());
        installMaestroHarness();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleMaestroLaunchIntent(intent);
        installMaestroHarness();
    }

    @Override
    public void onResume() {
        super.onResume();
        installMaestroHarness();
    }

    private void handleMaestroLaunchIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_MAESTRO_AUTORUN_PROXY_REGRESSION, false)) {
            return;
        }

        maestroPendingRun = true;
        maybeRunQueuedMaestroProxyRegression();
    }

    private void installMaestroHarness() {
        boolean overlayInstalled = installMaestroOverlay();
        boolean bridgeInjected = ensureMaestroHarnessBridge();
        if (overlayInstalled && bridgeInjected) {
            syncMaestroReadyFromPage();
            if (maestroPendingRun && !maestroRunning) {
                maybeRunQueuedMaestroProxyRegression();
            }
        }
        if (!overlayInstalled || !bridgeInjected || !maestroReady) {
            WebView retryView = bridge != null ? bridge.getWebView() : null;
            scheduleMaestroHarnessRetry(retryView);
        }
    }

    private boolean installMaestroOverlay() {
        View rootView = findViewById(android.R.id.content);
        if (!(rootView instanceof ViewGroup root)) {
            Log.d(TAG, "Content root not ready yet");
            return false;
        }

        if (maestroOverlay != null) {
            bringMaestroOverlayToFront(root);
            maestroHarnessInstalled = true;
            return true;
        }

        final int horizontalPadding = dp(12);
        final int verticalPadding = dp(12);
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.parseColor("#F4F7FF"));
        overlay.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        ViewCompat.setElevation(overlay, dp(12));
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        overlay.setClickable(false);
        overlay.setFocusable(false);
        ViewCompat.setOnApplyWindowInsetsListener(
            overlay,
            (view, insets) -> {
                int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
                view.setPadding(horizontalPadding, verticalPadding + topInset, horizontalPadding, verticalPadding);
                return insets;
            }
        );

        maestroReadyBanner = new TextView(this);
        maestroReadyBanner.setText(MAESTRO_BOOTING_TEXT);
        maestroReadyBanner.setContentDescription(MAESTRO_BOOTING_TEXT);
        maestroReadyBanner.setTextColor(Color.parseColor("#1B1F3B"));
        maestroReadyBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        maestroReadyBanner.setTypeface(maestroReadyBanner.getTypeface(), Typeface.BOLD);

        maestroRunButton = new Button(this);
        maestroRunButton.setText(MAESTRO_RUN_PROXY_TEXT);
        maestroRunButton.setContentDescription(MAESTRO_RUN_PROXY_TEXT);
        maestroRunButton.setAllCaps(false);
        maestroRunButton.setOnClickListener(view -> triggerMaestroProxyRegression());

        maestroProxyStatus = new TextView(this);
        maestroProxyStatus.setText("Not started");
        maestroProxyStatus.setContentDescription("Not started");
        maestroProxyStatus.setTextColor(Color.parseColor("#1B1F3B"));
        maestroProxyStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        maestroProxyDetails = new TextView(this);
        maestroProxyDetails.setText("");
        maestroProxyDetails.setContentDescription("");
        maestroProxyDetails.setTextColor(Color.parseColor("#334155"));
        maestroProxyDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(8);
        buttonRow.addView(maestroRunButton, buttonParams);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(8);

        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailsParams.topMargin = dp(4);

        overlay.addView(maestroReadyBanner);
        overlay.addView(buttonRow);
        overlay.addView(maestroProxyStatus, statusParams);
        overlay.addView(maestroProxyDetails, detailsParams);
        maestroOverlay = overlay;

        CoordinatorLayout.LayoutParams overlayParams = new CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        overlayParams.gravity = Gravity.TOP;

        root.addView(overlay, overlayParams);
        bringMaestroOverlayToFront(root);
        maestroHarnessInstalled = true;
        Log.d(TAG, "Installed native Maestro overlay");
        return true;
    }

    private void bringMaestroOverlayToFront(@Nullable ViewGroup root) {
        if (maestroOverlay == null) {
            return;
        }

        ViewGroup parentRoot = root;
        if (parentRoot == null) {
            View rootView = findViewById(android.R.id.content);
            if (rootView instanceof ViewGroup viewGroup) {
                parentRoot = viewGroup;
            }
        }

        if (parentRoot == null) {
            return;
        }

        parentRoot.bringChildToFront(maestroOverlay);
        maestroOverlay.bringToFront();
        ViewCompat.setElevation(maestroOverlay, dp(12));
        ViewCompat.requestApplyInsets(maestroOverlay);
        parentRoot.invalidate();
        final ViewGroup finalParentRoot = parentRoot;
        maestroOverlay.post(() -> {
            if (maestroOverlay == null || maestroOverlay.getParent() != finalParentRoot) {
                return;
            }
            finalParentRoot.bringChildToFront(maestroOverlay);
            maestroOverlay.bringToFront();
            finalParentRoot.invalidate();
        });
    }

    private boolean ensureMaestroHarnessBridge() {
        if (maestroHarnessBridgeInjected) {
            return true;
        }

        if (bridge == null || bridge.getWebView() == null) {
            Log.d(TAG, "Bridge WebView not ready yet");
            return false;
        }

        bridge.getWebView().addJavascriptInterface(new MaestroHarnessBridge(), "MaestroNativeHarness");
        maestroHarnessBridgeInjected = true;
        Log.d(TAG, "Injected Maestro JS bridge");
        return true;
    }

    private void scheduleMaestroHarnessRetry(@Nullable View retryView) {
        if ((maestroHarnessInstalled && maestroHarnessBridgeInjected && maestroReady) || maestroHarnessRetryCount >= MAESTRO_HARNESS_MAX_RETRIES) {
            if (!maestroHarnessBridgeInjected) {
                Log.w(TAG, "Stopped retrying before Maestro bridge became ready");
            } else if (!maestroReady) {
                Log.w(TAG, "Stopped retrying before Maestro page became ready");
            }
            return;
        }

        maestroHarnessRetryCount += 1;

        View targetView = retryView;
        if (targetView == null && getWindow() != null) {
            targetView = getWindow().getDecorView();
        }

        if (targetView != null) {
            targetView.postDelayed(this::installMaestroHarness, MAESTRO_HARNESS_RETRY_DELAY_MS);
        }
    }

    private void syncMaestroReadyFromPage() {
        if (bridge == null || bridge.getWebView() == null || maestroReady) {
            return;
        }

        WebView webView = bridge.getWebView();
        webView.post(() ->
            webView.evaluateJavascript(
                "(function(){var button=document.getElementById('maestro-run-proxy')||document.getElementById('run-proxy-regression');return !!(window.__capgoRunMaestroProxy||(button&&!button.disabled));})()",
                value -> {
                    if ("true".equals(value)) {
                        updateMaestroReady(true);
                    }
                }
            )
        );
    }

    private void triggerMaestroProxyRegression() {
        if (!maestroReady) {
            maestroPendingRun = true;
            updateMaestroStatus("Waiting for Maestro readiness", "Queued proxy regression run");
            return;
        }

        if (bridge == null || bridge.getWebView() == null) {
            return;
        }

        maestroPendingRun = false;
        updateMaestroRunning(true);
        bridge
            .getWebView()
            .post(() ->
                bridge
                    .getWebView()
                    .evaluateJavascript(
                        "(function(){if(window.__capgoRunMaestroProxy){window.__capgoRunMaestroProxy();return true;}var button=document.getElementById('maestro-run-proxy')||document.getElementById('run-proxy-regression');if(button){button.click();return true;}return false;})()",
                        null
                    )
            );
    }

    private void maybeRunQueuedMaestroProxyRegression() {
        if (!maestroPendingRun || maestroRunning || bridge == null || bridge.getWebView() == null) {
            return;
        }

        WebView webView = bridge.getWebView();
        webView.post(() ->
            webView.evaluateJavascript(
                "(function(){if(window.__capgoRunMaestroProxy){window.__capgoRunMaestroProxy();return 'started';}var button=document.getElementById('maestro-run-proxy')||document.getElementById('run-proxy-regression');if(button&&!button.disabled){button.click();return 'started';}return 'waiting';})()",
                value -> {
                    if ("\"started\"".equals(value)) {
                        maestroPendingRun = false;
                        updateMaestroRunning(true);
                        return;
                    }
                    webView.postDelayed(this::maybeRunQueuedMaestroProxyRegression, MAESTRO_AUTORUN_RETRY_DELAY_MS);
                }
            )
        );
    }

    private void updateMaestroReady(boolean ready) {
        maestroReady = ready;
        if (maestroReadyBanner != null) {
            String bannerText = ready ? MAESTRO_READY_TEXT : MAESTRO_BOOTING_TEXT;
            maestroReadyBanner.setText(bannerText);
            maestroReadyBanner.setContentDescription(bannerText);
        }
        if (maestroRunButton != null) {
            maestroRunButton.setVisibility(View.VISIBLE);
            maestroRunButton.setEnabled(!maestroRunning);
            maestroRunButton.setAlpha(ready ? 1f : 0.92f);
        }
        bringMaestroOverlayToFront(null);
        if (ready && maestroPendingRun && !maestroRunning) {
            maybeRunQueuedMaestroProxyRegression();
        }
    }

    private void updateMaestroRunning(boolean running) {
        maestroRunning = running;
        if (maestroRunButton != null) {
            maestroRunButton.setEnabled(!running);
        }
        bringMaestroOverlayToFront(null);
    }

    private void updateMaestroStatus(String status, String details) {
        if (maestroProxyStatus != null) {
            String safeStatus = status == null || status.isEmpty() ? "Not started" : status;
            maestroProxyStatus.setText(safeStatus);
            maestroProxyStatus.setContentDescription(safeStatus);
        }
        if (maestroProxyDetails != null) {
            String safeDetails = details == null ? "" : details;
            maestroProxyDetails.setText(safeDetails);
            maestroProxyDetails.setContentDescription(safeDetails);
        }
        bringMaestroOverlayToFront(null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class MaestroHarnessBridge {

        @JavascriptInterface
        public void setReady(boolean ready) {
            runOnUiThread(() -> updateMaestroReady(ready));
        }

        @JavascriptInterface
        public void setRunning(boolean running) {
            runOnUiThread(() -> updateMaestroRunning(running));
        }

        @JavascriptInterface
        public void setStatus(String status, String details) {
            runOnUiThread(() -> updateMaestroStatus(status, details));
        }
    }
}
