import { InAppBrowser, ToolBarType } from "@capgo/inappbrowser";
import { getLoopbackBaseUrl } from "./url.js";

const PROXY_PORT = 8123;
const RULE_MATRIX = [
  { ruleName: "entry-response", path: "/entry", mode: "response", includeBody: true },
  { ruleName: "meta-request", path: "/api/meta", methods: ["GET"], mode: "request", includeBody: false },
  { ruleName: "meta-response", path: "/api/meta", methods: ["GET"], mode: "response", includeBody: false },
  { ruleName: "fetch-request", path: "/api/fetch", methods: ["POST"], mode: "request", includeBody: true },
  { ruleName: "fetch-response", path: "/api/fetch", methods: ["POST"], mode: "response", includeBody: true },
  { ruleName: "xhr-request", path: "/api/xhr", methods: ["POST"], mode: "request", includeBody: true },
  { ruleName: "xhr-response", path: "/api/xhr", methods: ["POST"], mode: "response", includeBody: true },
];
const SUMMARY_CHECKS = [
  { key: "entryResponse", label: "Entry response", read: (summary, combined) => summary.entryResponse ?? combined.entryResponse },
  { key: "proxyEventId", label: "Proxy event id", read: (_summary, combined) => combined.proxyEventId },
  { key: "metaRequest", label: "Meta request", read: (summary) => summary.metaRequest },
  { key: "metaRequestBodyOmitted", label: "Meta request body omitted", read: (_summary, combined) => combined.metaRequestBodyOmitted },
  { key: "metaResponse", label: "Meta response", read: (summary) => summary.metaResponse },
  { key: "metaResponseBodyOmitted", label: "Meta response body omitted", read: (_summary, combined) => combined.metaResponseBodyOmitted },
  { key: "metaFlowRequestId", label: "Meta flow requestId", read: (_summary, combined) => combined.metaFlowRequestId },
  { key: "fetchRequest", label: "Fetch request", read: (summary) => summary.fetchRequest },
  { key: "fetchRequestBody", label: "Fetch request body", read: (summary) => summary.fetchRequestBody },
  { key: "fetchResponse", label: "Fetch response", read: (summary) => summary.fetchResponse },
  { key: "fetchFlowRequestId", label: "Fetch flow requestId", read: (_summary, combined) => combined.fetchFlowRequestId },
  { key: "xhrRequest", label: "XHR request", read: (summary) => summary.xhrRequest },
  { key: "xhrRequestBody", label: "XHR request body", read: (summary) => summary.xhrRequestBody },
  { key: "xhrResponse", label: "XHR response", read: (summary) => summary.xhrResponse },
  { key: "xhrFlowRequestId", label: "XHR flow requestId", read: (_summary, combined) => combined.xhrFlowRequestId },
];

function getProxyBaseUrl() {
  return getLoopbackBaseUrl(PROXY_PORT);
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
}

function encodeBase64Text(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCodePoint(...chunk);
  }
  return btoa(binary);
}

function decodeBase64Text(value) {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (char) => char.codePointAt(0));
  return new TextDecoder().decode(bytes);
}

function rewriteJsonBase64(base64Value, patch) {
  const payload = JSON.parse(decodeBase64Text(base64Value));
  Object.assign(payload, patch);
  return encodeBase64Text(JSON.stringify(payload));
}

function cloneHeaders(headers) {
  return headers ? { ...headers } : {};
}

function createProxyRules(proxyBasePattern) {
  return RULE_MATRIX.map((rule) => ({
    ...rule,
    regex: `${proxyBasePattern}${rule.path}$`,
  }));
}

function recordProxyEvent(event, seenProxyEventIds, flowRequestIds) {
  if (event.id) {
    seenProxyEventIds.add(event.id);
  }
  flowRequestIds.set(event.ruleName, event.requestId);
  return event.id != null;
}

function requestIdMatches(flowRequestIds, requestRule, responseRule) {
  const requestId = flowRequestIds.get(requestRule);
  return requestId != null && requestId === flowRequestIds.get(responseRule);
}

