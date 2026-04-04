import { BackgroundColor, InAppBrowser, ToolBarType } from "@capgo/inappbrowser";

const GRAILED_URL = "https://www.grailed.com/users/sign_up";
const FACEBOOK_URL = "https://www.facebook.com/marketplace/create";
const FACEBOOK_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

const GRAILED_RULES = [
  {
    ruleName: "grailed-google-sdk",
    regex: String.raw`^https://accounts\.google\.com/.*`,
    mode: "response",
    includeBody: false,
  },
  {
    ruleName: "grailed-apple-sdk",
    regex: String.raw`^https://.*appleid.*`,
    mode: "response",
    includeBody: false,
  },
  {
    ruleName: "grailed-facebook-sdk",
    regex: String.raw`^https://connect\.facebook\.net/.*`,
    mode: "response",
    includeBody: false,
  },
];

const FACEBOOK_PROXY_RULES = [
  {
    ruleName: "facebook-user-agent",
    regex: String.raw`^https://([a-zA-Z0-9-]+\.)*facebook\.com/.*`,
    mode: "request",
    includeBody: false,
  },
];

const GRAILED_STUBS = {
  "grailed-google-sdk": `
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
  "grailed-apple-sdk": `
    window.AppleID = {
      auth: {
        init: () => {},
        signIn: () => Promise.resolve(),
      },
    };
  `,
  "grailed-facebook-sdk": `
    window.FB = {
      init: () => {},
      login: () => {},
      getLoginStatus: () => {},
    };
  `,
};

const FACEBOOK_INJECTED_SCRIPT = String.raw`
;(async () => {
  const post = (message) => {
    if (window.mobileApp && typeof window.mobileApp.postMessage === "function") {
      window.mobileApp.postMessage(message);
    }
  };

  const extractJsonRoots = (json) => {
    const roots = [];
    let bracketCount = 0;
    let jsonStart = 0;
    let objectStarted = false;

    for (let index = 0; index < json.length; index += 1) {
      if (json[index] === "{") {
        bracketCount += 1;
        objectStarted = true;
      }

      if (!objectStarted) {
        jsonStart = index + 1;
      }

      if (json[index] === "}") {
        bracketCount -= 1;
      }

      if (bracketCount === 0 && objectStarted) {
        roots.push(json.substring(jsonStart, index + 1));
        jsonStart = index + 1;
        objectStarted = false;
      }
    }

    return roots;
  };

  const getProductUrl = (marketplaceId) => {
    return "https://www.facebook.com/marketplace/item/" + marketplaceId;
  };

  try {
    const sellingResponse = await fetch("https://www.facebook.com/marketplace/you/selling", {
      credentials: "include",
    });
    const html = await sellingResponse.text();
    const tokenMatch = html.match(/"DTSGInitialData",\\[\\],{"token":"(.*?)"/);

    if (!tokenMatch || tokenMatch.length < 2) {
      throw new Error("DTSG token not found");
    }

    const fbDtsg = tokenMatch[1];
    const headers = new Headers();
    headers.append("authority", "www.facebook.com");
    headers.append("accept", "*/*");
    headers.append("content-type", "application/x-www-form-urlencoded");
    headers.append("x-fb-friendly-name", "CometMarketplaceYouSellingFastContentContainerQuery");

    const body = new URLSearchParams();
    body.append(
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
    body.append("doc_id", "4987728437942946");
    body.append(
      "fb_api_req_friendly_name",
      "CometMarketplaceYouSellingFastContentContainerQuery",
    );
    body.append("fb_dtsg", fbDtsg);

    const graphqlResponse = await fetch("https://www.facebook.com/api/graphql/", {
      method: "POST",
      headers,
      body,
      credentials: "include",
      redirect: "follow",
    });

    const payload = await graphqlResponse.text();
    const roots = extractJsonRoots(payload);

    if (!roots[0]) {
      throw new Error("GraphQL payload did not contain a JSON root");
    }

    const parsed = JSON.parse(roots[0]);
    const edges = parsed?.data?.viewer?.marketplace_listing_sets?.edges ?? [];
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
      type: "facebookProxyListings",
      count: listings.length,
      listings,
    });
  } catch (error) {
    post({
      type: "facebookProxyError",
      message: error instanceof Error ? error.message : String(error),
    });
  }
})();
`;

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

function cloneHeaders(headers) {
  return headers ? { ...headers } : {};
}

function normalizeError(error) {
  return error instanceof Error ? error.message : String(error);
}

function summarizeValue(value) {
  if (typeof value === "string") {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch (error) {
    if (error instanceof Error) {
      return `[unserializable payload: ${error.message}]`;
    }
    return "[unserializable payload]";
  }
}

export function setupCrosslistProxyButtons(root) {
  const grailedButton = root.querySelector("#crosslist-grailed-proxy");
  const facebookLoginButton = root.querySelector("#crosslist-facebook-login");
  const facebookProxyButton = root.querySelector("#crosslist-facebook-script-proxy");
  const statusText = root.querySelector("#crosslist-proxy-status-text");
  const detailsText = root.querySelector("#crosslist-proxy-details");

  if (!grailedButton || !facebookLoginButton || !facebookProxyButton || !statusText || !detailsText) {
    return;
  }

  const buttons = [grailedButton, facebookLoginButton, facebookProxyButton];
  let listenerHandles = [];
  let activeFlowName = null;
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

  const removeListeners = async () => {
    const handles = listenerHandles;
    listenerHandles = [];
    for (const handle of handles) {
      try {
        await handle.remove();
      } catch (error) {
        console.debug("Failed to remove Crosslist proxy listener", error);
      }
    }
  };

  const teardown = async ({ closeBrowser = false } = {}) => {
    const shouldCloseBrowser = closeBrowser && browserOpened;
    browserOpened = false;
    activeFlowName = null;
    await removeListeners();
    if (shouldCloseBrowser) {
      try {
        await InAppBrowser.close();
      } catch (error) {
        console.debug("Failed to close previous browser session", error);
      }
    }
    setButtonsDisabled(false);
  };

  const continueRequest = async (event, request) => {
    await InAppBrowser.continueProxyRequest({
      requestId: event.requestId,
      request,
    });
  };

  const continueResponse = async (event, response) => {
    await InAppBrowser.continueProxyResponse({
      requestId: event.requestId,
      response,
    });
  };

  const failFlow = async (flowName, error) => {
    await teardown({ closeBrowser: true });
    setStatus(`${flowName} failed`, normalizeError(error));
  };

  const openScenario = async ({
    flowName,
    url,
    title,
    headers,
    proxyRules,
    onProxyRequest,
    onProxyResponse,
    onMessage,
    onPageLoaded,
  }) => {
    await teardown({ closeBrowser: true });
    setButtonsDisabled(true);
    activeFlowName = flowName;
    setStatus(`${flowName} starting...`);

    let pageLoadHandled = false;
    const handles = [
      InAppBrowser.addListener("closeEvent", async () => {
        const closedFlowName = activeFlowName ?? flowName;
        browserOpened = false;
        await teardown();
        setStatus(`${closedFlowName} closed`, "Browser dismissed.");
      }),
      InAppBrowser.addListener("pageLoadError", async (event) => {
        await failFlow(flowName, summarizeValue(event));
      }),
    ];

    if (onProxyRequest) {
      handles.push(
        InAppBrowser.addListener("proxyRequest", (event) => {
          onProxyRequest(event).catch((error) => failFlow(flowName, error));
        }),
      );
    }

    if (onProxyResponse) {
      handles.push(
        InAppBrowser.addListener("proxyResponse", (event) => {
          onProxyResponse(event).catch((error) => failFlow(flowName, error));
        }),
      );
    }

    if (onMessage) {
      handles.push(
        InAppBrowser.addListener("messageFromWebview", (event) => {
          onMessage(event).catch((error) => failFlow(flowName, error));
        }),
      );
    }

    if (onPageLoaded) {
      handles.push(
        InAppBrowser.addListener("browserPageLoaded", (event) => {
          if (pageLoadHandled) {
            return;
          }
          pageLoadHandled = true;
          onPageLoaded(event).catch((error) => failFlow(flowName, error));
        }),
      );
    }

    listenerHandles = await Promise.all(handles);

    try {
      await InAppBrowser.openWebView({
        url,
        title,
        toolbarType: ToolBarType.NAVIGATION,
        toolbarColor: "#111827",
        toolbarTextColor: "#ffffff",
        backgroundColor: BackgroundColor.WHITE,
        enabledSafeBottomMargin: true,
        headers,
        proxyRules,
      });
      browserOpened = true;
      setStatus(
        `${flowName} opened`,
        proxyRules?.length
          ? "Waiting for proxy activity and browser events..."
          : "Scenario opened without proxy interception.",
      );
    } catch (error) {
      await failFlow(flowName, error);
    }
  };

  grailedButton.addEventListener("click", () => {
    openScenario({
      flowName: "Grailed Google SDK Proxy",
      url: GRAILED_URL,
      title: "Grailed proxy demo",
      proxyRules: GRAILED_RULES,
      onProxyResponse: async (event) => {
        const script = GRAILED_STUBS[event.ruleName];
        if (!script) {
          await continueResponse(event, null);
          return;
        }

        await continueResponse(event, {
          status: 200,
          headers: {
            ...cloneHeaders(event.headers),
            "content-type": "application/javascript; charset=utf-8",
            "cache-control": "no-store",
          },
          body: encodeBase64Text(script),
        });

        setStatus(
          "Grailed proxy intercepted SDK response",
          `${event.ruleName}\n${event.url}`,
        );
      },
      onPageLoaded: async () => {
        setStatus(
          "Grailed proxy page loaded",
          "The social SDK requests are now served by split proxy response overrides.",
        );
      },
    }).catch((error) => {
      failFlow("Grailed Google SDK Proxy", error);
    });
  });

  facebookLoginButton.addEventListener("click", () => {
    openScenario({
      flowName: "Facebook Login",
      url: FACEBOOK_URL,
      title: "Facebook login demo",
      headers: {
        "User-Agent": FACEBOOK_USER_AGENT,
      },
      onPageLoaded: async () => {
        setStatus(
          "Facebook Login page loaded",
          "This matches the Crosslist login button: spoofed user-agent, no proxy rules.",
        );
      },
    }).catch((error) => {
      failFlow("Facebook Login", error);
    });
  });

  facebookProxyButton.addEventListener("click", () => {
    let requestRewriteLogged = false;

    openScenario({
      flowName: "Facebook Script Inject Proxy",
      url: FACEBOOK_URL,
      title: "Facebook script proxy demo",
      headers: {
        "User-Agent": FACEBOOK_USER_AGENT,
      },
      proxyRules: FACEBOOK_PROXY_RULES,
      onProxyRequest: async (event) => {
        await continueRequest(event, {
          headers: {
            ...cloneHeaders(event.headers),
            "User-Agent": FACEBOOK_USER_AGENT,
          },
        });

        if (!requestRewriteLogged) {
          requestRewriteLogged = true;
          setStatus(
            "Facebook proxy rewrote request headers",
            `${event.method} ${event.url}`,
          );
        }
      },
      onPageLoaded: async () => {
        await InAppBrowser.executeScript({
          code: FACEBOOK_INJECTED_SCRIPT,
        });
        setStatus(
          "Facebook script injected",
          "Waiting for window.mobileApp.postMessage() results from the marketplace probe...",
        );
      },
      onMessage: async (event) => {
        if (event.detail?.type === "facebookProxyError") {
          setStatus("Facebook script reported an error", event.detail.message);
          return;
        }

        if (event.detail?.type === "facebookProxyListings") {
          setStatus(
            `Facebook script returned ${event.detail.count} listing(s)`,
            summarizeValue(event.detail.listings),
          );
        }
      },
    }).catch((error) => {
      failFlow("Facebook Script Inject Proxy", error);
    });
  });
}
