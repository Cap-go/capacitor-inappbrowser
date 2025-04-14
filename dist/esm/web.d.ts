import { WebPlugin } from "@capacitor/core";
import type { InAppBrowserPlugin, OpenWebViewOptions, OpenOptions, GetCookieOptions, ClearCookieOptions } from "./definitions";
export declare class InAppBrowserWeb extends WebPlugin implements InAppBrowserPlugin {
    clearAllCookies(): Promise<any>;
    clearCache(): Promise<any>;
    open(options: OpenOptions): Promise<any>;
    clearCookies(options: ClearCookieOptions): Promise<any>;
    getCookies(options: GetCookieOptions): Promise<any>;
    openWebView(options: OpenWebViewOptions): Promise<any>;
    executeScript({ code }: {
        code: string;
    }): Promise<any>;
    close(): Promise<any>;
    setUrl(options: {
        url: string;
    }): Promise<any>;
    reload(): Promise<any>;
    postMessage(options: Record<string, any>): Promise<any>;
}
