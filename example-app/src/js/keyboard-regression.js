import { InAppBrowser, ToolBarType, BackgroundColor } from "@capgo/inappbrowser";

const KEYBOARD_OPEN_THRESHOLD_PX = 120;
const KEYBOARD_RESTORE_TOLERANCE_PX = 32;
const FINAL_MEASUREMENT_DELAY_MS = 800;
const VIEWPORT_POLL_INTERVAL_MS = 100;
const RUN_TIMEOUT_MS = 20_000;

let harnessAttached = false;
let harnessAttachPending = false;

function scheduleHarnessAttachRetry() {
  if (harnessAttachPending) {
    return;
  }

  harnessAttachPending = true;
  const retry = () => {
    harnessAttachPending = false;
    attachKeyboardRegressionHarness();
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", retry, { once: true });
    return;
  }

  window.requestAnimationFrame(retry);
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

function getViewportMetrics() {
  const visualViewport = window.visualViewport;

  return {
    windowInnerHeight: window.innerHeight,
    windowOuterHeight: window.outerHeight,
    documentClientHeight: document.documentElement?.clientHeight ?? null,
    visualViewportHeight: visualViewport ? Math.round(visualViewport.height) : null,
    visualViewportOffsetTop: visualViewport ? Math.round(visualViewport.offsetTop) : null,
  };
}

function getTrackedHeight(metrics) {
  return metrics.visualViewportHeight ?? metrics.windowInnerHeight ?? metrics.documentClientHeight ?? 0;
}

function formatMetrics(metrics) {
  return JSON.stringify(metrics, null, 2);
}

function buildKeyboardRegressionUrl() {
  const html = String.raw`<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <title>Keyboard Regression Page</title>
    <style>
      :root {
        color-scheme: light;
      }

      * {
        box-sizing: border-box;
      }

      body {
        margin: 0;
        min-height: 100vh;
        padding: 24px 20px 40px;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        background: linear-gradient(180deg, #f7fbff 0%, #eef4ff 100%);
        color: #102542;
      }

      main {
        max-width: 520px;
        margin: 0 auto;
      }

      h1 {
        margin: 0 0 12px;
        font-size: 28px;
      }

      p {
        margin: 0 0 16px;
        line-height: 1.5;
      }

      label {
        display: block;
        margin-bottom: 8px;
        font-weight: 600;
      }

      input,
      button {
        width: 100%;
        min-height: 52px;
        border-radius: 12px;
        border: 1px solid #c8d6ef;
        font-size: 18px;
      }

      input {
        padding: 14px 16px;
        background: #fff;
        margin-bottom: 16px;
      }

      button {
        margin-bottom: 12px;
        border: 0;
        background: #1d3557;
        color: #fff;
        font-weight: 600;
      }

      #dismiss-button {
        background: #457b9d;
      }

      #viewport-debug {
        margin-top: 18px;
        padding: 14px;
        border-radius: 12px;
        background: rgba(255, 255, 255, 0.85);
        color: #355070;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 13px;
        white-space: pre-wrap;
      }
    </style>
  </head>
  <body>
    <main>
      <h1>Keyboard Regression Page</h1>
      <p>Tap the input, hide the keyboard, then tap Close Browser.</p>
      <label for="keyboard-input">Password</label>
      <input
        id="keyboard-input"
        type="text"
        placeholder="Keyboard regression input"
        autocomplete="off"
        autocapitalize="none"
        spellcheck="false"
      />
      <button id="dismiss-button" type="button">Dismiss Keyboard</button>
      <button id="close-button" type="button">Close Browser</button>
      <div id="viewport-debug"></div>
    </main>
    <script>
      (function () {
        const input = document.getElementById("keyboard-input");
        const dismissButton = document.getElementById("dismiss-button");
        const closeButton = document.getElementById("close-button");
        const viewportDebug = document.getElementById("viewport-debug");
        let baselineHeight = null;
        let minHeight = null;
        let autoCloseStarted = false;

        const postMetrics = (payload) => {
          if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
            window.mobileApp.postMessage({
              detail: {
                type: "keyboardRegressionMetrics",
                ...payload,
              },
            });
          }
        };

        const renderMetrics = () => {
          const viewportHeight = window.visualViewport ? Math.round(window.visualViewport.height) : null;
          const viewportOffsetTop = window.visualViewport ? Math.round(window.visualViewport.offsetTop) : null;
          const trackedHeight = viewportHeight ?? window.innerHeight;

          if (baselineHeight === null) {
            baselineHeight = trackedHeight;
            minHeight = trackedHeight;
          }
          minHeight = Math.min(minHeight, trackedHeight);

          const keyboardDelta = baselineHeight - trackedHeight;
          const keyboardVisible = keyboardDelta >= 100;

          viewportDebug.textContent =
            "innerHeight=" + window.innerHeight +
            "\nvisualViewport.height=" + viewportHeight +
            "\nvisualViewport.offsetTop=" + viewportOffsetTop +
            "\nkeyboardDelta=" + keyboardDelta;

          postMetrics({
            baselineHeight,
            currentHeight: trackedHeight,
            minHeight,
            keyboardDelta,
            keyboardVisible,
          });
        };

        dismissButton.addEventListener("click", () => {
          input.blur();
          renderMetrics();
        });

        const closeBrowser = () => {
          if (window.mobileApp && typeof window.mobileApp.close === "function") {
            window.mobileApp.close();
          }
        };

        closeButton.addEventListener("click", closeBrowser);
        input.addEventListener("focus", () => {
          if (autoCloseStarted) {
            return;
          }

          autoCloseStarted = true;
          window.setTimeout(() => {
            input.blur();
            renderMetrics();
          }, 700);
          window.setTimeout(() => {
            closeBrowser();
          }, 1400);
        });

        window.addEventListener("resize", renderMetrics);
        if (window.visualViewport) {
          window.visualViewport.addEventListener("resize", renderMetrics);
        }

        renderMetrics();
      })();
    </script>
  </body>
</html>`;

  return `data:text/html;charset=utf-8,${encodeURIComponent(html)}`;
}

export function attachKeyboardRegressionHarness() {
  if (harnessAttached) {
    return;
  }

  const readyBanner = document.getElementById("maestro-ready-banner");
  const runButton = document.getElementById("maestro-run-keyboard");
  const status = document.getElementById("maestro-keyboard-status");
  const details = document.getElementById("maestro-keyboard-details");

  if (!readyBanner || !runButton || !status || !details) {
    scheduleHarnessAttachRetry();
    return;
  }

  harnessAttached = true;

  let currentRun = null;

  const setStatus = (text, detailText = "") => {
    status.textContent = text;
    details.textContent = detailText;
    syncMaestroNativeStatus(text, detailText);

    if (text === "Not started") {
      readyBanner.textContent = "Maestro Ready";
      return;
    }

    if (text.startsWith("Keyboard regression")) {
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
    if (run.pollId !== null) {
      window.clearInterval(run.pollId);
      run.pollId = null;
    }

    if (run.timeoutId !== null) {
      window.clearTimeout(run.timeoutId);
      run.timeoutId = null;
    }

    await removeHandle(run.closeHandle);
    run.closeHandle = null;

    await removeHandle(run.pageLoadedHandle);
    run.pageLoadedHandle = null;

    await removeHandle(run.messageHandle);
    run.messageHandle = null;
  };

  const failRun = async (run, reason, closeBrowser = false) => {
    if (currentRun !== run) {
      return;
    }

    currentRun = null;
    await cleanupRun(run);

    if (closeBrowser) {
      try {
        await InAppBrowser.close();
      } catch (error) {
        console.warn("Unable to close timed out keyboard regression run:", error);
      }
    }

    const finalMetrics = getViewportMetrics();
    const detailLines = [
      reason,
      "",
      `Host metrics: ${formatMetrics(finalMetrics)}`,
      "",
      `Page baseline height: ${run.pageBaselineHeight ?? "n/a"}px`,
      `Page minimum height: ${run.pageMinHeight ?? "n/a"}px`,
      `Page current height: ${run.pageCurrentHeight ?? "n/a"}px`,
      `Page reported keyboard open: ${run.pageKeyboardOpened}`,
    ];

    setStatus("Keyboard regression failed", detailLines.join("\n"));
    runButton.disabled = false;
    syncMaestroNativeRunning(false);
  };

  const finishRun = async (run) => {
    if (currentRun !== run) {
      return;
    }

    currentRun = null;
    await cleanupRun(run);

    await new Promise((resolve) => window.setTimeout(resolve, FINAL_MEASUREMENT_DELAY_MS));

    const finalMetrics = getViewportMetrics();
    const finalHeight = getTrackedHeight(finalMetrics);
    const keyboardDelta = run.baselineHeight - run.minHeight;
    const hostKeyboardOpened = keyboardDelta >= KEYBOARD_OPEN_THRESHOLD_PX;
    const keyboardOpened = run.pageKeyboardOpened || hostKeyboardOpened;
    const restored = Math.abs(finalHeight - run.baselineHeight) <= KEYBOARD_RESTORE_TOLERANCE_PX;
    const pageKeyboardDelta =
      run.pageBaselineHeight !== null && run.pageMinHeight !== null ? run.pageBaselineHeight - run.pageMinHeight : null;
    const detailLines = [
      `Baseline height: ${run.baselineHeight}px`,
      `Minimum height: ${run.minHeight}px`,
      `Final height: ${finalHeight}px`,
      `Host keyboard delta: ${keyboardDelta}px`,
      `Page baseline height: ${run.pageBaselineHeight ?? "n/a"}px`,
      `Page minimum height: ${run.pageMinHeight ?? "n/a"}px`,
      `Page current height: ${run.pageCurrentHeight ?? "n/a"}px`,
      `Page keyboard delta: ${pageKeyboardDelta ?? "n/a"}px`,
      `Page reported keyboard open: ${run.pageKeyboardOpened}`,
      `Host inferred keyboard open: ${hostKeyboardOpened}`,
      "",
      `Baseline metrics: ${formatMetrics(run.baselineMetrics)}`,
      "",
      `Minimum metrics: ${formatMetrics(run.minMetrics)}`,
      "",
      `Final metrics: ${formatMetrics(finalMetrics)}`,
    ];

    if (keyboardOpened && restored) {
      setStatus("Keyboard regression passed", detailLines.join("\n"));
      runButton.disabled = false;
      syncMaestroNativeRunning(false);
      return;
    }

    if (!keyboardOpened) {
      setStatus(
        "Keyboard regression inconclusive",
        ["Neither the host viewport nor the in-app browser page reported a keyboard-open state.", "", ...detailLines].join(
          "\n",
        ),
      );
      runButton.disabled = false;
      syncMaestroNativeRunning(false);
      return;
    }

    setStatus(
      "Keyboard regression failed",
      ["The host viewport stayed shrunk after the browser closed.", "", ...detailLines].join("\n"),
    );
    runButton.disabled = false;
    syncMaestroNativeRunning(false);
  };

  readyBanner.textContent = "Maestro Ready";
  runButton.disabled = false;
  setStatus("Not started");

  runButton.addEventListener("click", async () => {
    if (currentRun) {
      return;
    }

    const baselineMetrics = getViewportMetrics();
    const baselineHeight = getTrackedHeight(baselineMetrics);
    if (!baselineHeight) {
      setStatus("Keyboard regression failed to start", "Could not measure the host viewport.");
      return;
    }

    runButton.disabled = true;
    syncMaestroNativeRunning(true);
    setStatus(
      "Keyboard regression booting",
      `Baseline metrics: ${formatMetrics(baselineMetrics)}\n\nLoading the in-app browser...`,
    );

    const run = {
      baselineHeight,
      baselineMetrics,
      minHeight: baselineHeight,
      minMetrics: baselineMetrics,
      pollId: null,
      timeoutId: null,
      closeHandle: null,
      pageLoadedHandle: null,
      messageHandle: null,
      pageBaselineHeight: null,
      pageMinHeight: null,
      pageCurrentHeight: null,
      pageKeyboardOpened: false,
    };
    currentRun = run;

    run.timeoutId = window.setTimeout(() => {
      void failRun(run, "Timed out waiting for the browser to close.", true);
    }, RUN_TIMEOUT_MS);

    run.pollId = window.setInterval(() => {
      const metrics = getViewportMetrics();
      const trackedHeight = getTrackedHeight(metrics);

      if (trackedHeight > 0 && trackedHeight < run.minHeight) {
        run.minHeight = trackedHeight;
        run.minMetrics = metrics;
      }
    }, VIEWPORT_POLL_INTERVAL_MS);

    try {
      run.pageLoadedHandle = await InAppBrowser.addListener("browserPageLoaded", () => {
        setStatus(
          "Keyboard regression running",
          "Tap the input inside the browser, hide the keyboard, then tap Close Browser.",
        );
      });

      run.messageHandle = await InAppBrowser.addListener("messageFromWebview", (event) => {
        const detail = event.detail;
        if (detail?.type !== "keyboardRegressionMetrics") {
          return;
        }

        run.pageBaselineHeight = detail.baselineHeight ?? run.pageBaselineHeight;
        run.pageCurrentHeight = detail.currentHeight ?? run.pageCurrentHeight;
        run.pageMinHeight =
          run.pageMinHeight === null || run.pageMinHeight === undefined
            ? detail.minHeight
            : Math.min(run.pageMinHeight, detail.minHeight);
        run.pageKeyboardOpened =
          run.pageKeyboardOpened ||
          Boolean(detail.keyboardVisible) ||
          (typeof detail.keyboardDelta === "number" && detail.keyboardDelta >= KEYBOARD_OPEN_THRESHOLD_PX);
      });

      run.closeHandle = await InAppBrowser.addListener("closeEvent", () => {
        void finishRun(run);
      });

      await InAppBrowser.openWebView({
        url: buildKeyboardRegressionUrl(),
        toolbarType: ToolBarType.NAVIGATION,
        toolbarColor: "#1d3557",
        backgroundColor: BackgroundColor.WHITE,
        title: "Keyboard Regression",
        visibleTitle: true,
        enabledSafeBottomMargin: true,
      });
    } catch (error) {
      currentRun = null;
      await cleanupRun(run);
      runButton.disabled = false;
      syncMaestroNativeRunning(false);

      const errorMessage = error instanceof Error ? error.message : String(error);
      setStatus("Keyboard regression failed to start", errorMessage);
    }
  });
}
