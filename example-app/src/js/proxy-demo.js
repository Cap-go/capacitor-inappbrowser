import { CapacitorHttp } from "@capacitor/core";
import { InAppBrowser, InvisibilityMode, ToolBarType, addProxyHandler } from "@capgo/inappbrowser";

const GRAILED_URL = "https://www.grailed.com/users/sign_up";
const FACEBOOK_URL = "https://www.facebook.com/marketplace/create";
const DESKTOP_CHROME_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

const DESKTOP_CHROME_HEADERS = {
  "User-Agent": DESKTOP_CHROME_USER_AGENT,
  "Accept-Language": "en-US,en;q=0.9",
  "Sec-CH-UA": '"Google Chrome";v="144", "Chromium";v="144", "Not=A?Brand";v="24"',
  "Sec-CH-UA-Mobile": "?0",
  "Sec-CH-UA-Platform": '"Windows"',
};

const GRAILED_SDK_STUBS = [
  {
    match: (url) => url.includes("accounts.google.com"),
    body: `
      window.google = {
        accounts: {
          id: {
            initialize: () => {},
            renderButton: () => {},
            prompt: () => {},
          },
        },
      };
    `,
  },
  {
    match: (url) => url.includes("appleid"),
    body: `
      window.AppleID = {
        auth: {
          init: () => {},
          signIn: () => Promise.resolve(),
        },
      };
    `,
  },
  {
    match: (url) => url.includes("connect.facebook.net"),
    body: `
      window.FB = {
        init: () => {},
        login: () => {},
        getLoginStatus: () => {},
      };
    `,
  },
];

const GRAILED_GOOGLE_BROWSER_SPOOF = `
  (function() {
    const applyValue = (target, key, value) => {
      try {
        Object.defineProperty(target, key, {
          configurable: true,
          get: () => value,
        });
      } catch (_error) {}
    };

    const navigatorPrototype = Object.getPrototypeOf(window.navigator);
    applyValue(navigatorPrototype, "userAgent", ${JSON.stringify(DESKTOP_CHROME_USER_AGENT)});
    applyValue(navigatorPrototype, "platform", "Win32");
    applyValue(navigatorPrototype, "vendor", "Google Inc.");
    applyValue(navigatorPrototype, "language", "en-US");
    applyValue(navigatorPrototype, "languages", ["en-US", "en"]);
    applyValue(navigatorPrototype, "webdriver", false);
    applyValue(navigatorPrototype, "cookieEnabled", true);
    applyValue(navigatorPrototype, "onLine", true);

    window.chrome = window.chrome || {
      app: { isInstalled: false },
      runtime: {},
      csi: () => ({}),
      loadTimes: () => ({}),
    };

    if (!window.Notification) {
      window.Notification = function Notification() {};
    }
    try {
      window.Notification.permission = "default";
    } catch (_error) {}

    const originalPermissionsQuery =
      window.navigator.permissions && window.navigator.permissions.query
        ? window.navigator.permissions.query.bind(window.navigator.permissions)
        : null;

    if (window.navigator.permissions) {
      window.navigator.permissions.query = async (parameters) => {
        if (parameters && parameters.name === "notifications") {
          return {
            state: "default",
            onchange: null,
            addEventListener: () => {},
            removeEventListener: () => {},
            dispatchEvent: () => false,
          };
        }
        if (originalPermissionsQuery) {
          return originalPermissionsQuery(parameters);
        }
        return {
          state: "prompt",
          onchange: null,
          addEventListener: () => {},
          removeEventListener: () => {},
          dispatchEvent: () => false,
        };
      };
    }

    if (!window.navigator.userAgentData) {
      window.navigator.userAgentData = {
        brands: [
          { brand: "Google Chrome", version: "144" },
          { brand: "Chromium", version: "144" },
          { brand: "Not=A?Brand", version: "24" },
        ],
        mobile: false,
        platform: "Windows",
        getHighEntropyValues: async () => ({
          architecture: "x86",
          bitness: "64",
          mobile: false,
          model: "",
          platform: "Windows",
          platformVersion: "15.0.0",
          uaFullVersion: "144.0.0.0",
          fullVersionList: [
            { brand: "Google Chrome", version: "144.0.0.0" },
            { brand: "Chromium", version: "144.0.0.0" },
          ],
        }),
        toJSON() {
          return {
            brands: this.brands,
            mobile: this.mobile,
            platform: this.platform,
          };
        },
      };
    }

    window.__proxyDemoGrailedGoogleSpoof = true;
  })();
`;

