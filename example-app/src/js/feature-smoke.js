import {
  InAppBrowser,
  ToolBarType,
  BackgroundColor,
  InvisibilityMode,
  addProxyHandler,
} from "@capgo/inappbrowser";

const SMOKE_ORIGIN = "https://feature-smoke.capgo.test";
const ENTRY_URL = `${SMOKE_ORIGIN}/entry`;
const HISTORY_ENTRY_URL = `${SMOKE_ORIGIN}/history-entry`;
const SECOND_URL = `${SMOKE_ORIGIN}/second`;
const POPUP_URL = `${SMOKE_ORIGIN}/popup`;
const SCRIPT_URL = `${SMOKE_ORIGIN}/smoke.js`;
const TIMEOUT_MS = 180000;

const ENTRY_HTML = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Feature Smoke Entry</title>
    <link rel="icon" href="data:," />
  </head>
  <body>
    <main>
      <h1>Feature Smoke Entry</h1>
      <p id="status">Waiting for plugin bridge.</p>
      <script src="${SCRIPT_URL}"></script>
    </main>
  </body>
</html>`;

const POPUP_HTML = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Feature Smoke Popup</title>
    <link rel="icon" href="data:," />
  </head>
  <body>
    <main>
      <h1>Feature Smoke Popup</h1>
    </main>
  </body>
</html>`;

const SMOKE_SCRIPT = `(() => {
  const post = (step, extra = {}) => {
    if (!window.mobileApp || typeof window.mobileApp.postMessage !== "function") {
      return;
    }

    window.mobileApp.postMessage({
      detail: {
        type: "featureSmoke",
        step,
        ...extra,
      },
    });
  };

  console.log("feature-smoke-console-ok");
  document.cookie = "featureSmokeCookie=enabled; path=/; SameSite=Lax";

  window.addEventListener("messageFromNative", (event) => {
    post("post-message", {
      payload: event.detail || null,
    });
  });

  const postNavigationState = () => {
    if (window.location.href === "${SECOND_URL}") {
      post("second-ready", {
        href: window.location.href,
        title: document.title,
      });
      return;
    }
    post("entry-history-ready", {
      href: window.location.href,
    });
  };

  window.addEventListener("popstate", postNavigationState);
  window.addEventListener("pageshow", postNavigationState);

  window.setTimeout(() => {
    const status = document.getElementById("status");
    if (status) {
      status.textContent = "Plugin bridge ready.";
    }
    post("entry-ready", {
      cookie: document.cookie,
      href: window.location.href,
      preShowFlag: window.__featureSmokePreShow === true,
      title: document.title,
    });
    postNavigationState();
  }, 50);
})();`;

