import { BackgroundColor, InAppBrowser, ToolBarType } from "@capgo/capacitor-inappbrowser";
import {
  scheduleHarnessAttach,
  syncMaestroNativeRunning,
  syncMaestroNativeStatus,
} from "./maestro-native-harness.js";

const harnessState = {
  attached: false,
  pending: false,
};

function resolveVisualRegressionProbeUrl() {
  try {
    return new URL("visual-regression-probe.html", window.location.href).href;
  } catch (_error) {
    return "visual-regression-probe.html";
  }
}

export function attachVisualRegressionHarness() {
  if (harnessState.attached) {
    return;
  }

  const readyBanner = document.getElementById("maestro-ready-banner");
  const runButton = document.getElementById("maestro-run-visual-regression");
  const status = document.getElementById("maestro-visual-regression-status");
  const details = document.getElementById("maestro-visual-regression-details");

  if (!readyBanner || !runButton || !status || !details) {
    scheduleHarnessAttach(attachVisualRegressionHarness, () => harnessState);
    return;
  }

  harnessState.attached = true;
  let currentRun = null;

  const setStatus = (text, detailText = "") => {
    status.textContent = text;
    details.textContent = detailText;
    syncMaestroNativeStatus(text, detailText);

    if (text === "Not started") {
      readyBanner.textContent = "Maestro Ready";
      return;
    }

    if (text.includes("Visual regression")) {
      readyBanner.textContent = text;
    }
  };

  const removeHandle = async (handle) => {
    if (!handle) {
      return;
    }

    try {
      await handle.remove();
    } catch (error) {
      console.warn("Unable to remove InAppBrowser listener:", error);
    }
  };

  const cleanupRun = async (run) => {
    await removeHandle(run.pageLoadedHandle);
    run.pageLoadedHandle = null;
    await removeHandle(run.messageHandle);
    run.messageHandle = null;
  };

  readyBanner.textContent = "Maestro Ready";
  runButton.disabled = false;
  setStatus("Not started");

  window.__capgoRunMaestroVisualRegression = () => {
    runButton.click();
  };

  runButton.addEventListener("click", async () => {
    if (currentRun) {
      return;
    }

    runButton.disabled = true;
    syncMaestroNativeRunning(true);
    setStatus("Visual regression booting", "Opening blank toolbar layout probe...");

    const run = {
      pageLoadedHandle: null,
      messageHandle: null,
      ready: false,
    };
    currentRun = run;

    try {
      run.pageLoadedHandle = await InAppBrowser.addListener("browserPageLoaded", () => {
        setStatus("Visual regression loading", "Waiting for layout probe to paint...");
      });

      run.messageHandle = await InAppBrowser.addListener("messageFromWebview", (event) => {
        if (event.detail?.type !== "visualRegressionReady") {
          return;
        }

        run.ready = true;
        const probeHeight = event.detail.probeHeight ?? "n/a";
        setStatus("Visual regression ready", `Probe height: ${probeHeight}px`);
      });

      await InAppBrowser.openWebView({
        url: resolveVisualRegressionProbeUrl(),
        toolbarType: ToolBarType.BLANK,
        backgroundColor: BackgroundColor.WHITE,
        enabledSafeTopMargin: true,
        enabledSafeBottomMargin: true,
        isPresentAfterPageLoad: true,
      });
    } catch (error) {
      currentRun = null;
      await cleanupRun(run);
      runButton.disabled = false;
      syncMaestroNativeRunning(false);

      const errorMessage = error instanceof Error ? error.message : String(error);
      setStatus("Visual regression failed to start", errorMessage);
    }
  });
}