const FACEBOOK_SCRIPT = `;(async () => {
  const post = (payload) => {
    if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
      window.mobileApp.postMessage(payload);
    }
  };

  const extractJsonRoots = (json) => {
    const jsonRoots = [];
    let bracketCount = 0;
    let jsonStart = 0;
    let isObjectStartFound = false;

    for (let i = 0; i < json.length; i += 1) {
      if (json[i] === "{") {
        bracketCount += 1;
        isObjectStartFound = true;
      }

      if (!isObjectStartFound) {
        jsonStart = i + 1;
      }

      if (json[i] === "}") {
        bracketCount -= 1;
      }

      if (bracketCount === 0 && isObjectStartFound) {
        jsonRoots.push(json.substring(jsonStart, i + 1));
        jsonStart = i + 1;
        isObjectStartFound = false;
      }
    }

    return jsonRoots;
  };

  const getProductUrl = (marketplaceId) => "https://www.facebook.com/marketplace/item/" + marketplaceId;

  try {
    const sellingResponse = await fetch("https://www.facebook.com/marketplace/you/selling", {
      credentials: "include",
    });
    const html = await sellingResponse.text();
    const matches = html.match(/"DTSGInitialData",\\[\\],{"token":"(.*?)"/);

    if (!matches || matches.length < 2) {
      throw new Error("DTSG not found");
    }

    const username = matches[1];
    const headers = new Headers();
    headers.append("authority", "www.facebook.com");
    headers.append("accept", "*/*");
    headers.append("content-type", "application/x-www-form-urlencoded");
    headers.append("x-fb-friendly-name", "CometMarketplaceYouSellingFastContentContainerQuery");

    const form = new URLSearchParams();
    form.append(
      "variables",
      JSON.stringify({
        count: 10,
        state: "LIVE",
        status: ["IN_STOCK"],
        cursor: "1",
        order: "CREATION_TIMESTAMP_DESC",
        isBusinessOnMarketplaceEnabled: false,
        scale: 1,
        title_search: null,
      }),
    );
    form.append("doc_id", "4987728437942946");
    form.append("fb_api_req_friendly_name", "CometMarketplaceYouSellingFastContentContainerQuery");
    form.append("fb_dtsg", username);

    const graphResponse = await fetch("https://www.facebook.com/api/graphql/", {
      method: "POST",
      headers,
      body: form,
      redirect: "follow",
      credentials: "include",
    });

    const text = await graphResponse.text();
    const jsonRoots = extractJsonRoots(text);
    if (!jsonRoots[0]) {
      throw new Error("Invalid marketplace payload");
    }

    const productData = JSON.parse(jsonRoots[0]);
    const edges = productData?.data?.viewer?.marketplace_listing_sets?.edges ?? [];
    const listings = edges.map((product) => ({
      marketPlaceId: product?.node?.first_listing?.id ?? null,
      title: product?.node?.first_listing?.base_marketplace_listing_title ?? null,
      price: product?.node?.first_listing?.listing_price?.formatted_amount ?? null,
      coverImage: product?.node?.first_listing?.primary_listing_photo?.image?.uri ?? null,
      created: product?.node?.first_listing?.creation_time
        ? new Date(product.node.first_listing.creation_time * 1000).toJSON()
        : null,
      marketplaceUrl: product?.node?.first_listing?.id
        ? getProductUrl(product.node.first_listing.id)
        : null,
    }));

    post({
      type: "facebook-script-result",
      listings,
      count: listings.length,
    });
  } catch (error) {
    post({
      type: "facebook-script-error",
      message: error && error.message ? error.message : String(error),
    });
  }
})();`;

function toBase64(value) {
  const bytes = new TextEncoder().encode(value);
  return bytesToBase64(bytes);
}

function bytesToBase64(bytes) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

function fromBase64(value) {
  const binary = atob(value);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

async function toBase64Payload(value) {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return toBase64(value);
  }
  if (value instanceof Blob) {
    const buffer = await value.arrayBuffer();
    return bytesToBase64(new Uint8Array(buffer));
  }
  if (value instanceof ArrayBuffer) {
    return bytesToBase64(new Uint8Array(value));
  }
  if (ArrayBuffer.isView(value)) {
    return bytesToBase64(new Uint8Array(value.buffer, value.byteOffset, value.byteLength));
  }
  return toBase64(JSON.stringify(value));
}

function normalizeError(error) {
  return error && error.message ? error.message : String(error);
}

