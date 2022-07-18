import type { PluginListenerHandle } from '@capacitor/core';

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

export enum ToolBarType {
  ACTIVITY = "activity",
  NAVIGATION = "navigation",
  BLANK = "blank",
  DEFAULT = ""
}

export interface Headers {
  [key: string] : string;
}

export interface OpenOptions {
  url: string;
  headers?: Headers;
  isPresentAfterPageLoad?: boolean;
}

export interface DisclaimerOptions {
  title: string;
  message: string;
  confirmBtn: string;
  cancelBtn: string;
}

export interface OpenWebViewOptions {
  url: string;
  headers?: Headers;
  shareDisclaimer?: DisclaimerOptions;
  toolbarType?: ToolBarType;
  shareSubject?: string;
  title: string;
  isPresentAfterPageLoad?: boolean;
}

// CapBrowser.addListener("urlChangeEvent", (info:  any) => {
//   console.log(info.url)
// })

// CapBrowser.addListener("confirmBtnClicked", (info:  any) => {
//   // will be triggered when user clicks on confirm button when disclaimer is required, works only on iOS
//   console.log(info.url)
// })
export interface InAppBrowserPlugin {
  open(options: OpenOptions): Promise<any>;
  close(): Promise<any>;
  openWebView(options: OpenWebViewOptions): Promise<any>;
  setUrl(options: {url: string}): Promise<any>;
  /**
   * Listen for url change 
   *
   * @since 0.0.1
   */
  addListener(
    eventName: 'urlChangeEvent',
    listenerFunc: UrlChangeListener,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  /**
   * Will be triggered when user clicks on confirm button when disclaimer is required, works only on iOS
   *
   * @since 0.0.1
   */
  addListener(
    eventName: 'confirmBtnClicked',
    listenerFunc: ConfirmBtnListener,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}
