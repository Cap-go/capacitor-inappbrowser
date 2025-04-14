import type { PluginListenerHandle } from "@capacitor/core";
export interface UrlEvent {
    /**
     * Emit when the url changes
     *
     * @since 0.0.1
     */
    url: string;
}
export interface BtnEvent {
    /**
     * Emit when a button is clicked.
     *
     * @since 0.0.1
     */
    url: string;
}
export type UrlChangeListener = (state: UrlEvent) => void;
export type ConfirmBtnListener = (state: BtnEvent) => void;
export type ButtonNearListener = (state: object) => void;
export declare enum BackgroundColor {
    WHITE = "white",
    BLACK = "black"
}
export declare enum ToolBarType {
    /**
     * Shows a simple toolbar with just a close button and share button
     * @since 0.1.0
     */
    ACTIVITY = "activity",
    /**
     * Shows a full navigation toolbar with back/forward buttons
     * @since 0.1.0
     */
    NAVIGATION = "navigation",
    /**
     * Shows no toolbar
     * @since 0.1.0
     */
    BLANK = "blank"
}
export interface Headers {
    [key: string]: string;
}
export interface GetCookieOptions {
    url: string;
    includeHttpOnly?: boolean;
}
export interface ClearCookieOptions {
    url: string;
}
export interface Credentials {
    username: string;
    password: string;
}
export interface OpenOptions {
    /**
     * Target URL to load.
     * @since 0.1.0
     */
    url: string;
    /**
     * if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.
     * @since 0.1.0
     */
    isPresentAfterPageLoad?: boolean;
    /**
     * if true the deeplink will not be opened, if false the deeplink will be opened when clicked on the link
     * @since 0.1.0
     */
    preventDeeplink?: boolean;
}
export interface DisclaimerOptions {
    /**
     * Title of the disclaimer dialog
     * @default "Title"
     */
    title: string;
    /**
     * Message shown in the disclaimer dialog
     * @default "Message"
     */
    message: string;
    /**
     * Text for the confirm button
     * @default "Confirm"
     */
    confirmBtn: string;
    /**
     * Text for the cancel button
     * @default "Cancel"
     */
    cancelBtn: string;
}
export interface OpenWebViewOptions {
    /**
     * Target URL to load.
     * @since 0.1.0
     * @example "https://capgo.app"
     */
    url: string;
    /**
     * Headers to send with the request.
     * @since 0.1.0
     * @example
     * headers: {
     *   'Custom-Header': 'test-value',
     *   'Authorization': 'Bearer test-token'
     * }
     * Test URL: https://www.whatismybrowser.com/detect/what-http-headers-is-my-browser-sending/
     */
    headers?: Headers;
    /**
     * Credentials to send with the request and all subsequent requests for the same host.
     * @since 6.1.0
     * @example
     * credentials: {
     *   username: 'test-user',
     *   password: 'test-pass'
     * }
     * Test URL: https://www.whatismybrowser.com/detect/what-http-headers-is-my-browser-sending/
     */
    credentials?: Credentials;
    /**
     * materialPicker: if true, uses Material Design theme for date and time pickers on Android.
     * This improves the appearance of HTML date inputs to use modern Material Design UI instead of the old style pickers.
     * @since 7.4.1
     * @default false
     * @example
     * materialPicker: true
     * Test URL: https://show-picker.glitch.me/demo.html
     */
    materialPicker?: boolean;
    /**
     * JavaScript Interface:
     * The webview automatically injects a JavaScript interface providing:
     * - `window.mobileApp.close()`: Closes the webview from JavaScript
     * - `window.mobileApp.postMessage(obj)`: Sends a message to the app (listen via "messageFromWebview" event)
     *
     * @example
     * // In your webpage loaded in the webview:
     * document.getElementById('closeBtn').addEventListener('click', () => {
     *   window.mobileApp.close();
     * });
     *
     * // Send data to the app
     * window.mobileApp.postMessage({ action: 'login', data: { user: 'test' }});
     *
     * @since 6.10.0
     */
    jsInterface?: never;
    /**
     * Share options for the webview. When provided, shows a disclaimer dialog before sharing content.
     * This is useful for:
     * - Warning users about sharing sensitive information
     * - Getting user consent before sharing
     * - Explaining what will be shared
     * - Complying with privacy regulations
     *
     * Note: shareSubject is required when using shareDisclaimer
     * @since 0.1.0
     * @example
     * shareDisclaimer: {
     *   title: 'Disclaimer',
     *   message: 'This is a test disclaimer',
     *   confirmBtn: 'Accept',
     *   cancelBtn: 'Decline'
     * }
     * Test URL: https://capgo.app
     */
    shareDisclaimer?: DisclaimerOptions;
    /**
     * Toolbar type determines the appearance and behavior of the browser's toolbar
     * - "activity": Shows a simple toolbar with just a close button and share button
     * - "navigation": Shows a full navigation toolbar with back/forward buttons
     * - "blank": Shows no toolbar
     * - "": Default toolbar with close button
     * @since 0.1.0
     * @default ToolBarType.DEFAULT
     * @example
     * toolbarType: ToolBarType.ACTIVITY,
     * title: 'Activity Toolbar Test'
     * Test URL: https://capgo.app
     */
    toolbarType?: ToolBarType;
    /**
     * Subject text for sharing. Required when using shareDisclaimer.
     * This text will be used as the subject line when sharing content.
     * @since 0.1.0
     * @example "Share this page"
     */
    shareSubject?: string;
    /**
     * Title of the browser
     * @since 0.1.0
     * @default 'New Window'
     * @example "Camera Test"
     */
    title?: string;
    /**
     * Background color of the browser
     * @since 0.1.0
     * @default BackgroundColor.BLACK
     */
    backgroundColor?: BackgroundColor;
    /**
     * If true, active the native navigation within the webview, Android only
     * @default false
     * @example
     * activeNativeNavigationForWebview: true,
     * disableGoBackOnNativeApplication: true
     * Test URL: https://capgo.app
     */
    activeNativeNavigationForWebview?: boolean;
    /**
     * Disable the possibility to go back on native application,
     * useful to force user to stay on the webview, Android only
     * @default false
     * @example
     * disableGoBackOnNativeApplication: true
     * Test URL: https://capgo.app
     */
    disableGoBackOnNativeApplication?: boolean;
    /**
     * Open url in a new window fullscreen
     * isPresentAfterPageLoad: if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.
     * @since 0.1.0
     * @default false
     * @example
     * isPresentAfterPageLoad: true,
     * preShowScript: "await import('https://unpkg.com/darkreader@4.9.89/darkreader.js');\nDarkReader.enable({ brightness: 100, contrast: 90, sepia: 10 });"
     * Test URL: https://capgo.app
     */
    isPresentAfterPageLoad?: boolean;
    /**
     * Whether the website in the webview is inspectable or not, ios only
     * @default false
     */
    isInspectable?: boolean;
    /**
     * Whether the webview opening is animated or not, ios only
     * @default true
     */
    isAnimated?: boolean;
    /**
     * Shows a reload button that reloads the web page
     * @since 1.0.15
     * @default false
     * @example
     * showReloadButton: true
     * Test URL: https://capgo.app
     */
    showReloadButton?: boolean;
    /**
     * CloseModal: if true a confirm will be displayed when user clicks on close button, if false the browser will be closed immediately.
     * @since 1.1.0
     * @default false
     * @example
     * closeModal: true,
     * closeModalTitle: 'Close Window',
     * closeModalDescription: 'Are you sure you want to close?',
     * closeModalOk: 'Yes, close',
     * closeModalCancel: 'No, stay'
     * Test URL: https://capgo.app
     */
    closeModal?: boolean;
    /**
     * CloseModalTitle: title of the confirm when user clicks on close button
     * @since 1.1.0
     * @default 'Close'
     */
    closeModalTitle?: string;
    /**
     * CloseModalDescription: description of the confirm when user clicks on close button
     * @since 1.1.0
     * @default 'Are you sure you want to close this window?'
     */
    closeModalDescription?: string;
    /**
     * CloseModalOk: text of the confirm button when user clicks on close button
     * @since 1.1.0
     * @default 'Close'
     */
    closeModalOk?: string;
    /**
     * CloseModalCancel: text of the cancel button when user clicks on close button
     * @since 1.1.0
     * @default 'Cancel'
     */
    closeModalCancel?: string;
    /**
     * visibleTitle: if true the website title would be shown else shown empty
     * @since 1.2.5
     * @default true
     */
    visibleTitle?: boolean;
    /**
     * toolbarColor: color of the toolbar in hex format
     * @since 1.2.5
     * @default '#ffffff'
     * @example
     * toolbarColor: '#FF5733'
     * Test URL: https://capgo.app
     */
    toolbarColor?: string;
    /**
     * toolbarTextColor: color of the buttons and title in the toolbar in hex format
     * When set, it overrides the automatic light/dark mode detection for text color
     * @since 6.10.0
     * @default calculated based on toolbarColor brightness
     * @example
     * toolbarTextColor: '#FFFFFF'
     * Test URL: https://capgo.app
     */
    toolbarTextColor?: string;
    /**
     * showArrow: if true an arrow would be shown instead of cross for closing the window
     * @since 1.2.5
     * @default false
     * @example
     * showArrow: true
     * Test URL: https://capgo.app
     */
    showArrow?: boolean;
    /**
     * ignoreUntrustedSSLError: if true, the webview will ignore untrusted SSL errors allowing the user to view the website.
     * @since 6.1.0
     * @default false
     */
    ignoreUntrustedSSLError?: boolean;
    /**
     * preShowScript: if isPresentAfterPageLoad is true and this variable is set the plugin will inject a script before showing the browser.
     * This script will be run in an async context. The plugin will wait for the script to finish (max 10 seconds)
     * @since 6.6.0
     * @example
     * preShowScript: "await import('https://unpkg.com/darkreader@4.9.89/darkreader.js');\nDarkReader.enable({ brightness: 100, contrast: 90, sepia: 10 });"
     * Test URL: https://capgo.app
     */
    preShowScript?: string;
    /**
     * proxyRequests is a regex expression. Please see [this pr](https://github.com/Cap-go/capacitor-inappbrowser/pull/222) for more info. (Android only)
     * @since 6.9.0
     */
    proxyRequests?: string;
    /**
     * buttonNearDone allows for a creation of a custom button near the done/close button.
     * The button is only shown when toolbarType is not "activity", "navigation", or "blank".
     *
     * For Android:
     * - iconType must be "asset"
     * - icon path should be in the public folder (e.g. "monkey.svg")
     * - width and height are optional, defaults to 48dp
     * - button is positioned at the end of toolbar with 8dp margin
     *
     * For iOS:
     * - iconType can be "sf-symbol" or "asset"
     * - for sf-symbol, icon should be the symbol name
     * - for asset, icon should be the asset name
     * @since 6.7.0
     * @example
     * buttonNearDone: {
     *   ios: {
     *     iconType: 'sf-symbol',
     *     icon: 'star.fill'
     *   },
     *   android: {
     *     iconType: 'asset',
     *     icon: 'public/monkey.svg',
     *     width: 24,
     *     height: 24
     *   }
     * }
     * Test URL: https://capgo.app
     */
    buttonNearDone?: {
        ios: {
            iconType: "sf-symbol" | "asset";
            icon: string;
        };
        android: {
            iconType: "asset" | "vector";
            icon: string;
            width?: number;
            height?: number;
        };
    };
    /**
     * textZoom: sets the text zoom of the page in percent.
     * Allows users to increase or decrease the text size for better readability.
     * @since 7.6.0
     * @default 100
     * @example
     * textZoom: 120
     * Test URL: https://capgo.app
     */
    textZoom?: number;
    /**
     * preventDeeplink: if true, the deeplink will not be opened, if false the deeplink will be opened when clicked on the link. on IOS each schema need to be added to info.plist file under LSApplicationQueriesSchemes when false to make it work.
     * @since 0.1.0
     * @default false
     * @example
     * preventDeeplink: true
     * Test URL: https://aasa-tester.capgo.app/
     */
    preventDeeplink?: boolean;
}
export interface InAppBrowserPlugin {
    /**
     * Open url in a new window fullscreen, on android it use chrome custom tabs, on ios it use SFSafariViewController
     *
     * @since 0.1.0
     */
    open(options: OpenOptions): Promise<any>;
    /**
     * Clear cookies of url
     *
     * @since 0.5.0
     */
    clearCookies(options: ClearCookieOptions): Promise<any>;
    /**
     * Clear all cookies
     *
     * @since 6.5.0
     */
    clearAllCookies(): Promise<any>;
    /**
     * Clear cache
     *
     * @since 6.5.0
     */
    clearCache(): Promise<any>;
    /**
     * Get cookies for a specific URL.
     * @param options The options, including the URL to get cookies for.
     * @returns A promise that resolves with the cookies.
     */
    getCookies(options: GetCookieOptions): Promise<Record<string, string>>;
    /**
     * Close the webview.
     */
    close(): Promise<any>;
    /**
     * Open url in a new webview with toolbars, and enhanced capabilities, like camera access, file access, listen events, inject javascript, bi directional communication, etc.
     *
     * JavaScript Interface:
     * When you open a webview with this method, a JavaScript interface is automatically injected that provides:
     * - `window.mobileApp.close()`: Closes the webview from JavaScript
     * - `window.mobileApp.postMessage({detail: {message: 'myMessage'}})`: Sends a message from the webview to the app, detail object is the data you want to send to the webview
     *
     * @since 0.1.0
     */
    openWebView(options: OpenWebViewOptions): Promise<any>;
    /**
     * Injects JavaScript code into the InAppBrowser window.
     */
    executeScript({ code }: {
        code: string;
    }): Promise<void>;
    /**
     * Sends an event to the webview(inappbrowser). you can listen to this event in the inappbrowser JS with window.addEventListener("messageFromNative", listenerFunc: (event: Record<string, any>) => void)
     * detail is the data you want to send to the webview, it's a requirement of Capacitor we cannot send direct objects
     * Your object has to be serializable to JSON, so no functions or other non-JSON-serializable types are allowed.
     */
    postMessage(options: {
        detail: Record<string, any>;
    }): Promise<void>;
    /**
     * Sets the URL of the webview.
     */
    setUrl(options: {
        url: string;
    }): Promise<any>;
    /**
     * Listen for url change, only for openWebView
     *
     * @since 0.0.1
     */
    addListener(eventName: "urlChangeEvent", listenerFunc: UrlChangeListener): Promise<PluginListenerHandle>;
    addListener(eventName: "buttonNearDoneClick", listenerFunc: ButtonNearListener): Promise<PluginListenerHandle>;
    /**
     * Listen for close click only for openWebView
     *
     * @since 0.4.0
     */
    addListener(eventName: "closeEvent", listenerFunc: UrlChangeListener): Promise<PluginListenerHandle>;
    /**
     * Will be triggered when user clicks on confirm button when disclaimer is required
     *
     * @since 0.0.1
     */
    addListener(eventName: "confirmBtnClicked", listenerFunc: ConfirmBtnListener): Promise<PluginListenerHandle>;
    /**
     * Will be triggered when event is sent from webview(inappbrowser), to send an event to the main app use window.mobileApp.postMessage({ "detail": { "message": "myMessage" } })
     * detail is the data you want to send to the main app, it's a requirement of Capacitor we cannot send direct objects
     * Your object has to be serializable to JSON, no functions or other non-JSON-serializable types are allowed.
     *
     * This method is inject at runtime in the webview
     */
    addListener(eventName: "messageFromWebview", listenerFunc: (event: {
        detail: Record<string, any>;
    }) => void): Promise<PluginListenerHandle>;
    /**
     * Will be triggered when page is loaded
     */
    addListener(eventName: "browserPageLoaded", listenerFunc: () => void): Promise<PluginListenerHandle>;
    /**
     * Will be triggered when page load error
     */
    addListener(eventName: "pageLoadError", listenerFunc: () => void): Promise<PluginListenerHandle>;
    /**
     * Remove all listeners for this plugin.
     *
     * @since 1.0.0
     */
    removeAllListeners(): Promise<void>;
    /**
     * Reload the current web page.
     *
     * @since 1.0.0
     */
    reload(): Promise<any>;
}
/**
 * JavaScript APIs available in the InAppBrowser WebView.
 *
 * These APIs are automatically injected into all webpages loaded in the InAppBrowser WebView.
 *
 * @example
 * // Closing the webview from JavaScript
 * window.mobileApp.close();
 *
 * // Sending a message from webview to the native app
 * window.mobileApp.postMessage({ key: 'value' });
 *
 * @since 6.10.0
 */
export interface InAppBrowserWebViewAPIs {
    /**
     * mobileApp - Global object injected into the WebView providing communication with the native app
     */
    mobileApp: {
        /**
         * Close the WebView from JavaScript
         *
         * @example
         * // Add a button to close the webview
         * const closeButton = document.createElement('button');
         * closeButton.textContent = 'Close WebView';
         * closeButton.addEventListener('click', () => {
         *   window.mobileApp.close();
         * });
         * document.body.appendChild(closeButton);
         *
         * @since 6.10.0
         */
        close(): void;
        /**
         * Send a message from the WebView to the native app
         * The native app can listen for these messages with the "messageFromWebview" event
         *
         * @param message Object to send to the native app
         * @example
         * // Send data to native app
         * window.mobileApp.postMessage({
         *   action: 'dataSubmitted',
         *   data: { username: 'test', email: 'test@example.com' }
         * });
         *
         * @since 6.10.0
         */
        postMessage(message: Record<string, any>): void;
    };
}
