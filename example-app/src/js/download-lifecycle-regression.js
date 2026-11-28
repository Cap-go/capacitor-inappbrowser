import { Capacitor } from "@capacitor/core";
import { InAppBrowser, InvisibilityMode, ToolBarType, BackgroundColor } from "@capgo/capacitor-inappbrowser";

import { openQaToolsPanel } from "./qa-tools.js";
import { syncMaestroNativeRunning, syncMaestroNativeStatus } from "./maestro-native-harness.js";

// Served by example-app/scripts/download-lifecycle-server.mjs on the CI host or the dev machine.
// The Android emulator reaches the host loopback through 10.0.2.2.
const SERVER_PORT = 8787;
const COMPLETE_SIZE = 256 * 1024;
const RUN_TIMEOUT_MS = 60_000;
const QUIET_WINDOW_MS = 1500;

// Status lines only change to "Lifecycle OK" after the plugin events were checked.
export const DOWNLOAD_LIFECYCLE_STATUS = {
  completed: "Lifecycle OK: completed",
  cancelled: "Lifecycle OK: cancelled",
  failed: "Lifecycle OK: failed",
  killArmed: "Lifecycle OK: kill armed",
};

const state = { attached: false, pending: false };
let activeRun = 0;

function serverBaseUrl() {
  const host = Capacitor.getPlatform() === "android" ? "10.0.2.2" : "127.0.0.1";
  return `http://${host}:${SERVER_PORT}`;
}

function sleep(ms) {
  return new Promise((resolve) => globalThis.setTimeout(resolve, ms));
}

async function waitUntil(predicate, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicate()) {
      return;
    }
    await sleep(50);
  }
  throw new Error(`Timed out waiting for ${label}`);
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

// Records every lifecycle event for one run. Nothing here is synthesized: entries only come from plugin listeners.
async function createRecorder() {
  const events = [];
  const push = (type) => (event) => events.push({ type, event, at: Date.now() });
  const handles = await Promise.all([
    InAppBrowser.addListener("downloadStarted", push("started")),
    InAppBrowser.addListener("downloadProgress", push("progress")),
    InAppBrowser.addListener("downloadCompleted", push("completed")),
    InAppBrowser.addListener("downloadFailed", push("failed")),
    InAppBrowser.addListener("downloadCancelled", push("cancelled")),
  ]);
  return {
    events,
    async remove() {
      await Promise.all(handles.map((handle) => handle.remove().catch(() => {})));
    },
  };
}

function eventsFor(events, downloadId) {
  return events.filter((entry) => entry.event.downloadId === downloadId);
}

function terminalEvents(events, downloadId) {
  return eventsFor(events, downloadId).filter((entry) => ["completed", "failed", "cancelled"].includes(entry.type));
}

function firstStarted(events, webViewId) {
  return events.find((entry) => entry.type === "started" && (!entry.event.id || entry.event.id === webViewId));
}

function assertSingleTerminal(events, downloadId, expectedType) {
  const terminals = terminalEvents(events, downloadId);
  assert(terminals.length === 1, `expected one terminal event, got ${terminals.map((entry) => entry.type).join(",") || "none"}`);
  assert(terminals[0].type === expectedType, `expected ${expectedType}, got ${terminals[0].type}`);
  const terminalIndex = events.indexOf(terminals[0]);
  const lateProgress = events.slice(terminalIndex + 1).filter((entry) => entry.event.downloadId === downloadId);
  assert(lateProgress.length === 0, `got ${lateProgress.length} events after ${expectedType}`);
}

function assertProgressMonotonic(progress) {
  for (let index = 1; index < progress.length; index += 1) {
    assert(
      progress[index].event.bytesReceived >= progress[index - 1].event.bytesReceived,
      "downloadProgress bytesReceived went backwards",
    );
  }
}