function summarize(value) {
  if (typeof value === "string") {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch (_error) {
    return String(value);
  }
}

function formatConsoleMessage(event) {
  const level = String(event?.level || "log").toUpperCase();
  const location = [event?.source, Number.isFinite(event?.line) ? event.line : null]
    .filter(Boolean)
    .join(":");
  return {
    title: `Hidden webview JS ${level}`,
    details: [event?.message, location].filter(Boolean).join("\n"),
  };
}

function hostnameMatches(hostname, domain) {
  return hostname === domain || hostname.endsWith(`.${domain}`);
}

function shouldSpoofGrailedHeaders(hostname) {
  return (
    hostnameMatches(hostname, "grailed.com") ||
    hostnameMatches(hostname, "google.com") ||
    hostnameMatches(hostname, "gstatic.com") ||
    hostnameMatches(hostname, "googleusercontent.com")
  );
}

function isHtmlResponse(headers) {
  const contentType = headers?.["content-type"] || headers?.["Content-Type"] || "";
  return typeof contentType === "string" && contentType.includes("text/html");
}

function injectHtmlSnippet(html, snippet) {
  if (html.includes("</head>")) {
    return html.replace("</head>", `${snippet}</head>`);
  }
  if (html.includes("</body>")) {
    return html.replace("</body>", `${snippet}</body>`);
  }
  return `${html}${snippet}`;
}

function sleep(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function buildGrailedGoogleClickScript() {
  return `
    (() => {
      const post = (payload) => {
        if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
          window.mobileApp.postMessage(payload);
        }
      };

      const describe = (element) =>
        [
          element?.innerText,
          element?.textContent,
          element?.getAttribute?.("aria-label"),
          element?.getAttribute?.("data-testid"),
          element?.getAttribute?.("title"),
        ]
          .filter(Boolean)
          .join(" ")
          .replace(/\\s+/g, " ")
          .trim();

      const selectors = ["button", "a", "[role='button']", "[data-testid]", "[aria-label]"];
      let attempts = 0;
      const timer = window.setInterval(() => {
        attempts += 1;
        const candidates = Array.from(document.querySelectorAll(selectors.join(",")));
        const button = candidates.find((element) => /google/i.test(describe(element)));

        if (button) {
          window.clearInterval(timer);
          button.click();
          post({
            type: "proxy-demo-grailed-clicked-google",
            href: window.location.href,
            title: document.title,
          });
          return;
        }

        if (attempts >= 30) {
          window.clearInterval(timer);
          post({
            type: "proxy-demo-grailed-google-button-missing",
            href: window.location.href,
            title: document.title,
          });
        }
      }, 400);
    })();
  `;
}

function buildGoogleAutomationScript({ email, password, twoFactorCode }) {
  return `
    (() => {
      const EMAIL = ${JSON.stringify(email)};
      const PASSWORD = ${JSON.stringify(password)};
      const OTP = ${JSON.stringify(twoFactorCode || "")};

      const post = (payload) => {
        if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
          window.mobileApp.postMessage(payload);
        }
      };

      const elements = (selector) => Array.from(document.querySelectorAll(selector));
      const textOf = (element) =>
        [
          element?.innerText,
          element?.textContent,
          element?.getAttribute?.("aria-label"),
          element?.getAttribute?.("title"),
          element?.getAttribute?.("data-testid"),
          element?.getAttribute?.("name"),
        ]
          .filter(Boolean)
          .join(" ")
          .replace(/\\s+/g, " ")
          .trim();

      const clickMatching = (patterns) => {
        const candidates = elements("button, a, div[role='button'], [role='link'], span[role='button']");
        const button = candidates.find((candidate) =>
          patterns.some((pattern) => pattern.test(textOf(candidate))),
        );
        if (button) {
          button.click();
          return true;
        }
        return false;
      };

      const setInputValue = (input, value) => {
        const descriptor =
          Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value") ||
          Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value");
        input.focus();
        if (descriptor && descriptor.set) {
          descriptor.set.call(input, value);
        } else {
          input.value = value;
        }
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
        input.blur();
      };

      if (clickMatching([/use another account/i, /add another account/i])) {
        post({
          type: "proxy-demo-google-account-chooser",
          url: window.location.href,
        });
        return;
      }

      const emailInput =
        document.querySelector("input[type='email']") ||
        document.querySelector("input[name='identifier']") ||
        document.querySelector("input[autocomplete='username']");
      if (emailInput && EMAIL) {
        setInputValue(emailInput, EMAIL);
        clickMatching([/next/i, /continue/i]);
        post({
          type: "proxy-demo-google-email-submitted",
          url: window.location.href,
        });
        return;
      }

      const passwordInput =
        document.querySelector("input[type='password']") ||
        document.querySelector("input[name='Passwd']") ||
        document.querySelector("input[autocomplete='current-password']");
      if (passwordInput && PASSWORD) {
        setInputValue(passwordInput, PASSWORD);
        clickMatching([/next/i, /continue/i]);
        post({
          type: "proxy-demo-google-password-submitted",
          url: window.location.href,
        });
        return;
      }

      const otpInput =
        document.querySelector("input[autocomplete='one-time-code']") ||
        document.querySelector("input[type='tel']") ||
        document.querySelector("input[type='number']") ||
        elements("input").find((input) => /code|otp|verification/i.test(textOf(input)));
      if (otpInput) {
        if (OTP) {
          setInputValue(otpInput, OTP);
          clickMatching([/next/i, /continue/i, /verify/i, /done/i]);
          post({
            type: "proxy-demo-google-otp-submitted",
            url: window.location.href,
          });
        } else {
          post({
            type: "proxy-demo-google-otp-required",
            url: window.location.href,
          });
        }
        return;
      }

      if (window.location.hostname.includes("grailed.com") || window.location.href.includes("gis_transform")) {
        post({
          type: "proxy-demo-google-login-complete",
          url: window.location.href,
        });
        return;
      }

      if (/challenge|chooser|consent|signin/i.test(window.location.href)) {
        post({
          type: "proxy-demo-google-manual-step",
          url: window.location.href,
          title: document.title,
        });
        return;
      }

      post({
        type: "proxy-demo-google-noop",
        url: window.location.href,
        title: document.title,
      });
    })();
  `;
}

function buildGrailedSessionProbeScript() {
  return `
    (() => {
      const post = (payload) => {
        if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
          window.mobileApp.postMessage(payload);
        }
      };

      const bodyText = (document.body?.innerText || "").replace(/\\s+/g, " ").toLowerCase();
      const loggedInMarkers = [
        "sell",
        "wardrobe",
        "closet",
        "messages",
        "favorites",
        "logout",
        "settings",
        "my account",
      ];
      const loggedIn = loggedInMarkers.some((marker) => bodyText.includes(marker)) && !bodyText.includes("sign up");

      post({
        type: "proxy-demo-grailed-session-probe",
        loggedIn,
        url: window.location.href,
        title: document.title,
        cookieCount: document.cookie
          .split(";")
          .map((cookie) => cookie.trim())
          .filter(Boolean).length,
      });
    })();
  `;
}

export function setupProxyDemoButtons(root) {
  const grailedStubButton = root.querySelector("#proxy-demo-grailed-stub");
  const grailedGoogleButton = root.querySelector("#proxy-demo-grailed-google-login");
  const grailedBackgroundLoginButton = root.querySelector("#proxy-demo-grailed-background-login");
  const facebookButton = root.querySelector("#proxy-demo-facebook-login");
  const facebookProxyButton = root.querySelector("#proxy-demo-facebook-script");
  const googleEmailInput = root.querySelector("#proxy-demo-google-email");
  const googlePasswordInput = root.querySelector("#proxy-demo-google-password");
  const googleOtpInput = root.querySelector("#proxy-demo-google-otp");
  const showPrimaryButton = root.querySelector("#proxy-demo-show-primary");
  const showPopupButton = root.querySelector("#proxy-demo-show-popup");
  const statusText = root.querySelector("#proxy-demo-status-text");
  const detailsText = root.querySelector("#proxy-demo-details");
  const historyText = root.querySelector("#proxy-demo-history");

  if (
    !grailedStubButton ||
    !grailedGoogleButton ||
    !grailedBackgroundLoginButton ||
    !facebookButton ||
    !facebookProxyButton ||
    !googleEmailInput ||
    !googlePasswordInput ||
    !googleOtpInput ||
    !showPrimaryButton ||
    !showPopupButton ||
    !statusText ||
    !detailsText ||
    !historyText
  ) {
    return;
  }

  const buttons = [
    grailedStubButton,
    grailedGoogleButton,
    grailedBackgroundLoginButton,
    facebookButton,
    facebookProxyButton,
  ];
  let proxyHandle = null;
  let listenerHandles = [];
  let primaryWebViewId = null;
  let popupWindowIds = new Set();
  let knownWindowIds = new Set();
  let closeEventInterceptor = null;
  let statusHistory = [];
  let popupOpenTimeout = null;

  const setButtonsDisabled = (disabled) => {
    buttons.forEach((button) => {
      button.disabled = disabled;
    });
  };

  const updateDebugButtons = () => {
    showPrimaryButton.disabled = !primaryWebViewId;
    showPopupButton.disabled = popupWindowIds.size === 0;
  };

  const clearStatusHistory = () => {
    statusHistory = [];
    historyText.textContent = "No events yet.";
  };

  const pushHistory = (message, details = "") => {
    const now = new Date();
    const stamp = now.toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    const entry = [`[${stamp}] ${message}`, details].filter(Boolean).join("\n");
    statusHistory = [entry, ...statusHistory].slice(0, 20);
    historyText.textContent = statusHistory.join("\n\n");
  };

  const setStatus = (message, details = "") => {
    statusText.textContent = message;
    detailsText.textContent = details;
    pushHistory(message, details);
  };

  const clearPopupOpenTimeout = () => {
    if (popupOpenTimeout) {
      window.clearTimeout(popupOpenTimeout);
      popupOpenTimeout = null;
    }
  };

  const schedulePopupOpenTimeout = () => {
    clearPopupOpenTimeout();
    popupOpenTimeout = window.setTimeout(() => {
      setStatus(
        "Grailed click ran, but no popup opened",
        "The injected script clicked a Google-related element on the hidden Grailed page, but iOS has not emitted popupWindowOpened. Use the reveal buttons to inspect the hidden Grailed window or popup state.",
      );
    }, 7000);
  };

  const trackWindow = (id, { primary = false, popup = false } = {}) => {
    if (!id) {
      return;
    }
    knownWindowIds.add(id);
    if (primary) {
      primaryWebViewId = id;
    }
    if (popup) {
      popupWindowIds.add(id);
    }
    updateDebugButtons();
  };

  const untrackWindow = (id) => {
    if (!id) {
      return;
    }
    knownWindowIds.delete(id);
    popupWindowIds.delete(id);
    if (primaryWebViewId === id) {
      primaryWebViewId = null;
    }
    updateDebugButtons();
  };

  const closeTrackedWindows = async () => {
    const ids = [...knownWindowIds].reverse();
    knownWindowIds = new Set();
    popupWindowIds = new Set();
    primaryWebViewId = null;
    updateDebugButtons();
    for (const id of ids) {
      try {
        await InAppBrowser.close({ id });
      } catch (_error) {}
    }
  };

  const resetState = async ({ closeBrowser = false } = {}) => {
    const handles = listenerHandles;
    listenerHandles = [];
    closeEventInterceptor = null;
    clearPopupOpenTimeout();
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
    if (closeBrowser) {
      await closeTrackedWindows();
    } else {
      knownWindowIds = new Set();
      popupWindowIds = new Set();
      primaryWebViewId = null;
      updateDebugButtons();
    }
    setButtonsDisabled(false);
  };

  const installCommonListeners = async (name) => {
    listenerHandles.push(
      await InAppBrowser.addListener("pageLoadError", async (event) => {
        const idSuffix = event?.id ? ` (${event.id})` : "";
        await resetState({ closeBrowser: true });
        setStatus(`${name} failed`, `pageLoadError${idSuffix}`);
      }),
    );

    listenerHandles.push(
      await InAppBrowser.addListener("closeEvent", async (event) => {
        if (closeEventInterceptor) {
          const handled = await closeEventInterceptor(event);
          if (handled) {
            return;
          }
        }
        if (event?.id) {
          untrackWindow(event.id);
        }
        await resetState({ closeBrowser: true });
        setStatus(`${name} closed`, event?.url || "Browser dismissed.");
      }),
    );
  };

  const startScenario = async (name, runner) => {
    clearStatusHistory();
    setButtonsDisabled(true);
    setStatus(`${name} starting...`);
    await resetState({ closeBrowser: true });
    setButtonsDisabled(true);

    try {
      await installCommonListeners(name);
      await runner();
      setButtonsDisabled(false);
    } catch (error) {
      await resetState({ closeBrowser: true });
      setStatus(`${name} failed`, normalizeError(error));
    }
  };

  const readGoogleCredentials = () => ({
    email: googleEmailInput.value.trim(),
    password: googlePasswordInput.value,
    twoFactorCode: googleOtpInput.value.trim(),
  });

  const attachManualPopupStatus = async (label) => {
    listenerHandles.push(
      await InAppBrowser.addListener("popupWindowOpened", async (event) => {
        clearPopupOpenTimeout();
        trackWindow(event.id, { popup: true });
        setStatus(
          `${label} popup opened`,
          event.visible
            ? event.url || "Popup opened in a managed child window."
            : "Popup opened hidden in the background.",
        );
      }),
    );
  };

  showPrimaryButton.addEventListener("click", async () => {
    if (!primaryWebViewId) {
      setStatus("No hidden Grailed window to show", "Start the background flow first.");
      return;
    }
    await InAppBrowser.show({ id: primaryWebViewId });
    setStatus("Revealed hidden Grailed window", primaryWebViewId);
  });

  showPopupButton.addEventListener("click", async () => {
    const popupId = [...popupWindowIds].at(-1);
    if (!popupId) {
      setStatus("No popup window to show", "No managed popup is currently tracked.");
      return;
    }
    await InAppBrowser.show({ id: popupId });
    setStatus("Revealed hidden popup window", popupId);
  });

  grailedStubButton.addEventListener("click", async () => {
    await startScenario("Grailed SDK Stub Proxy", async () => {
      proxyHandle = await addProxyHandler(async (request) => {
        const match = GRAILED_SDK_STUBS.find((stub) => stub.match(request.url));
        if (!match) {
          return null;
        }
        setStatus("Grailed SDK stub served", request.url);
        return new Response(match.body, {
          status: 200,
          headers: {
            "Content-Type": "application/javascript; charset=utf-8",
          },
        });
      });

      listenerHandles.push(
        await InAppBrowser.addListener("browserPageLoaded", async () => {
          setStatus(
            "Grailed page loaded",
            "Blocked social SDKs are being stubbed through the proxy handler.",
          );
        }),
      );

      const result = await InAppBrowser.openWebView({
        url: GRAILED_URL,
        proxyRequests: true,
        toolbarType: ToolBarType.NAVIGATION,
        title: "Grailed stub proxy demo",
      });
      trackWindow(result.id, { primary: true });
    });
  });

  grailedGoogleButton.addEventListener("click", async () => {
    await startScenario("Grailed Google Login Proxy", async () => {
      let googleHtmlInjected = false;

      await attachManualPopupStatus("Grailed Google login");

      proxyHandle = await addProxyHandler(async (request) => {
        const requestUrl = new URL(request.url);

        if (request.phase === "outbound" && shouldSpoofGrailedHeaders(requestUrl.hostname)) {
          return {
            request: {
              url: request.url,
              headers: {
                ...(request.headers || {}),
                ...DESKTOP_CHROME_HEADERS,
              },
              body: request.body,
            },
          };
        }

        if (
          request.phase === "inbound" &&
          hostnameMatches(requestUrl.hostname, "google.com") &&
          request.responseBody &&
          isHtmlResponse(request.responseHeaders)
        ) {
          googleHtmlInjected = true;
          setStatus("Injected browser spoof into Google HTML", request.url);
          return {
            status: request.status || 200,
            headers: request.responseHeaders || {},
            body: toBase64(
              injectHtmlSnippet(
                fromBase64(request.responseBody),
                `<script>${GRAILED_GOOGLE_BROWSER_SPOOF}</script>`,
              ),
            ),
          };
        }

        return null;
      });

      listenerHandles.push(
        await InAppBrowser.addListener("browserPageLoaded", async (event) => {
          await InAppBrowser.executeScript({
            id: event?.id,
            code: GRAILED_GOOGLE_BROWSER_SPOOF,
          });
          setStatus(
            "Grailed Google login proxy active",
            googleHtmlInjected
              ? "Desktop browser spoof was injected into Google pages and reapplied after navigation."
              : "Desktop browser spoof is active for the current page. Try the Google login button now.",
          );
        }),
      );

      const result = await InAppBrowser.openWebView({
        url: GRAILED_URL,
        proxyRequests: true,
        headers: DESKTOP_CHROME_HEADERS,
        isPresentAfterPageLoad: true,
        preShowScript: GRAILED_GOOGLE_BROWSER_SPOOF,
        preShowScriptInjectionTime: "documentStart",
        enableGooglePaySupport: true,
        toolbarType: ToolBarType.NAVIGATION,
        title: "Grailed Google login proxy demo",
      });
      trackWindow(result.id, { primary: true });
    });
  });

  grailedBackgroundLoginButton.addEventListener("click", async () => {
    const credentials = readGoogleCredentials();
    if (!credentials.email || !credentials.password) {
      setStatus(
        "Grailed background login blocked",
        "Enter the Google email and password fields first.",
      );
      return;
    }

    await startScenario("Grailed Background Login", async () => {
      let googlePopupId = null;
      let popupFlowCompleted = false;
      let probingSession = false;
      let googleHtmlInjected = false;

      proxyHandle = await addProxyHandler(async (request) => {
        const requestUrl = new URL(request.url);

        if (request.phase === "outbound" && shouldSpoofGrailedHeaders(requestUrl.hostname)) {
          return {
            request: {
              url: request.url,
              headers: {
                ...(request.headers || {}),
                ...DESKTOP_CHROME_HEADERS,
              },
              body: request.body,
            },
          };
        }

        if (
          request.phase === "inbound" &&
          hostnameMatches(requestUrl.hostname, "google.com") &&
          request.responseBody &&
          isHtmlResponse(request.responseHeaders)
        ) {
          googleHtmlInjected = true;
          setStatus("Injected browser spoof into hidden Google HTML", request.url);
          return {
            status: request.status || 200,
            headers: request.responseHeaders || {},
            body: toBase64(
              injectHtmlSnippet(
                fromBase64(request.responseBody),
                `<script>${GRAILED_GOOGLE_BROWSER_SPOOF}</script>`,
              ),
            ),
          };
        }

        return null;
      });

      const runGrailedSessionProbe = async (reason) => {
        if (!primaryWebViewId || probingSession) {
          return;
        }
        probingSession = true;
        await sleep(1500);
        await InAppBrowser.executeScript({
          id: primaryWebViewId,
          code: buildGrailedSessionProbeScript(),
        });
        setStatus("Checking Grailed session", `Probe reason: ${reason}`);
      };

      const runGoogleAutomationStep = async (reason) => {
        if (!googlePopupId) {
          return;
        }
        await sleep(700);
        await InAppBrowser.executeScript({
          id: googlePopupId,
          code: buildGoogleAutomationScript(credentials),
        });
        setStatus("Driving Google popup in background", `Automation trigger: ${reason}`);
      };

      closeEventInterceptor = async (event) => {
        if (event?.id && popupWindowIds.has(event.id)) {
          const closedPopupId = event.id;
          untrackWindow(closedPopupId);
          if (googlePopupId === closedPopupId) {
            googlePopupId = null;
            popupFlowCompleted = true;
            setStatus(
              "Google popup closed",
              "Popup finished or redirected away. Checking Grailed session next.",
            );
            await runGrailedSessionProbe("popup-closed");
          }
          return true;
        }
        return false;
      };

      listenerHandles.push(
        await InAppBrowser.addListener("popupWindowOpened", async (event) => {
          if (event.parentId !== primaryWebViewId) {
            return;
          }
          googlePopupId = event.id;
          trackWindow(event.id, { popup: true });
          setStatus(
            "Captured Google popup in background",
            event.url || "Popup window opened hidden and is ready for automation.",
          );
          await runGoogleAutomationStep("popup-opened");
        }),
      );

      listenerHandles.push(
        await InAppBrowser.addListener("browserPageLoaded", async (event) => {
          if (!event?.id) {
            return;
          }

          await InAppBrowser.executeScript({
            id: event.id,
            code: GRAILED_GOOGLE_BROWSER_SPOOF,
          });

          if (event.id === primaryWebViewId) {
            if (!googlePopupId && !popupFlowCompleted) {
              await sleep(1200);
              await InAppBrowser.executeScript({
                id: primaryWebViewId,
                code: buildGrailedGoogleClickScript(),
              });
              setStatus(
                "Clicked Grailed Google button in background",
                "Waiting for the managed Google popup to be created.",
              );
              return;
            }

            if (popupFlowCompleted) {
              await runGrailedSessionProbe("grailed-reloaded");
            }
            return;
          }

          if (event.id === googlePopupId) {
            await runGoogleAutomationStep("google-page-loaded");
          }
        }),
      );

      listenerHandles.push(
        await InAppBrowser.addListener("consoleMessage", async (event) => {
          if (!event?.id || !knownWindowIds.has(event.id)) {
            return;
          }

          const level = String(event.level || "log").toLowerCase();
          if (!["log", "info", "warn", "error", "debug", "assert"].includes(level)) {
            return;
          }

          const { title, details } = formatConsoleMessage(event);
          pushHistory(title, details);
        }),
      );

      listenerHandles.push(
        await InAppBrowser.addListener("messageFromWebview", async (event) => {
          const detail =
            event?.detail && typeof event.detail === "object"
              ? { ...event.detail, id: event.id }
              : event;

          switch (detail?.type) {
            case "proxy-demo-grailed-clicked-google":
              setStatus(
                "Grailed click script fired",
                "The hidden Grailed page received the injected click. This does not mean iOS opened a popup yet. Waiting for popupWindowOpened next.",
              );
              schedulePopupOpenTimeout();
              break;
            case "proxy-demo-grailed-google-button-missing":
              setStatus(
                "Grailed Google button not found",
                summarize({ url: detail.href, title: detail.title }),
              );
              break;
            case "proxy-demo-google-account-chooser":
              setStatus("Google account chooser detected", detail.url || "");
              break;
            case "proxy-demo-google-email-submitted":
              setStatus("Submitted Google email", detail.url || "");
              break;
            case "proxy-demo-google-password-submitted":
              setStatus("Submitted Google password", detail.url || "");
              break;
            case "proxy-demo-google-otp-required":
              setStatus(
                "Google 2FA code required",
                "Fill the optional 2FA field in the example app and try again.",
              );
              break;
            case "proxy-demo-google-otp-submitted":
              setStatus("Submitted Google 2FA code", detail.url || "");
              break;
            case "proxy-demo-google-manual-step":
              setStatus(
                "Google presented an extra step",
                summarize({ url: detail.url, title: detail.title }),
              );
              break;
            case "proxy-demo-google-noop":
              setStatus(
                "Google popup waiting on another page",
                summarize({ url: detail.url, title: detail.title }),
              );
              break;
            case "proxy-demo-google-login-complete":
              popupFlowCompleted = true;
              setStatus("Google login flow completed", detail.url || "Checking Grailed session.");
              if (googlePopupId) {
                const popupIdToClose = googlePopupId;
                googlePopupId = null;
                untrackWindow(popupIdToClose);
                try {
                  await InAppBrowser.close({ id: popupIdToClose });
                } catch (_error) {}
              }
              await runGrailedSessionProbe("google-login-complete");
              break;
            case "proxy-demo-grailed-session-probe":
              if (detail.loggedIn) {
                await resetState({ closeBrowser: true });
                setStatus(
                  "Grailed background login finished",
                  summarize({
                    loggedIn: detail.loggedIn,
                    url: detail.url,
                    title: detail.title,
                    cookieCount: detail.cookieCount,
                  }),
                );
              } else {
                probingSession = false;
                setStatus(
                  popupFlowCompleted ? "Grailed session not confirmed yet" : "Grailed page still loading",
                  summarize({
                    loggedIn: detail.loggedIn,
                    url: detail.url,
                    title: detail.title,
                    cookieCount: detail.cookieCount,
                  }),
                );
              }
              break;
            default:
              break;
          }
        }),
      );

      const result = await InAppBrowser.openWebView({
        url: GRAILED_URL,
        proxyRequests: true,
        headers: DESKTOP_CHROME_HEADERS,
        hidden: true,
        hiddenPopupWindow: true,
        invisibilityMode: InvisibilityMode.FAKE_VISIBLE,
        isPresentAfterPageLoad: true,
        captureConsoleLogs: true,
        enableGooglePaySupport: true,
        preShowScript: GRAILED_GOOGLE_BROWSER_SPOOF,
        preShowScriptInjectionTime: "documentStart",
        toolbarType: ToolBarType.NAVIGATION,
        title: "Grailed background login demo",
      });
      trackWindow(result.id, { primary: true });
      setStatus(
        "Grailed background login started",
        googleHtmlInjected
          ? `Hidden Grailed window created: ${result.id}. Google HTML rewrites are active.`
          : `Hidden Grailed window created: ${result.id}. Waiting for the first page load.`,
      );
    });
  });

  facebookButton.addEventListener("click", async () => {
    await startScenario("Facebook Login", async () => {
      listenerHandles.push(
        await InAppBrowser.addListener("browserPageLoaded", async () => {
          setStatus(
            "Facebook Login opened",
            "Loaded without proxying, using the spoofed desktop browser headers.",
          );
        }),
      );

      const result = await InAppBrowser.openWebView({
        url: FACEBOOK_URL,
        headers: {
          "user-agent": DESKTOP_CHROME_USER_AGENT,
        },
        toolbarType: ToolBarType.NAVIGATION,
        title: "Facebook login demo",
      });
      trackWindow(result.id, { primary: true });
    });
  });

  facebookProxyButton.addEventListener("click", async () => {
    await startScenario("Facebook Script Proxy", async () => {
      let injected = false;

      proxyHandle = await addProxyHandler(async (request) => {
        const requestUrl = new URL(request.url);

        if (requestUrl.pathname === "/assets/facebook-script.js") {
          setStatus("Facebook helper script served", request.url);
          return {
            status: 200,
            headers: {
              "Content-Type": "application/javascript",
            },
            body: toBase64(FACEBOOK_SCRIPT),
          };
        }

        if (!requestUrl.hostname.includes("facebook.com")) {
          return null;
        }

        const headers = { ...(request.headers || {}), "User-Agent": DESKTOP_CHROME_USER_AGENT };

        try {
          const response = await CapacitorHttp.request({
            url: request.url,
            method: request.method,
            headers,
            data: request.body ? fromBase64(request.body) : undefined,
            responseType: "blob",
            webFetchExtra: { credentials: "include" },
          });

          return {
            status: response.status,
            headers: response.headers || {},
            body: await toBase64Payload(response.data),
          };
        } catch (error) {
          setStatus("Facebook upstream proxy failed", normalizeError(error));
          return null;
        }
      });

      listenerHandles.push(
        await InAppBrowser.addListener("browserPageLoaded", async () => {
          if (injected) {
            return;
          }
          injected = true;
          await InAppBrowser.executeScript({
            code: `
              (() => {
                const script = document.createElement("script");
                script.type = "module";
                script.src = "/assets/facebook-script.js";
                document.head.appendChild(script);
              })();
            `,
          });
          setStatus(
            "Facebook script injected",
            "Waiting for script results from the marketplace probe...",
          );
        }),
      );

      listenerHandles.push(
        await InAppBrowser.addListener("messageFromWebview", async (event) => {
          const detail = event.detail ?? event ?? {};
          if (detail.type === "facebook-script-error") {
            setStatus("Facebook script error", detail.message ?? "Unknown error");
            return;
          }
          if (detail.type === "facebook-script-result") {
            setStatus(
              `Facebook script returned ${detail.count ?? 0} listing(s)`,
              summarize(detail.listings ?? []),
            );
          }
        }),
      );

      const result = await InAppBrowser.openWebView({
        url: FACEBOOK_URL,
        proxyRequests: true,
        headers: {
          "user-agent": DESKTOP_CHROME_USER_AGENT,
        },
        toolbarType: ToolBarType.NAVIGATION,
        title: "Facebook script proxy demo",
      });
      trackWindow(result.id, { primary: true });
    });
  });
}