function combinedSummaryFrom(summary, state, openedWebViewId) {
  return {
    ...summary,
    metaRequestBodyOmitted: state.metaRequestBodyOmitted,
    metaResponseBodyOmitted: state.metaResponseBodyOmitted,
    proxyEventId:
      state.proxyEventIdsValid &&
      openedWebViewId !== null &&
      state.seenProxyEventIds.size === 1 &&
      state.seenProxyEventIds.has(openedWebViewId),
    metaFlowRequestId: requestIdMatches(state.flowRequestIds, "meta-request", "meta-response"),
    fetchFlowRequestId: requestIdMatches(state.flowRequestIds, "fetch-request", "fetch-response"),
    xhrFlowRequestId: requestIdMatches(state.flowRequestIds, "xhr-request", "xhr-response"),
  };
}

function summarizeChecks(summary, combinedSummary) {
  const failedChecks = [];
  const lines = SUMMARY_CHECKS.map(({ key, label, read }) => {
    const passed = Boolean(read(summary, combinedSummary));
    if (!passed) {
      failedChecks.push(key);
    }
    return `${label} ${passed ? "OK" : "FAILED"}`;
  });
  return { failedChecks, lines };
}

function failureDetailsFrom(detail) {
  if (detail.reason) {
    return detail.reason;
  }
  if (Array.isArray(detail.failed) && detail.failed.length > 0) {
    return detail.failed.join(", ");
  }
  return "Unknown proxy error";
}

