var capacitorInAppBrowser = (function (exports, core) {
    'use strict';

    exports.BackgroundColor = void 0;
    (function (BackgroundColor) {
        BackgroundColor["WHITE"] = "white";
        BackgroundColor["BLACK"] = "black";
    })(exports.BackgroundColor || (exports.BackgroundColor = {}));
    exports.ToolBarType = void 0;
    (function (ToolBarType) {
        /**
         * Shows a simple toolbar with just a close button and share button
         * @since 0.1.0
         */
        ToolBarType["ACTIVITY"] = "activity";
        /**
         * Shows a full navigation toolbar with back/forward buttons
         * @since 0.1.0
         */
        ToolBarType["NAVIGATION"] = "navigation";
        /**
         * Shows no toolbar
         * @since 0.1.0
         */
        ToolBarType["BLANK"] = "blank";
    })(exports.ToolBarType || (exports.ToolBarType = {}));

    const InAppBrowser = core.registerPlugin("InAppBrowser", {
        web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.InAppBrowserWeb()),
    });

    class InAppBrowserWeb extends core.WebPlugin {
        clearAllCookies() {
            console.log("clearAllCookies");
            return Promise.resolve();
        }
        clearCache() {
            console.log("clearCache");
            return Promise.resolve();
        }
        async open(options) {
            console.log("open", options);
            return options;
        }
        async clearCookies(options) {
            console.log("cleanCookies", options);
            return;
        }
        async getCookies(options) {
            // Web implementation to get cookies
            return options;
        }
        async openWebView(options) {
            console.log("openWebView", options);
            return options;
        }
        async executeScript({ code }) {
            console.log("code", code);
            return code;
        }
        async close() {
            console.log("close");
            return;
        }
        async setUrl(options) {
            console.log("setUrl", options.url);
            return;
        }
        async reload() {
            console.log("reload");
            return;
        }
        async postMessage(options) {
            console.log("postMessage", options);
            return options;
        }
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        InAppBrowserWeb: InAppBrowserWeb
    });

    exports.InAppBrowser = InAppBrowser;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
