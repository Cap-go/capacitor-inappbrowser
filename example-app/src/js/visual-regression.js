import { BackgroundColor, InAppBrowser, ToolBarType } from "@capgo/capacitor-inappbrowser";

let harnessAttached = false;
let harnessAttachPending = false;

function scheduleHarnessAttachRetry() {
  if (harnessAttachPending) {
    return;
  }

  harnessAttachPending = true;
  const retry = () => {
    harnessAttachPending = false;
    attachVisualRegressionHarness();
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

function buildVisualRegressionUrl() {
  const html = String.raw`<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <title>Visual Layout Probe</title>
    <style>
      :root {
        color-scheme: light;
      }

      * {
        box-sizing: border-box;
      }

      html,
      body {
        margin: 0;
        height: 100%;
      }

      body {
        display: flex;
        flex-direction: column;
        background: #f8fafc;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      }

      #visual-regression-probe {
        display: flex;
        flex: 1;
        flex-direction: column;
        min-height: 100%;
      }

      .stripe-top {
        height: 56px;
        background: #2563eb;
        flex-shrink: 0;
      }

      .stripe-bottom {
        height: 48px;
        background: #dc2626;
        flex-shrink: 0;
      }

      main {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #22c55e;
        padding: 24px;
      }

      h1 {
        margin: 0;
        font-size: 28px;
        color: #052e16;
        text-align: center;
      }

      p {
        margin: 12px 0 0;
        color: #14532d;
        text-align: center;
        font-size: 16px;
      }
    </style>
  </head>
  <body>
    <div id="visual-regression-probe">
      <div class="stripe-top" aria-hidden="true"></div>
      <main>
        <div>
          <h1>Layout probe</h1>
          <p id="visual-regression-ready-label">Visual regression ready</p>
        </div>
      </main>
      <div class="stripe-bottom" aria-hidden="true"></div>
    </div>
    <script>
      (function () {
        const postReady = () => {
          if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
            window.mobileApp.postMessage({
              detail: {
                type: "visualRegressionReady",
                probeHeight: document.getElementById("visual-regression-probe")?.offsetHeight ?? null,
              },
            });
          }
        };

        window.requestAnimationFrame(() => {
          window.requestAnimationFrame(postReady);
        });
      })();
    </script>
  </body>
</html>`;

  return `data:text/html;charset=utf-8,${encodeURIComponent(html)}`;
}

export function attachVisualRegressionHarness() {
  if (harnessAttached) {
    return;
  }

  const readyBanner = document.getElementById("maestro-ready-banner");
  const runButton = document.getElementById("maestro-run-visual-regression");
  const status = document.getElementById("maestro-visual-regression-status");
  const details = document.getElementById("maestro-visual-regression-details");

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
        url: buildVisualRegressionUrl(),
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