function textResponse(body, contentType) {
  return new Response(body, {
    status: 200,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": contentType,
    },
  });
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function normalizeError(error) {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function isEntrySmokeUrl(url) {
  return url === ENTRY_URL || url === HISTORY_ENTRY_URL;
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

export function attachFeatureSmokeHarness() {
  const runButton = document.getElementById("maestro-run-feature-smoke");
  const status = document.getElementById("maestro-feature-smoke-status");
  const details = document.getElementById("maestro-feature-smoke-details");

  if (!runButton || !status || !details) {
    return;
  }

  const setStatus = (message, detailText = "") => {
    status.textContent = message;
    details.textContent = detailText;
    withMaestroNativeHarness((harness) => {
      if (typeof harness.setStatus === "function") {
        harness.setStatus(message, detailText);
      }
    });
  };

  const setRunning = (running) => {
    runButton.disabled = running;
    withMaestroNativeHarness((harness) => {
      if (typeof harness.setRunning === "function") {
        harness.setRunning(Boolean(running));
      }
    });
  };

  const markReady = () => {
    runButton.disabled = false;
    setStatus("Feature smoke ready");
  };

  const markStep = (steps, step) => {
    steps.push(step);
    setStatus(`Feature smoke running: ${step}`, steps.join("\n"));
  };

  const waitUntil = async (label, predicate, timeout = TIMEOUT_MS) => {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeout) {
      const result = await predicate();
      if (result) {
        return result;
      }
      await sleep(100);
    }
    throw new Error(`Timed out waiting for ${label}`);
  };

  runButton.addEventListener("click", async () => {
    if (runButton.disabled) {
      return;
    }

    setRunning(true);

    const handles = [];
    const messages = [];
    const consoleMessages = [];
    const screenshots = [];
    const closeEvents = [];
    const popupEvents = [];
    const pageLoads = [];
    const urlChanges = [];
    const steps = [];
    let webviewId = null;
    let popupId = null;

    const waitForMessage = (step, timeout) =>
      waitUntil(
        step,
        () => messages.find((message) => message.step === step),
        timeout,
      );

    try {
      await InAppBrowser.removeAllListeners();
      await InAppBrowser.clearAllCookies();
      await InAppBrowser.clearCache();

      handles.push(
        await InAppBrowser.addListener("messageFromWebview", (event) => {
          const detail = event.detail || {};
          if (detail.type !== "featureSmoke") {
            return;
          }
          messages.push({
            id: event.id,
            ...detail,
          });
        }),
      );
      handles.push(
        await InAppBrowser.addListener("consoleMessage", (event) => {
          consoleMessages.push(event);
        }),
      );
      handles.push(
        await InAppBrowser.addListener("screenshotTaken", (event) => {
          screenshots.push(event);
        }),
      );
      handles.push(
        await InAppBrowser.addListener("popupWindowOpened", (event) => {
          popupEvents.push(event);
        }),
      );
      handles.push(
        await InAppBrowser.addListener("browserPageLoaded", (event) => {
          pageLoads.push(event);
        }),
      );
      handles.push(
        await InAppBrowser.addListener("urlChangeEvent", (event) => {
          urlChanges.push(event);
        }),
      );
      handles.push(
        await InAppBrowser.addListener("closeEvent", (event) => {
          closeEvents.push(event);
        }),
      );
      handles.push(
        await InAppBrowser.addListener("pageLoadError", (event) => {
          messages.push({
            id: event.id,
            step: "page-load-error",
          });
        }),
      );

      handles.push(
        await addProxyHandler((request) => {
          if (request.url === ENTRY_URL || request.url === HISTORY_ENTRY_URL || request.url === SECOND_URL) {
            return textResponse(ENTRY_HTML, "text/html");
          }
          if (request.url === POPUP_URL) {
            return textResponse(POPUP_HTML, "text/html");
          }
          if (request.url === SCRIPT_URL) {
            return textResponse(SMOKE_SCRIPT, "application/javascript");
          }
          return null;
        }),
      );

      markStep(steps, "open hidden proxied webview");
      const openResult = await InAppBrowser.openWebView({
        url: ENTRY_URL,
        proxyRequests: true,
        toolbarType: ToolBarType.BLANK,
        backgroundColor: BackgroundColor.WHITE,
        hidden: true,
        invisibilityMode: InvisibilityMode.FAKE_VISIBLE,
        captureConsoleLogs: true,
        allowScreenshotsFromWebPage: true,
        hiddenPopupWindow: true,
        enableGooglePaySupport: true,
        activeNativeNavigationForWebview: true,
        enabledSafeBottomMargin: true,
        enabledSafeTopMargin: true,
        isPresentAfterPageLoad: true,
        preShowScript: "window.__featureSmokePreShow = true;",
        preShowScriptInjectionTime: "documentStart",
        width: 360,
        height: 640,
        x: 0,
        y: 0,
      });
      webviewId = openResult.id;

      const entryReady = await waitForMessage("entry-ready");
      if (!entryReady.preShowFlag) {
        throw new Error("preShowScript did not run before entry script");
      }
      await waitUntil("consoleMessage", () =>
        consoleMessages.some((event) => String(event.message).includes("feature-smoke-console-ok")),
      );
      markStep(steps, "page load, proxy, console, preShowScript");

      const cookies = await InAppBrowser.getCookies({ url: SMOKE_ORIGIN });
      if (cookies.featureSmokeCookie !== "enabled") {
        throw new Error("featureSmokeCookie was not available through getCookies()");
      }
      markStep(steps, "getCookies");

      await InAppBrowser.postMessage({
        id: webviewId,
        detail: {
          action: "native-ping",
          marker: "post-message-ok",
        },
      });
      const postMessage = await waitForMessage("post-message");
      if (postMessage.payload?.marker !== "post-message-ok") {
        throw new Error("postMessage payload was not echoed by the webview");
      }
      markStep(steps, "postMessage");

      await InAppBrowser.executeScript({
        id: webviewId,
        code: `
          window.mobileApp.postMessage({
            detail: {
              type: "featureSmoke",
              step: "execute-script",
              title: document.title
            }
          });
        `,
      });
      const scriptMessage = await waitForMessage("execute-script");
      if (scriptMessage.title !== "Feature Smoke Entry") {
        throw new Error("executeScript returned the wrong document title");
      }
      markStep(steps, "executeScript");

      await InAppBrowser.updateDimensions({ id: webviewId, width: 320, height: 560, x: 0, y: 0 });
      await InAppBrowser.setEnabledSafeTopMargin({ id: webviewId, enabled: false });
      await InAppBrowser.setEnabledSafeBottomMargin({ id: webviewId, enabled: true });
      markStep(steps, "dimensions and safe margins");

      await InAppBrowser.show({ id: webviewId });
      await sleep(500);
      const screenshot = await InAppBrowser.takeScreenshot({ id: webviewId });
      if (!screenshot.base64 || !screenshot.width || !screenshot.height) {
        throw new Error("takeScreenshot returned an empty result");
      }
      await waitUntil("screenshotTaken", () => screenshots.length > 0);
      await InAppBrowser.hide({ id: webviewId });
      markStep(steps, "show, takeScreenshot, hide");

      await InAppBrowser.executeScript({
        id: webviewId,
        code: `
          window.mobileApp.takeScreenshot()
            .then(function(result) {
              window.mobileApp.postMessage({
                detail: {
                  type: "featureSmoke",
                  step: "bridge-screenshot",
                  hasData: Boolean(result && result.base64),
                  width: result ? result.width : 0,
                  height: result ? result.height : 0
                }
              });
            })
            .catch(function(error) {
              window.mobileApp.postMessage({
                detail: {
                  type: "featureSmoke",
                  step: "bridge-screenshot-error",
                  error: error && error.message ? error.message : String(error)
                }
              });
            });
        `,
      });
      const bridgeScreenshotResult = await waitUntil("bridge screenshot result", () =>
        messages.find(
          (message) =>
            message.step === "bridge-screenshot" || message.step === "bridge-screenshot-error",
        ),
      );
      if (bridgeScreenshotResult.step === "bridge-screenshot-error") {
        throw new Error(
          `window.mobileApp.takeScreenshot() failed: ${bridgeScreenshotResult.error || "unknown error"}`,
        );
      }
      if (
        !bridgeScreenshotResult.hasData ||
        !bridgeScreenshotResult.width ||
        !bridgeScreenshotResult.height
      ) {
        throw new Error("window.mobileApp.takeScreenshot() returned an empty result");
      }
      markStep(steps, "webview screenshot bridge");

      await InAppBrowser.setUrl({ id: webviewId, url: HISTORY_ENTRY_URL });
      await waitUntil("setUrl history entry", () =>
        messages.some((message) => message.step === "entry-ready" && message.href === HISTORY_ENTRY_URL),
      );
      await waitUntil("setUrl urlChangeEvent", () =>
        urlChanges.some((event) => event.id === webviewId && event.url === HISTORY_ENTRY_URL),
      );
      markStep(steps, "setUrl");

      await InAppBrowser.executeScript({
        id: webviewId,
        code: `
          window.location.assign(${JSON.stringify(SECOND_URL)});
        `,
      });
      const secondReady = await waitForMessage("second-ready");
      if (secondReady.href !== SECOND_URL) {
        throw new Error("Navigation did not reach the expected URL");
      }
      await waitUntil("second urlChangeEvent", () =>
        urlChanges.some((event) => event.id === webviewId && event.url === SECOND_URL),
      );
      const entryHistoryCountBeforeBack = messages.filter(
        (message) => message.step === "entry-history-ready" && isEntrySmokeUrl(message.href),
      ).length;
      const historyUrlChangeCountBeforeBack = urlChanges.filter(
        (event) => event.id === webviewId && isEntrySmokeUrl(event.url),
      ).length;
      let backResult = { canGoBack: false };
      for (let attempt = 0; attempt < 8; attempt += 1) {
        backResult = await InAppBrowser.goBack({ id: webviewId });
        if (backResult.canGoBack) {
          break;
        }
        await sleep(500);
      }
      if (!backResult.canGoBack) {
        throw new Error("goBack() reported that history was unavailable");
      }
      try {
        await waitUntil(
          "entry page after goBack",
          async () => {
            const hasPassiveBackSignal =
              messages.filter((message) => message.step === "entry-history-ready" && isEntrySmokeUrl(message.href))
                .length > entryHistoryCountBeforeBack ||
              urlChanges.filter((event) => event.id === webviewId && isEntrySmokeUrl(event.url)).length >
                historyUrlChangeCountBeforeBack;
            if (hasPassiveBackSignal) {
              return true;
            }

            try {
              await InAppBrowser.executeScript({
                id: webviewId,
                code: `
                  window.mobileApp.postMessage({
                    detail: {
                      type: "featureSmoke",
                      step: "go-back-location",
                      href: window.location.href
                    }
                  });
                `,
              });
            } catch (_error) {}

            return messages.some((message) => message.step === "go-back-location" && isEntrySmokeUrl(message.href));
          },
          30000,
        );
      } catch (_error) {
        const observedBackLocations = [
          ...new Set(
            messages
              .filter((message) => message.step === "go-back-location" && message.href)
              .map((message) => message.href),
          ),
        ];
        throw new Error(
          `Timed out waiting for entry page after goBack${
            observedBackLocations.length ? `; observed ${observedBackLocations.join(", ")}` : ""
          }`,
        );
      }
      markStep(steps, "urlChangeEvent and goBack");

      const pageLoadCountBeforeReload = pageLoads.filter((event) => event.id === webviewId).length;
      await InAppBrowser.reload({ id: webviewId });
      await waitUntil(
        "reload page load",
        () => pageLoads.filter((event) => event.id === webviewId).length > pageLoadCountBeforeReload,
      );
      markStep(steps, "reload");

      await InAppBrowser.executeScript({
        id: webviewId,
        code: `window.open(${JSON.stringify(POPUP_URL)}, "_blank");`,
      });
      const popupEvent = await waitUntil("popupWindowOpened", () =>
        popupEvents.find((event) => event.parentId === webviewId || event.url === POPUP_URL),
      );
      if (!popupEvent?.id) {
        throw new Error("popupWindowOpened did not include a popup id");
      }
      popupId = popupEvent.id;
      if (popupId) {
        await InAppBrowser.close({ id: popupId });
        popupId = null;
      }
      markStep(steps, "hidden popup window");

      const version = await InAppBrowser.getPluginVersion();
      if (!version.version) {
        throw new Error("getPluginVersion() returned an empty version");
      }
      markStep(steps, `getPluginVersion ${version.version}`);

      await InAppBrowser.clearCookies({ id: webviewId, url: SMOKE_ORIGIN });
      await InAppBrowser.clearAllCookies({ id: webviewId });
      markStep(steps, "clearCookies and clearAllCookies");

      await InAppBrowser.close({ id: webviewId });
      await waitUntil("closeEvent", () => closeEvents.some((event) => event.id === webviewId));
      webviewId = null;

      setStatus("Feature smoke passed", `Passed steps:\n${steps.join("\n")}`);
    } catch (error) {
      setStatus("Feature smoke failed", normalizeError(error));
      if (popupId) {
        try {
          await InAppBrowser.close({ id: popupId });
        } catch (_cleanupError) {}
      }
      if (webviewId) {
        try {
          await InAppBrowser.close({ id: webviewId });
        } catch (_cleanupError) {}
      }
    } finally {
      for (const handle of handles.reverse()) {
        if (!handle || typeof handle.remove !== "function") {
          continue;
        }
        try {
          await handle.remove();
        } catch (_error) {}
      }
      setRunning(false);
    }
  });

  markReady();
}
