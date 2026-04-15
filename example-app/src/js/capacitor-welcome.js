import { SplashScreen } from "@capacitor/splash-screen";
import { SystemBars, SystemBarType } from "@capacitor/core";
import {
  InAppBrowser,
  ToolBarType,
  BackgroundColor,
  InvisibilityMode,
} from "@capgo/inappbrowser";
import { setupProxyDemoButtons } from "./proxy-demo.js";
import { setupProxyRegression } from "./proxy-regression.js";
import { attachKeyboardRegressionHarness } from "./keyboard-regression.js";
import { url as configuredTestWebappUrl } from "./url.js";

// Default URL configuration
let testWebappUrl = "http://localhost:8000/index.php";

function getConfiguredTestWebappUrl() {
  return configuredTestWebappUrl || testWebappUrl;
}

window.customElements.define(
  "capacitor-welcome",
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: "open" });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        display: block;
        width: 100%;
        height: 100%;
      }
      h1, h2, h3, h4, h5 {
        text-transform: uppercase;
      }
      .button {
        display: inline-block;
        padding: 10px;
        background-color: #73B5F6;
        color: #fff;
        font-size: 0.9em;
        border: 0;
        border-radius: 3px;
        text-decoration: none;
        cursor: pointer;
      }
      main {
        padding: 15px;
      }
      main hr { height: 1px; background-color: #eee; border: 0; }
      main h1 {
        font-size: 1.4em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      main h2 {
        font-size: 1.1em;
      }
      main h3 {
        font-size: 0.9em;
      }
      main p {
        color: #333;
      }
      main pre {
        white-space: pre-line;
      }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>InAppBrowser Test App</h1>
      </capacitor-welcome-titlebar>
      <main>
        <p>
          This app is designed to test the Capacitor InAppBrowser plugin, specifically to reproduce and debug back button navigation issues.
        </p>
        <h2>Download Handling</h2>
        <p>
          Open a page that immediately downloads a blob text file. With native download handling enabled, the downloaded file should reopen inside the webview.
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="handle-downloads-toggle" checked style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Handle downloads natively</span>
          </label>
        </p>
        <p>
          <button class="button" id="open-download-demo" style="background-color: #198754;">Open Auto Download Demo</button>
        </p>
        <p>
          <button class="button" id="open-download-demo-listener" style="background-color: #0ea5e9;">Open Auto Download Demo + Close On Event</button>
        </p>
        <p id="download-event-status" style="margin-top: 8px; padding: 10px 12px; border-radius: 8px; background-color: #f8f9fa; color: #495057; font-size: 0.85em;">
          Download listener idle.
        </p>
        <hr />
        <h2>Custom URL</h2>
        <p>
          Enter a URL to open in the in-app browser.
        </p>
        <p style="display: flex; gap: 8px; align-items: center;">
          <input type="text" id="custom-url-input" value="https://google.com" placeholder="https://example.com" style="flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 3px; margin-bottom: 10px; font-size: 0.9em; box-sizing: border-box;" />
          <button id="clear-url-button" style="background-color: #dc3545; color: white; border: none; border-radius: 3px; padding: 8px 12px; cursor: pointer; font-size: 0.9em; margin-bottom: 10px;">🗑️</button>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="prevent-deeplink-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Prevent Deeplinks (block external app opening)</span>
          </label>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="spoof-firebase-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Spoof Firebase (inject Service Worker polyfill)</span>
          </label>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="spoof-useragent-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Use Spoofed User Agent (Android Chrome)</span>
          </label>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="enable-google-pay-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Enable Google Pay Support</span>
          <label style="display: block; font-size: 0.9em; margin-bottom: 5px;">
            <span>Toolbar Type:</span>
          </label>
          <select id="toolbar-type-select" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 3px; font-size: 0.9em; box-sizing: border-box;">
            <option value="navigation">Navigation (back/forward/reload)</option>
            <option value="activity">Activity (close/share)</option>
            <option value="compact">Compact (close only)</option>
            <option value="blank">Blank (no toolbar)</option>
          </select>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="native-navigation-gestures-toggle" checked style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Native Navigation Gestures (swipe left/right)</span>
          </label>
        </p>
        <p>
          <button class="button" id="open-custom-url" style="background-color: #007bff;">Open Custom URL</button>
        </p>
        <h2>Proxy Regression</h2>
        <p>
          Run a self-contained proxy flow that serves the page, script, fetch, and XHR through <code>addProxyHandler()</code>.
        </p>
        <p>
          <button class="button" id="run-proxy-regression" style="background-color: #5b39f7;">Run Proxy Regression Test</button>
        </p>
        <div id="proxy-regression-status" style="margin-top: 10px; padding: 10px; background-color: #eef1ff; border-radius: 5px; font-size: 0.8em; color: #1b1f3b;">
          <strong>Status:</strong> <span id="proxy-regression-status-text">Not started</span>
          <div id="proxy-regression-details" style="margin-top: 6px;"></div>
        </div>
        <hr />
        <h2>Proxy Demo Scenarios</h2>
        <p>
          Open real websites and exercise the proxy paths directly from this example app.
        </p>
        <p style="display: flex; gap: 8px; flex-wrap: wrap;">
          <button class="button" id="proxy-demo-grailed-stub" style="background-color: #1f7a8c;">Grailed SDK Stub Proxy</button>
          <button class="button" id="proxy-demo-grailed-google-login" style="background-color: #126b4c;">Grailed Google Login Proxy</button>
          <button class="button" id="proxy-demo-grailed-background-login" style="background-color: #0b8f68;">Grailed Background Login</button>
          <button class="button" id="proxy-demo-facebook-login" style="background-color: #1877f2;">Facebook Login</button>
          <button class="button" id="proxy-demo-facebook-script" style="background-color: #0f5dcf;">Facebook Script Proxy</button>
        </p>
        <div style="display: grid; gap: 8px; max-width: 440px; margin-bottom: 12px;">
          <input id="proxy-demo-google-email" type="email" placeholder="Google email" style="padding: 10px; border: 1px solid #c9d7d1; border-radius: 6px;" />
          <input id="proxy-demo-google-password" type="password" placeholder="Google password" style="padding: 10px; border: 1px solid #c9d7d1; border-radius: 6px;" />
          <input id="proxy-demo-google-otp" type="text" placeholder="Google 2FA code (optional)" style="padding: 10px; border: 1px solid #c9d7d1; border-radius: 6px;" />
        </div>
        <p style="font-size: 0.75em; color: #3f5f53; margin-top: -4px;">
          The background Grailed demo keeps both the Grailed page and the Google popup hidden, drives them with injected JavaScript, and reports each step here.
        </p>
        <p style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px;">
          <button class="button" id="proxy-demo-show-primary" style="background-color: #5b7c6f;" disabled>Show hidden Grailed window</button>
          <button class="button" id="proxy-demo-show-popup" style="background-color: #466d8f;" disabled>Show hidden popup</button>
        </p>
        <div id="proxy-demo-status" style="margin-top: 10px; padding: 10px; background-color: #eefaf7; border-radius: 5px; font-size: 0.8em; color: #12372a;">
          <strong>Status:</strong> <span id="proxy-demo-status-text">Not started</span>
          <div id="proxy-demo-details" style="margin-top: 6px; white-space: pre-wrap; word-break: break-word;"></div>
          <div style="margin-top: 10px;">
            <strong>Steps:</strong>
            <pre id="proxy-demo-history" style="margin-top: 6px; padding: 8px; background: rgba(18,55,42,0.06); border-radius: 4px; white-space: pre-wrap; word-break: break-word; max-height: 180px; overflow-y: auto;">No events yet.</pre>
          </div>
        </div>
        <hr />
        <h2>Target Blank Test</h2>
        <p>
          Open a deterministic target="_blank" HTTPS test page inside the plugin and verify that the linked page stays inside the current webview.
        </p>
        <p>
          <button class="button" id="open-blank-target-test" style="background-color: #0f766e;">Open Blank Target HTTPS Test</button>
        </p>
        <div id="blank-target-test-status" style="margin-top: 10px; padding: 10px; background-color: #ecfeff; border-radius: 5px; font-size: 0.8em; color: #134e4a;">
          <div><strong>Blank target test status:</strong> <span id="blank-target-status-text">Idle</span></div>
          <div><strong>Blank target test result:</strong> <span id="blank-target-result-text">not run</span></div>
          <div><strong>Blank target last URL:</strong> <span id="blank-target-last-url-text">none</span></div>
        </div>
        <hr />
        <h2>In-App Browser Demo</h2>
        <p>
          Open the Capacitor InAppBrowser plugin documentation in an in-app browser.
        </p>
        <p>
          <button class="button" id="open-browser">Open In-App Browser</button>
        </p>
        <p>
          <button class="button" id="open-browser-with-blocked-host">Open In-App Browser in blocked host</button>
        </p>
        <hr />
        <h2>System Bars</h2>
        <p>
          Use the SystemBars API to show the system UI after changing visibility.
        </p>
        <p>
          <button class="button" id="system-bars-show-all">Show All System Bars</button>
          <button class="button" id="system-bars-show-status">Show Status Bar</button>
          <button class="button" id="system-bars-show-navigation">Show Navigation Bar</button>
          <button class="button" id="system-bars-hide-navigation">Hide Navigation Bar</button>
        </p>
        <h2>WebView Visibility</h2>
        <p>
          Hide or show the current InAppBrowser webview. Use the toolbar button near Done to hide it too.
        </p>
        <p>
          <button class="button" id="webview-hide">Hide WebView</button>
          <button class="button" id="webview-show">Show WebView</button>
        </p>
        <h2>Back Button Test</h2>
        <p>
          Test the back button issue with our custom test webapp. This opens a PHP webapp designed to reproduce navigation issues.
        </p>
        <p>
          <button class="button" id="open-test-webapp" style="background-color: #28a745;">🧪 Open Test Webapp (Navigation Mode)</button>
        </p>
        <p>
          <button class="button" id="open-test-webapp-activity" style="background-color: #ffc107; color: #212529;">🧪 Open Test Webapp (Activity Mode)</button>
        </p>
        <div id="webapp-status" style="margin-top: 10px; padding: 10px; background-color: #f8f9fa; border-radius: 5px; font-size: 0.8em; color: #666;">
          <strong>Setup:</strong> Make sure to copy url.js.example to url.js and configure your local server URL.
        </div>
        <hr />
        <h2>Hidden WebView Test</h2>
        <p>
          Test the hidden webview feature. Opens example.com invisibly, extracts DOM content via JavaScript, and displays it here.
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="hidden-fake-visible-toggle" checked style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Fake visible (fullscreen metrics)</span>
          </label>
        </p>
        <p>
          <button class="button" id="test-hidden-webview" style="background-color: #6f42c1;">👻 Load Hidden WebView</button>
          <button class="button" id="close-hidden-webview" style="background-color: #dc3545; margin-left: 8px;">✖ Close Hidden</button>
          <button class="button" id="check-hidden-visibility" style="background-color: #17a2b8; margin-left: 8px;">👁️ Check visibility</button>
          <button class="button" id="check-hidden-dimensions" style="background-color: #20c997; margin-left: 8px;">📏 Check dimensions</button>
          <button class="button" id="refresh-hidden-dom" style="background-color: #6c757d; margin-left: 8px;">🔄 Refresh DOM</button>
        </p>
        <div id="hidden-webview-status" style="margin-top: 10px; padding: 10px; background-color: #e7e3f1; border-radius: 5px; font-size: 0.8em; color: #333;">
          <strong>Status:</strong> <span id="hidden-status-text">Not started</span>
        </div>
        <div id="hidden-webview-result" style="margin-top: 10px; padding: 10px; background-color: #f8f9fa; border-radius: 5px; font-size: 0.75em; color: #333; max-height: 300px; overflow-y: auto; display: none;">
          <strong>DOM Content:</strong>
          <pre id="dom-content-output" style="white-space: pre-wrap; word-break: break-word; margin-top: 8px;"></pre>
        </div>
        <div id="hidden-webview-metrics" style="margin-top: 10px; padding: 10px; background-color: #f8f9fa; border-radius: 5px; font-size: 0.75em; color: #333; max-height: 300px; overflow-y: auto; display: none;">
          <strong>Dimensions:</strong>
          <pre id="metrics-output" style="white-space: pre-wrap; word-break: break-word; margin-top: 8px;"></pre>
        </div>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;

      attachKeyboardRegressionHarness();

      // Helper function to validate URL
      function isValidUrl(string) {
        try {
          const url = new URL(string);
          return url.protocol === 'http:' || url.protocol === 'https:';
        } catch (_) {
          return false;
        }
      }

      function createAutoDownloadDemoUrl() {
        const content = [
          "Capgo download demo successful.",
          "This file was downloaded natively by InAppBrowser.",
        ].join("\\n");

        const downloadDemoHtml = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Auto Download Demo</title>
    <style>
      body {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        margin: 0;
        min-height: 100vh;
        display: grid;
        place-items: center;
        background: linear-gradient(180deg, #f8f9fa 0%, #dbeafe 100%);
        color: #111827;
      }
      .card {
        width: min(420px, calc(100vw - 32px));
        padding: 24px;
        border-radius: 20px;
        background: rgba(255, 255, 255, 0.92);
        box-shadow: 0 24px 60px rgba(15, 23, 42, 0.14);
      }
      h1 {
        margin: 0 0 12px;
        font-size: 1.4rem;
      }
      p {
        margin: 0;
        line-height: 1.5;
      }
    </style>
  </head>
  <body>
    <div class="card">
      <h1>Preparing download</h1>
      <p id="status">Creating a sample blob file and handing it to the native download flow.</p>
    </div>
    <script>
      const status = document.getElementById("status");
      const blob = new Blob([${JSON.stringify(content)}], { type: "text/plain" });
      const downloadUrl = URL.createObjectURL(blob);
      const downloadLink = document.createElement("a");
      downloadLink.href = downloadUrl;
      downloadLink.download = "capgo-download-demo.txt";
      downloadLink.textContent = "Download sample file";
      document.body.appendChild(downloadLink);
      status.textContent = "Starting download...";
      window.setTimeout(() => downloadLink.click(), 300);
    </script>
  </body>
</html>`;

        return `data:text/html;charset=utf-8,${encodeURIComponent(downloadDemoHtml)}`;
      }

      let closeOnNextDownloadEvent = false;
      const downloadStatusElement = () => self.shadowRoot.querySelector("#download-event-status");
      const downloadListenerButtonElement = () => self.shadowRoot.querySelector("#open-download-demo-listener");

      function setDownloadStatus(message, { backgroundColor = "#f8f9fa", color = "#495057" } = {}) {
        const statusElement = downloadStatusElement();
        if (!statusElement) {
          return;
        }

        statusElement.textContent = message;
        statusElement.style.backgroundColor = backgroundColor;
        statusElement.style.color = color;
      }

      function setDownloadListenerButtonLabel(label) {
        const button = downloadListenerButtonElement();
        if (!button) {
          return;
        }
        button.textContent = label;
      }

      let downloadListenerHandles = [];

      async function createDownloadListeners() {
        while (downloadListenerHandles.length > 0) {
          const listenerHandle = downloadListenerHandles.pop();
          if (!listenerHandle || typeof listenerHandle.remove !== "function") {
            continue;
          }
          try {
            await listenerHandle.remove();
          } catch (error) {
            console.warn("Could not remove stale download listener:", error);
          }
        }

        downloadListenerHandles = await Promise.all([
          InAppBrowser.addListener("downloadCompleted", (event) => {
            setDownloadListenerButtonLabel(`Event OK: ${event.fileName}`);
            setDownloadStatus(
              `Download completed: ${event.fileName} via ${event.handledBy}`,
              { backgroundColor: "#dcfce7", color: "#166534" },
            );

            if (closeOnNextDownloadEvent && event.id) {
              closeOnNextDownloadEvent = false;
              InAppBrowser.close({ id: event.id }).catch((error) => {
                console.warn("Could not close webview after download event:", error);
              });
            }
          }),
          InAppBrowser.addListener("downloadFailed", (event) => {
            closeOnNextDownloadEvent = false;
            setDownloadListenerButtonLabel("Event Failed");
            const fileLabel = event.fileName ? ` for ${event.fileName}` : "";
            setDownloadStatus(
              `Download failed${fileLabel}: ${event.error}`,
              { backgroundColor: "#fee2e2", color: "#991b1b" },
            );
          }),
        ]);

        return downloadListenerHandles;
      }

      async function openAutoDownloadDemo({ closeOnEvent = false } = {}) {
        const handleDownloadsToggle = self.shadowRoot.querySelector("#handle-downloads-toggle");
        closeOnNextDownloadEvent = closeOnEvent && handleDownloadsToggle.checked;
        setDownloadListenerButtonLabel(
          closeOnEvent && handleDownloadsToggle.checked
            ? "Waiting For Download Event..."
            : "Open Auto Download Demo + Close On Event",
        );
        setDownloadStatus(
          handleDownloadsToggle.checked
            ? closeOnEvent
              ? "Waiting for native download event, then closing the webview..."
              : "Waiting for native download event..."
            : "Native download handling disabled for this run.",
          {
            backgroundColor: handleDownloadsToggle.checked ? "#e0f2fe" : "#f8f9fa",
            color: handleDownloadsToggle.checked ? "#075985" : "#495057",
          },
        );

        try {
          await createDownloadListeners();
          await InAppBrowser.openWebView({
            url: createAutoDownloadDemoUrl(),
            title: "Auto Download Demo",
            toolbarColor: "#198754",
            toolbarType: ToolBarType.NAVIGATION,
            backgroundColor: BackgroundColor.WHITE,
            visibleTitle: true,
            showReloadButton: true,
            enabledSafeBottomMargin: true,
            handleDownloads: handleDownloadsToggle.checked,
          });
        } catch (error) {
          closeOnNextDownloadEvent = false;
          console.error("Error opening auto download demo:", error);
        }
      }

      const blankTargetExpectedUrl = "https://example.com/#blank-target-webview";
      const blankTargetTestHtml = `
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>Target Blank Test Page</title>
            <style>
              body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                padding: 24px;
                line-height: 1.5;
                color: #0f172a;
                background: #f8fafc;
              }
              a {
                display: inline-block;
                margin-top: 16px;
                padding: 12px 16px;
                border-radius: 999px;
                background: #0f766e;
                color: white;
                text-decoration: none;
                font-weight: 600;
              }
            </style>
          </head>
          <body>
            <h1>Target Blank Test Page</h1>
            <p>This link should open inside the current InAppBrowser webview.</p>
            <a href="${blankTargetExpectedUrl}" target="_blank" rel="noopener noreferrer">Open Example Domain</a>
          </body>
        </html>
      `;
      const blankTargetTestUrl = `data:text/html;charset=utf-8,${encodeURIComponent(blankTargetTestHtml)}`;
      const blankTargetStatusText = self.shadowRoot.querySelector("#blank-target-status-text");
      const blankTargetResultText = self.shadowRoot.querySelector("#blank-target-result-text");
      const blankTargetLastUrlText = self.shadowRoot.querySelector("#blank-target-last-url-text");
      let blankTargetTestActive = false;
      let blankTargetWebViewId = null;
      let blankTargetListenerHandles = [];

      function setBlankTargetState({ status, result, lastUrl }) {
        blankTargetStatusText.textContent = status;
        blankTargetResultText.textContent = result;
        blankTargetLastUrlText.textContent = lastUrl;
      }

      function isBlankTargetEvent(result) {
        return blankTargetTestActive && result?.id === blankTargetWebViewId;
      }

      async function clearBlankTargetListeners() {
        const handles = blankTargetListenerHandles;
        blankTargetListenerHandles = [];

        await Promise.all(
          handles.map((handle) => {
            if (!handle || typeof handle.remove !== "function") {
              return Promise.resolve();
            }

            return Promise.resolve(handle.remove()).catch(() => {});
          }),
        );
      }

      async function attachBlankTargetListeners() {
        await clearBlankTargetListeners();

        blankTargetListenerHandles = [
          await InAppBrowser.addListener("urlChangeEvent", (result) => {
            if (!isBlankTargetEvent(result)) {
              return;
            }

            setBlankTargetState({
              status: result.url === blankTargetExpectedUrl ? "Linked page loaded" : "Navigating",
              result: blankTargetResultText.textContent,
              lastUrl: result.url,
            });
          }),
          await InAppBrowser.addListener("closeEvent", async (result) => {
            if (!isBlankTargetEvent(result)) {
              return;
            }

            const closedUrl = result.url || "unknown";
            blankTargetTestActive = false;
            blankTargetWebViewId = null;

            setBlankTargetState({
              status: "Closed",
              result:
                closedUrl === blankTargetExpectedUrl
                  ? "internal navigation confirmed"
                  : `closed on ${closedUrl}`,
              lastUrl: closedUrl,
            });

            await clearBlankTargetListeners();
          }),
          await InAppBrowser.addListener("pageLoadError", async (result) => {
            if (!isBlankTargetEvent(result)) {
              return;
            }

            blankTargetTestActive = false;
            blankTargetWebViewId = null;
            setBlankTargetState({
              status: "Page load error",
              result: "page load error",
              lastUrl: blankTargetLastUrlText.textContent,
            });

            await clearBlankTargetListeners();
          }),
        ];
      }

      setBlankTargetState({
        status: "Idle",
        result: "not run",
        lastUrl: "none",
      });

      async function fetchHiddenDomContent({ statusText, resultDiv, domOutput }) {
        statusText.textContent = "Refreshing DOM content...";
        try {
          await InAppBrowser.executeScript({
            code: `
              (function() {
                var domContent = document.documentElement.outerHTML;
                var payload = JSON.stringify({
                  detail: {
                    type: 'domContent',
                    content: domContent,
                    title: document.title,
                    url: window.location.href
                  }
                });
                
                // Try mobileApp first (both platforms with bridge)
                if (window.mobileApp && window.mobileApp.postMessage) {
                  window.mobileApp.postMessage(JSON.parse(payload));
                }
                else {
                  console.error('No message interface available');
                }
              })();
            `
          });
          statusText.textContent = "DOM refresh triggered. Waiting for content...";
        } catch (scriptError) {
          console.error("Script execution error:", scriptError);
          statusText.textContent = "Error refreshing DOM: " + scriptError.message;
          resultDiv.style.display = "none";
          domOutput.textContent = "";
        }
      }

      const maestroReadyBanner = document.getElementById("maestro-ready-banner");
      const maestroRunProxyButton = document.getElementById("maestro-run-proxy");
      const maestroProxyStatus = document.getElementById("maestro-proxy-status");
      const maestroProxyDetails = document.getElementById("maestro-proxy-details");

      const withMaestroNativeHarness = (callback) => {
        const harness = window.MaestroNativeHarness;
        if (!harness || typeof callback !== "function") {
          return;
        }
        try {
          callback(harness);
        } catch (_error) {}
      };

      const syncMaestroNativeReady = (ready) => {
        withMaestroNativeHarness((harness) => {
          if (typeof harness.setReady === "function") {
            harness.setReady(Boolean(ready));
          }
        });
      };

      const syncMaestroNativeRunning = (running) => {
        withMaestroNativeHarness((harness) => {
          if (typeof harness.setRunning === "function") {
            harness.setRunning(Boolean(running));
          }
        });
      };

      const syncMaestroNativeStatus = (message, details = "") => {
        withMaestroNativeHarness((harness) => {
          if (typeof harness.setStatus === "function") {
            harness.setStatus(message, details);
          }
        });
      };

      const updateMaestroStatus = (message, details = "") => {
        if (maestroProxyStatus) {
          maestroProxyStatus.textContent = message;
        }
        if (maestroProxyDetails) {
          maestroProxyDetails.textContent = details;
        }
        syncMaestroNativeStatus(message, details);
      };

      const updateMaestroRunning = (running) => {
        if (maestroRunProxyButton) {
          maestroRunProxyButton.disabled = running;
        }
        syncMaestroNativeRunning(running);
      };

      const proxyRegressionControls = setupProxyRegression(self.shadowRoot, {
        onStatusChange: updateMaestroStatus,
        onRunningChange: updateMaestroRunning,
      });

      if (proxyRegressionControls?.run && maestroRunProxyButton) {
        window.__capgoRunMaestroProxy = () => {
          proxyRegressionControls.run({
            keepBrowserOpenOnFinish: typeof window.MaestroNativeHarness === "undefined",
          });
        };
        maestroRunProxyButton.addEventListener("click", () => {
          proxyRegressionControls.run({ keepBrowserOpenOnFinish: true });
        });
        maestroRunProxyButton.disabled = false;
        if (maestroReadyBanner) {
          maestroReadyBanner.textContent = "Maestro Ready";
        }
        syncMaestroNativeReady(true);
      } else if (maestroReadyBanner) {
        maestroReadyBanner.textContent = "Maestro Unavailable";
        window.__capgoRunMaestroProxy = undefined;
        syncMaestroNativeReady(false);
      }

      setupProxyDemoButtons(self.shadowRoot);

      // Custom URL handler
      self.shadowRoot
        .querySelector("#open-custom-url")
        .addEventListener("click", async function (e) {
          const input = self.shadowRoot.querySelector("#custom-url-input");
          const preventDeeplinkToggle = self.shadowRoot.querySelector("#prevent-deeplink-toggle");
          const spoofFirebaseToggle = self.shadowRoot.querySelector("#spoof-firebase-toggle");
          const spoofUserAgentToggle = self.shadowRoot.querySelector("#spoof-useragent-toggle");
          const enableGooglePayToggle = self.shadowRoot.querySelector("#enable-google-pay-toggle");
          const enableGooglePay = enableGooglePayToggle.checked;
          const toolbarTypeSelect = self.shadowRoot.querySelector("#toolbar-type-select");
          const nativeNavigationGesturesToggle = self.shadowRoot.querySelector("#native-navigation-gestures-toggle");
          const url = input.value.trim();
          const preventDeeplink = preventDeeplinkToggle.checked;
          const spoofFirebase = spoofFirebaseToggle.checked;
          const spoofUserAgent = spoofUserAgentToggle.checked;
          const toolbarType = toolbarTypeSelect.value;
          const nativeNavigationGestures = nativeNavigationGesturesToggle.checked;
          
          if (!url) {
            alert("Please enter a URL");
            return;
          }
          
          // Auto-prepend https:// if no protocol is specified
          let urlToOpen = url;
          if (!url.startsWith('http://') && !url.startsWith('https://')) {
            urlToOpen = 'https://' + url;
          }
          
          if (!isValidUrl(urlToOpen)) {
            alert("Please enter a valid URL (e.g., https://example.com)");
            return;
          }
          
          // Firebase polyfill script
          const firebasePolyfill = `
            (function() {
              console.log('[InAppBrowser] Injecting comprehensive Firebase Messaging polyfill');
              
              // Override browser detection first
              Object.defineProperty(navigator, 'userAgent', {
                get: function() {
                  return 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
                }
              });
              
              // Create a mock ServiceWorkerRegistration
              const mockRegistration = {
                active: null,
                installing: null,
                waiting: null,
                scope: '/',
                update: function() { return Promise.resolve(); },
                unregister: function() { return Promise.resolve(true); },
                pushManager: {
                  subscribe: function() { return Promise.resolve({ endpoint: '', keys: {} }); },
                  getSubscription: function() { return Promise.resolve(null); },
                  permissionState: function() { return Promise.resolve('granted'); }
                }
              };
              
              // Polyfill for navigator.serviceWorker
              if (!window.navigator.serviceWorker) {
                console.log('[InAppBrowser] Service Worker not available natively');
                
                window.navigator.serviceWorker = {
                  register: function(scriptURL, options) {
                    console.log('[InAppBrowser] Service Worker registration attempted:', scriptURL);
                    return Promise.resolve(mockRegistration);
                  },
                  getRegistration: function() { return Promise.resolve(mockRegistration); },
                  getRegistrations: function() { return Promise.resolve([mockRegistration]); },
                  ready: Promise.resolve(mockRegistration),
                  controller: null,
                  addEventListener: function() {},
                  removeEventListener: function() {}
                };
              }
              
              // Polyfill for window.ServiceWorker
              if (!window.ServiceWorker) {
                window.ServiceWorker = function() {};
                window.ServiceWorker.state = 'activated';
              }
              
              // Polyfill for Notification API
              if (!window.Notification) {
                window.Notification = function(title, options) {
                  console.log('[InAppBrowser] Notification created:', title);
                  this.title = title;
                  this.body = options?.body || '';
                  this.icon = options?.icon || '';
                  this.tag = options?.tag || '';
                  this.data = options?.data || {};
                  this.requireInteraction = options?.requireInteraction || false;
                  this.silent = options?.silent || false;
                  this.timestamp = Date.now();
                };
                window.Notification.permission = 'granted';
                window.Notification.requestPermission = function() {
                  return Promise.resolve('granted');
                };
                window.Notification.prototype.close = function() {
                  console.log('[InAppBrowser] Notification closed');
                };
                window.Notification.prototype.addEventListener = function() {};
                window.Notification.prototype.removeEventListener = function() {};
              }
              
              // Polyfill for PushManager
              if (!window.PushManager) {
                window.PushManager = function() {};
              }
              
              // Polyfill for BackgroundSyncManager
              if (!self.sync || !self.registration) {
                if (!self.sync) {
                  self.sync = {
                    register: function() { return Promise.resolve(); },
                    getTags: function() { return Promise.resolve([]); }
                  };
                }
                if (!self.registration) {
                  self.registration = mockRegistration;
                }
              }
              
              console.log('[InAppBrowser] Firebase polyfill injection complete');
            })();
          `;
          
          const options = {
            url: urlToOpen,
            toolbarColor: "#007bff",
            toolbarType: toolbarType,
            backgroundColor: BackgroundColor.WHITE,
            title: "Custom URL",
            showReloadButton: toolbarType === 'navigation',
            visibleTitle: true,
            enabledSafeBottomMargin: true,
            preventDeeplink: preventDeeplink,
            enableGooglePaySupport: enableGooglePay,
            activeNativeNavigationForWebview: nativeNavigationGestures,
            buttonNearDone: {
              ios: {
                iconType: "sf-symbol",
                icon: "eye.slash",
              },
              android: {
                iconType: "vector",
                icon: "ic_launcher_foreground",
                width: 24,
                height: 24,
              },
            },
          };
          
          if (spoofUserAgent) {
            options.headers = {
              'User-Agent': 'Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36'
            };
          }
          
          // Add Firebase spoofing if enabled
          if (spoofFirebase) {
            options.isPresentAfterPageLoad = true;
            options.preShowScript = firebasePolyfill;
            options.preShowScriptInjectionTime = 'documentStart';
          }
          
          try {
            await InAppBrowser.openWebView(options);

            // Add event listeners after opening the browser
            InAppBrowser.addListener("urlChangeEvent", (result) => {
              console.log("URL changed:", result.url);
            });

            InAppBrowser.addListener("closeEvent", () => {
              console.log("Close button pressed");
            });

            InAppBrowser.addListener("browserPageLoaded", () => {
              console.log("Page loaded");
            });

            InAppBrowser.addListener("pageLoadError", () => {
              console.log("Page load error");
            });
          } catch (e) {
            console.error("Error opening custom URL:", e);
            alert("Error opening URL. Please check the URL and try again.");
          }
        });

      self.shadowRoot
        .querySelector("#open-blank-target-test")
        .addEventListener("click", async function () {
          blankTargetTestActive = true;
          blankTargetWebViewId = null;
          setBlankTargetState({
            status: "Opening test webview...",
            result: "waiting for navigation",
            lastUrl: "none",
          });

          try {
            await attachBlankTargetListeners();

            const { id } = await InAppBrowser.openWebView({
              url: blankTargetTestUrl,
              toolbarType: ToolBarType.COMPACT,
              backgroundColor: BackgroundColor.WHITE,
              title: "Target Blank Test",
              visibleTitle: true,
              showReloadButton: false,
              activeNativeNavigationForWebview: false,
              enabledSafeBottomMargin: true,
              openBlankTargetInWebView: true,
            });
            blankTargetWebViewId = id;
          } catch (e) {
            blankTargetTestActive = false;
            blankTargetWebViewId = null;
            await clearBlankTargetListeners();
            console.error("Error opening blank target test:", e);
            setBlankTargetState({
              status: "Error",
              result: e.message,
              lastUrl: "none",
            });
          }
        });

      // Add Enter key support for the input field
      self.shadowRoot
        .querySelector("#custom-url-input")
        .addEventListener("keypress", async function (e) {
          if (e.key === 'Enter') {
            const button = self.shadowRoot.querySelector("#open-custom-url");
            button.click();
          }
        });

      // Add clear button handler
      self.shadowRoot
        .querySelector("#clear-url-button")
        .addEventListener("click", function (e) {
          const input = self.shadowRoot.querySelector("#custom-url-input");
          input.value = '';
          input.focus();
        });

      self.shadowRoot
        .querySelector("#open-browser")
        .addEventListener("click", async function (e) {
          try {
            await InAppBrowser.openWebView({
              url: "https://github.com/Cap-go/capacitor-inappbrowser",
              toolbarColor: "#000000",
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.BLACK,
              title: "Capacitor InAppBrowser",
              enabledSafeBottomMargin: true,
            });

            // Add event listeners after opening the browser
            InAppBrowser.addListener("urlChange", (result) => {
              console.log("URL changed:", result.url);
            });

            InAppBrowser.addListener("closePressed", () => {
              console.log("Close button pressed");
            });

            InAppBrowser.addListener("sharePressed", () => {
              console.log("Share button pressed");
            });
          } catch (e) {
            console.error("Error opening in-app browser:", e);
          }
        });

      self.shadowRoot
        .querySelector("#open-browser-with-blocked-host")
        .addEventListener("click", async function (e) {
          try {
            await InAppBrowser.openWebView({
              url: "https://github.com/Cap-go/capacitor-inappbrowser",
              toolbarColor: "#000000",
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.BLACK,
              title: "Capacitor InAppBrowser, blocked GitHub host",
              enabledSafeBottomMargin: true,
              blockedHosts: ["github.com"],
            });

            // Add event listeners after opening the browser
            InAppBrowser.addListener("urlChange", (result) => {
              console.log("URL changed:", result.url);
            });

            InAppBrowser.addListener("closePressed", () => {
              console.log("Close button pressed");
            });

            InAppBrowser.addListener("sharePressed", () => {
              console.log("Share button pressed");
            });
          } catch (e) {
            console.error("Error opening in-app browser:", e);
          }
        });

      self.shadowRoot
        .querySelector("#open-download-demo")
        .addEventListener("click", async function () {
          await openAutoDownloadDemo({ closeOnEvent: false });
        });

      self.shadowRoot
        .querySelector("#open-download-demo-listener")
        .addEventListener("click", async function () {
          await openAutoDownloadDemo({ closeOnEvent: true });
        });

      self.shadowRoot
        .querySelector("#system-bars-show-all")
        .addEventListener("click", async function () {
          try {
            await SystemBars.show();
          } catch (e) {
            console.error("Error showing system bars:", e);
          }
        });

      self.shadowRoot
        .querySelector("#system-bars-show-status")
        .addEventListener("click", async function () {
          try {
            await SystemBars.show({ bar: SystemBarType.StatusBar });
          } catch (e) {
            console.error("Error showing status bar:", e);
          }
        });

      self.shadowRoot
        .querySelector("#system-bars-show-navigation")
        .addEventListener("click", async function () {
          try {
            await SystemBars.show({ bar: SystemBarType.NavigationBar });
          } catch (e) {
            console.error("Error showing navigation bar:", e);
          }
        });

      self.shadowRoot
        .querySelector("#system-bars-hide-navigation")
        .addEventListener("click", async function () {
          try {
            await SystemBars.hide({ bar: SystemBarType.NavigationBar });
          } catch (e) {
            console.error("Error hiding navigation bar:", e);
          }
        });

      self.shadowRoot
        .querySelector("#webview-hide")
        .addEventListener("click", async function () {
          try {
            await InAppBrowser.hide();
          } catch (e) {
            console.error("Error hiding webview:", e);
          }
        });

      self.shadowRoot
        .querySelector("#webview-show")
        .addEventListener("click", async function () {
          try {
            await InAppBrowser.show();
          } catch (e) {
            console.error("Error showing webview:", e);
          }
        });

      InAppBrowser.addListener("buttonNearDoneClick", async () => {
        try {
          await InAppBrowser.hide();
        } catch (e) {
          console.error("Error hiding webview from toolbar button:", e);
        }
      });

      // Test webapp with navigation toolbar (main test for back button issue)
      self.shadowRoot
        .querySelector("#open-test-webapp")
        .addEventListener("click", async function (e) {
          try {
            const urlToUse = getConfiguredTestWebappUrl();

            await InAppBrowser.openWebView({
              url: urlToUse,
              toolbarColor: "#ffffff",
              toolbarTextColor: "#000000",
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.WHITE,
              title: "Back Button Test - Navigation Mode",
              showReloadButton: true,
              visibleTitle: true,
              showArrow: false,
            });

            // Add comprehensive event listeners for debugging
            InAppBrowser.addListener("urlChangeEvent", (result) => {
              console.log("🔄 URL changed:", result.url);
            });

            InAppBrowser.addListener("closeEvent", () => {
              console.log("❌ Close button pressed");
            });

            InAppBrowser.addListener("browserPageLoaded", () => {
              console.log("✅ Page loaded");
            });

            InAppBrowser.addListener("pageLoadError", () => {
              console.log("❌ Page load error");
            });

            InAppBrowser.addListener("messageFromWebview", (event) => {
              console.log("💬 Message from webview:", event.detail);
            });
          } catch (e) {
            console.error("Error opening test webapp:", e);
            alert(
              "Error opening test webapp. Make sure your local server is running and url.js is configured correctly.",
            );
          }
        });

      // Hidden WebView Test
      self.shadowRoot
        .querySelector("#test-hidden-webview")
        .addEventListener("click", async function (e) {
          const statusText = self.shadowRoot.querySelector("#hidden-status-text");
          const resultDiv = self.shadowRoot.querySelector("#hidden-webview-result");
          const metricsDiv = self.shadowRoot.querySelector("#hidden-webview-metrics");
          const domOutput = self.shadowRoot.querySelector("#dom-content-output");
          const metricsOutput = self.shadowRoot.querySelector("#metrics-output");
          const fakeVisibleToggle = self.shadowRoot.querySelector("#hidden-fake-visible-toggle");
          
          try {
            statusText.textContent = "Opening hidden webview...";
            resultDiv.style.display = "none";
            metricsDiv.style.display = "none";

            await InAppBrowser.removeAllListeners();
            downloadListenerHandles = [];
            await InAppBrowser.openWebView({
              url: "https://example.com",
              hidden: true,
              invisibilityMode: fakeVisibleToggle && fakeVisibleToggle.checked
                ? InvisibilityMode.FAKE_VISIBLE
                : InvisibilityMode.AWARE,
              buttonNearDone: {
                ios: {
                  iconType: "sf-symbol",
                  icon: "eye.slash",
                },
                android: {
                  iconType: "vector",
                  icon: "ic_launcher_foreground",
                  width: 24,
                  height: 24,
                },
              },
            });
            
            statusText.textContent = "WebView opened (hidden). Waiting for page load...";
           
            InAppBrowser.addListener("messageFromWebview", (event) => {
              console.log("Message from hidden webview:", event);
              if (event.detail && event.detail.type === 'domContent') {
                statusText.textContent = `DOM extracted from: ${event.detail.title} (${event.detail.url})`;
                domOutput.textContent = event.detail.content;
                resultDiv.style.display = "block";
              } else if (event.detail && event.detail.type === 'visibilityState') {
                statusText.textContent = `document.visibilityState: ${event.detail.state}`;
              } else if (event.detail && event.detail.type === 'dimensions') {
                statusText.textContent = "Dimensions received.";
                metricsOutput.textContent = JSON.stringify(event.detail.data, null, 2);
                metricsDiv.style.display = "block";
              }
            });

            InAppBrowser.addListener("buttonNearDoneClick", async () => {
              try {
                await InAppBrowser.hide();
              } catch (e) {
                console.error("Error hiding webview from toolbar button:", e);
              }
            });

            InAppBrowser.addListener("browserPageLoaded", async () => {
              statusText.textContent = "Page loaded! Extracting DOM content...";
            
              setTimeout(async () => {
                await fetchHiddenDomContent({ statusText, resultDiv, domOutput });
              }, 500);
            });          
          } catch (e) {
            console.error("Error with hidden webview:", e);
            statusText.textContent = "Error: " + e.message;
          }
        });

      // Close Hidden WebView
        self.shadowRoot
          .querySelector("#close-hidden-webview")
          .addEventListener("click", async function (e) {
            const statusText = self.shadowRoot.querySelector("#hidden-status-text");
            try {
              await InAppBrowser.close();
              statusText.textContent = "Hidden webview closed.";
            } catch (e) {
              console.error("Error closing hidden webview:", e);
              statusText.textContent = "Error closing: " + e.message;
            }
          });

        self.shadowRoot
          .querySelector("#check-hidden-visibility")
          .addEventListener("click", async function (e) {
            const statusText = self.shadowRoot.querySelector("#hidden-status-text");
            try {
              statusText.textContent = "Checking document.visibilityState...";
              await InAppBrowser.executeScript({
                code: `
                  (function() {
                    var state = document.visibilityState;
                    var payload = JSON.stringify({
                      detail: {
                        type: 'visibilityState',
                        state: state
                      }
                    });

                    if (window.mobileApp && window.mobileApp.postMessage) {
                      window.mobileApp.postMessage(JSON.parse(payload));
                    }
                    else {
                      console.error('No message interface available');
                    }
                  })();
                `
              });
            } catch (e) {
              console.error("Error checking visibility:", e);
              statusText.textContent = "Hidden webview not open or script failed.";
            }
          });

        self.shadowRoot
          .querySelector("#check-hidden-dimensions")
          .addEventListener("click", async function (e) {
            const statusText = self.shadowRoot.querySelector("#hidden-status-text");
            try {
              statusText.textContent = "Checking dimensions...";
              await InAppBrowser.executeScript({
                code: `
                  (function() {
                    var data = {
                      window: {
                        innerWidth: window.innerWidth,
                        innerHeight: window.innerHeight,
                        outerWidth: window.outerWidth,
                        outerHeight: window.outerHeight,
                        devicePixelRatio: window.devicePixelRatio
                      },
                      viewport: {
                        visualViewportWidth: window.visualViewport ? window.visualViewport.width : null,
                        visualViewportHeight: window.visualViewport ? window.visualViewport.height : null
                      },
                      document: {
                        clientWidth: document.documentElement ? document.documentElement.clientWidth : null,
                        clientHeight: document.documentElement ? document.documentElement.clientHeight : null,
                        scrollWidth: document.documentElement ? document.documentElement.scrollWidth : null,
                        scrollHeight: document.documentElement ? document.documentElement.scrollHeight : null,
                        bodyClientWidth: document.body ? document.body.clientWidth : null,
                        bodyClientHeight: document.body ? document.body.clientHeight : null,
                        bodyScrollWidth: document.body ? document.body.scrollWidth : null,
                        bodyScrollHeight: document.body ? document.body.scrollHeight : null
                      }
                    };

                    var payload = JSON.stringify({
                      detail: {
                        type: 'dimensions',
                        data: data
                      }
                    });

                    if (window.mobileApp && window.mobileApp.postMessage) {
                      window.mobileApp.postMessage(JSON.parse(payload));
                    }
                    else {
                      console.error('No message interface available');
                    }
                  })();
                `
              });
            } catch (e) {
              console.error("Error checking dimensions:", e);
              statusText.textContent = "Hidden webview not open or script failed.";
            }
          });

        self.shadowRoot
          .querySelector("#refresh-hidden-dom")
          .addEventListener("click", async function () {
            const statusText = self.shadowRoot.querySelector("#hidden-status-text");
            const resultDiv = self.shadowRoot.querySelector("#hidden-webview-result");
            const domOutput = self.shadowRoot.querySelector("#dom-content-output");
            await fetchHiddenDomContent({ statusText, resultDiv, domOutput });
          });

      // Test webapp with activity toolbar (comparison test)
      self.shadowRoot
        .querySelector("#open-test-webapp-activity")
        .addEventListener("click", async function (e) {
          try {
            const urlToUse = getConfiguredTestWebappUrl();

            await InAppBrowser.openWebView({
              url: urlToUse,
              toolbarColor: "#ffc107",
              toolbarTextColor: "#212529",
              toolbarType: ToolBarType.ACTIVITY,
              backgroundColor: BackgroundColor.WHITE,
              title: "Back Button Test - Activity Mode",
              showReloadButton: false,
              visibleTitle: true,
              showArrow: true,
            });

            // Add event listeners for comparison
            InAppBrowser.addListener("urlChangeEvent", (result) => {
              console.log("🔄 [Activity Mode] URL changed:", result.url);
            });

            InAppBrowser.addListener("closeEvent", () => {
              console.log("❌ [Activity Mode] Close button pressed");
            });
          } catch (e) {
            console.error("Error opening test webapp in activity mode:", e);
            alert(
              "Error opening test webapp. Make sure your local server is running and url.js is configured correctly.",
            );
          }
        });
    }
  },
);

window.customElements.define(
  "capacitor-welcome-titlebar",
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: "open" });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 15px 15px 15px 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        font-size: 0.9em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  },
);
