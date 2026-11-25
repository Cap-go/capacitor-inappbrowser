import { InAppBrowser, BackgroundColor } from "@capgo/capacitor-inappbrowser";
import { openQaToolsPanel } from "./qa-tools.js";

const SLOT_ID = "in-app-browser-toolbar-slot";
const NATIVE_SHOWN_EVENT = "inAppBrowserToolbarNativeShown";
const WEB_PROJECTION_EVENT = "inAppBrowserToolbarWebProjectionStarted";
const PASS_OFF = "Native UI engine off passed";
const PASS_ON = "Native UI engine on passed";
const PASS_FAIL = "Native UI engine validation failed as expected";

let harnessAttached = false;

function setStatus(statusEl, detailsEl, status, details = "") {
  if (statusEl) {
    statusEl.textContent = status;
  }
  if (detailsEl) {
    detailsEl.textContent = details;
  }
}

async function closeIfNeeded() {
  try {
    await InAppBrowser.close();
  } catch (_error) {}
}

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

function slotHasWebProjection(slot) {
  return slot.childElementCount > 0;
}

export async function runNativeUIEngineOffRegression(statusEl, detailsEl) {
  openQaToolsPanel();
  setStatus(statusEl, detailsEl, "Running engine-off flow", "");
  await closeIfNeeded();
  let spuriousNativeShown = false;
  const listener = await InAppBrowser.addListener(NATIVE_SHOWN_EVENT, () => {
    spuriousNativeShown = true;
  });
  try {
    const { id } = await InAppBrowser.openWebView({
      url: "https://example.com/",
      title: "Engine Off",
      backgroundColor: BackgroundColor.WHITE,
      enableNativeUIEngine: false,
    });
    if (!id) {
      throw new Error("openWebView did not return an id");
    }
    if (spuriousNativeShown) {
      throw new Error(`${NATIVE_SHOWN_EVENT} fired while enableNativeUIEngine is false`);
    }
    await InAppBrowser.close();
    if (spuriousNativeShown) {
      throw new Error(`${NATIVE_SHOWN_EVENT} fired while enableNativeUIEngine is false`);
    }
    setStatus(statusEl, detailsEl, PASS_OFF, `webview id ${id}`);
  } finally {
    await listener.remove();
  }
}

export async function runNativeUIEngineOnRegression(statusEl, detailsEl) {
  openQaToolsPanel();
  const slot = document.getElementById(SLOT_ID);
  if (!slot) {
    throw new Error(`Missing host slot #${SLOT_ID}`);
  }
  setStatus(statusEl, detailsEl, "Running engine-on flow", "");
  await closeIfNeeded();
  let nativeShown = false;
  let webProjectionStarted = false;
  let openedId = null;
  const nativeListener = await InAppBrowser.addListener(NATIVE_SHOWN_EVENT, (event) => {
    if (event?.id === openedId) {
      nativeShown = true;
    }
  });
  const webListener = await InAppBrowser.addListener(WEB_PROJECTION_EVENT, (event) => {
    if (event?.id === openedId) {
      webProjectionStarted = true;
    }
  });
  try {
    const { id } = await InAppBrowser.openWebView({
      url: "https://example.com/",
      title: "Engine On",
      backgroundColor: BackgroundColor.WHITE,
      enableNativeUIEngine: true,
      nativeUIProjectionSelector: `#${SLOT_ID}`,
    });
    if (!id) {
      throw new Error("openWebView did not return an id");
    }
    openedId = id;
    try {
      await waitUntil(
        () => nativeShown && webProjectionStarted && slotHasWebProjection(slot),
        15_000,
      );
      await InAppBrowser.close();
      setStatus(statusEl, detailsEl, PASS_ON, `webview id ${id}`);
    } catch (error) {
      await closeIfNeeded();
      throw error;
    }
  } finally {
    await nativeListener.remove();
    await webListener.remove();
  }
}

export async function runNativeUIEngineValidationRegression(statusEl, detailsEl) {
  openQaToolsPanel();
  setStatus(statusEl, detailsEl, "Running validation failure flow", "");
  try {
    await InAppBrowser.openWebView({
      url: "https://example.com/",
      enableNativeUIEngine: true,
    });
    await closeIfNeeded();
    throw new Error("openWebView should reject when selector is missing");
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!message.includes("nativeUIProjectionSelector")) {
      throw error;
    }
    setStatus(statusEl, detailsEl, PASS_FAIL, message);
  }
}

export function attachNativeUIEngineRegressionHarness() {
  if (harnessAttached) {
    return;
  }
  const runOff = document.getElementById("maestro-run-native-ui-engine-off");
  const runOn = document.getElementById("maestro-run-native-ui-engine-on");
  const runFail = document.getElementById("maestro-run-native-ui-engine-fail");
  const status = document.getElementById("maestro-native-ui-engine-status");
  const details = document.getElementById("maestro-native-ui-engine-details");
  const banner = document.getElementById("maestro-ready-banner");

  const markReady = () => {
    if (banner) {
      banner.textContent = "Maestro Ready";
    }
    [runOff, runOn, runFail].forEach((button) => {
      if (button) {
        button.disabled = false;
      }
    });
  };

  const wire = (button, runner) => {
    if (!button) {
      return;
    }
    button.addEventListener("click", () => {
      button.disabled = true;
      runner(status, details)
        .catch((error) => {
          const message = error instanceof Error ? error.message : String(error);
          setStatus(status, details, "Native UI engine regression failed", message);
        })
        .finally(() => {
          button.disabled = false;
        });
    });
  };

  wire(runOff, runNativeUIEngineOffRegression);
  wire(runOn, runNativeUIEngineOnRegression);
  wire(runFail, runNativeUIEngineValidationRegression);

  harnessAttached = true;
  markReady();
}