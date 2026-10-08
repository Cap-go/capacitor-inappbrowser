import { CapacitorUpdater } from '@capgo/capacitor-updater';
import { Capacitor } from '@capacitor/core';
import { SplashScreen } from '@capacitor/splash-screen';
import { SystemBars, SystemBarType } from '@capacitor/core';
import {
  InAppBrowser,
  ToolBarType,
  BackgroundColor,
  InvisibilityMode,
} from '@capgo/capacitor-inappbrowser';
import { setupProxyDemoButtons } from './proxy-demo.js';
import { setupProxyRegression } from './proxy-regression.js';
import { attachKeyboardRegressionHarness } from './keyboard-regression.js';
import { attachFeatureSmokeHarness } from './feature-smoke.js';
import { setupFullscreenDemo } from './fullscreen-demo.js';
import { url as configuredTestWebappUrl } from './url.js';
import { openQaToolsPanel } from './qa-tools.js';

// Default URL configuration
let testWebappUrl = 'http://localhost:8000/index.php';

function getConfiguredTestWebappUrl() {
  return configuredTestWebappUrl || testWebappUrl;
}

window.customElements.define(
  'capacitor-welcome',
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: 'open' });

      root.innerHTML = `
    <style>
      :host {
        color-scheme: light dark;
        --ink: #0f172a;
        --muted: #64748b;
        --surface: #f8fafc;
        --card: #ffffff;
        --border: #e2e8f0;
        --brand: #1b8f5a;
        --brand-strong: #146b44;
        --radius: 14px;
        --shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        display: block;
        width: 100%;
        min-height: 100%;
        background: var(--surface);
        color: var(--ink);
      }
      @media (prefers-color-scheme: dark) {
        :host {
          --ink: #e2e8f0;
          --muted: #94a3b8;
          --surface: #0b1220;
          --card: #111827;
          --border: #1f2937;
          --shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
        }
      }
      * { box-sizing: border-box; }
      .app-header {
        padding: 20px 16px 12px;
        background: linear-gradient(160deg, var(--brand) 0%, var(--brand-strong) 100%);
        color: #fff;
      }
      .app-header h1 {
        margin: 0;
        font-size: 1.35rem;
        font-weight: 700;
        letter-spacing: -0.02em;
      }
      .app-header p {
        margin: 8px 0 0;
        font-size: 0.9rem;
        opacity: 0.92;
        line-height: 1.45;
      }
      .chip-row {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-top: 12px;
      }
      .chip {
        font-size: 0.75rem;
        font-weight: 600;
        padding: 4px 10px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.18);
        border: 1px solid rgba(255, 255, 255, 0.25);
      }
      main {
        padding: 12px 12px 24px;
        display: grid;
        gap: 12px;
        max-width: 720px;
        margin: 0 auto;
      }
      .card {
        background: var(--card);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 16px;
      }
      .card h2 {
        margin: 0 0 6px;
        font-size: 1.05rem;
        font-weight: 700;
      }
      .card .lede {
        margin: 0 0 12px;
        color: var(--muted);
        font-size: 0.88rem;
        line-height: 1.45;
      }
      .field-grid {
        display: grid;
        gap: 10px;
      }
      .field-row {
        display: flex;
        gap: 8px;
        align-items: stretch;
      }
      input[type="text"],
      input[type="email"],
      input[type="password"],
      select {
        width: 100%;
        padding: 10px 12px;
        border: 1px solid var(--border);
        border-radius: 10px;
        font-size: 0.9rem;
        background: var(--surface);
        color: var(--ink);
      }
      label.check {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        font-size: 0.88rem;
        line-height: 1.35;
        cursor: pointer;
      }
      label.check input {
        width: 18px;
        height: 18px;
        margin-top: 2px;
        flex-shrink: 0;
      }
      .button-row {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-top: 4px;
      }
      .button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: 10px 14px;
        background: var(--brand);
        color: #fff;
        font-size: 0.88rem;
        font-weight: 600;
        border: 0;
        border-radius: 10px;
        cursor: pointer;
      }
      .button:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .button.secondary { background: #334155; }
      .button.danger { background: #dc2626; }
      .button.info { background: #0284c7; }
      .button.warn { background: #ca8a04; color: #1f2937; }
      .button.purple { background: #6d28d9; }
      .button.teal { background: #0f766e; }
      .status-pane {
        margin-top: 10px;
        padding: 10px 12px;
        border-radius: 10px;
        background: var(--surface);
        border: 1px solid var(--border);
        font-size: 0.82rem;
        line-height: 1.45;
        color: var(--muted);
        white-space: pre-wrap;
        word-break: break-word;
      }
      .status-pane strong { color: var(--ink); }
      .log-pane {
        background: var(--card);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        padding: 12px 16px;
      }
      .log-pane h2 {
        margin: 0 0 8px;
        font-size: 0.95rem;
      }
      #app-activity-log {
        margin: 0;
        font-size: 0.8rem;
        line-height: 1.45;
        color: var(--muted);
        max-height: 140px;
        overflow-y: auto;
        white-space: pre-wrap;
      }
      pre {
        white-space: pre-wrap;
        word-break: break-word;
        margin: 0;
      }
    </style>
    <div>
      <header class="app-header">
        <h1>InAppBrowser Test App</h1>
        <p>Try common plugin flows on device or web. Maestro regression controls live in QA tools at the bottom of the page.</p>
        <div class="chip-row">
          <span class="chip">openWebView</span>
          <span class="chip">toolbar options</span>
          <span class="chip">downloads</span>
          <span class="chip">messaging</span>
        </div>
      </header>
      <main>
        <section class="log-pane" aria-live="polite">
          <h2>Activity</h2>
          <pre id="app-activity-log">Tap an action to see results here.</pre>
        </section>

        <section class="card">
          <h2>Open a URL</h2>
          <p class="lede">Load any HTTPS page with toolbar, deeplink, and user agent options.</p>
          <div class="field-grid">
            <div class="field-row">
              <input type="text" id="custom-url-input" value="https://example.com" placeholder="https://example.com" />
              <button type="button" id="clear-url-button" class="button danger" title="Clear URL">Clear</button>
            </div>
            <label class="check"><input type="checkbox" id="prevent-deeplink-toggle" /><span>Prevent deeplinks (block external app opening)</span></label>
            <label class="check"><input type="checkbox" id="spoof-firebase-toggle" /><span>Spoof Firebase (inject Service Worker polyfill)</span></label>
            <label class="check"><input type="checkbox" id="spoof-useragent-toggle" /><span>Use spoofed user agent (Android Chrome)</span></label>
            <label class="check"><input type="checkbox" id="enable-google-pay-toggle" /><span>Enable Google Pay support</span></label>
            <label class="check"><span>Toolbar type</span></label>
            <select id="toolbar-type-select">
              <option value="navigation">Navigation (back, forward, reload)</option>
              <option value="activity">Activity (close, share)</option>
              <option value="compact">Compact (close only)</option>
              <option value="blank">Blank (no toolbar)</option>
            </select>
            <label class="check"><input type="checkbox" id="native-navigation-gestures-toggle" checked /><span>Native navigation gestures (swipe)</span></label>
            <div class="button-row">
              <button type="button" class="button" id="open-custom-url">Open custom URL</button>
            </div>
          </div>
        </section>

        <section class="card">
          <h2>Documentation browser</h2>
          <p class="lede">Open the plugin repo in a styled in-app webview.</p>
          <div class="button-row">
            <button type="button" class="button secondary" id="open-browser">Open in-app browser</button>
            <button type="button" class="button secondary" id="open-browser-with-blocked-host">Open with blocked host</button>
          </div>
        </section>

        <section class="card">
          <h2>Download handling</h2>
          <p class="lede">Trigger a blob download and observe native handling or listener events.</p>
          <label class="check"><input type="checkbox" id="handle-downloads-toggle" checked /><span>Handle downloads natively</span></label>
          <div class="button-row">
            <button type="button" class="button" id="open-download-demo">Open auto download demo</button>
            <button type="button" class="button info" id="open-download-demo-listener">Open auto download + close on event</button>
          </div>
          <p id="download-event-status" class="status-pane">Download listener idle.</p>
        </section>

        <section class="card">
          <h2>Fullscreen demo</h2>
          <p class="lede">Exercise fullscreen, hidden open, and deferred show flows.</p>
          <label class="check"><input type="checkbox" id="fullscreen-start" checked /><span>Start fullscreen</span></label>
          <label class="check"><input type="checkbox" id="fullscreen-hidden" /><span>Open hidden</span></label>
          <label class="check"><input type="checkbox" id="fullscreen-deferred" /><span>Wait for page load</span></label>
          <div class="button-row">
            <button type="button" class="button" id="fullscreen-open">Open fullscreen demo</button>
            <button type="button" class="button secondary" id="fullscreen-show" disabled>Show demo</button>
          </div>
          <p id="fullscreen-status" class="status-pane">Open the demo to check native fullscreen and retained page state.</p>
        </section>

        <section class="card">
          <h2>WebView visibility</h2>
          <p class="lede">Hide or show the active webview. The toolbar near Done can hide it too.</p>
          <div class="button-row">
            <button type="button" class="button secondary" id="webview-hide">Hide WebView</button>
            <button type="button" class="button" id="webview-show">Show WebView</button>
          </div>
        </section>

        <section class="card">
          <h2>Hidden WebView</h2>
          <p class="lede">Load a page invisibly, then read DOM, visibility, and dimensions here.</p>
          <label class="check"><input type="checkbox" id="hidden-fake-visible-toggle" checked /><span>Fake visible (fullscreen metrics)</span></label>
          <div class="button-row">
            <button type="button" class="button purple" id="test-hidden-webview">Load hidden WebView</button>
            <button type="button" class="button danger" id="close-hidden-webview">Close hidden</button>
            <button type="button" class="button info" id="check-hidden-visibility">Check visibility</button>
            <button type="button" class="button teal" id="check-hidden-dimensions">Check dimensions</button>
            <button type="button" class="button secondary" id="refresh-hidden-dom">Refresh DOM</button>
          </div>
          <div id="hidden-webview-status" class="status-pane"><strong>Status:</strong> <span id="hidden-status-text">Not started</span></div>
          <div id="hidden-webview-result" class="status-pane" style="display: none; max-height: 220px; overflow-y: auto;">
            <strong>DOM content</strong>
            <pre id="dom-content-output"></pre>
          </div>
          <div id="hidden-webview-metrics" class="status-pane" style="display: none; max-height: 220px; overflow-y: auto;">
            <strong>Dimensions</strong>
            <pre id="metrics-output"></pre>
          </div>
        </section>

        <section class="card">
          <h2>Navigation test webapp</h2>
          <p class="lede">Open the PHP harness to debug back button behavior. Configure url.js first.</p>
          <div class="button-row">
            <button type="button" class="button" id="open-test-webapp">Open test webapp (navigation)</button>
            <button type="button" class="button warn" id="open-test-webapp-activity">Open test webapp (activity)</button>
          </div>
          <div id="webapp-status" class="status-pane"><strong>Setup:</strong> Copy url.js.example to url.js and set your local server URL.</div>
        </section>

        <section class="card">
          <h2>Target blank link</h2>
          <p class="lede">Verify target="_blank" HTTPS links stay inside the current webview.</p>
          <div class="button-row">
            <button type="button" class="button teal" id="open-blank-target-test">Open blank target HTTPS test</button>
          </div>
          <div id="blank-target-test-status" class="status-pane">
            <div><strong>Status:</strong> <span id="blank-target-status-text">Idle</span></div>
            <div><strong>Result:</strong> <span id="blank-target-result-text">not run</span></div>
            <div><strong>Last URL:</strong> <span id="blank-target-last-url-text">none</span></div>
          </div>
        </section>

        <section class="card">
          <h2>System bars</h2>
          <p class="lede">Toggle status and navigation bars through the SystemBars API.</p>
          <div class="button-row">
            <button type="button" class="button secondary" id="system-bars-show-all">Show all system bars</button>
            <button type="button" class="button secondary" id="system-bars-show-status">Show status bar</button>
            <button type="button" class="button secondary" id="system-bars-show-navigation">Show navigation bar</button>
            <button type="button" class="button danger" id="system-bars-hide-navigation">Hide navigation bar</button>
          </div>
        </section>

        <section class="card">
          <h2>Proxy regression (manual)</h2>
          <p class="lede">Self-contained proxy flow through <code>addProxyHandler()</code>.</p>
          <div class="button-row">
            <button type="button" class="button" id="run-proxy-regression" style="background: #5b39f7;">Run proxy regression test</button>
          </div>
          <div id="proxy-regression-status" class="status-pane">
            <strong>Status:</strong> <span id="proxy-regression-status-text">Not started</span>
            <div id="proxy-regression-details"></div>
          </div>
        </section>

        <section class="card">
          <h2>Proxy demo scenarios</h2>
          <p class="lede">Exercise real sites and proxy paths from the example app.</p>
          <div class="button-row">
            <button type="button" class="button teal" id="proxy-demo-grailed-stub">Grailed SDK stub proxy</button>
            <button type="button" class="button" id="proxy-demo-grailed-google-login">Grailed Google login proxy</button>
            <button type="button" class="button" id="proxy-demo-grailed-background-login">Grailed background login</button>
            <button type="button" class="button info" id="proxy-demo-facebook-login">Facebook login</button>
            <button type="button" class="button secondary" id="proxy-demo-facebook-script">Facebook script proxy</button>
          </div>
          <div class="field-grid" style="margin-top: 10px;">
            <input id="proxy-demo-google-email" type="email" placeholder="Google email" />
            <input id="proxy-demo-google-password" type="password" placeholder="Google password" />
            <input id="proxy-demo-google-otp" type="text" placeholder="Google 2FA code (optional)" />
          </div>
          <p class="lede" style="margin-top: 8px;">Background Grailed demo keeps pages hidden and reports steps below.</p>
          <div class="button-row">
            <button type="button" class="button secondary" id="proxy-demo-show-primary" disabled>Show hidden Grailed window</button>
            <button type="button" class="button secondary" id="proxy-demo-show-popup" disabled>Show hidden popup</button>
          </div>
          <div id="proxy-demo-status" class="status-pane">
            <strong>Status:</strong> <span id="proxy-demo-status-text">Not started</span>
            <div id="proxy-demo-details"></div>
            <div style="margin-top: 8px;"><strong>Steps</strong></div>
            <pre id="proxy-demo-history">No events yet.</pre>
          </div>
        </section>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;

      attachKeyboardRegressionHarness();
      attachFeatureSmokeHarness();

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
          'Capgo download demo successful.',
          'This file was downloaded natively by InAppBrowser.',
        ].join('\\n');

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
      const downloadStatusElement = () => self.shadowRoot.querySelector('#download-event-status');
      const downloadListenerButtonElement = () =>
        self.shadowRoot.querySelector('#open-download-demo-listener');
      const maestroRunDownloadButton = document.getElementById('maestro-run-download');
      const maestroDownloadStatus = document.getElementById('maestro-download-status');

      function setMaestroDownloadStatus(message) {
        if (maestroDownloadStatus) {
          maestroDownloadStatus.textContent = message;
        }
      }

      function setDownloadStatus(message, { backgroundColor = '#f8f9fa', color = '#495057' } = {}) {
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
          if (!listenerHandle || typeof listenerHandle.remove !== 'function') {
            continue;
          }
          try {
            await listenerHandle.remove();
          } catch (error) {
            console.warn('Could not remove stale download listener:', error);
          }
        }

        downloadListenerHandles = await Promise.all([
          InAppBrowser.addListener('downloadCompleted', (event) => {
            const successLabel = `Event OK: ${event.fileName}`;
            setDownloadListenerButtonLabel(successLabel);
            setMaestroDownloadStatus(successLabel);
            if (maestroRunDownloadButton) {
              maestroRunDownloadButton.disabled = false;
            }
            setDownloadStatus(`Download completed: ${event.fileName} via ${event.handledBy}`, {
              backgroundColor: '#dcfce7',
              color: '#166534',
            });

            if (closeOnNextDownloadEvent && event.id) {
              closeOnNextDownloadEvent = false;
              InAppBrowser.close({ id: event.id }).catch((error) => {
                console.warn('Could not close webview after download event:', error);
              });
            }
          }),
          InAppBrowser.addListener('downloadFailed', (event) => {
            closeOnNextDownloadEvent = false;
            setDownloadListenerButtonLabel('Event Failed');
            setMaestroDownloadStatus('Event Failed');
            if (maestroRunDownloadButton) {
              maestroRunDownloadButton.disabled = false;
            }
            const fileLabel = event.fileName ? ` for ${event.fileName}` : '';
            setDownloadStatus(`Download failed${fileLabel}: ${event.error}`, {
              backgroundColor: '#fee2e2',
              color: '#991b1b',
            });
          }),
        ]);

        return downloadListenerHandles;
      }

      async function openAutoDownloadDemo({ closeOnEvent = false } = {}) {
        const handleDownloadsToggle = self.shadowRoot.querySelector('#handle-downloads-toggle');
        closeOnNextDownloadEvent = closeOnEvent && handleDownloadsToggle.checked;
        if (maestroRunDownloadButton) {
          maestroRunDownloadButton.disabled = true;
        }
        setDownloadListenerButtonLabel(
          closeOnEvent && handleDownloadsToggle.checked
            ? 'Waiting For Download Event...'
            : 'Open Auto Download Demo + Close On Event',
        );
        const downloadMessage = handleDownloadsToggle.checked
          ? closeOnEvent
            ? 'Waiting for native download event, then closing the webview...'
            : 'Waiting for native download event...'
          : 'Native download handling disabled for this run.';
        setMaestroDownloadStatus(downloadMessage);
        setDownloadStatus(downloadMessage, {
          backgroundColor: handleDownloadsToggle.checked ? '#e0f2fe' : '#f8f9fa',
          color: handleDownloadsToggle.checked ? '#075985' : '#495057',
        });

        try {
          await createDownloadListeners();
          await InAppBrowser.openWebView({
            url: createAutoDownloadDemoUrl(),
            title: 'Auto Download Demo',
            toolbarColor: '#198754',
            toolbarType: ToolBarType.NAVIGATION,
            backgroundColor: BackgroundColor.WHITE,
            visibleTitle: true,
            showReloadButton: true,
            enabledSafeBottomMargin: true,
            handleDownloads: handleDownloadsToggle.checked,
          });
        } catch (error) {
          closeOnNextDownloadEvent = false;
          if (maestroRunDownloadButton) {
            maestroRunDownloadButton.disabled = false;
          }
          setMaestroDownloadStatus('Download open failed');
          console.error('Error opening auto download demo:', error);
        }
      }

      const blankTargetExpectedUrl = 'https://example.com/#blank-target-webview';
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
      const blankTargetButton = self.shadowRoot.querySelector('#open-blank-target-test');
      const blankTargetStatusText = self.shadowRoot.querySelector('#blank-target-status-text');
      const blankTargetResultText = self.shadowRoot.querySelector('#blank-target-result-text');
      const blankTargetLastUrlText = self.shadowRoot.querySelector('#blank-target-last-url-text');
      const maestroRunBlankTargetButton = document.getElementById('maestro-run-blank-target');
      const maestroBlankTargetStatus = document.getElementById('maestro-blank-target-status');
      const maestroBlankTargetDetails = document.getElementById('maestro-blank-target-details');
      let blankTargetTestActive = false;
      let blankTargetWebViewId = null;
      let blankTargetListenerHandles = [];

      function setBlankTargetButtonsDisabled(disabled) {
        if (blankTargetButton) {
          blankTargetButton.disabled = disabled;
        }
        if (maestroRunBlankTargetButton) {
          maestroRunBlankTargetButton.disabled = disabled;
        }
      }

      function updateMaestroBlankTargetState({ status, result, lastUrl }) {
        if (!maestroBlankTargetStatus || !maestroBlankTargetDetails) {
          return;
        }

        if (
          status === 'Closed' &&
          result === 'internal navigation confirmed' &&
          lastUrl === blankTargetExpectedUrl
        ) {
          maestroBlankTargetStatus.textContent = 'Blank target regression passed';
        } else if (status === 'Idle') {
          maestroBlankTargetStatus.textContent = 'Not started';
        } else if (
          status === 'Page load error' ||
          (status === 'Closed' && result !== 'internal navigation confirmed')
        ) {
          maestroBlankTargetStatus.textContent = 'Blank target regression failed';
        } else {
          maestroBlankTargetStatus.textContent = `Blank target regression: ${status}`;
        }

        maestroBlankTargetDetails.textContent = `${result}\n${lastUrl}`;
      }

      function setBlankTargetState({ status, result, lastUrl }) {
        blankTargetStatusText.textContent = status;
        blankTargetResultText.textContent = result;
        blankTargetLastUrlText.textContent = lastUrl;
        updateMaestroBlankTargetState({ status, result, lastUrl });
      }

      function isBlankTargetEvent(result) {
        if (!blankTargetTestActive) {
          return false;
        }

        if (!result?.id) {
          return blankTargetWebViewId === null;
        }

        if (blankTargetWebViewId === null) {
          blankTargetWebViewId = result.id;
          return true;
        }

        return result.id === blankTargetWebViewId;
      }

      async function clearBlankTargetListeners() {
        const handles = blankTargetListenerHandles;
        blankTargetListenerHandles = [];

        await Promise.all(
          handles.map((handle) => {
            if (!handle || typeof handle.remove !== 'function') {
              return Promise.resolve();
            }

            return Promise.resolve(handle.remove()).catch(() => {});
          }),
        );
      }

      async function attachBlankTargetListeners() {
        await clearBlankTargetListeners();

        blankTargetListenerHandles = [
          await InAppBrowser.addListener('urlChangeEvent', (result) => {
            if (!isBlankTargetEvent(result)) {
              return;
            }

            setBlankTargetState({
              status: result.url === blankTargetExpectedUrl ? 'Linked page loaded' : 'Navigating',
              result: blankTargetResultText.textContent,
              lastUrl: result.url,
            });
          }),
          await InAppBrowser.addListener('closeEvent', async (result) => {
            if (!isBlankTargetEvent(result)) {
              return;
            }

            const closedUrl = result.url || 'unknown';
            blankTargetTestActive = false;
            blankTargetWebViewId = null;

            setBlankTargetState({
              status: 'Closed',
              result:
                closedUrl === blankTargetExpectedUrl
                  ? 'internal navigation confirmed'
                  : `closed on ${closedUrl}`,
              lastUrl: closedUrl,
            });
            setBlankTargetButtonsDisabled(false);

            await clearBlankTargetListeners();
          }),
          await InAppBrowser.addListener('pageLoadError', async (result) => {
            if (!isBlankTargetEvent(result)) {
              return;
            }

            blankTargetTestActive = false;
            blankTargetWebViewId = null;
            setBlankTargetState({
              status: 'Page load error',
              result: 'page load error',
              lastUrl: blankTargetLastUrlText.textContent,
            });
            setBlankTargetButtonsDisabled(false);

            await clearBlankTargetListeners();
          }),
        ];
      }

      setBlankTargetState({
        status: 'Idle',
        result: 'not run',
        lastUrl: 'none',
      });

      async function fetchHiddenDomContent({ id, statusText, resultDiv, domOutput }) {
        if (!id || id !== hiddenWebViewId) return;
        statusText.textContent = 'Refreshing DOM content...';
        try {
          await InAppBrowser.executeScript({
            id,
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
            `,
          });
          if (id !== hiddenWebViewId) return;
          statusText.textContent = 'DOM refresh triggered. Waiting for content...';
        } catch (scriptError) {
          if (id !== hiddenWebViewId) return;
          console.error('Script execution error:', scriptError);
          statusText.textContent = 'Error refreshing DOM: ' + scriptError.message;
          resultDiv.style.display = 'none';
          domOutput.textContent = '';
        }
      }

      const maestroReadyBanner = document.getElementById('maestro-ready-banner');
      const maestroRunProxyButton = document.getElementById('maestro-run-proxy');
      const maestroProxyStatus = document.getElementById('maestro-proxy-status');
      const maestroProxyDetails = document.getElementById('maestro-proxy-details');

      const withMaestroNativeHarness = (callback) => {
        const harness = window.MaestroNativeHarness;
        if (!harness || typeof callback !== 'function') {
          return;
        }
        try {
          callback(harness);
        } catch (_error) {}
      };

      const syncMaestroNativeReady = (ready) => {
        withMaestroNativeHarness((harness) => {
          if (typeof harness.setReady === 'function') {
            harness.setReady(Boolean(ready));
          }
        });
      };

      const syncMaestroNativeRunning = (running) => {
        withMaestroNativeHarness((harness) => {
          if (typeof harness.setRunning === 'function') {
            harness.setRunning(Boolean(running));
          }
        });
      };

      const syncMaestroNativeStatus = (message, details = '') => {
        withMaestroNativeHarness((harness) => {
          if (typeof harness.setStatus === 'function') {
            harness.setStatus(message, details);
          }
        });
      };

      const updateMaestroStatus = (message, details = '') => {
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
            keepBrowserOpenOnFinish: typeof window.MaestroNativeHarness === 'undefined',
          });
        };
        maestroRunProxyButton.addEventListener('click', () => {
          proxyRegressionControls.run({ keepBrowserOpenOnFinish: true });
        });
        maestroRunProxyButton.disabled = false;
        if (maestroReadyBanner) {
          maestroReadyBanner.textContent = 'Maestro Ready';
        }
        openQaToolsPanel();
        syncMaestroNativeReady(true);
      } else if (maestroReadyBanner) {
        maestroReadyBanner.textContent = 'Maestro Unavailable';
        window.__capgoRunMaestroProxy = undefined;
        syncMaestroNativeReady(false);
      }

      setupProxyDemoButtons(self.shadowRoot);
      setupFullscreenDemo(self.shadowRoot);

      // Custom URL handler
      self.shadowRoot
        .querySelector('#open-custom-url')
        .addEventListener('click', async function (e) {
          const input = self.shadowRoot.querySelector('#custom-url-input');
          const preventDeeplinkToggle = self.shadowRoot.querySelector('#prevent-deeplink-toggle');
          const spoofFirebaseToggle = self.shadowRoot.querySelector('#spoof-firebase-toggle');
          const spoofUserAgentToggle = self.shadowRoot.querySelector('#spoof-useragent-toggle');
          const enableGooglePayToggle = self.shadowRoot.querySelector('#enable-google-pay-toggle');
          const enableGooglePay = enableGooglePayToggle.checked;
          const toolbarTypeSelect = self.shadowRoot.querySelector('#toolbar-type-select');
          const nativeNavigationGesturesToggle = self.shadowRoot.querySelector(
            '#native-navigation-gestures-toggle',
          );
          const url = input.value.trim();
          const preventDeeplink = preventDeeplinkToggle.checked;
          const spoofFirebase = spoofFirebaseToggle.checked;
          const spoofUserAgent = spoofUserAgentToggle.checked;
          const toolbarType = toolbarTypeSelect.value;
          const nativeNavigationGestures = nativeNavigationGesturesToggle.checked;

          if (!url) {
            alert('Please enter a URL');
            return;
          }

          // Auto-prepend https:// if no protocol is specified
          let urlToOpen = url;
          if (!url.startsWith('http://') && !url.startsWith('https://')) {
            urlToOpen = 'https://' + url;
          }

          if (!isValidUrl(urlToOpen)) {
            alert('Please enter a valid URL (e.g., https://example.com)');
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
            toolbarColor: '#007bff',
            toolbarType: toolbarType,
            backgroundColor: BackgroundColor.WHITE,
            title: 'Custom URL',
            showReloadButton: toolbarType === 'navigation',
            visibleTitle: true,
            enabledSafeBottomMargin: true,
            preventDeeplink: preventDeeplink,
            enableGooglePaySupport: enableGooglePay,
            activeNativeNavigationForWebview: nativeNavigationGestures,
            buttonNearDone: {
              ios: {
                iconType: 'sf-symbol',
                icon: 'eye.slash',
              },
              android: {
                iconType: 'vector',
                icon: 'ic_launcher_foreground',
                width: 24,
                height: 24,
              },
            },
          };

          if (spoofUserAgent) {
            options.headers = {
              'User-Agent':
                'Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36',
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
            InAppBrowser.addListener('urlChangeEvent', (result) => {
              console.log('URL changed:', result.url);
            });

            InAppBrowser.addListener('closeEvent', () => {
              console.log('Close button pressed');
            });

            InAppBrowser.addListener('browserPageLoaded', () => {
              console.log('Page loaded');
            });

            InAppBrowser.addListener('pageLoadError', () => {
              console.log('Page load error');
            });
          } catch (e) {
            console.error('Error opening custom URL:', e);
            alert('Error opening URL. Please check the URL and try again.');
          }
        });

      blankTargetButton.addEventListener('click', async function () {
        blankTargetTestActive = true;
        blankTargetWebViewId = null;
        setBlankTargetButtonsDisabled(true);
        setBlankTargetState({
          status: 'Opening test webview...',
          result: 'waiting for navigation',
          lastUrl: 'none',
        });

        try {
          await attachBlankTargetListeners();

          const { id } = await InAppBrowser.openWebView({
            url: blankTargetTestUrl,
            toolbarType: ToolBarType.COMPACT,
            backgroundColor: BackgroundColor.WHITE,
            title: 'Target Blank Test',
            visibleTitle: true,
            showReloadButton: false,
            activeNativeNavigationForWebview: false,
            enabledSafeBottomMargin: true,
            preventDeeplink: true,
          });
          if (blankTargetTestActive) {
            blankTargetWebViewId = id;
          }
        } catch (e) {
          blankTargetTestActive = false;
          blankTargetWebViewId = null;
          setBlankTargetButtonsDisabled(false);
          await clearBlankTargetListeners();
          console.error('Error opening blank target test:', e);
          setBlankTargetState({
            status: 'Error',
            result: e.message,
            lastUrl: 'none',
          });
        }
      });

      if (maestroRunBlankTargetButton) {
        maestroRunBlankTargetButton.addEventListener('click', () => {
          blankTargetButton.click();
        });
        maestroRunBlankTargetButton.disabled = false;
      }

      // Add Enter key support for the input field
      self.shadowRoot
        .querySelector('#custom-url-input')
        .addEventListener('keypress', async function (e) {
          if (e.key === 'Enter') {
            const button = self.shadowRoot.querySelector('#open-custom-url');
            button.click();
          }
        });

      // Add clear button handler
      self.shadowRoot.querySelector('#clear-url-button').addEventListener('click', function (e) {
        const input = self.shadowRoot.querySelector('#custom-url-input');
        input.value = '';
        input.focus();
      });

      self.shadowRoot.querySelector('#open-browser').addEventListener('click', async function (e) {
        try {
          await InAppBrowser.openWebView({
            url: 'https://github.com/Cap-go/capacitor-inappbrowser',
            toolbarColor: '#000000',
            toolbarType: ToolBarType.NAVIGATION,
            backgroundColor: BackgroundColor.BLACK,
            title: 'Capacitor InAppBrowser',
            enabledSafeBottomMargin: true,
          });

          // Add event listeners after opening the browser
          InAppBrowser.addListener('urlChange', (result) => {
            console.log('URL changed:', result.url);
          });

          InAppBrowser.addListener('closePressed', () => {
            console.log('Close button pressed');
          });

          InAppBrowser.addListener('sharePressed', () => {
            console.log('Share button pressed');
          });
        } catch (e) {
          console.error('Error opening in-app browser:', e);
        }
      });

      self.shadowRoot
        .querySelector('#open-browser-with-blocked-host')
        .addEventListener('click', async function (e) {
          try {
            await InAppBrowser.openWebView({
              url: 'https://github.com/Cap-go/capacitor-inappbrowser',
              toolbarColor: '#000000',
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.BLACK,
              title: 'Capacitor InAppBrowser, blocked GitHub host',
              enabledSafeBottomMargin: true,
              blockedHosts: ['github.com'],
            });

            // Add event listeners after opening the browser
            InAppBrowser.addListener('urlChange', (result) => {
              console.log('URL changed:', result.url);
            });

            InAppBrowser.addListener('closePressed', () => {
              console.log('Close button pressed');
            });

            InAppBrowser.addListener('sharePressed', () => {
              console.log('Share button pressed');
            });
          } catch (e) {
            console.error('Error opening in-app browser:', e);
          }
        });

      self.shadowRoot
        .querySelector('#open-download-demo')
        .addEventListener('click', async function () {
          await openAutoDownloadDemo({ closeOnEvent: false });
        });

      self.shadowRoot
        .querySelector('#open-download-demo-listener')
        .addEventListener('click', async function () {
          await openAutoDownloadDemo({ closeOnEvent: true });
        });

      if (maestroRunDownloadButton) {
        maestroRunDownloadButton.addEventListener('click', async function () {
          await openAutoDownloadDemo({ closeOnEvent: true });
        });
        maestroRunDownloadButton.disabled = false;
      }

      self.shadowRoot
        .querySelector('#system-bars-show-all')
        .addEventListener('click', async function () {
          try {
            await SystemBars.show();
          } catch (e) {
            console.error('Error showing system bars:', e);
          }
        });

      self.shadowRoot
        .querySelector('#system-bars-show-status')
        .addEventListener('click', async function () {
          try {
            await SystemBars.show({ bar: SystemBarType.StatusBar });
          } catch (e) {
            console.error('Error showing status bar:', e);
          }
        });

      self.shadowRoot
        .querySelector('#system-bars-show-navigation')
        .addEventListener('click', async function () {
          try {
            await SystemBars.show({ bar: SystemBarType.NavigationBar });
          } catch (e) {
            console.error('Error showing navigation bar:', e);
          }
        });

      self.shadowRoot
        .querySelector('#system-bars-hide-navigation')
        .addEventListener('click', async function () {
          try {
            await SystemBars.hide({ bar: SystemBarType.NavigationBar });
          } catch (e) {
            console.error('Error hiding navigation bar:', e);
          }
        });

      self.shadowRoot.querySelector('#webview-hide').addEventListener('click', async function () {
        try {
          await InAppBrowser.hide();
        } catch (e) {
          console.error('Error hiding webview:', e);
        }
      });

      self.shadowRoot.querySelector('#webview-show').addEventListener('click', async function () {
        try {
          await InAppBrowser.show();
        } catch (e) {
          console.error('Error showing webview:', e);
        }
      });

      InAppBrowser.addListener('buttonNearDoneClick', async () => {
        try {
          await InAppBrowser.hide();
        } catch (e) {
          console.error('Error hiding webview from toolbar button:', e);
        }
      });

      // Test webapp with navigation toolbar (main test for back button issue)
      self.shadowRoot
        .querySelector('#open-test-webapp')
        .addEventListener('click', async function (e) {
          try {
            const urlToUse = getConfiguredTestWebappUrl();

            await InAppBrowser.openWebView({
              url: urlToUse,
              toolbarColor: '#ffffff',
              toolbarTextColor: '#000000',
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.WHITE,
              title: 'Back Button Test - Navigation Mode',
              showReloadButton: true,
              visibleTitle: true,
              showArrow: false,
            });

            // Add comprehensive event listeners for debugging
            InAppBrowser.addListener('urlChangeEvent', (result) => {
              console.log('🔄 URL changed:', result.url);
            });

            InAppBrowser.addListener('closeEvent', () => {
              console.log('❌ Close button pressed');
            });

            InAppBrowser.addListener('browserPageLoaded', () => {
              console.log('✅ Page loaded');
            });

            InAppBrowser.addListener('pageLoadError', () => {
              console.log('❌ Page load error');
            });

            InAppBrowser.addListener('messageFromWebview', (event) => {
              console.log('💬 Message from webview:', event.detail);
            });
          } catch (e) {
            console.error('Error opening test webapp:', e);
            alert(
              'Error opening test webapp. Make sure your local server is running and url.js is configured correctly.',
            );
          }
        });

      let hiddenWebViewListenerHandles = [];
      let hiddenWebViewId;
      let hiddenDomTimer;
      let hiddenWebViewRun = 0;

      async function removeHiddenWebViewListeners() {
        hiddenWebViewRun++;
        clearTimeout(hiddenDomTimer);
        hiddenDomTimer = undefined;
        hiddenWebViewId = undefined;
        const handles = hiddenWebViewListenerHandles;
        hiddenWebViewListenerHandles = [];
        await Promise.all(handles.map((handle) => handle.remove()));
      }

      async function addHiddenWebViewListener(id, event, listener) {
        if (hiddenWebViewId !== id) return;
        const handle = await InAppBrowser.addListener(event, listener);
        if (hiddenWebViewId === id) hiddenWebViewListenerHandles.push(handle);
        else await handle.remove();
      }

      // Hidden WebView Test
      self.shadowRoot
        .querySelector('#test-hidden-webview')
        .addEventListener('click', async function (e) {
          const statusText = self.shadowRoot.querySelector('#hidden-status-text');
          const openButton = e.currentTarget;
          openButton.disabled = true;
          const resultDiv = self.shadowRoot.querySelector('#hidden-webview-result');
          const metricsDiv = self.shadowRoot.querySelector('#hidden-webview-metrics');
          const domOutput = self.shadowRoot.querySelector('#dom-content-output');
          const metricsOutput = self.shadowRoot.querySelector('#metrics-output');
          const fakeVisibleToggle = self.shadowRoot.querySelector('#hidden-fake-visible-toggle');

          let run;
          try {
            statusText.textContent = 'Opening hidden webview...';
            resultDiv.style.display = 'none';
            metricsDiv.style.display = 'none';

            const previousId = hiddenWebViewId;
            const cleanup = removeHiddenWebViewListeners();
            run = hiddenWebViewRun;
            await cleanup;
            if (previousId) await InAppBrowser.close({ id: previousId });
            if (run !== hiddenWebViewRun) return;
            const { id } = await InAppBrowser.openWebView({
              url: 'https://example.com',
              hidden: true,
              invisibilityMode:
                fakeVisibleToggle && fakeVisibleToggle.checked
                  ? InvisibilityMode.FAKE_VISIBLE
                  : InvisibilityMode.AWARE,
              buttonNearDone: {
                ios: {
                  iconType: 'sf-symbol',
                  icon: 'eye.slash',
                },
                android: {
                  iconType: 'vector',
                  icon: 'ic_launcher_foreground',
                  width: 24,
                  height: 24,
                },
              },
            });

            if (run !== hiddenWebViewRun) {
              await InAppBrowser.close({ id });
              return;
            }
            hiddenWebViewId = id;
            statusText.textContent = 'WebView opened (hidden). Waiting for page load...';

            await addHiddenWebViewListener(id, 'messageFromWebview', (event) => {
              if (event.id !== id || hiddenWebViewId !== id) return;
              console.log('Message from hidden webview:', event);
              if (event.detail && event.detail.type === 'domContent') {
                statusText.textContent = `DOM extracted from: ${event.detail.title} (${event.detail.url})`;
                domOutput.textContent = event.detail.content;
                resultDiv.style.display = 'block';
              } else if (event.detail && event.detail.type === 'visibilityState') {
                statusText.textContent = `document.visibilityState: ${event.detail.state}`;
              } else if (event.detail && event.detail.type === 'dimensions') {
                statusText.textContent = 'Dimensions received.';
                metricsOutput.textContent = JSON.stringify(event.detail.data, null, 2);
                metricsDiv.style.display = 'block';
              }
            });

            await addHiddenWebViewListener(id, 'buttonNearDoneClick', async (event) => {
              if (event.id !== id || hiddenWebViewId !== id) return;
              try {
                await InAppBrowser.hide({ id });
              } catch (e) {
                console.error('Error hiding webview from toolbar button:', e);
              }
            });

            await addHiddenWebViewListener(id, 'browserPageLoaded', (event) => {
              if (event.id !== id || hiddenWebViewId !== id) return;
              statusText.textContent = 'Page loaded! Extracting DOM content...';

              clearTimeout(hiddenDomTimer);
              hiddenDomTimer = setTimeout(async () => {
                await fetchHiddenDomContent({ id, statusText, resultDiv, domOutput });
              }, 500);
            });
            await addHiddenWebViewListener(id, 'closeEvent', async (event) => {
              if (event.id !== id || hiddenWebViewId !== id) return;
              const cleanup = removeHiddenWebViewListeners();
              const closedRun = hiddenWebViewRun;
              await cleanup;
              if (closedRun === hiddenWebViewRun) statusText.textContent = 'Hidden webview closed.';
            });
          } catch (e) {
            if (run !== hiddenWebViewRun) return;
            const id = hiddenWebViewId;
            await removeHiddenWebViewListeners();
            if (id) await InAppBrowser.close({ id }).catch(console.error);
            console.error('Error with hidden webview:', e);
            statusText.textContent = 'Error: ' + e.message;
          } finally {
            openButton.disabled = false;
          }
        });

      // Close Hidden WebView
      self.shadowRoot
        .querySelector('#close-hidden-webview')
        .addEventListener('click', async function (e) {
          const statusText = self.shadowRoot.querySelector('#hidden-status-text');
          let closedRun;
          try {
            const id = hiddenWebViewId;
            const cleanup = removeHiddenWebViewListeners();
            closedRun = hiddenWebViewRun;
            await cleanup;
            if (id) await InAppBrowser.close({ id });
            if (closedRun === hiddenWebViewRun) statusText.textContent = 'Hidden webview closed.';
          } catch (e) {
            if (closedRun !== hiddenWebViewRun) return;
            console.error('Error closing hidden webview:', e);
            statusText.textContent = 'Error closing: ' + e.message;
          }
        });

      self.shadowRoot
        .querySelector('#check-hidden-visibility')
        .addEventListener('click', async function (e) {
          const statusText = self.shadowRoot.querySelector('#hidden-status-text');
          const id = hiddenWebViewId;
          if (!id) return;
          try {
            statusText.textContent = 'Checking document.visibilityState...';
            await InAppBrowser.executeScript({
              id,
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
                `,
            });
          } catch (e) {
            if (id !== hiddenWebViewId) return;
            console.error('Error checking visibility:', e);
            statusText.textContent = 'Hidden webview not open or script failed.';
          }
        });

      self.shadowRoot
        .querySelector('#check-hidden-dimensions')
        .addEventListener('click', async function (e) {
          const statusText = self.shadowRoot.querySelector('#hidden-status-text');
          const id = hiddenWebViewId;
          if (!id) return;
          try {
            statusText.textContent = 'Checking dimensions...';
            await InAppBrowser.executeScript({
              id,
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
                `,
            });
          } catch (e) {
            if (id !== hiddenWebViewId) return;
            console.error('Error checking dimensions:', e);
            statusText.textContent = 'Hidden webview not open or script failed.';
          }
        });

      self.shadowRoot
        .querySelector('#refresh-hidden-dom')
        .addEventListener('click', async function () {
          const statusText = self.shadowRoot.querySelector('#hidden-status-text');
          const resultDiv = self.shadowRoot.querySelector('#hidden-webview-result');
          const domOutput = self.shadowRoot.querySelector('#dom-content-output');
          await fetchHiddenDomContent({ id: hiddenWebViewId, statusText, resultDiv, domOutput });
        });

      // Test webapp with activity toolbar (comparison test)
      self.shadowRoot
        .querySelector('#open-test-webapp-activity')
        .addEventListener('click', async function (e) {
          try {
            const urlToUse = getConfiguredTestWebappUrl();

            await InAppBrowser.openWebView({
              url: urlToUse,
              toolbarColor: '#ffc107',
              toolbarTextColor: '#212529',
              toolbarType: ToolBarType.ACTIVITY,
              backgroundColor: BackgroundColor.WHITE,
              title: 'Back Button Test - Activity Mode',
              showReloadButton: false,
              visibleTitle: true,
              showArrow: true,
            });

            // Add event listeners for comparison
            InAppBrowser.addListener('urlChangeEvent', (result) => {
              console.log('🔄 [Activity Mode] URL changed:', result.url);
            });

            InAppBrowser.addListener('closeEvent', () => {
              console.log('❌ [Activity Mode] Close button pressed');
            });
          } catch (e) {
            console.error('Error opening test webapp in activity mode:', e);
            alert(
              'Error opening test webapp. Make sure your local server is running and url.js is configured correctly.',
            );
          }
        });
    }
  },
);

window.customElements.define(
  'capacitor-welcome-titlebar',
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: 'open' });
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

if (Capacitor.isNativePlatform()) {
  CapacitorUpdater.notifyAppReady().catch((error) => {
    console.error('Capgo notifyAppReady failed', error);
  });
}
