import { InAppBrowser, ToolBarType, addProxyHandler } from "@capgo/inappbrowser";

const ENTRY_URL = "https://proxy.capgo.test/entry";
const SCRIPT_URL = "https://proxy.capgo.test/app.js";
const FETCH_URL = "https://proxy.capgo.test/api/fetch";
const XHR_URL = "https://proxy.capgo.test/api/xhr";

const ENTRY_HTML = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>Proxy Regression Entry</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="icon" href="data:," />
  </head>
  <body>
    <main>
      <h1>Proxy Regression Entry</h1>
      <p id="script-status">Script pending</p>
      <p id="fetch-status">Fetch pending</p>
      <p id="xhr-status">XHR pending</p>
      <script src="${SCRIPT_URL}"></script>
    </main>
  </body>
</html>`;

const PROXY_SCRIPT = `(() => {
  const setText = (id, value) => {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = value;
    }
  };

  const postResult = (detail) => {
    if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
      window.mobileApp.postMessage({ detail });
    }
  };

  const loadXhr = () =>
    new Promise((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.open("GET", ${JSON.stringify(XHR_URL)}, true);
      request.onload = () => {
        try {
          resolve(JSON.parse(request.responseText));
        } catch (error) {
          reject(error);
        }
      };
      request.onerror = () => reject(new Error("xhr-request-failed"));
      request.send();
    });

  (async () => {
    try {
      setText("script-status", "Script loaded");
      const fetchResult = await fetch(${JSON.stringify(FETCH_URL)}).then((response) => response.json());
      setText("fetch-status", fetchResult.message);

      const xhrResult = await loadXhr();
      setText("xhr-status", xhrResult.message);

      postResult({
        type: "proxyRegression",
        state: "passed",
        scriptMessage: "Script loaded",
        fetchMessage: fetchResult.message,
        xhrMessage: xhrResult.message,
      });
    } catch (error) {
      const reason = error && error.message ? error.message : String(error);
      postResult({
        type: "proxyRegression",
        state: "failed",
        reason,
      });
    }
  })();
})();`;

function toBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

function textResponse(body, contentType) {
  return {
    status: 200,
    headers: {
      "Content-Type": contentType,
    },
    body: toBase64(body),
  };
}

function jsonResponse(payload) {
  return textResponse(JSON.stringify(payload), "application/json");
}

export function setupProxyRegression(root, options = {}) {
  const runButton = root.querySelector("#run-proxy-regression");
  const statusText = root.querySelector("#proxy-regression-status-text");
  const detailsText = root.querySelector("#proxy-regression-details");

  if (!runButton || !statusText || !detailsText) {
    return null;
  }

  const notifyStatusChange =
    typeof options.onStatusChange === "function"
      ? options.onStatusChange
      : () => {};
  const notifyRunningChange =
    typeof options.onRunningChange === "function"
      ? options.onRunningChange
      : () => {};

  let proxyHandle = null;
  let listenerHandles = [];
  let browserOpened = false;
  let completed = false;
  let keepBrowserOpenOnFinish = false;

  const resetState = async () => {
    const handles = listenerHandles;
    listenerHandles = [];
    for (const handle of handles) {
      try {
        await handle.remove();
      } catch (_error) {}
    }
    if (proxyHandle) {
      try {
        await proxyHandle.remove();
      } catch (_error) {}
      proxyHandle = null;
    }
  };

  const setStatus = (message, details = "") => {
    statusText.textContent = message;
    detailsText.textContent = details;
    notifyStatusChange(message, details);
  };

  const finish = async (message, details = "", shouldCloseBrowser = true) => {
    completed = true;
    setStatus(message, details);
    runButton.disabled = false;
    notifyRunningChange(false);
    const shouldClose = browserOpened && shouldCloseBrowser && !keepBrowserOpenOnFinish;
    browserOpened = false;
    await resetState();
    if (shouldClose) {
      try {
        await InAppBrowser.close();
      } catch (_error) {}
    }
  };

  const runRegression = async (runOptions = {}) => {
    if (runButton.disabled) {
      return;
    }

    keepBrowserOpenOnFinish = Boolean(runOptions.keepBrowserOpenOnFinish);
    runButton.disabled = true;
    notifyRunningChange(true);
    browserOpened = false;
    completed = false;
    setStatus("Proxy regression running...", "");

    await resetState();

    proxyHandle = await addProxyHandler(async (request) => {
      if (request.url === ENTRY_URL) {
        return textResponse(ENTRY_HTML, "text/html");
      }
      if (request.url === SCRIPT_URL) {
        return new Response(PROXY_SCRIPT, {
          status: 200,
          headers: {
            "Content-Type": "application/javascript",
          },
        });
      }
      if (request.url === FETCH_URL) {
        return jsonResponse({ message: "Fetch OK" });
      }
      if (request.url === XHR_URL) {
        return new Response(JSON.stringify({ message: "XHR OK" }), {
          status: 200,
          headers: {
            "Content-Type": "application/json",
          },
        });
      }
      return null;
    });

    listenerHandles.push(
      await InAppBrowser.addListener("messageFromWebview", async (event) => {
        const detail = event.detail ?? {};
        if (detail.type !== "proxyRegression") {
          return;
        }

        if (detail.state === "passed") {
          await finish(
            "Proxy regression passed",
            `${detail.scriptMessage} | ${detail.fetchMessage} | ${detail.xhrMessage}`,
          );
          return;
        }

        await finish("Proxy regression failed", detail.reason ?? "Unknown proxy error");
      }),
    );

    listenerHandles.push(
      await InAppBrowser.addListener("pageLoadError", async () => {
        if (!completed) {
          await finish("Proxy regression failed", "pageLoadError");
        }
      }),
    );

    listenerHandles.push(
      await InAppBrowser.addListener("closeEvent", async () => {
        if (!completed) {
          runButton.disabled = false;
          browserOpened = false;
          await resetState();
          setStatus("Proxy regression failed", "Browser closed before results arrived");
        }
      }),
    );

    try {
      await InAppBrowser.openWebView({
        url: ENTRY_URL,
        proxyRequests: true,
        toolbarType: ToolBarType.BLANK,
      });
      browserOpened = true;
    } catch (error) {
      const reason = error && error.message ? error.message : String(error);
      await finish("Proxy regression failed", reason);
    }
  };

  runButton.addEventListener("click", runRegression);
  notifyStatusChange(statusText.textContent, detailsText.textContent);
  notifyRunningChange(false);

  return {
    run: runRegression,
  };
}
