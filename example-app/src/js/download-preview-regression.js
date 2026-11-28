import { InAppBrowser, ToolBarType, BackgroundColor } from "@capgo/capacitor-inappbrowser";

const RUN_TIMEOUT_MS = 90_000;
const NONE_OK_PREFIX = "Event OK: download-preview-none";
const INVALID_OK_PREFIX = "Event OK: downloadPreview rejected";

let harnessAttached = false;
let harnessAttachPending = false;
let activeRunGeneration = 0;

async function waitUntil(predicate, timeoutMs, label, intervalMs = 50) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicate()) {
      return;
    }
    await new Promise((resolve) => globalThis.setTimeout(resolve, intervalMs));
  }
  throw new Error(`Timed out waiting for ${label}`);
}

function withMaestroNativeHarness(callback) {
  const harness = window.MaestroNativeHarness;
  if (!harness || typeof callback !== "function") {
    return;
  }
  callback(harness);
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

// The page posts a heartbeat with a per-load id. If a preview replaced the page,
// the heartbeat would stop or the load id would change.
function buildDownloadPreviewTestUrl() {
  const html = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Download preview none page</title>
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 24px; }
      #result { font-size: 20px; font-weight: bold; margin: 16px 0; }
      #finish { display: inline-block; padding: 16px 24px; font-size: 20px; background: #166534; color: #fff; border: 0; border-radius: 8px; }
      a { display: block; margin: 12px 0; font-size: 18px; }
    </style>
  </head>
  <body>
    <h1>Download preview none page</h1>
    <p id="status">Waiting for downloads.</p>
    <p id="result"></p>
    <a id="pdf-link" href="#">Download PDF</a>
    <a id="text-link" href="#">Download text file</a>
    <button id="finish" type="button">Finish test</button>
    <script>
      const loadId = Math.random().toString(36).slice(2);
      const post = (detail) => window.mobileApp.postMessage({ detail });
      let beat = 0;
      setInterval(() => { beat += 1; post({ type: "alive", loadId, beat }); }, 300);
      const pdfBytes = new TextEncoder().encode("%PDF-1.4\\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\\n2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\\ntrailer<</Root 1 0 R>>\\n%%EOF");
      const textBytes = new TextEncoder().encode("Capgo download preview none text file.\\n");
      const wire = (id, bytes, type, name) => {
        const link = document.getElementById(id);
        link.href = URL.createObjectURL(new Blob([bytes], { type }));
        link.download = name;
      };
      wire("pdf-link", pdfBytes, "application/pdf", "preview-none.pdf");
      wire("text-link", textBytes, "text/plain", "preview-none.txt");
      window.__setResult = (text) => { document.getElementById("result").textContent = text; };
      document.getElementById("finish").addEventListener("click", () => post({ type: "finish", loadId }));
    </script>
  </body>
</html>`;

  return `data:text/html;charset=utf-8,${encodeURIComponent(html)}`;
}

export function attachDownloadPreviewRegressionHarness() {
  if (harnessAttached) {
    return;
  }

  const noneButton = document.getElementById("maestro-run-download-preview-none");
  const invalidButton = document.getElementById("maestro-run-download-preview-invalid");
  const status = document.getElementById("maestro-download-preview-status");
  const details = document.getElementById("maestro-download-preview-details");

  if (!noneButton || !invalidButton || !status || !details) {
    if (!harnessAttachPending) {
      harnessAttachPending = true;
      const retry = () => {
        harnessAttachPending = false;
        attachDownloadPreviewRegressionHarness();
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

  const runNone = async () => {
    const generation = ++activeRunGeneration;
    const isActive = () => generation === activeRunGeneration;

    noneButton.disabled = true;
    invalidButton.disabled = true;
    syncMaestroNativeRunning(true);
    setStatus("Download preview none running", "Opening webview...");

    const completed = [];
    const failed = [];
    const alive = [];
    let finishRequested = false;
    let webViewId = null;
    let runCancelled = false;
    const listeners = [];
    let timeoutId = null;

    const timeoutPromise = new Promise((_, reject) => {
      timeoutId = globalThis.setTimeout(() => {
        runCancelled = true;
        reject(new Error("Download preview none run timed out"));
      }, RUN_TIMEOUT_MS);
    });

    const work = async () => {
      listeners.push(
        await InAppBrowser.addListener("downloadCompleted", (event) => completed.push(event)),
        await InAppBrowser.addListener("downloadFailed", (event) => failed.push(event)),
        await InAppBrowser.addListener("messageFromWebview", (event) => {
          const message = event.detail;
          if (message && message.type === "alive") {
            alive.push(message);
          } else if (message && message.type === "finish") {
            finishRequested = true;
          }
        }),
      );

      const { id } = await InAppBrowser.openWebView({
        url: buildDownloadPreviewTestUrl(),
        title: "Download preview none page",
        toolbarType: ToolBarType.NAVIGATION,
        backgroundColor: BackgroundColor.WHITE,
        visibleTitle: true,
        showReloadButton: false,
        enabledSafeBottomMargin: true,
        handleDownloads: true,
        downloadPreview: "none",
      });
      if (runCancelled || !isActive()) {
        await InAppBrowser.close({ id }).catch(() => {});
        return;
      }
      webViewId = id;

      await waitUntil(() => alive.length > 0, 20_000, "the first page heartbeat");
      if (runCancelled || !isActive()) {
        return;
      }
      const loadId = alive[0].loadId;

      await InAppBrowser.executeScript({
        id: webViewId,
        code: "document.getElementById('pdf-link').click();",
      });
      await waitUntil(() => completed.length >= 1 || failed.length > 0, 30_000, "the PDF download event");
      await InAppBrowser.executeScript({
        id: webViewId,
        code: "document.getElementById('text-link').click();",
      });
      await waitUntil(() => completed.length >= 2 || failed.length > 0, 30_000, "the text download event");

      if (failed.length > 0) {
        throw new Error(`downloadFailed: ${failed[0].error}`);
      }
      const handledBy = completed.map((event) => event.handledBy);
      if (handledBy.some((value) => value !== "none")) {
        throw new Error(`Expected handledBy none, got ${handledBy.join(",")}`);
      }
      const names = completed.map((event) => event.fileName).join(",");
      if (!/preview-none.*\.pdf,preview-none.*\.txt/.test(names)) {
        throw new Error(`Unexpected saved file names: ${names}`);
      }

      // The same page instance must keep running after both downloads: no preview replaced it.
      const beatsAtCompletion = alive.length;
      await waitUntil(() => alive.length >= beatsAtCompletion + 5, 15_000, "page heartbeats after the downloads");
      const loadIds = new Set(alive.map((message) => message.loadId));
      if (loadIds.size !== 1 || !loadIds.has(loadId)) {
        throw new Error("The page was replaced after a download");
      }

      const resultText = `RESULT download completed handledBy=${handledBy.join(",")} files=${names}`;
      await InAppBrowser.executeScript({
        id: webViewId,
        code: `window.__setResult(${JSON.stringify(resultText)});`,
      });

      // Hold the page open so the Maestro flow can look at the screen, then wait for its Finish tap.
      await waitUntil(() => finishRequested, 60_000, "the Finish test tap");

      await InAppBrowser.close({ id: webViewId });
      webViewId = null;

      if (!isActive()) {
        return;
      }
      setStatus(
        `${NONE_OK_PREFIX} handledBy=${handledBy.join(",")} files=${names}`,
        `Page heartbeats after downloads: ${alive.length - beatsAtCompletion}, same page load: yes`,
      );
    };

    try {
      await Promise.race([work(), timeoutPromise]);
    } catch (error) {
      if (isActive()) {
        const message = error instanceof Error ? error.message : String(error);
        setStatus("Download preview none failed", message);
      }
      if (webViewId) {
        await InAppBrowser.close({ id: webViewId }).catch(() => {});
      }
      throw error;
    } finally {
      if (timeoutId !== null) {
        globalThis.clearTimeout(timeoutId);
      }
      for (const listener of listeners) {
        await listener.remove().catch(() => {});
      }
      if (isActive()) {
        noneButton.disabled = false;
        invalidButton.disabled = false;
        syncMaestroNativeRunning(false);
      }
    }
  };

  const expectRejected = async (options, label) => {
    let opened = null;
    try {
      opened = await InAppBrowser.openWebView(options);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (!message.includes("downloadPreview must be") || !message.includes("'none'")) {
        throw new Error(`${label}: unexpected rejection: ${message}`);
      }
      return message;
    }
    if (opened && opened.id) {
      await InAppBrowser.close({ id: opened.id }).catch(() => {});
    }
    throw new Error(`${label}: openWebView resolved instead of rejecting`);
  };

  const runInvalid = async () => {
    const generation = ++activeRunGeneration;
    const isActive = () => generation === activeRunGeneration;

    noneButton.disabled = true;
    invalidButton.disabled = true;
    syncMaestroNativeRunning(true);
    setStatus("Download preview invalid running", "Opening webviews with an invalid value...");

    try {
      const withDownloads = await expectRejected(
        { url: "https://example.com/", handleDownloads: true, downloadPreview: "bogus" },
        "bogus with handleDownloads",
      );
      const withoutDownloads = await expectRejected(
        { url: "https://example.com/", downloadPreview: "bogus" },
        "bogus without handleDownloads",
      );
      if (isActive()) {
        setStatus(
          `${INVALID_OK_PREFIX} with and without handleDownloads`,
          `${withDownloads} | ${withoutDownloads}`,
        );
      }
    } catch (error) {
      if (isActive()) {
        const message = error instanceof Error ? error.message : String(error);
        setStatus("Download preview invalid failed", message);
      }
      throw error;
    } finally {
      if (isActive()) {
        noneButton.disabled = false;
        invalidButton.disabled = false;
        syncMaestroNativeRunning(false);
      }
    }
  };

  window.__capgoRunMaestroDownloadPreviewNone = () => {
    runNone().catch((error) => {
      console.error("Download preview none regression failed:", error);
    });
  };
  window.__capgoRunMaestroDownloadPreviewInvalid = () => {
    runInvalid().catch((error) => {
      console.error("Download preview invalid regression failed:", error);
    });
  };

  noneButton.addEventListener("click", () => window.__capgoRunMaestroDownloadPreviewNone());
  invalidButton.addEventListener("click", () => window.__capgoRunMaestroDownloadPreviewInvalid());
  noneButton.disabled = false;
  invalidButton.disabled = false;
  setStatus("Download preview regression ready", "");
}
