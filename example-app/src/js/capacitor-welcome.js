import { SplashScreen } from "@capacitor/splash-screen";
import {
  InAppBrowser,
  ToolBarType,
  BackgroundColor,
} from "@capgo/inappbrowser";

// Default URL configuration
let testWebappUrl = "http://localhost:8000/index.php";

window.customElements.define(
  "capacitor-welcome",
  class extends HTMLElement {
    constructor() {
      super();

      SplashScreen.hide();

      const root = this.attachShadow({ mode: "open" });

      root.innerHTML = `
    <style>
      :host {
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        display: block;
        width: 100%;
        height: 100%;
      }
      h1, h2, h3, h4, h5 {
        text-transform: uppercase;
      }
      .button {
        display: inline-block;
        padding: 10px;
        background-color: #73B5F6;
        color: #fff;
        font-size: 0.9em;
        border: 0;
        border-radius: 3px;
        text-decoration: none;
        cursor: pointer;
      }
      main {
        padding: 15px;
      }
      main hr { height: 1px; background-color: #eee; border: 0; }
      main h1 {
        font-size: 1.4em;
        text-transform: uppercase;
        letter-spacing: 1px;
      }
      main h2 {
        font-size: 1.1em;
      }
      main h3 {
        font-size: 0.9em;
      }
      main p {
        color: #333;
      }
      main pre {
        white-space: pre-line;
      }
    </style>
    <div>
      <capacitor-welcome-titlebar>
        <h1>InAppBrowser Test App</h1>
      </capacitor-welcome-titlebar>
      <main>
        <p>
          This app is designed to test the Capacitor InAppBrowser plugin, specifically to reproduce and debug back button navigation issues.
        </p>
        <h2>Custom URL</h2>
        <p>
          Enter a URL to open in the in-app browser.
        </p>
        <p style="display: flex; gap: 8px; align-items: center;">
          <input type="text" id="custom-url-input" value="https://google.com" placeholder="https://example.com" style="flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 3px; margin-bottom: 10px; font-size: 0.9em; box-sizing: border-box;" />
          <button id="clear-url-button" style="background-color: #dc3545; color: white; border: none; border-radius: 3px; padding: 8px 12px; cursor: pointer; font-size: 0.9em; margin-bottom: 10px;">🗑️</button>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="prevent-deeplink-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Prevent Deeplinks (block external app opening)</span>
          </label>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="spoof-firebase-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Spoof Firebase (inject Service Worker polyfill)</span>
          </label>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="enable-google-pay-toggle" style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Enable Google Pay Support</span>
          <label style="display: block; font-size: 0.9em; margin-bottom: 5px;">
            <span>Toolbar Type:</span>
          </label>
          <select id="toolbar-type-select" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 3px; font-size: 0.9em; box-sizing: border-box;">
            <option value="navigation">Navigation (back/forward/reload)</option>
            <option value="activity">Activity (close/share)</option>
            <option value="compact">Compact (close only)</option>
            <option value="blank">Blank (no toolbar)</option>
          </select>
        </p>
        <p style="margin-bottom: 10px;">
          <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9em;">
            <input type="checkbox" id="native-navigation-gestures-toggle" checked style="width: 18px; height: 18px; cursor: pointer;" />
            <span>Native Navigation Gestures (swipe left/right)</span>
          </label>
        </p>
        <p>
          <button class="button" id="open-custom-url" style="background-color: #007bff;">Open Custom URL</button>
        </p>
        <hr />
        <h2>In-App Browser Demo</h2>
        <p>
          Open the Capacitor InAppBrowser plugin documentation in an in-app browser.
        </p>
        <p>
          <button class="button" id="open-browser">Open In-App Browser</button>
        </p>
        <p>
          <button class="button" id="open-browser-with-blocked-host">Open In-App Browser in blocked host</button>
        </p>
        <h2>Back Button Test</h2>
        <p>
          Test the back button issue with our custom test webapp. This opens a PHP webapp designed to reproduce navigation issues.
        </p>
        <p>
          <button class="button" id="open-test-webapp" style="background-color: #28a745;">🧪 Open Test Webapp (Navigation Mode)</button>
        </p>
        <p>
          <button class="button" id="open-test-webapp-activity" style="background-color: #ffc107; color: #212529;">🧪 Open Test Webapp (Activity Mode)</button>
        </p>
        <div id="webapp-status" style="margin-top: 10px; padding: 10px; background-color: #f8f9fa; border-radius: 5px; font-size: 0.8em; color: #666;">
          <strong>Setup:</strong> Make sure to copy url.js.example to url.js and configure your local server URL.
        </div>
      </main>
    </div>
    `;
    }

    connectedCallback() {
      const self = this;

      // Helper function to validate URL
      function isValidUrl(string) {
        try {
          const url = new URL(string);
          return url.protocol === 'http:' || url.protocol === 'https:';
        } catch (_) {
          return false;
        }
      }

      // Custom URL handler
      self.shadowRoot
        .querySelector("#open-custom-url")
        .addEventListener("click", async function (e) {
          const input = self.shadowRoot.querySelector("#custom-url-input");
          const preventDeeplinkToggle = self.shadowRoot.querySelector("#prevent-deeplink-toggle");
          const spoofFirebaseToggle = self.shadowRoot.querySelector("#spoof-firebase-toggle");
          const enableGooglePayToggle = self.shadowRoot.querySelector("#enable-google-pay-toggle");
          const preventDeeplink = preventDeeplinkToggle.checked;
          const spoofFirebase = spoofFirebaseToggle.checked;
          const enableGooglePay = enableGooglePayToggle.checked;
          const toolbarTypeSelect = self.shadowRoot.querySelector("#toolbar-type-select");
          const nativeNavigationGesturesToggle = self.shadowRoot.querySelector("#native-navigation-gestures-toggle");
          const url = input.value.trim();
          const preventDeeplink = preventDeeplinkToggle.checked;
          const spoofFirebase = spoofFirebaseToggle.checked;
          const toolbarType = toolbarTypeSelect.value;
          const nativeNavigationGestures = nativeNavigationGesturesToggle.checked;
          
          if (!url) {
            alert("Please enter a URL");
            return;
          }
          
          // Auto-prepend https:// if no protocol is specified
          let urlToOpen = url;
          if (!url.startsWith('http://') && !url.startsWith('https://')) {
            urlToOpen = 'https://' + url;
          }
          
          if (!isValidUrl(urlToOpen)) {
            alert("Please enter a valid URL (e.g., https://example.com)");
            return;
          }
          
          // Firebase polyfill script
          const firebasePolyfill = `
            (function() {
              console.log('[InAppBrowser] Injecting comprehensive Firebase Messaging polyfill');
              
              // Override browser detection first
              Object.defineProperty(navigator, 'userAgent', {
                get: function() {
                  return 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
                }
              });
              
              // Create a mock ServiceWorkerRegistration
              const mockRegistration = {
                active: null,
                installing: null,
                waiting: null,
                scope: '/',
                update: function() { return Promise.resolve(); },
                unregister: function() { return Promise.resolve(true); },
                pushManager: {
                  subscribe: function() { return Promise.resolve({ endpoint: '', keys: {} }); },
                  getSubscription: function() { return Promise.resolve(null); },
                  permissionState: function() { return Promise.resolve('granted'); }
                }
              };
              
              // Polyfill for navigator.serviceWorker
              if (!window.navigator.serviceWorker) {
                console.log('[InAppBrowser] Service Worker not available natively');
                
                window.navigator.serviceWorker = {
                  register: function(scriptURL, options) {
                    console.log('[InAppBrowser] Service Worker registration attempted:', scriptURL);
                    return Promise.resolve(mockRegistration);
                  },
                  getRegistration: function() { return Promise.resolve(mockRegistration); },
                  getRegistrations: function() { return Promise.resolve([mockRegistration]); },
                  ready: Promise.resolve(mockRegistration),
                  controller: null,
                  addEventListener: function() {},
                  removeEventListener: function() {}
                };
              }
              
              // Polyfill for window.ServiceWorker
              if (!window.ServiceWorker) {
                window.ServiceWorker = function() {};
                window.ServiceWorker.state = 'activated';
              }
              
              // Polyfill for Notification API
              if (!window.Notification) {
                window.Notification = function(title, options) {
                  console.log('[InAppBrowser] Notification created:', title);
                  this.title = title;
                  this.body = options?.body || '';
                  this.icon = options?.icon || '';
                  this.tag = options?.tag || '';
                  this.data = options?.data || {};
                  this.requireInteraction = options?.requireInteraction || false;
                  this.silent = options?.silent || false;
                  this.timestamp = Date.now();
                };
                window.Notification.permission = 'granted';
                window.Notification.requestPermission = function() {
                  return Promise.resolve('granted');
                };
                window.Notification.prototype.close = function() {
                  console.log('[InAppBrowser] Notification closed');
                };
                window.Notification.prototype.addEventListener = function() {};
                window.Notification.prototype.removeEventListener = function() {};
              }
              
              // Polyfill for PushManager
              if (!window.PushManager) {
                window.PushManager = function() {};
              }
              
              // Polyfill for BackgroundSyncManager
              if (!self.sync || !self.registration) {
                if (!self.sync) {
                  self.sync = {
                    register: function() { return Promise.resolve(); },
                    getTags: function() { return Promise.resolve([]); }
                  };
                }
                if (!self.registration) {
                  self.registration = mockRegistration;
                }
              }
              
              console.log('[InAppBrowser] Firebase polyfill injection complete');
            })();
          `;
          
          const options = {
            url: urlToOpen,
            toolbarColor: "#007bff",
            toolbarType: toolbarType,
            backgroundColor: BackgroundColor.WHITE,
            title: "Custom URL",
            showReloadButton: toolbarType === 'navigation',
            visibleTitle: true,
            enabledSafeBottomMargin: true,
            preventDeeplink: preventDeeplink,
            enableGooglePaySupport: enableGooglePay,
            activeNativeNavigationForWebview: nativeNavigationGestures,
          };
          
          // Add Firebase spoofing if enabled
          if (spoofFirebase) {
            options.isPresentAfterPageLoad = true;
            options.preShowScript = firebasePolyfill;
            options.preShowScriptInjectionTime = 'documentStart';
          }
          
          try {
            await InAppBrowser.openWebView(options);

            // Add event listeners after opening the browser
            InAppBrowser.addListener("urlChangeEvent", (result) => {
              console.log("URL changed:", result.url);
            });

            InAppBrowser.addListener("closeEvent", () => {
              console.log("Close button pressed");
            });

            InAppBrowser.addListener("browserPageLoaded", () => {
              console.log("Page loaded");
            });

            InAppBrowser.addListener("pageLoadError", () => {
              console.log("Page load error");
            });
          } catch (e) {
            console.error("Error opening custom URL:", e);
            alert("Error opening URL. Please check the URL and try again.");
          }
        });

      // Add Enter key support for the input field
      self.shadowRoot
        .querySelector("#custom-url-input")
        .addEventListener("keypress", async function (e) {
          if (e.key === 'Enter') {
            const button = self.shadowRoot.querySelector("#open-custom-url");
            button.click();
          }
        });

      // Add clear button handler
      self.shadowRoot
        .querySelector("#clear-url-button")
        .addEventListener("click", function (e) {
          const input = self.shadowRoot.querySelector("#custom-url-input");
          input.value = '';
          input.focus();
        });

      self.shadowRoot
        .querySelector("#open-browser")
        .addEventListener("click", async function (e) {
          try {
            await InAppBrowser.openWebView({
              url: "https://github.com/Cap-go/capacitor-inappbrowser",
              toolbarColor: "#000000",
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.BLACK,
              title: "Capacitor InAppBrowser",
              enabledSafeBottomMargin: true,
            });

            // Add event listeners after opening the browser
            InAppBrowser.addListener("urlChange", (result) => {
              console.log("URL changed:", result.url);
            });

            InAppBrowser.addListener("closePressed", () => {
              console.log("Close button pressed");
            });

            InAppBrowser.addListener("sharePressed", () => {
              console.log("Share button pressed");
            });
          } catch (e) {
            console.error("Error opening in-app browser:", e);
          }
        });

      self.shadowRoot
        .querySelector("#open-browser-with-blocked-host")
        .addEventListener("click", async function (e) {
          try {
            await InAppBrowser.openWebView({
              url: "https://github.com/Cap-go/capacitor-inappbrowser",
              toolbarColor: "#000000",
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.BLACK,
              title: "Capacitor InAppBrowser, blocked GitHub host",
              enabledSafeBottomMargin: true,
              blockedHosts: ["github.com"],
            });

            // Add event listeners after opening the browser
            InAppBrowser.addListener("urlChange", (result) => {
              console.log("URL changed:", result.url);
            });

            InAppBrowser.addListener("closePressed", () => {
              console.log("Close button pressed");
            });

            InAppBrowser.addListener("sharePressed", () => {
              console.log("Share button pressed");
            });
          } catch (e) {
            console.error("Error opening in-app browser:", e);
          }
        });

      // Test webapp with navigation toolbar (main test for back button issue)
      self.shadowRoot
        .querySelector("#open-test-webapp")
        .addEventListener("click", async function (e) {
          try {
            // Try to load custom URL configuration
            let urlToUse = testWebappUrl;
            try {
              const { url } = await import("./url.js");
              urlToUse = url;
            } catch (e) {
              console.warn(
                "url.js not found, using default URL. Copy url.js.example to url.js and configure your server URL.",
              );
            }

            await InAppBrowser.openWebView({
              url: urlToUse,
              toolbarColor: "#ffffff",
              toolbarTextColor: "#000000",
              toolbarType: ToolBarType.NAVIGATION,
              backgroundColor: BackgroundColor.WHITE,
              title: "Back Button Test - Navigation Mode",
              showReloadButton: true,
              visibleTitle: true,
              showArrow: false,
            });

            // Add comprehensive event listeners for debugging
            InAppBrowser.addListener("urlChangeEvent", (result) => {
              console.log("🔄 URL changed:", result.url);
            });

            InAppBrowser.addListener("closeEvent", () => {
              console.log("❌ Close button pressed");
            });

            InAppBrowser.addListener("browserPageLoaded", () => {
              console.log("✅ Page loaded");
            });

            InAppBrowser.addListener("pageLoadError", () => {
              console.log("❌ Page load error");
            });

            InAppBrowser.addListener("messageFromWebview", (event) => {
              console.log("💬 Message from webview:", event.detail);
            });
          } catch (e) {
            console.error("Error opening test webapp:", e);
            alert(
              "Error opening test webapp. Make sure your local server is running and url.js is configured correctly.",
            );
          }
        });

      // Test webapp with activity toolbar (comparison test)
      self.shadowRoot
        .querySelector("#open-test-webapp-activity")
        .addEventListener("click", async function (e) {
          try {
            // Try to load custom URL configuration
            let urlToUse = testWebappUrl;
            try {
              const { url } = await import("./url.js");
              urlToUse = url;
            } catch (e) {
              console.warn(
                "url.js not found, using default URL. Copy url.js.example to url.js and configure your server URL.",
              );
            }

            await InAppBrowser.openWebView({
              url: urlToUse,
              toolbarColor: "#ffc107",
              toolbarTextColor: "#212529",
              toolbarType: ToolBarType.ACTIVITY,
              backgroundColor: BackgroundColor.WHITE,
              title: "Back Button Test - Activity Mode",
              showReloadButton: false,
              visibleTitle: true,
              showArrow: true,
            });

            // Add event listeners for comparison
            InAppBrowser.addListener("urlChangeEvent", (result) => {
              console.log("🔄 [Activity Mode] URL changed:", result.url);
            });

            InAppBrowser.addListener("closeEvent", () => {
              console.log("❌ [Activity Mode] Close button pressed");
            });
          } catch (e) {
            console.error("Error opening test webapp in activity mode:", e);
            alert(
              "Error opening test webapp. Make sure your local server is running and url.js is configured correctly.",
            );
          }
        });
    }
  },
);

window.customElements.define(
  "capacitor-welcome-titlebar",
  class extends HTMLElement {
    constructor() {
      super();
      const root = this.attachShadow({ mode: "open" });
      root.innerHTML = `
    <style>
      :host {
        position: relative;
        display: block;
        padding: 15px 15px 15px 15px;
        text-align: center;
        background-color: #73B5F6;
      }
      ::slotted(h1) {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol";
        font-size: 0.9em;
        font-weight: 600;
        color: #fff;
      }
    </style>
    <slot></slot>
    `;
    }
  },
);
