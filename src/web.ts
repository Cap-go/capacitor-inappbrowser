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

export class InAppBrowserWeb extends WebPlugin implements InAppBrowserPlugin {
  private readonly webViews = new Map<string, { window: Window; url: string; timer: number }>();
  private webViewCounter = 0;

  private watchClosed(id: string): void {
    const entry = this.webViews.get(id);
    if (!entry?.window.closed) {
      return;
    }
    window.clearInterval(entry.timer);
    this.webViews.delete(id);
    this.notifyListeners('closeEvent', { id, url: entry.url });
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
    const opened = window.open(options.url, '_blank');
    if (!opened) {
      return Promise.reject(new Error('Popup blocked'));
    }
    return Promise.resolve();
  }

  clearCookies(options: ClearCookieOptions): Promise<any> {
    console.log('cleanCookies', options);
    return Promise.resolve();
  }

  getCookies(options: GetCookieOptions): Promise<any> {
    // Web implementation to get cookies
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
    // Open a blank window first, then navigate. The opener is kept on purpose:
    // browsers only let script close() windows that still reference their opener.
    const opened = window.open('', '_blank', features);
    if (!opened) {
      return Promise.reject(new Error('Popup blocked'));
    }
    opened.location.href = options.url;

    const id = `web-${++this.webViewCounter}`;
    const timer = window.setInterval(() => this.watchClosed(id), 500);
    this.webViews.set(id, { window: opened, url: options.url, timer });
    return Promise.resolve({ id });
  }

  executeScript({ code }: { code: string }): Promise<any> {
    console.log('code', code);
    return Promise.resolve(code);
  }

  close(options?: { id?: string }): Promise<any> {
    const id =
      options?.id ??
      Array.from(this.webViews.entries())
        .reverse()
        .find(([, entry]) => !entry.window.closed)?.[0];
    if (!id) {
      return Promise.resolve();
    }
    const entry = this.webViews.get(id);
    if (!entry) {
      return Promise.resolve();
    }
    entry.window.close();
    this.watchClosed(id);
    return Promise.resolve();
  }

  hide(options?: { id?: string }): Promise<void> {
    console.log('hide', options);
    return Promise.resolve();
  }

  show(options?: { id?: string }): Promise<void> {
    console.log('show', options);
    return Promise.resolve();
  }

  sendToBack(options?: { id?: string; transparentBackground?: boolean }): Promise<void> {
    console.log('sendToBack not supported on web', options);
    return Promise.resolve();
  }

  bringToFront(options?: BringToFrontOptions): Promise<void> {
    console.log('bringToFront not supported on web', options);
    return Promise.resolve();
  }

  dispatchInputEvent(options: DispatchInputEventOptions): Promise<void> {
    console.log('dispatchInputEvent not supported on web', options);
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
  postMessage(options: Record<string, any>): Promise<any> {
    console.log('postMessage', options);
    return Promise.resolve(options);
  }

  takeScreenshot(options?: { id?: string }): Promise<ScreenshotResult> {
    console.log('takeScreenshot not supported on web', options);
    return Promise.reject(this.unimplemented('Screenshots are not supported on web.'));
  }

  goBack(): Promise<any> {
    console.log('goBack');
    return Promise.resolve();
  }

  getPluginVersion(): Promise<{ version: string }> {
    return Promise.resolve({ version: 'web' });
  }

  updateDimensions(options: DimensionOptions): Promise<void> {
    console.log('updateDimensions', options);
    // Web platform doesn't support dimension control
    return Promise.resolve();
  }

  handleProxyRequest(options: Parameters<InAppBrowserPlugin['handleProxyRequest']>[0]): Promise<void> {
    console.log('handleProxyRequest not supported on web', options);
    return Promise.resolve();
  }

  setEnabledSafeTopMargin(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setEnabledSafeTopMargin not supported on web', options);
    return Promise.resolve();
  }

  setFullscreen(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setFullscreen not supported on web', options);
    return Promise.reject(this.unimplemented('Fullscreen is only supported by native openWebView presentations.'));
  }

  getFullscreen(options?: { id?: string }): Promise<{ enabled: boolean }> {
    console.log('getFullscreen not supported on web', options);
    return Promise.reject(this.unimplemented('Fullscreen is only supported by native openWebView presentations.'));
  }

  setEnabledSafeBottomMargin(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setEnabledSafeBottomMargin not supported on web', options);
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

    const popup = window.open(options.authEndpoint, 'Authorization', settings);
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
