import { registerPlugin } from "@capacitor/core";
const InAppBrowser = registerPlugin("InAppBrowser", {
    web: () => import("./web").then((m) => new m.InAppBrowserWeb()),
});
export * from "./definitions";
export { InAppBrowser };
//# sourceMappingURL=index.js.map