async function openDownloadPage(kind) {
  let loadedId = null;
  let anyLoad = false;
  const pageLoaded = await InAppBrowser.addListener("browserPageLoaded", (event) => {
    anyLoad = true;
    loadedId = event.id ?? loadedId;
  });
  const { id } = await InAppBrowser.openWebView({
    url: `${serverBaseUrl()}/page/${kind}`,
    title: `Download Lifecycle ${kind}`,
    toolbarType: ToolBarType.NAVIGATION,
    backgroundColor: BackgroundColor.WHITE,
    // Hidden so the harness status stays on screen for Maestro while the transfer runs.
    hidden: true,
    invisibilityMode: InvisibilityMode.FAKE_VISIBLE,
    handleDownloads: true,
  });
  try {
    await waitUntil(() => anyLoad && (!loadedId || loadedId === id), 20_000, `${kind} page load`);
  } finally {
    await pageLoaded.remove().catch(() => {});
  }
  // Trigger the download from the page itself, the same way a user tap on a download link would.
  await InAppBrowser.executeScript({ id, code: "document.getElementById('download-link').click();" });
  return id;
}

const scenarios = {
  async completed(recorder) {
    const webViewId = await openDownloadPage("complete");
    await waitUntil(() => firstStarted(recorder.events, webViewId), 20_000, "downloadStarted");
    const { downloadId } = firstStarted(recorder.events, webViewId).event;
    assert(typeof downloadId === "string" && downloadId.length > 0, "downloadStarted has no downloadId");
    await waitUntil(() => terminalEvents(recorder.events, downloadId).length > 0, 30_000, "terminal event");
    await sleep(QUIET_WINDOW_MS);

    assertSingleTerminal(recorder.events, downloadId, "completed");
    const mine = eventsFor(recorder.events, downloadId);
    assert(mine[0].type === "started", `first event was ${mine[0].type}, not started`);
    const progress = mine.filter((entry) => entry.type === "progress");
    assert(progress.length >= 2, `expected at least 2 downloadProgress events, got ${progress.length}`);
    assertProgressMonotonic(progress);
    const last = progress[progress.length - 1].event;
    assert(last.bytesReceived === COMPLETE_SIZE, `final bytesReceived ${last.bytesReceived} != ${COMPLETE_SIZE}`);
    assert(last.bytesExpected === COMPLETE_SIZE, `bytesExpected ${last.bytesExpected} != ${COMPLETE_SIZE}`);
    const completed = terminalEvents(recorder.events, downloadId)[0].event;
    assert(
      typeof completed.fileName === "string" && completed.fileName.includes("capgo-lifecycle-complete"),
      `unexpected fileName ${completed.fileName}`,
    );
    await InAppBrowser.close({ id: webViewId }).catch(() => {});
    return `${DOWNLOAD_LIFECYCLE_STATUS.completed}`;
  },

  async cancelled(recorder) {
    const webViewId = await openDownloadPage("cancel");
    await waitUntil(() => firstStarted(recorder.events, webViewId), 20_000, "downloadStarted");
    const { downloadId } = firstStarted(recorder.events, webViewId).event;
    await waitUntil(
      () => eventsFor(recorder.events, downloadId).some((entry) => entry.type === "progress" && entry.event.bytesReceived > 0),
      20_000,
      "downloadProgress with bytes",
    );
    await InAppBrowser.cancelDownload({ downloadId });
    await waitUntil(() => terminalEvents(recorder.events, downloadId).length > 0, 10_000, "downloadCancelled");
    await sleep(QUIET_WINDOW_MS);

    assertSingleTerminal(recorder.events, downloadId, "cancelled");
    const progress = eventsFor(recorder.events, downloadId).filter((entry) => entry.type === "progress");
    const lastBytes = progress[progress.length - 1].event.bytesReceived;
    assert(lastBytes < 2 * 1024 * 1024, "transfer finished before cancel took effect");
    await InAppBrowser.close({ id: webViewId }).catch(() => {});
    return `${DOWNLOAD_LIFECYCLE_STATUS.cancelled}`;
  },

  async failed(recorder) {
    const webViewId = await openDownloadPage("fail");
    await waitUntil(() => firstStarted(recorder.events, webViewId), 20_000, "downloadStarted");
    const { downloadId } = firstStarted(recorder.events, webViewId).event;
    await waitUntil(() => terminalEvents(recorder.events, downloadId).length > 0, 30_000, "downloadFailed");
    await sleep(QUIET_WINDOW_MS);

    assertSingleTerminal(recorder.events, downloadId, "failed");
    const failed = terminalEvents(recorder.events, downloadId)[0].event;
    assert(typeof failed.error === "string" && failed.error.length > 0, "downloadFailed has no error");
    await InAppBrowser.close({ id: webViewId }).catch(() => {});
    return `${DOWNLOAD_LIFECYCLE_STATUS.failed}`;
  },

  // Leaves the transfer running on purpose. The Maestro flow kills the app right after this status shows.
  async killArmed(recorder) {
    const webViewId = await openDownloadPage("kill");
    await waitUntil(() => firstStarted(recorder.events, webViewId), 20_000, "downloadStarted");
    const { downloadId } = firstStarted(recorder.events, webViewId).event;
    await waitUntil(
      () => eventsFor(recorder.events, downloadId).some((entry) => entry.type === "progress" && entry.event.bytesReceived > 0),
      20_000,
      "downloadProgress with bytes",
    );
    assert(terminalEvents(recorder.events, downloadId).length === 0, "kill download ended before the app was killed");
    return `${DOWNLOAD_LIFECYCLE_STATUS.killArmed}`;
  },
};

