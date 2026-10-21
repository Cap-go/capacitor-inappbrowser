import { WebPlugin } from '@capacitor/core';

import type {
  InAppBrowserPlugin,
  OpenWebViewOptions,
  OpenOptions,
  GetCookieOptions,
  ClearCookieOptions,
  BringToFrontOptions,
  DimensionOptions,
  DispatchInputEventOptions,
  OpenSecureWindowOptions,
  OpenSecureWindowResponse,
  ScreenshotResult,
} from './definitions';
import { buildConsoleCaptureScript, parseCapgoConsolePostMessage } from './web-console-capture';

type ConsoleCaptureWindow = Window & { __capgoInAppBrowserConsoleCaptureInstalled?: boolean };

type TrackedWebView = {
  window: Window;
  url: string;
  timer: number;
  /** Origin used as postMessage target. Updated from the last message the page sent (handles redirects). */
  origin: string;
  captureConsoleLogs: boolean;
  /** Child document where console capture hooks were last confirmed active. */
  consoleCaptureInstalledDocument?: Document;
  consoleCaptureTimer?: number;
};

const WEB_HEADERS_UNSUPPORTED_MESSAGE =
  '[InAppBrowser] Custom request headers are not supported on web and are ignored.';

/** Whether a webview message origin may replace the postMessage target (full origin string). */
export function mayAdoptPostMessageOrigin(openedUrl: string, messageOrigin: string): boolean {
  try {
    const base = globalThis.location?.href;
    const opened = base ? new URL(openedUrl, base) : new URL(openedUrl);
    const incoming = new URL(messageOrigin);
    if (opened.protocol !== incoming.protocol) {
      return false;
    }
    if (opened.port !== incoming.port) {
      return false;
    }
    if (opened.origin === incoming.origin) {
      return true;
    }
    const openedHost = opened.hostname;
    const incomingHost = incoming.hostname;
    return incomingHost === openedHost || incomingHost.endsWith(`.${openedHost}`);
  } catch {
    return false;
  }
}

export class InAppBrowserWeb extends WebPlugin implements InAppBrowserPlugin {
  private readonly webViews = new Map<string, TrackedWebView>();
  private webViewCounter = 0;

  constructor() {
    super();
    if (globalThis.window !== undefined) {
      globalThis.window.addEventListener('message', this.handleWindowMessage);
    }
  }