export function setupProxyRegression(root) {
  const runButton = root.querySelector("#run-proxy-regression");
  const statusText = root.querySelector("#proxy-regression-status-text");
  const detailsText = root.querySelector("#proxy-regression-details");

  if (!runButton || !statusText || !detailsText) {
    return;
  }

  const state = {
    listenerHandles: [],
    browserOpened: false,
    completed: false,
    metaRequestBodyOmitted: false,
    metaResponseBodyOmitted: false,
    proxyEventIdsValid: true,
    seenProxyEventIds: new Set(),
    flowRequestIds: new Map(),
  };
  let openedWebViewId = null;

  const setStatus = (message, details = "") => {
    statusText.textContent = message;
    detailsText.textContent = details;
  };

  const removeListeners = async () => {
    const handles = state.listenerHandles;
    state.listenerHandles = [];
    for (const handle of handles) {
      try {
        await handle.remove();
      } catch (error) {
        console.debug("Failed to remove proxy listener", error);
      }
    }
  };

  const finish = async (message, details = "") => {
    state.completed = true;
    setStatus(message, details);
    runButton.disabled = false;
    const shouldClose = state.browserOpened;
    state.browserOpened = false;
    await removeListeners();
    if (shouldClose) {
      try {
        await InAppBrowser.close();
      } catch (error) {
        console.debug("Failed to close InAppBrowser", error);
      }
    }
  };

  const failIntercept = async (stage, event, error) => {
    const message = error instanceof Error ? error.message : String(error);
    try {
      if (stage === "request") {
        await InAppBrowser.continueProxyRequest({
          requestId: event.requestId,
          request: null,
        });
      } else {
        await InAppBrowser.continueProxyResponse({
          requestId: event.requestId,
          response: null,
        });
      }
    } catch (continuationError) {
      console.debug("Failed to continue intercepted proxy flow", continuationError);
    }
    await finish("Proxy regression failed", `${stage} intercept failed: ${message}`);
  };

  async function handleProxyRequest(event) {
    if (!recordProxyEvent(event, state.seenProxyEventIds, state.flowRequestIds)) {
      state.proxyEventIdsValid = false;
    }

    let modifiedRequest = null;

    switch (event.ruleName) {
    case "meta-request":
      state.metaRequestBodyOmitted = event.body == null;
      modifiedRequest = {
        headers: cloneHeaders(event.headers),
      };
      modifiedRequest.headers["x-proxy-meta-request"] = event.ruleName;
      break;
    case "fetch-request":
    case "xhr-request": {
      const headers = cloneHeaders(event.headers);
      headers["x-proxy-request-rule"] = event.ruleName;
      modifiedRequest = { headers };
      if (event.body) {
        modifiedRequest.body = rewriteJsonBase64(event.body, {
          changed: true,
          requestRule: event.ruleName,
        });
      }
      break;
    }
    default:
      break;
    }

    await InAppBrowser.continueProxyRequest({
      requestId: event.requestId,
      request: modifiedRequest,
    });
  }

  async function handleProxyResponse(event) {
    if (!recordProxyEvent(event, state.seenProxyEventIds, state.flowRequestIds)) {
      state.proxyEventIdsValid = false;
    }

    let modifiedResponse = null;

    switch (event.ruleName) {
    case "meta-response":
      state.metaResponseBodyOmitted = event.body == null;
      modifiedResponse = {
        headers: cloneHeaders(event.headers),
      };
      modifiedResponse.headers["x-proxy-meta-response"] = event.ruleName;
      break;
    case "entry-response":
      if (event.body) {
        const html = decodeBase64Text(event.body).replace(
          "</head>",
          '<meta name="proxy-entry" content="rewritten" /></head>',
        );
        modifiedResponse = {
          body: encodeBase64Text(html),
        };
      }
      break;
    case "fetch-response":
    case "xhr-response":
      if (event.body) {
        modifiedResponse = {
          body: rewriteJsonBase64(event.body, {
            proxyResponseRule: event.ruleName,
          }),
        };
      }
      break;
    default:
      break;
    }

    await InAppBrowser.continueProxyResponse({
      requestId: event.requestId,
      response: modifiedResponse,
    });
  }

  async function handleProxyResultMessage(event) {
    const detail = event.detail ?? undefined;
    if (!detail || detail.type !== "proxyRegression") {
      return;
    }

    if (detail.state === "passed") {
      const summary = detail.summary ?? {};
      const combinedSummary = combinedSummaryFrom(summary, state, openedWebViewId);
      const { failedChecks, lines } = summarizeChecks(summary, combinedSummary);
      await finish(
        failedChecks.length === 0 ? "Proxy regression passed" : "Proxy regression failed",
        lines.join("\n"),
      );
      return;
    }

    await finish("Proxy regression failed", failureDetailsFrom(detail));
  }

  runButton.addEventListener("click", async () => {
    runButton.disabled = true;
    state.browserOpened = false;
    openedWebViewId = null;
    state.completed = false;
    state.metaRequestBodyOmitted = false;
    state.metaResponseBodyOmitted = false;
    state.proxyEventIdsValid = true;
    state.seenProxyEventIds.clear();
    state.flowRequestIds.clear();
    setStatus("Proxy regression running...");

    await removeListeners();

    const proxyBaseUrl = getProxyBaseUrl();
    const proxyBasePattern = escapeRegex(proxyBaseUrl);
    const entryUrl = `${proxyBaseUrl}/entry`;

    state.listenerHandles = await Promise.all([
      InAppBrowser.addListener("proxyRequest", (event) => {
        handleProxyRequest(event).catch((error) => failIntercept("request", event, error));
      }),
      InAppBrowser.addListener("proxyResponse", (event) => {
        handleProxyResponse(event).catch((error) => failIntercept("response", event, error));
      }),
      InAppBrowser.addListener("messageFromWebview", (event) => {
        handleProxyResultMessage(event).catch((error) => {
          finish("Proxy regression failed", error?.message ?? String(error));
        });
      }),
      InAppBrowser.addListener("pageLoadError", async () => {
        if (!state.completed) {
          await finish("Proxy regression failed", "pageLoadError");
        }
      }),
      InAppBrowser.addListener("closeEvent", async () => {
        if (!state.completed) {
          state.browserOpened = false;
          runButton.disabled = false;
          await removeListeners();
          setStatus("Proxy regression failed", "Browser closed before results arrived");
        }
      }),
    ]);

    try {
      const { id } = await InAppBrowser.openWebView({
        url: entryUrl,
        toolbarType: ToolBarType.BLANK,
        proxyRules: createProxyRules(proxyBasePattern),
      });
      openedWebViewId = id;
      state.browserOpened = true;
    } catch (error) {
      await finish("Proxy regression failed", error?.message ?? String(error));
    }
  });
}
