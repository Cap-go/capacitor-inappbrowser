import { WebPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

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

type TrackedWebView = {
  window: Window;
  url: string;
  timer: number;
  /** Origin used as postMessage target. Updated from the last message the page sent (handles redirects). */
  origin: string;
  /** Object URL of the isolating wrapper document for header loads, revoked on close. */
  objectUrl?: string;
};

const HTML_ATTRIBUTE_ESCAPES: Record<string, string> = { '&': '&amp;', '"': '&quot;', '<': '&lt;', '>': '&gt;' };

const escapeHtmlAttribute = (value: string): string => value.replace(/[&"<>]/g, (c) => HTML_ATTRIBUTE_ESCAPES[c]);

const OPENING_HEAD_TAG = /<head(?:\s[^>]*)?>/i;

/**
 * Wraps fetched HTML in a sandboxed iframe (opaque origin, no allow-same-origin) so the remote page
 * cannot read the app's storage or DOM even though the wrapper is a Blob created by the app origin.
 * A <base href> pointing at the final response URL keeps relative URLs resolving against the target.
 * The wrapper relays messages from the app (its opener) into the sandboxed frame.
 */
export const buildIsolatedDocument = (html: string, baseHref: string): string => {
  const baseTag = `<base href="${escapeHtmlAttribute(baseHref)}">`;
  const inner = OPENING_HEAD_TAG.test(html)
    ? html.replace(OPENING_HEAD_TAG, (tag) => `${tag}${baseTag}`)
    : `${baseTag}${html}`;
  const sandbox =
    'allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox allow-top-navigation-by-user-activation';
  const relay =
    "(function(){var f=document.getElementById('capgo-iab-frame');" +
    "window.addEventListener('message',function(e){if(e.source===window.opener&&f&&f.contentWindow){f.contentWindow.postMessage(e.data,'*');}});})();";
  return (
    '<!doctype html><html><head><meta charset="utf-8">' +
    '<style>html,body,iframe{margin:0;padding:0;border:0;width:100%;height:100%;display:block}</style></head>' +
    `<body><iframe id="capgo-iab-frame" sandbox="${sandbox}" referrerpolicy="no-referrer" srcdoc="${escapeHtmlAttribute(inner)}"></iframe>` +
    `<script>${relay}</script></body></html>`
  );
};

export class InAppBrowserWeb extends WebPlugin implements InAppBrowserPlugin {
  private readonly webViews = new Map<string, TrackedWebView>();
  private webViewCounter = 0;

  constructor() {
    super();
    if (globalThis.window !== undefined) {
      globalThis.window.addEventListener('message', this.handleWindowMessage);
    }
  }

  addListener(eventName: string, listenerFunc: Parameters<WebPlugin['addListener']>[1]): Promise<PluginListenerHandle> {
    if (eventName === 'urlChangeEvent') {
      return Promise.reject(this.unimplemented('URL change events are not supported on web.'));
    }
    return super.addListener(eventName, listenerFunc);
  }

  private readonly handleWindowMessage = (event: MessageEvent): void => {
    const match = Array.from(this.webViews.entries()).find(([, entry]) => this.isFromWebView(entry, event.source));
    if (!match) {
      return;
    }
    const [id, entry] = match;
    if (!entry.objectUrl && event.origin && event.origin !== 'null') {
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
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      this.notifyListeners('messageFromWebview', { ...(data as Record<string, unknown>), id });
    } else if (typeof event.data === 'string') {
      this.notifyListeners('messageFromWebview', { id, rawMessage: event.data });
    }
  };

  private isFromWebView(entry: TrackedWebView, source: MessageEventSource | null): boolean {
    if (!source) {
      return false;
    }
    if (entry.window === source) {
      return true;
    }
    try {
      // Frames inside the opened window (e.g. the sandboxed frame used for header loads) report their own source.
      return (source as Window).top === entry.window;
    } catch {
      return false;
    }
  }

  private watchClosed(id: string): void {
    const entry = this.webViews.get(id);
    if (!entry?.window.closed) {
      return;
    }
    globalThis.clearInterval(entry.timer);
    if (entry.objectUrl) {
      URL.revokeObjectURL(entry.objectUrl);
    }
    this.webViews.delete(id);
    this.notifyListeners('closeEvent', { id, url: entry.url });
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

  private navigateOpenedWindow(id: string, win: Window, url: string, headers?: Record<string, string>): void {
    if (!this.hasHeaders(headers)) {
      win.location.href = url;
      return;
    }

    void (async () => {
      try {
        const response = await fetch(url, { headers });
        if (!response.ok) {
          throw new Error(`Request failed with status ${response.status}`);
        }
        const html = await response.text();
        const parsed = new URL(response.url || url, globalThis.location?.href ?? 'https://localhost/');
        const baseHref = `${parsed.origin}${parsed.pathname}${parsed.search}`;
        const blob = new Blob([buildIsolatedDocument(html, baseHref)], { type: 'text/html;charset=utf-8' });
        const objectUrl = URL.createObjectURL(blob);
        const entry = this.webViews.get(id);
        if (!entry || win.closed) {
          URL.revokeObjectURL(objectUrl);
          return;
        }
        entry.objectUrl = objectUrl;
        win.location.href = objectUrl;
      } catch (error) {
        console.error('[InAppBrowser] Failed to load web view with headers', error);
        win.close();
        this.watchClosed(id);
      }
    })();
  }

  clearAllCookies(): Promise<any> {
    return Promise.reject(this.unimplemented('Cookie operations are not supported on web.'));
  }

  clearCache(): Promise<any> {
    console.log('clearCache');
    return Promise.resolve();
  }

  clearAllBrowsingData(): Promise<any> {
    return Promise.reject(this.unimplemented('Browsing data operations are not supported on web.'));
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

  clearCookies(_options: ClearCookieOptions): Promise<any> {
    return Promise.reject(this.unimplemented('Cookie operations are not supported on web.'));
  }

  getCookies(_options: GetCookieOptions): Promise<any> {
    return Promise.reject(this.unimplemented('Cookie operations are not supported on web.'));
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
    // Header loads render inside an app-origin wrapper document, so messages target the app origin there.
    const origin = this.hasHeaders(options.headers)
      ? (globalThis.location?.origin ?? 'null')
      : this.resolveTargetOrigin(options.url);
    this.webViews.set(id, { window: opened, url: options.url, timer, origin });
    this.navigateOpenedWindow(id, opened, options.url, options.headers);
    return Promise.resolve({ id });
  }

  executeScript(_options: { code: string }): Promise<any> {
    return Promise.reject(this.unimplemented('Script execution is not supported on web.'));
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

  setUrl(_options: { url: string }): Promise<any> {
    return Promise.reject(this.unimplemented('setUrl is not supported on web.'));
  }

  reload(_options?: { id?: string }): Promise<any> {
    return Promise.reject(this.unimplemented('reload is not supported on web.'));
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
    return Promise.reject(this.unimplemented('goBack is not supported on web.'));
  }

  getPluginVersion(): Promise<{ version: string }> {
    return Promise.resolve({ version: 'web' });
  }

  updateDimensions(_options: DimensionOptions): Promise<void> {
    console.log('updateDimensions', _options);
    return Promise.resolve();
  }

  handleProxyRequest(_options: Parameters<InAppBrowserPlugin['handleProxyRequest']>[0]): Promise<void> {
    return Promise.reject(this.unimplemented('Proxy requests are not supported on web.'));
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