  private readonly handleWindowMessage = (event: MessageEvent): void => {
    const match = Array.from(this.webViews.entries()).find(([, entry]) => this.isFromWebView(entry, event.source));
    if (!match) {
      return;
    }
    const [id, entry] = match;
    if (
      entry.window === event.source &&
      event.origin &&
      event.origin !== 'null' &&
      mayAdoptPostMessageOrigin(entry.url, event.origin)
    ) {
      entry.origin = event.origin;
    }
    let data: unknown = event.data;
    if (typeof data === 'string') {
      try {
        data = JSON.parse(data);
      } catch {
        this.notifyListeners('messageFromWebview', { id, rawMessage: event.data });
        return;
      }
    }
    if (entry.captureConsoleLogs && event.source === entry.window) {
      const childWindow = entry.window as ConsoleCaptureWindow;
      if (
        this.isConsoleCaptureActive(childWindow) &&
        event.origin &&
        event.origin !== 'null' &&
        mayAdoptPostMessageOrigin(entry.url, event.origin)
      ) {
        const consolePayload = parseCapgoConsolePostMessage(data);
        if (consolePayload) {
          this.notifyListeners('consoleMessage', { id, ...consolePayload });
          return;
        }
      }
    }
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      this.notifyListeners('messageFromWebview', { ...(data as Record<string, unknown>), id });
    } else if (typeof event.data === 'string') {
      this.notifyListeners('messageFromWebview', { id, rawMessage: event.data });
    }
  };

  private isFromWebView(entry: TrackedWebView, source: MessageEventSource | null): boolean {
    return Boolean(source && entry.window === source);
  }

  private watchClosed(id: string): void {
    const entry = this.webViews.get(id);
    if (!entry?.window.closed) {
      return;
    }
    globalThis.clearInterval(entry.timer);
    if (entry.consoleCaptureTimer !== undefined) {
      globalThis.clearInterval(entry.consoleCaptureTimer);
    }
    this.webViews.delete(id);
    this.notifyListeners('closeEvent', { id, url: entry.url });
  }

  private childDocumentHref(entry: TrackedWebView): string | undefined {
    try {
      const href = entry.window.location.href;
      if (!href || href === 'about:blank') {
        return undefined;
      }
      return href;
    } catch {
      return undefined;
    }
  }

  private scheduleConsoleCapture(id: string): void {
    const entry = this.webViews.get(id);
    if (!entry?.captureConsoleLogs || entry.consoleCaptureTimer !== undefined) {
      return;
    }
    const tryInstall = (): void => {
      const current = this.webViews.get(id);
      if (!current || current.window.closed) {
        if (current?.consoleCaptureTimer !== undefined) {
          globalThis.clearInterval(current.consoleCaptureTimer);
          current.consoleCaptureTimer = undefined;
        }
        return;
      }
      if (!this.childDocumentHref(current)) {
        return;
      }
      const child = current.window as ConsoleCaptureWindow;
      const doc = child.document;
      if (this.isConsoleCaptureActive(child) && current.consoleCaptureInstalledDocument === doc) {
        return;
      }
      this.tryInstallConsoleCapture(current);
      if (this.isConsoleCaptureActive(child)) {
        current.consoleCaptureInstalledDocument = doc;
      }
    };
    tryInstall();
    entry.consoleCaptureTimer = globalThis.setInterval(tryInstall, 250);
  }

  private isConsoleCaptureActive(child: ConsoleCaptureWindow): boolean {
    return child.__capgoInAppBrowserConsoleCaptureInstalled === true;
  }

  private tryInstallConsoleCapture(entry: TrackedWebView): void {
    const child = entry.window;
    if (child.closed || !this.childDocumentHref(entry)) {
      return;
    }
    try {
      const script = buildConsoleCaptureScript();
      const doc = child.document;
      if (!doc) {
        return;
      }
      const target = doc.head ?? doc.documentElement;
      if (!target) {
        return;
      }
      const element = doc.createElement('script');
      element.text = script;
      target.appendChild(element);
      element.remove();
    } catch {
      return;
    }
  }

  private resolveWebViewEntry(id?: string): { id: string; entry: TrackedWebView } | undefined {
    if (id) {
      const entry = this.webViews.get(id);
      if (entry && !entry.window.closed) {
        return { id, entry };
      }
      return undefined;
    }
    const match = Array.from(this.webViews.entries())
      .reverse()
      .find(([, entry]) => !entry.window.closed);
    if (!match) {
      return undefined;
    }
    return { id: match[0], entry: match[1] };
  }

  private resolveTargetOrigin(url: string): string {
    try {
      return new URL(url).origin;
    } catch {
      return globalThis.location?.origin ?? 'null';
    }
  }

  private hasHeaders(headers?: Record<string, string>): headers is Record<string, string> {
    return headers != null && Object.keys(headers).length > 0;
  }

  private navigateOpenedWindow(win: Window, url: string, headers?: Record<string, string>): void {
    if (this.hasHeaders(headers)) {
      console.warn(WEB_HEADERS_UNSUPPORTED_MESSAGE);
    }
    win.location.href = url;
  }

  clearAllCookies(): Promise<any> {
    console.log('clearAllCookies');
    return Promise.resolve();
  }

  clearCache(): Promise<any> {
    console.log('clearCache');
    return Promise.resolve();
  }

  clearAllBrowsingData(): Promise<any> {
    console.log('clearAllBrowsingData');
    return Promise.resolve();
  }

  open(options: OpenOptions): Promise<any> {
    const opened = globalThis.open('', '_blank');
    if (!opened) {
      return Promise.reject(new Error('Popup blocked'));
    }
    opened.opener = null;
    opened.location.href = options.url;
    return Promise.resolve();
  }

  clearCookies(options: ClearCookieOptions): Promise<any> {
    console.log('cleanCookies', options);
    return Promise.resolve();
  }

  getCookies(options: GetCookieOptions): Promise<any> {
    return Promise.resolve(options);
  }

  openWebView(options: OpenWebViewOptions): Promise<any> {
    if (options.fullscreen) {
      return Promise.reject(this.unimplemented('Fullscreen is only supported by native openWebView presentations.'));
    }
    const { popup = false, width, height } = options.web ?? {};
    const features = popup
      ? ['popup=yes', width ? `width=${width}` : '', height ? `height=${height}` : ''].filter(Boolean).join(',')
      : '';
    const opened = globalThis.open('', '_blank', features);
    if (!opened) {
      return Promise.reject(new Error('Popup blocked'));
    }

    const id = `web-${++this.webViewCounter}`;
    const timer = globalThis.setInterval(() => this.watchClosed(id), 500);
    const origin = this.resolveTargetOrigin(options.url);
    const captureConsoleLogs = options.captureConsoleLogs === true;
    this.webViews.set(id, {
      window: opened,
      url: options.url,
      timer,
      origin,
      captureConsoleLogs,
    });
    this.navigateOpenedWindow(opened, options.url, options.headers);
    if (captureConsoleLogs) {
      this.scheduleConsoleCapture(id);
    }
    return Promise.resolve({ id });
  }

  executeScript({ code }: { code: string }): Promise<any> {
    console.log('code', code);
    return Promise.resolve(code);
  }

  close(options?: { id?: string }): Promise<any> {
    const resolved = this.resolveWebViewEntry(options?.id);
    if (!resolved) {
      return Promise.resolve();
    }
    resolved.entry.window.close();
    this.watchClosed(resolved.id);
    return Promise.resolve();
  }

  hide(_options?: { id?: string }): Promise<void> {
    console.log('hide', _options);
    return Promise.resolve();
  }

  show(_options?: { id?: string }): Promise<void> {
    console.log('show', _options);
    return Promise.resolve();
  }

  sendToBack(_options?: { id?: string; transparentBackground?: boolean }): Promise<void> {
    console.log('sendToBack not supported on web', _options);
    return Promise.resolve();
  }

  bringToFront(_options?: BringToFrontOptions): Promise<void> {
    console.log('bringToFront not supported on web', _options);
    return Promise.resolve();
  }

  dispatchInputEvent(_options: DispatchInputEventOptions): Promise<void> {
    console.log('dispatchInputEvent not supported on web', _options);
    return Promise.resolve();
  }

  setUrl(options: { url: string }): Promise<any> {
    console.log('setUrl', options.url);
    return Promise.resolve();
  }

  reload(options?: { id?: string }): Promise<any> {
    console.log('reload', options);
    return Promise.resolve();
  }

  postMessage(options: { detail: Record<string, any>; id?: string }): Promise<void> {
    const resolved = this.resolveWebViewEntry(options.id);
    if (resolved) {
      resolved.entry.window.postMessage(options.detail, resolved.entry.origin);
    }
    return Promise.resolve();
  }

  takeScreenshot(_options?: { id?: string }): Promise<ScreenshotResult> {
    console.log('takeScreenshot not supported on web', _options);
    return Promise.reject(this.unimplemented('Screenshots are not supported on web.'));
  }

  goBack(): Promise<any> {
    console.log('goBack');
    return Promise.resolve();
  }

  getPluginVersion(): Promise<{ version: string }> {
    return Promise.resolve({ version: 'web' });
  }

  updateDimensions(_options: DimensionOptions): Promise<void> {
    console.log('updateDimensions', _options);
    return Promise.resolve();
  }

  handleProxyRequest(options: Parameters<InAppBrowserPlugin['handleProxyRequest']>[0]): Promise<void> {
    console.log('handleProxyRequest not supported on web', options);
    return Promise.resolve();
  }

  setEnabledSafeTopMargin(_options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setEnabledSafeTopMargin not supported on web', _options);
    return Promise.resolve();
  }

  setFullscreen(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setFullscreen not supported on web', options);
    return Promise.reject(this.unimplemented('Fullscreen is only supported by native openWebView presentations.'));
  }

  getFullscreen(_options?: { id?: string }): Promise<{ enabled: boolean }> {
    console.log('getFullscreen not supported on web', _options);
    return Promise.reject(this.unimplemented('Fullscreen is only supported by native openWebView presentations.'));
  }

  setEnabledSafeBottomMargin(_options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setEnabledSafeBottomMargin not supported on web', _options);
    return Promise.resolve();
  }

  async openSecureWindow(options: OpenSecureWindowOptions): Promise<OpenSecureWindowResponse> {
    const w = 600;
    const h = 550;
    const settings = [
      ['width', w],
      ['height', h],
      ['left', screen.width / 2 - w / 2],
      ['top', screen.height / 2 - h / 2],
    ]
      .map((x) => x.join('='))
      .join(',');

    const popup = globalThis.open(options.authEndpoint, 'Authorization', settings);
    if (!popup) {
      throw new Error('Failed to open secure window');
    }
    if (typeof popup.focus === 'function') {
      popup.focus();
    }
    return new Promise((resolve, reject) => {
      const bc = new BroadcastChannel(options.broadcastChannelName || 'oauth-channel');
      bc.addEventListener('message', (event) => {
        if (event.data.startsWith(options.redirectUri)) {
          bc.close();
          resolve({ redirectedUri: event.data });
        } else {
          bc.close();
          reject(new Error('Redirect URI does not match, expected ' + options.redirectUri + ' but got ' + event.data));
        }
      });
      setTimeout(() => {
        bc.close();
        reject(new Error('The sign-in flow timed out'));
      }, 5 * 60000);
    });
  }
}
