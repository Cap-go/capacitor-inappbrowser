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
  return new Promise((resolve) => globalThis.setTimeout(resolve, ms));
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
  const harness = globalThis.MaestroNativeHarness;
  if (!harness || typeof callback !== "function") {
    return;
  }

  try {
    callback(harness);
  } catch (error) {
    console.warn("Unable to update Maestro native harness", error);
  }
}

async function waitUntil(label, predicate, timeout = TIMEOUT_MS, getTimeoutMessage) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeout) {
    const result = await predicate();
    if (result) {
      return result;
    }
    await sleep(100);
  }
  const timeoutMessage = typeof getTimeoutMessage === "function" ? getTimeoutMessage() : `Timed out waiting for ${label}`;
  throw new Error(timeoutMessage);
}

function featureSmokeProxyResponse(request) {
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
}

function createSmokeContext(setStatus) {
  const ctx = {
    closeEvents: [],
    consoleMessages: [],
    handles: [],
    messages: [],
    pageLoads: [],
    popupEvents: [],
    popupId: null,
    screenshots: [],
    setStatus,
    steps: [],
    urlChanges: [],
    webviewId: null,
  };

  ctx.markStep = (step) => {
    ctx.steps.push(step);
    ctx.setStatus(`Feature smoke running: ${step}`, ctx.steps.join("\n"));
  };
  ctx.waitForMessage = (step, timeout) =>
    waitUntil(step, () => ctx.messages.find((message) => message.step === step), timeout);

  return ctx;
}

function recordFeatureMessage(ctx, event) {
  const detail = event.detail || {};
  if (detail.type !== "featureSmoke") {
    return;
  }
  ctx.messages.push({
    id: event.id,
    ...detail,
  });
}

function recordPageLoadError(ctx, event) {
  ctx.messages.push({
    id: event.id,
    step: "page-load-error",
  });
}

async function setupSmokeHarness(ctx) {
  await InAppBrowser.removeAllListeners();
  await InAppBrowser.clearAllCookies();
  await InAppBrowser.clearCache();

  const listenerHandles = await Promise.all([
    InAppBrowser.addListener("messageFromWebview", (event) => recordFeatureMessage(ctx, event)),
    InAppBrowser.addListener("consoleMessage", (event) => ctx.consoleMessages.push(event)),
    InAppBrowser.addListener("screenshotTaken", (event) => ctx.screenshots.push(event)),
    InAppBrowser.addListener("popupWindowOpened", (event) => ctx.popupEvents.push(event)),
    InAppBrowser.addListener("browserPageLoaded", (event) => ctx.pageLoads.push(event)),
    InAppBrowser.addListener("urlChangeEvent", (event) => ctx.urlChanges.push(event)),
    InAppBrowser.addListener("closeEvent", (event) => ctx.closeEvents.push(event)),
    InAppBrowser.addListener("pageLoadError", (event) => recordPageLoadError(ctx, event)),
  ]);
  const proxyHandle = await addProxyHandler(featureSmokeProxyResponse);
  ctx.handles.push(...listenerHandles, proxyHandle);
}

async function openSmokeWebview(ctx) {
  ctx.markStep("open hidden proxied webview");
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
  ctx.webviewId = openResult.id;
}

async function verifyStartup(ctx) {
  const entryReady = await ctx.waitForMessage("entry-ready");
  if (!entryReady.preShowFlag) {
    throw new Error("preShowScript did not run before entry script");
  }
  await waitUntil("consoleMessage", () =>
    ctx.consoleMessages.some((event) => String(event.message).includes("feature-smoke-console-ok")),
  );
  ctx.markStep("page load, proxy, console, preShowScript");
}

async function verifyCookies(ctx) {
  const cookies = await InAppBrowser.getCookies({ url: SMOKE_ORIGIN });
  if (cookies.featureSmokeCookie !== "enabled") {
    throw new Error("featureSmokeCookie was not available through getCookies()");
  }
  ctx.markStep("getCookies");
}

async function verifyPostMessage(ctx) {
  await InAppBrowser.postMessage({
    id: ctx.webviewId,
    detail: {
      action: "native-ping",
      marker: "post-message-ok",
    },
  });
  const postMessage = await ctx.waitForMessage("post-message");
  if (postMessage.payload?.marker !== "post-message-ok") {
    throw new Error("postMessage payload was not echoed by the webview");
  }
  ctx.markStep("postMessage");
}