export function attachDownloadLifecycleRegressionHarness() {
  if (state.attached) {
    return;
  }

  const status = document.getElementById("maestro-download-lifecycle-status");
  const details = document.getElementById("maestro-download-lifecycle-details");
  const buttons = {
    completed: document.getElementById("maestro-run-download-lifecycle-complete"),
    cancelled: document.getElementById("maestro-run-download-lifecycle-cancel"),
    failed: document.getElementById("maestro-run-download-lifecycle-fail"),
    killArmed: document.getElementById("maestro-run-download-lifecycle-kill"),
  };

  if (!status || Object.values(buttons).some((button) => !button)) {
    if (!state.pending) {
      state.pending = true;
      const retry = () => {
        state.pending = false;
        attachDownloadLifecycleRegressionHarness();
      };
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", retry, { once: true });
      } else {
        window.requestAnimationFrame(retry);
      }
    }
    return;
  }

  state.attached = true;

  const setStatus = (line, extra = "") => {
    status.textContent = line;
    if (details) {
      details.textContent = extra;
    }
    if (extra) {
      console.info(`[download-lifecycle] ${line}: ${extra}`);
    }
    syncMaestroNativeStatus(line, extra);
  };

  const setButtonsDisabled = (disabled) => {
    Object.values(buttons).forEach((button) => {
      button.disabled = disabled;
    });
  };

  const run = async (name) => {
    const runId = ++activeRun;
    setButtonsDisabled(true);
    syncMaestroNativeRunning(true);
    setStatus(`Download lifecycle ${name} running`);
    let recorder = null;
    let timeoutId = null;
    try {
      recorder = await createRecorder();
      const timeout = new Promise((_, reject) => {
        timeoutId = globalThis.setTimeout(() => reject(new Error(`${name} timed out`)), RUN_TIMEOUT_MS);
      });
      const okLine = await Promise.race([scenarios[name](recorder), timeout]);
      if (runId === activeRun) {
        const summary = recorder.events.map((entry) => entry.type).join(" > ");
        setStatus(okLine, summary);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error(`Download lifecycle ${name} failed:`, error);
      if (runId === activeRun) {
        const summary = recorder ? recorder.events.map((entry) => entry.type).join(" > ") : "";
        setStatus(`Lifecycle FAIL: ${name}: ${message}`, summary);
      }
    } finally {
      if (timeoutId !== null) {
        globalThis.clearTimeout(timeoutId);
      }
      // The kill scenario keeps its listeners so events keep landing until the process dies.
      if (recorder && name !== "killArmed") {
        await recorder.remove();
      }
      if (runId === activeRun) {
        setButtonsDisabled(false);
        syncMaestroNativeRunning(false);
      }
    }
  };

  Object.entries(buttons).forEach(([name, button]) => {
    button.addEventListener("click", () => {
      run(name).catch((error) => console.error(`Download lifecycle ${name} crashed:`, error));
    });
    button.disabled = false;
  });

  openQaToolsPanel();
  setStatus("Download lifecycle ready");
}
