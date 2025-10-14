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
              console.warn("url.js not found, using default URL. Copy url.js.example to url.js and configure your server URL.");
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
            alert("Error opening test webapp. Make sure your local server is running and url.js is configured correctly.");
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
              console.warn("url.js not found, using default URL. Copy url.js.example to url.js and configure your server URL.");
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
            alert("Error opening test webapp. Make sure your local server is running and url.js is configured correctly.");
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
