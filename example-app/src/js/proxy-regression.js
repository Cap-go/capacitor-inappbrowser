import { Capacitor } from "@capacitor/core";
import { InAppBrowser, ToolBarType } from "@capgo/inappbrowser";

const PROXY_PORT = 8123;

function getProxyBaseUrl() {
  return Capacitor.getPlatform() === "android"
    ? `http://10.0.2.2:${PROXY_PORT}`
    : `http://127.0.0.1:${PROXY_PORT}`;
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function encodeBase64Text(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

function decodeBase64Text(value) {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function rewriteJsonBase64(base64Value, patch) {
  const payload = JSON.parse(decodeBase64Text(base64Value));
  Object.assign(payload, patch);
  return encodeBase64Text(JSON.stringify(payload));
}

export function setupProxyRegression(root) {
  const runButton = root.querySelector("#run-proxy-regression");
  const statusText = root.querySelector("#proxy-regression-status-text");
  const detailsText = root.querySelector("#proxy-regression-details");

  if (!runButton || !statusText || !detailsText) {
    return;
  }

  let listenerHandles = [];
  let browserOpened = false;
  let completed = false;
  let metaRequestBodyOmitted = false;
  let metaResponseBodyOmitted = false;
  let openedWebViewId = null;
  let proxyEventIdsValid = true;
  const seenProxyEventIds = new Set();
  const flowRequestIds = new Map();

  const setStatus = (message, details = "") => {
    statusText.textContent = message;
    detailsText.textContent = details;
  };

  const removeListeners = async () => {
    const handles = listenerHandles;
    listenerHandles = [];
    for (const handle of handles) {
      try {
        await handle.remove();
      } catch (_error) {}
    }
  };

  const finish = async (message, details = "") => {
    completed = true;
    setStatus(message, details);
    runButton.disabled = false;
    const shouldClose = browserOpened;
    browserOpened = false;
    await removeListeners();
    if (shouldClose) {
      try {
        await InAppBrowser.close();
      } catch (_error) {}
    }
  };

  const failIntercept = async (stage, event, error) => {
    const message = error instanceof Error ? error.message : String(error);
    try {
      if (stage === "request") {
        await InAppBrowser.continueProxyRequest({
          requestId: event.requestId,
          modifiedRequest: null,
        });
      } else {
        await InAppBrowser.continueProxyResponse({
          requestId: event.requestId,
          modifiedResponse: null,
        });
      }
    } catch (_error) {}
    await finish("Proxy regression failed", `${stage} intercept failed: ${message}`);
  };

  async function handleProxyRequest(event) {
    if (!event.id) {
      proxyEventIdsValid = false;
    } else {
      seenProxyEventIds.add(event.id);
    }
    flowRequestIds.set(event.ruleName, event.requestId);

    let modifiedRequest = null;

    if (event.ruleName === "meta-request") {
      metaRequestBodyOmitted = !event.body;
      modifiedRequest = {
        headers: {
          ...(event.headers || {}),
          "x-proxy-meta-request": event.ruleName,
        },
      };
    }

    if (event.ruleName === "fetch-request" || event.ruleName === "xhr-request") {
      const headers = {
        ...(event.headers || {}),
        "x-proxy-request-rule": event.ruleName,
      };
      modifiedRequest = { headers };

      if (event.body) {
        modifiedRequest.body = rewriteJsonBase64(event.body, {
          changed: true,
          requestRule: event.ruleName,
        });
      }
    }

    await InAppBrowser.continueProxyRequest({
      requestId: event.requestId,
      modifiedRequest,
    });
  }

  async function handleProxyResponse(event) {
    if (!event.id) {
      proxyEventIdsValid = false;
    } else {
      seenProxyEventIds.add(event.id);
    }
    flowRequestIds.set(event.ruleName, event.requestId);

    let modifiedResponse = null;

    if (event.ruleName === "meta-response") {
      metaResponseBodyOmitted = !event.body;
      modifiedResponse = {
        headers: {
          ...(event.headers || {}),
          "x-proxy-meta-response": event.ruleName,
        },
      };
    }

    if (event.ruleName === "entry-response" && event.body) {
      const html = decodeBase64Text(event.body).replace(
        "</head>",
        '<meta name="proxy-entry" content="rewritten" /></head>',
      );
      modifiedResponse = {
        body: encodeBase64Text(html),
      };
    }

    if ((event.ruleName === "fetch-response" || event.ruleName === "xhr-response") && event.body) {
      modifiedResponse = {
        body: rewriteJsonBase64(event.body, {
          proxyResponseRule: event.ruleName,
        }),
      };
    }

    await InAppBrowser.continueProxyResponse({
      requestId: event.requestId,
      modifiedResponse,
    });
  }

  runButton.addEventListener("click", async () => {
    runButton.disabled = true;
    browserOpened = false;
    openedWebViewId = null;
    completed = false;
    metaRequestBodyOmitted = false;
    metaResponseBodyOmitted = false;
    proxyEventIdsValid = true;
    seenProxyEventIds.clear();
    flowRequestIds.clear();
    setStatus("Proxy regression running...", "");

    await removeListeners();

    const proxyBaseUrl = getProxyBaseUrl();
    const proxyBasePattern = escapeRegex(proxyBaseUrl);
    const entryUrl = `${proxyBaseUrl}/entry`;

    listenerHandles.push(
      await InAppBrowser.addListener("proxyRequestIntercept", (event) => {
        void handleProxyRequest(event).catch((error) => failIntercept("request", event, error));
      }),
    );

    listenerHandles.push(
      await InAppBrowser.addListener("proxyResponseIntercept", (event) => {
        void handleProxyResponse(event).catch((error) => failIntercept("response", event, error));
      }),
    );

    listenerHandles.push(
      await InAppBrowser.addListener("messageFromWebview", async (event) => {
        const detail = event.detail ?? {};
        if (detail.type !== "proxyRegression") {
          return;
        }

        if (detail.state === "passed") {
          const summary = detail.summary ?? {};
          const combinedSummary = {
            ...summary,
            metaRequestBodyOmitted,
            metaResponseBodyOmitted,
            proxyEventId:
              proxyEventIdsValid &&
              openedWebViewId !== null &&
              seenProxyEventIds.size === 1 &&
              seenProxyEventIds.has(openedWebViewId),
            metaFlowRequestId:
              flowRequestIds.get("meta-request") != null &&
              flowRequestIds.get("meta-request") === flowRequestIds.get("meta-response"),
            fetchFlowRequestId:
              flowRequestIds.get("fetch-request") != null &&
              flowRequestIds.get("fetch-request") === flowRequestIds.get("fetch-response"),
            xhrFlowRequestId:
              flowRequestIds.get("xhr-request") != null &&
              flowRequestIds.get("xhr-request") === flowRequestIds.get("xhr-response"),
          };
          const failedChecks = Object.entries(combinedSummary)
            .filter(([, value]) => !value)
            .map(([key]) => key);
          const lines = [
            summary.entryResponse ? "Entry response OK" : "Entry response FAILED",
            combinedSummary.proxyEventId ? "Proxy event id OK" : "Proxy event id FAILED",
            summary.metaRequest ? "Meta request OK" : "Meta request FAILED",
            metaRequestBodyOmitted ? "Meta request body omitted OK" : "Meta request body omitted FAILED",
            summary.metaResponse ? "Meta response OK" : "Meta response FAILED",
            metaResponseBodyOmitted ? "Meta response body omitted OK" : "Meta response body omitted FAILED",
            combinedSummary.metaFlowRequestId ? "Meta flow requestId OK" : "Meta flow requestId FAILED",
            summary.fetchRequest ? "Fetch request OK" : "Fetch request FAILED",
            summary.fetchRequestBody ? "Fetch request body OK" : "Fetch request body FAILED",
            summary.fetchResponse ? "Fetch response OK" : "Fetch response FAILED",
            combinedSummary.fetchFlowRequestId ? "Fetch flow requestId OK" : "Fetch flow requestId FAILED",
            summary.xhrRequest ? "XHR request OK" : "XHR request FAILED",
            summary.xhrRequestBody ? "XHR request body OK" : "XHR request body FAILED",
            summary.xhrResponse ? "XHR response OK" : "XHR response FAILED",
            combinedSummary.xhrFlowRequestId ? "XHR flow requestId OK" : "XHR flow requestId FAILED",
          ];
          if (failedChecks.length === 0) {
            await finish("Proxy regression passed", lines.join("\n"));
            return;
          }
          await finish("Proxy regression failed", lines.join("\n"));
          return;
        }

        const failureDetails = detail.reason
          ? detail.reason
          : Array.isArray(detail.failed) && detail.failed.length > 0
            ? detail.failed.join(", ")
            : "Unknown proxy error";
        await finish("Proxy regression failed", failureDetails);
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
          browserOpened = false;
          runButton.disabled = false;
          await removeListeners();
          setStatus("Proxy regression failed", "Browser closed before results arrived");
        }
      }),
    );

    try {
      const { id } = await InAppBrowser.openWebView({
        url: entryUrl,
        toolbarType: ToolBarType.BLANK,
        proxyRules: [
          {
            ruleName: "entry-response",
            urlPattern: `${proxyBasePattern}/entry$`,
            intercept: "response",
            includeBody: true,
          },
          {
            ruleName: "meta-request",
            urlPattern: `${proxyBasePattern}/api/meta$`,
            methods: ["GET"],
            intercept: "request",
            includeBody: false,
          },
          {
            ruleName: "meta-response",
            urlPattern: `${proxyBasePattern}/api/meta$`,
            methods: ["GET"],
            intercept: "response",
            includeBody: false,
          },
          {
            ruleName: "fetch-request",
            urlPattern: `${proxyBasePattern}/api/fetch$`,
            methods: ["POST"],
            intercept: "request",
            includeBody: true,
          },
          {
            ruleName: "fetch-response",
            urlPattern: `${proxyBasePattern}/api/fetch$`,
            methods: ["POST"],
            intercept: "response",
            includeBody: true,
          },
          {
            ruleName: "xhr-request",
            urlPattern: `${proxyBasePattern}/api/xhr$`,
            methods: ["POST"],
            intercept: "request",
            includeBody: true,
          },
          {
            ruleName: "xhr-response",
            urlPattern: `${proxyBasePattern}/api/xhr$`,
            methods: ["POST"],
            intercept: "response",
            includeBody: true,
          },
        ],
      });
      openedWebViewId = id;
      browserOpened = true;
    } catch (error) {
      const reason = error && error.message ? error.message : String(error);
      await finish("Proxy regression failed", reason);
    }
  });
}
