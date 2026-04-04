import { CapacitorHttp } from "@capacitor/core";
import { InAppBrowser, ToolBarType, addProxyHandler } from "@capgo/inappbrowser";

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

export function setupProxyDemoButtons(root) {
  const grailedStubButton = root.querySelector("#proxy-demo-grailed-stub");
  const grailedGoogleButton = root.querySelector("#proxy-demo-grailed-google-login");
  const facebookButton = root.querySelector("#proxy-demo-facebook-login");
  const facebookProxyButton = root.querySelector("#proxy-demo-facebook-script");
  const statusText = root.querySelector("#proxy-demo-status-text");
  const detailsText = root.querySelector("#proxy-demo-details");

  if (
    !grailedStubButton ||
    !grailedGoogleButton ||
    !facebookButton ||
    !facebookProxyButton ||
    !statusText ||
    !detailsText
  ) {
    return;
  }

  const buttons = [grailedStubButton, grailedGoogleButton, facebookButton, facebookProxyButton];
  let proxyHandle = null;
  let listenerHandles = [];
  let browserOpened = false;

  const setButtonsDisabled = (disabled) => {
    buttons.forEach((button) => {
      button.disabled = disabled;
    });
  };

  const setStatus = (message, details = "") => {
    statusText.textContent = message;
    detailsText.textContent = details;
  };

  const resetState = async ({ closeBrowser = false } = {}) => {
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
    const shouldClose = closeBrowser && browserOpened;
    browserOpened = false;
    if (shouldClose) {
      try {
        await InAppBrowser.close();
      } catch (_error) {}
    }
    setButtonsDisabled(false);
  };

  const installCommonListeners = async (name) => {
    listenerHandles.push(
      await InAppBrowser.addListener("pageLoadError", async () => {
        await resetState();
        setStatus(`${name} failed`, "pageLoadError");
      }),
    );

    listenerHandles.push(
      await InAppBrowser.addListener("closeEvent", async () => {
        await resetState();
        setStatus(`${name} closed`, "Browser dismissed.");
      }),
    );
  };

  const startScenario = async (name, runner) => {
    setButtonsDisabled(true);
    setStatus(`${name} starting...`);
    await resetState({ closeBrowser: true });
    setButtonsDisabled(true);

    try {
      await installCommonListeners(name);
      await runner();
      browserOpened = true;
      setButtonsDisabled(false);
    } catch (error) {
      await resetState();
      setStatus(`${name} failed`, normalizeError(error));
    }
  };

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

      await InAppBrowser.openWebView({
        url: GRAILED_URL,
        proxyRequests: true,
        toolbarType: ToolBarType.NAVIGATION,
        title: "Grailed stub proxy demo",
      });
    });
  });

  grailedGoogleButton.addEventListener("click", async () => {
    await startScenario("Grailed Google Login Proxy", async () => {
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
        await InAppBrowser.addListener("browserPageLoaded", async () => {
          await InAppBrowser.executeScript({
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

      await InAppBrowser.openWebView({
        url: GRAILED_URL,
        proxyRequests: true,
        headers: DESKTOP_CHROME_HEADERS,
        isPresentAfterPageLoad: true,
        preShowScript: GRAILED_GOOGLE_BROWSER_SPOOF,
        preShowScriptInjectionTime: "documentStart",
        toolbarType: ToolBarType.NAVIGATION,
        title: "Grailed Google login proxy demo",
      });
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

      await InAppBrowser.openWebView({
        url: FACEBOOK_URL,
        headers: {
          "user-agent": DESKTOP_CHROME_USER_AGENT,
        },
        toolbarType: ToolBarType.NAVIGATION,
        title: "Facebook login demo",
      });
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
            data: request.body ? atob(request.body) : undefined,
            responseType: "blob",
            webFetchExtra: { credentials: "include" },
          });

          let body = "";
          if (typeof response.data === "string") {
            body = response.data;
          } else if (response.data) {
            body = btoa(unescape(encodeURIComponent(JSON.stringify(response.data))));
          }

          return {
            status: response.status,
            headers: response.headers || {},
            body,
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
              (function() {
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
          const detail = event.detail ?? {};
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

      await InAppBrowser.openWebView({
        url: FACEBOOK_URL,
        proxyRequests: true,
        headers: {
          "user-agent": DESKTOP_CHROME_USER_AGENT,
        },
        toolbarType: ToolBarType.NAVIGATION,
        title: "Facebook script proxy demo",
      });
    });
  });
}
