export function withMaestroNativeHarness(callback) {
  const harness = window.MaestroNativeHarness;
  if (!harness || typeof callback !== "function") {
    return;
  }

  try {
    callback(harness);
  } catch (_error) {}
}

export function syncMaestroNativeRunning(running) {
  withMaestroNativeHarness((harness) => {
    if (typeof harness.setRunning === "function") {
      harness.setRunning(Boolean(running));
    }
  });
}

export function syncMaestroNativeStatus(message, details = "") {
  withMaestroNativeHarness((harness) => {
    if (typeof harness.setStatus === "function") {
      harness.setStatus(message, details);
    }
  });
}

export function scheduleHarnessAttach(attach, getState) {
  if (getState().attached) {
    return;
  }

  if (getState().pending) {
    return;
  }

  getState().pending = true;
  const retry = () => {
    getState().pending = false;
    attach();
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", retry, { once: true });
    return;
  }

  window.requestAnimationFrame(retry);
}