import { InAppBrowser, ToolBarType, BackgroundColor } from "@capgo/capacitor-inappbrowser";
import { openQaToolsPanel } from "./qa-tools.js";

const CUSTOM_SCHEME_URL = "capgo-maestro-deeplink://blocked";
const HTTPS_URL = "https://example.com/";
const RUN_TIMEOUT_MS = 60_000;

let harnessAttached = false;
let harnessAttachPending = false;

function sleep(ms) {
  return new Promise((resolve) => globalThis.setTimeout(resolve, ms));
}

function withMaestroNativeHarness(callback) {
  const harness = window.MaestroNativeHarness;
  if (!harness || typeof callback !== "function") {
    return;
  }

  try {
    callback(harness);
  } catch (_error) {}
}

function syncMaestroNativeRunning(running) {
  withMaestroNativeHarness((harness) => {
    if (typeof harness.setRunning === "function") {
      harness.setRunning(Boolean(running));
    }
  });
}

function buildDeepLinkPreventedTestUrl() {
  const html = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Deep Link Prevented Test</title>
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 24px; }
      a { display: block; margin: 16px 0; font-size: 18px; }
    </style>
  </head>
  <body>
  <h1>Deep Link Prevented Test</h1>
  <p id="status">Ready for regression.</p>
  <a id="deeplink-link" href="${CUSTOM_SCHEME_URL}">Open custom scheme</a>
  <a id="https-link" href="${HTTPS_URL}">Open example.com https</a>
  </body>
</html>`;

  return `data:text/html;charset=utf-8,${encodeURIComponent(html)}`;
}

export function attachDeepLinkPreventedRegressionHarness() {
  if (harnessAttached) {
    return;
  }

  const runButton = document.getElementById("maestro-run-deep-link-prevented");
  const status = document.getElementById("maestro-deep-link-prevented-status");
  const details = document.getElementById("maestro-deep-link-prevented-details");

  if (!runButton || !status || !details) {
    if (!harnessAttachPending) {
      harnessAttachPending = true;
      const retry = () => {
        harnessAttachPending = false;
        attachDeepLinkPreventedRegressionHarness();
      };
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", retry, { once: true });
      } else {
        window.requestAnimationFrame(retry);
      }
    }
    return;
  }

  harnessAttached = true;

  const setStatus = (line, extra = "") => {
    status.textContent = line;
    details.textContent = extra;
  };

  const runRegression = async () => {
    runButton.disabled = true;
    syncMaestroNativeRunning(true);
    setStatus("Deep link prevented regression running", "Opening webview...");

    const preventedEvents = [];
    let webViewId = null;
    let deepLinkListener = null;

    const timeout = globalThis.setTimeout(() => {
      throw new Error("Deep link prevented regression timed out");
    }, RUN_TIMEOUT_MS);

    try {
      deepLinkListener = await InAppBrowser.addListener("deepLinkPrevented", (event) => {
        preventedEvents.push(event);
      });

      const { id } = await InAppBrowser.openWebView({
        url: buildDeepLinkPreventedTestUrl(),
        title: "Deep Link Prevented Test",
        toolbarType: ToolBarType.NAVIGATION,
        backgroundColor: BackgroundColor.WHITE,
        visibleTitle: true,
        showReloadButton: false,
        enabledSafeBottomMargin: true,
        preventDeeplink: true,
      });
      webViewId = id;

      await new Promise((resolve) => {
        let settled = false;
        const finish = () => {
          if (settled) {
            return;
          }
          settled = true;
          resolve();
        };
        InAppBrowser.addListener("browserPageLoaded", (event) => {
          if (event.id && webViewId && event.id !== webViewId) {
            return;
          }
          finish();
        }).catch(() => finish());
        globalThis.setTimeout(finish, 3000);
      });
      await sleep(400);

      await InAppBrowser.executeScript({
        id: webViewId,
        code: "document.getElementById('deeplink-link').click();",
      });

      await sleep(1200);

      const customSchemeEvent = preventedEvents.find((event) => event.url === CUSTOM_SCHEME_URL);
      if (!customSchemeEvent) {
        throw new Error(`Expected deepLinkPrevented for ${CUSTOM_SCHEME_URL}`);
      }

      const eventCountBeforeHttps = preventedEvents.length;

      await InAppBrowser.executeScript({
        id: webViewId,
        code: "document.getElementById('https-link').click();",
      });

      await sleep(1500);

      if (preventedEvents.length !== eventCountBeforeHttps) {
        throw new Error("deepLinkPrevented fired for ordinary https navigation");
      }

      await InAppBrowser.close({ id: webViewId });
      webViewId = null;

      const successDetails = [
        `Event OK: ${CUSTOM_SCHEME_URL}`,
        "Negative OK: https produced no deepLinkPrevented",
      ].join("\n");
      setStatus("Deep link prevented regression passed", successDetails);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setStatus("Deep link prevented regression failed", message);
      if (webViewId) {
        await InAppBrowser.close({ id: webViewId }).catch(() => {});
      }
      throw error;
    } finally {
      globalThis.clearTimeout(timeout);
      if (deepLinkListener) {
        await deepLinkListener.remove().catch(() => {});
      }
      runButton.disabled = false;
      syncMaestroNativeRunning(false);
    }
  };

  window.__capgoRunMaestroDeepLinkPrevented = () => {
    runRegression().catch((error) => {
      console.error("Deep link prevented regression failed:", error);
    });
  };

  runButton.addEventListener("click", () => {
    window.__capgoRunMaestroDeepLinkPrevented();
  });
  runButton.disabled = false;
  openQaToolsPanel();
  setStatus("Deep link prevented regression ready", "");
}