async function verifyExecuteScript(ctx) {
  await InAppBrowser.executeScript({
    id: ctx.webviewId,
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
  const scriptMessage = await ctx.waitForMessage("execute-script");
  if (scriptMessage.title !== "Feature Smoke Entry") {
    throw new Error("executeScript returned the wrong document title");
  }
  ctx.markStep("executeScript");
}

async function verifySizingAndNativeScreenshot(ctx) {
  await InAppBrowser.updateDimensions({ id: ctx.webviewId, width: 320, height: 560, x: 0, y: 0 });
  await InAppBrowser.setEnabledSafeTopMargin({ id: ctx.webviewId, enabled: false });
  await InAppBrowser.setEnabledSafeBottomMargin({ id: ctx.webviewId, enabled: true });
  ctx.markStep("dimensions and safe margins");

  await InAppBrowser.show({ id: ctx.webviewId });
  await sleep(500);
  const screenshot = await InAppBrowser.takeScreenshot({ id: ctx.webviewId });
  if (!screenshot.base64 || !screenshot.width || !screenshot.height) {
    throw new Error("takeScreenshot returned an empty result");
  }
  await waitUntil("screenshotTaken", () => ctx.screenshots.length > 0);
  await InAppBrowser.hide({ id: ctx.webviewId });
  ctx.markStep("show, takeScreenshot, hide");
}

async function verifyBridgeScreenshot(ctx) {
  await InAppBrowser.executeScript({
    id: ctx.webviewId,
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
  const result = await waitUntil("bridge screenshot result", () =>
    ctx.messages.find(
      (message) => message.step === "bridge-screenshot" || message.step === "bridge-screenshot-error",
    ),
  );
  if (result.step === "bridge-screenshot-error") {
    throw new Error(`window.mobileApp.takeScreenshot() failed: ${result.error || "unknown error"}`);
  }
  if (!result.hasData || !result.width || !result.height) {
    throw new Error("window.mobileApp.takeScreenshot() returned an empty result");
  }
  ctx.markStep("webview screenshot bridge");
}

async function verifySetUrl(ctx) {
  await InAppBrowser.setUrl({ id: ctx.webviewId, url: HISTORY_ENTRY_URL });
  await waitUntil("setUrl history entry", () =>
    ctx.messages.some((message) => message.step === "entry-ready" && message.href === HISTORY_ENTRY_URL),
  );
  await waitUntil("setUrl urlChangeEvent", () =>
    ctx.urlChanges.some((event) => event.id === ctx.webviewId && event.url === HISTORY_ENTRY_URL),
  );
  ctx.markStep("setUrl");
}

async function navigateToSecondPage(ctx) {
  await InAppBrowser.executeScript({
    id: ctx.webviewId,
    code: `
      window.location.assign(${JSON.stringify(SECOND_URL)});
    `,
  });
  const secondReady = await ctx.waitForMessage("second-ready");
  if (secondReady.href !== SECOND_URL) {
    throw new Error("Navigation did not reach the expected URL");
  }
  await waitUntil("second urlChangeEvent", () =>
    ctx.urlChanges.some((event) => event.id === ctx.webviewId && event.url === SECOND_URL),
  );
}

async function verifyBackNavigation(ctx) {
  const entryHistoryCountBeforeBack = ctx.messages.filter(
    (message) => message.step === "entry-history-ready" && isEntrySmokeUrl(message.href),
  ).length;
  const historyUrlChangeCountBeforeBack = ctx.urlChanges.filter(
    (event) => event.id === ctx.webviewId && isEntrySmokeUrl(event.url),
  ).length;

  for (let attempt = 0; attempt < 8; attempt += 1) {
    const backResult = await InAppBrowser.goBack({ id: ctx.webviewId });
    if (backResult.canGoBack) {
      break;
    }
    await sleep(500);
  }

  await waitUntil(
    "entry page after goBack",
    () =>
      hasBackNavigationSignal(ctx, entryHistoryCountBeforeBack, historyUrlChangeCountBeforeBack),
    TIMEOUT_MS,
    () => backNavigationTimeoutMessage(ctx),
  );
  ctx.markStep("urlChangeEvent and goBack");
}

async function hasBackNavigationSignal(ctx, entryHistoryCountBeforeBack, historyUrlChangeCountBeforeBack) {
  const hasPassiveBackSignal =
    ctx.messages.filter((message) => message.step === "entry-history-ready" && isEntrySmokeUrl(message.href)).length >
      entryHistoryCountBeforeBack ||
    ctx.urlChanges.filter((event) => event.id === ctx.webviewId && isEntrySmokeUrl(event.url)).length >
      historyUrlChangeCountBeforeBack;
  if (hasPassiveBackSignal) {
    return true;
  }

  await postCurrentLocation(ctx);
  return ctx.messages.some((message) => message.step === "go-back-location" && isEntrySmokeUrl(message.href));
}

async function postCurrentLocation(ctx) {
  try {
    await InAppBrowser.executeScript({
      id: ctx.webviewId,
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
  } catch (error) {
    console.warn("Unable to probe webview location after goBack", error);
  }
}

function backNavigationTimeoutMessage(ctx) {
  const observedBackLocations = [
    ...new Set(
      ctx.messages
        .filter((message) => message.step === "go-back-location" && message.href)
        .map((message) => message.href),
    ),
  ];
  return `Timed out waiting for entry page after goBack${
    observedBackLocations.length ? `; observed ${observedBackLocations.join(", ")}` : ""
  }`;
}

async function verifyReload(ctx) {
  const pageLoadCountBeforeReload = ctx.pageLoads.filter((event) => event.id === ctx.webviewId).length;
  await InAppBrowser.reload({ id: ctx.webviewId });
  await waitUntil(
    "reload page load",
    () => ctx.pageLoads.filter((event) => event.id === ctx.webviewId).length > pageLoadCountBeforeReload,
  );
  ctx.markStep("reload");
}

async function verifyNavigationAndReload(ctx) {
  await verifySetUrl(ctx);
  await navigateToSecondPage(ctx);
  await verifyBackNavigation(ctx);
  await verifyReload(ctx);
}

async function verifyHiddenPopup(ctx) {
  await InAppBrowser.executeScript({
    id: ctx.webviewId,
    code: `window.open(${JSON.stringify(POPUP_URL)}, "_blank");`,
  });
  const popupEvent = await waitUntil("popupWindowOpened", () =>
    ctx.popupEvents.find((event) => event.parentId === ctx.webviewId || event.url === POPUP_URL),
  );
  if (!popupEvent?.id) {
    throw new Error("popupWindowOpened did not include a popup id");
  }
  ctx.popupId = popupEvent.id;
  await closeSmokeTarget(ctx, "popupId");
  ctx.markStep("hidden popup window");
}

async function verifyPluginVersion(ctx) {
  const version = await InAppBrowser.getPluginVersion();
  if (!version.version) {
    throw new Error("getPluginVersion() returned an empty version");
  }
  ctx.markStep(`getPluginVersion ${version.version}`);
}

async function verifyCookieClearing(ctx) {
  await InAppBrowser.clearCookies({ id: ctx.webviewId, url: SMOKE_ORIGIN });
  await InAppBrowser.clearAllCookies({ id: ctx.webviewId });
  ctx.markStep("clearCookies and clearAllCookies");
}

async function closePrimaryWebview(ctx) {
  const closingId = ctx.webviewId;
  await InAppBrowser.close({ id: closingId });
  await waitUntil("closeEvent", () => ctx.closeEvents.some((event) => event.id === closingId));
  ctx.webviewId = null;
}

async function runFeatureSmokeFlow(ctx) {
  await setupSmokeHarness(ctx);
  await openSmokeWebview(ctx);
  await verifyStartup(ctx);
  await verifyCookies(ctx);
  await verifyPostMessage(ctx);
  await verifyExecuteScript(ctx);
  await verifySizingAndNativeScreenshot(ctx);
  await verifyBridgeScreenshot(ctx);
  await verifyNavigationAndReload(ctx);
  await verifyHiddenPopup(ctx);
  await verifyPluginVersion(ctx);
  await verifyCookieClearing(ctx);
  await closePrimaryWebview(ctx);
}

async function closeSmokeTarget(ctx, targetKey) {
  const targetId = ctx[targetKey];
  if (!targetId) {
    return;
  }
  try {
    await InAppBrowser.close({ id: targetId });
  } catch (error) {
    console.warn(`Unable to close smoke target ${targetId}`, error);
  }
  ctx[targetKey] = null;
}

async function closeOpenSmokeTargets(ctx) {
  await closeSmokeTarget(ctx, "popupId");
  await closeSmokeTarget(ctx, "webviewId");
}

async function removeSmokeHandles(ctx) {
  const handles = [...ctx.handles].reverse();
  for (const handle of handles) {
    await removeSmokeHandle(handle);
  }
  ctx.handles.length = 0;
}

async function removeSmokeHandle(handle) {
  if (!handle || typeof handle.remove !== "function") {
    return;
  }
  try {
    await handle.remove();
  } catch (error) {
    console.warn("Unable to remove smoke test listener", error);
  }
}

async function runFeatureSmokeClick(runButton, setRunning, setStatus) {
  if (runButton.disabled) {
    return;
  }

  setRunning(true);
  const ctx = createSmokeContext(setStatus);
  try {
    await runFeatureSmokeFlow(ctx);
    setStatus("Feature smoke passed", `Passed steps:\n${ctx.steps.join("\n")}`);
  } catch (error) {
    setStatus("Feature smoke failed", normalizeError(error));
    await closeOpenSmokeTargets(ctx);
  } finally {
    await removeSmokeHandles(ctx);
    setRunning(false);
  }
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

  runButton.addEventListener("click", () => {
    void runFeatureSmokeClick(runButton, setRunning, setStatus);
  });

  runButton.disabled = false;
  setStatus("Feature smoke ready");
}
