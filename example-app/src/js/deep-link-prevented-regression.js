import { InAppBrowser, ToolBarType, BackgroundColor } from "@capgo/capacitor-inappbrowser";
import { openQaToolsPanel } from "./qa-tools.js";

const CUSTOM_SCHEME_URL = "capgo-maestro-deeplink://blocked";
const HTTPS_URL = "https://example.com/";
const RUN_TIMEOUT_MS = 60_000;
const EVENT_OK_STATUS = "Event OK: capgo-maestro-deeplink://blocked";

let harnessAttached = false;
let harnessAttachPending = false;
let activeRegressionGeneration = 0;

async function waitUntil(predicate, timeoutMs, intervalMs = 50) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicate()) {
      return;
    }
    await new Promise((resolve) => globalThis.setTimeout(resolve, intervalMs));
  }
  throw new Error("Timed out waiting for condition");
}

async function waitForStableCount(readCount, stableMs, timeoutMs) {
  let lastCount = readCount();
  let stableSince = Date.now();
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await new Promise((resolve) => globalThis.setTimeout(resolve, 50));
    const current = readCount();
    if (current !== lastCount) {
      lastCount = current;
      stableSince = Date.now();
      continue;
    }
    if (Date.now() - stableSince >= stableMs) {
      return lastCount;
    }
  }
  return lastCount;
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

function syncMaestroNativeStatus(message, details = "") {
  withMaestroNativeHarness((harness) => {
    if (typeof harness.setStatus === "function") {
      harness.setStatus(message, details);
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
    syncMaestroNativeStatus(line, extra);
  };

  const runRegression = async () => {
    const runGeneration = ++activeRegressionGeneration;
    const isActiveRun = () => runGeneration === activeRegressionGeneration;

    runButton.disabled = true;
    syncMaestroNativeRunning(true);
    setStatus("Deep link prevented regression running", "Opening webview...");

    const preventedEvents = [];
    let webViewId = null;
    let deepLinkListener = null;
    let timeoutId = null;

    const timeoutPromise = new Promise((_, reject) => {
      timeoutId = globalThis.setTimeout(() => {
        reject(new Error("Deep link prevented regression timed out"));
      }, RUN_TIMEOUT_MS);
    });

    const work = async () => {
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

      let regressionPageLoaded = false;
      const pageLoadedListener = await InAppBrowser.addListener("browserPageLoaded", (event) => {
        if (event.id && webViewId && event.id !== webViewId) {
          return;
        }
        regressionPageLoaded = true;
      });
      await waitUntil(() => regressionPageLoaded, 15_000);
      await pageLoadedListener.remove().catch(() => {});

      await InAppBrowser.executeScript({
        id: webViewId,
        code: "document.getElementById('deeplink-link').click();",
      });

      await waitUntil(
        () => preventedEvents.some((event) => event.url === CUSTOM_SCHEME_URL),
        15_000,
      );

      const eventCountBeforeHttps = preventedEvents.length;

      await InAppBrowser.executeScript({
        id: webViewId,
        code: "document.getElementById('https-link').click();",
      });

      const stableCount = await waitForStableCount(() => preventedEvents.length, 500, 12_000);
      if (stableCount !== eventCountBeforeHttps) {
        throw new Error("deepLinkPrevented fired for ordinary https navigation");
      }

      await InAppBrowser.close({ id: webViewId });
      webViewId = null;

      if (!isActiveRun()) {
        return;
      }

      const successDetails = [
        "Deep link prevented regression passed",
        "Negative OK: https produced no deepLinkPrevented",
      ].join("\n");
      setStatus(EVENT_OK_STATUS, successDetails);
    };

    try {
      await Promise.race([work(), timeoutPromise]);
    } catch (error) {
      if (isActiveRun()) {
        const message = error instanceof Error ? error.message : String(error);
        setStatus("Deep link prevented regression failed", message);
      }
      if (webViewId) {
        await InAppBrowser.close({ id: webViewId }).catch(() => {});
      }
      throw error;
    } finally {
      if (timeoutId !== null) {
        globalThis.clearTimeout(timeoutId);
      }
      if (deepLinkListener) {
        await deepLinkListener.remove().catch(() => {});
      }
      if (isActiveRun()) {
        runButton.disabled = false;
        syncMaestroNativeRunning(false);
      }
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