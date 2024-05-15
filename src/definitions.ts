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

export enum BackgroundColor {
  WHITE = "white",
  BLACK = "black",
}
export enum ToolBarType {
  ACTIVITY = "activity",
  NAVIGATION = "navigation",
  BLANK = "blank",
  DEFAULT = "",
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
  cache?: boolean;
}

export interface OpenOptions {
  /**
   * Target URL to load.
   * @since 0.1.0
   */
  url: string;
  /**
   * Headers to send with the request.
   * @since 0.1.0
   */
  headers?: Headers;
  /**
   * if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.
   * @since 0.1.0
   */
  isPresentAfterPageLoad?: boolean;
  preventDeeplink?: boolean;
}

export interface DisclaimerOptions {
  title: string;
  message: string;
  confirmBtn: string;
  cancelBtn: string;
}

export interface OpenWebViewOptions {
  /**
   * Target URL to load.
   * @since 0.1.0
   */
  url: string;
  /**
   * Headers to send with the request.
   * @since 0.1.0
   */
  headers?: Headers;
  /**
   * share options
   * @since 0.1.0
   */
  shareDisclaimer?: DisclaimerOptions;
  /**
   * Toolbar type
   * @since 0.1.0
   * @default ToolBarType.DEFAULT
   */
  toolbarType?: ToolBarType;
  /**
   * Share subject
   * @since 0.1.0
   */
  shareSubject?: string;
  /**
   * Title of the browser
   * @since 0.1.0
   * @default 'New Window'
   */
  title?: string;
  /**
   * Background color of the browser, only on IOS
   * @since 0.1.0
   * @default BackgroundColor.BLACK
   */
  backgroundColor?: BackgroundColor;
  /**
   * If true, active the native navigation within the webview, Android only
   *
   * @default false
   */
  activeNativeNavigationForWebview?: boolean;
  /**
   * Disable the possibility to go back on native application,
   * usefull to force user to stay on the webview, Android only
   *
   * @default false
   */
  disableGoBackOnNativeApplication?: boolean;
  /**
   * Open url in a new window fullscreen
   *
   * isPresentAfterPageLoad: if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.
   * @since 0.1.0
   * @default false
   */
  isPresentAfterPageLoad?: boolean;
  /**
   * Whether the website in the webview is inspectable or not, ios only
   *
   * @default false
   */
  isInspectable?: boolean;
  /**
   * Whether the webview opening is animated or not, ios only
   *
   * @default true
   */
  isAnimated?: boolean;
  /**
   * Shows a reload button that reloads the web page
   * @since 1.0.15
   * @default false
   */
  showReloadButton?: boolean;
  /**
   * CloseModal: if true a confirm will be displayed when user clicks on close button, if false the browser will be closed immediately.
   *
   * @since 1.1.0
   * @default false
   */
  closeModal?: boolean;
  /**
   * CloseModalTitle: title of the confirm when user clicks on close button, only on IOS
   *
   * @since 1.1.0
   * @default 'Close'
   */
  closeModalTitle?: string;
  /**
   * CloseModalDescription: description of the confirm when user clicks on close button, only on IOS
   *
   * @since 1.1.0
   * @default 'Are you sure you want to close this window?'
   */
  closeModalDescription?: string;
  /**
   * CloseModalOk: text of the confirm button when user clicks on close button, only on IOS
   *
   * @since 1.1.0
   * @default 'Close'
   */
  closeModalOk?: string;
  /**
   * CloseModalCancel: text of the cancel button when user clicks on close button, only on IOS
   *
   * @since 1.1.0
   * @default 'Cancel'
   */
  closeModalCancel?: string;
  /**
   * visibleTitle: if true the website title would be shown else shown empty
   *
   * @since 1.2.5
   * @default true
   */
  visibleTitle?: boolean;
  /**
   * toolbarColor: color of the toolbar in hex format
   *
   * @since 1.2.5
   * @default '#ffffff''
   */
  toolbarColor?: string;
  /**
   * showArrow: if true an arrow would be shown instead of cross for closing the window
   *
   * @since 1.2.5
   * @default false
   */
  showArrow?: boolean;
  /**
   * ignoreUntrustedSSLError: if true, the webview will ignore untrusted SSL errors allowing the user to view the website.
   *
   * @since 6.1.0
   * @default false
   */
  ignoreUntrustedSSLError?: boolean;
}

export interface InAppBrowserPlugin {
  /**
   * Open url in a new window fullscreen
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
   * Get cookies for a specific URL.
   * @param options The options, including the URL to get cookies for.
   * @returns A promise that resolves with the cookies.
   */
  getCookies(options: GetCookieOptions): Promise<Record<string, string>>;

  close(): Promise<any>;
  /**
   * Open url in a new webview with toolbars
   *
   * @since 0.1.0
   */
  openWebView(options: OpenWebViewOptions): Promise<any>;
  /**
   * Injects JavaScript code into the InAppBrowser window.
   */
  executeScript({ code }: { code: string }): Promise<void>;
  setUrl(options: { url: string }): Promise<any>;
  /**
   * Listen for url change, only for openWebView
   *
   * @since 0.0.1
   */
  addListener(
    eventName: "urlChangeEvent",
    listenerFunc: UrlChangeListener,
  ): Promise<PluginListenerHandle>;

  /**
   * Listen for close click only for openWebView
   *
   * @since 0.4.0
   */
  addListener(
    eventName: "closeEvent",
    listenerFunc: UrlChangeListener,
  ): Promise<PluginListenerHandle>;
  /**
   * Will be triggered when user clicks on confirm button when disclaimer is required, works only on iOS
   *
   * @since 0.0.1
   */
  addListener(
    eventName: "confirmBtnClicked",
    listenerFunc: ConfirmBtnListener,
  ): Promise<PluginListenerHandle>;

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
  reload(): Promise<any>; // Add this line
}
