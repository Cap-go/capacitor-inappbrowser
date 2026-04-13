package app.capgo.inappbrowser;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int MAESTRO_HARNESS_MAX_RETRIES = 50;
    private static final long MAESTRO_HARNESS_RETRY_DELAY_MS = 100L;

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
        installMaestroHarness();
    }

    private void installMaestroHarness() {
        if (maestroHarnessInstalled) {
            return;
        }

        if (bridge == null || bridge.getWebView() == null) {
            scheduleMaestroHarnessRetry(null);
            return;
        }

        WebView webView = bridge.getWebView();
        if (!maestroHarnessBridgeInjected) {
            webView.addJavascriptInterface(new MaestroHarnessBridge(), "MaestroNativeHarness");
            maestroHarnessBridgeInjected = true;
        }

        ViewGroup root = (ViewGroup) webView.getParent();
        if (root == null) {
            scheduleMaestroHarnessRetry(webView);
            return;
        }

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.parseColor("#F4F7FF"));
        overlay.setPadding(dp(12), dp(12), dp(12), dp(12));
        ViewCompat.setElevation(overlay, dp(12));
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        overlay.setClickable(false);
        overlay.setFocusable(false);

        maestroReadyBanner = new TextView(this);
        maestroReadyBanner.setText("Maestro Booting");
        maestroReadyBanner.setContentDescription("Maestro Booting");
        maestroReadyBanner.setTextColor(Color.parseColor("#1B1F3B"));
        maestroReadyBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        maestroReadyBanner.setTypeface(maestroReadyBanner.getTypeface(), Typeface.BOLD);

        maestroRunButton = new Button(this);
        maestroRunButton.setText("Run Proxy Regression (Maestro)");
        maestroRunButton.setContentDescription("Run Proxy Regression (Maestro)");
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

        CoordinatorLayout.LayoutParams overlayParams = new CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        overlayParams.gravity = Gravity.TOP;

        root.addView(overlay, overlayParams);
        root.bringChildToFront(overlay);
        maestroHarnessInstalled = true;
    }

    private void scheduleMaestroHarnessRetry(@Nullable View retryView) {
        if (maestroHarnessInstalled || maestroHarnessRetryCount >= MAESTRO_HARNESS_MAX_RETRIES) {
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

    private void updateMaestroReady(boolean ready) {
        maestroReady = ready;
        if (maestroReadyBanner != null) {
            String bannerText = ready ? "Maestro Ready" : "Maestro Booting";
            maestroReadyBanner.setText(bannerText);
            maestroReadyBanner.setContentDescription(bannerText);
        }
        if (maestroRunButton != null) {
            maestroRunButton.setVisibility(View.VISIBLE);
            maestroRunButton.setEnabled(!maestroRunning);
            maestroRunButton.setAlpha(ready ? 1f : 0.92f);
        }
        if (ready && maestroPendingRun && !maestroRunning) {
            triggerMaestroProxyRegression();
        }
    }

    private void updateMaestroRunning(boolean running) {
        maestroRunning = running;
        if (maestroRunButton != null) {
            maestroRunButton.setEnabled(!running);
        }
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
