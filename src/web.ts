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
  private webViews = new Map<string, { window: Window; url: string; timer: number }>();
  private webViewCounter = 0;

  private watchClosed(id: string): void {
    const entry = this.webViews.get(id);
    if (!entry || !entry.window.closed) {
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
  async open(options: OpenOptions): Promise<any> {
    const opened = window.open(options.url, '_blank');
    if (!opened) {
      throw new Error('Popup blocked');
    }
    return;
  }

  async clearCookies(options: ClearCookieOptions): Promise<any> {
    console.log('cleanCookies', options);
    return;
  }

  async getCookies(options: GetCookieOptions): Promise<any> {
    // Web implementation to get cookies
    return options;
  }

  async openWebView(options: OpenWebViewOptions): Promise<any> {
    if (options.fullscreen) {
      throw this.unimplemented('Fullscreen is only supported by native openWebView presentations.');
    }
    const { popup = false, width, height } = options.web ?? {};
    const features = popup
      ? ['popup=yes', width ? `width=${width}` : '', height ? `height=${height}` : ''].filter(Boolean).join(',')
      : '';
    // Open a blank window first, then navigate. The opener is kept on purpose:
    // browsers only let script close() windows that still reference their opener.
    const opened = window.open('', '_blank', features);
    if (!opened) {
      throw new Error('Popup blocked');
    }
    opened.location.href = options.url;

    const id = `web-${++this.webViewCounter}`;
    const timer = window.setInterval(() => this.watchClosed(id), 500);
    this.webViews.set(id, { window: opened, url: options.url, timer });
    return { id };
  }

  async executeScript({ code }: { code: string }): Promise<any> {
    console.log('code', code);
    return code;
  }

  async close(options?: { id?: string }): Promise<any> {
    const id = options?.id ?? Array.from(this.webViews.keys()).pop();
    if (!id) {
      return;
    }
    const entry = this.webViews.get(id);
    if (!entry) {
      return;
    }
    entry.window.close();
    this.watchClosed(id);
    return;
  }

  async hide(options?: { id?: string }): Promise<void> {
    console.log('hide', options);
    return;
  }

  async show(options?: { id?: string }): Promise<void> {
    console.log('show', options);
    return;
  }

  async sendToBack(options?: { id?: string; transparentBackground?: boolean }): Promise<void> {
    console.log('sendToBack not supported on web', options);
    return;
  }

  async bringToFront(options?: BringToFrontOptions): Promise<void> {
    console.log('bringToFront not supported on web', options);
    return;
  }

  async dispatchInputEvent(options: DispatchInputEventOptions): Promise<void> {
    console.log('dispatchInputEvent not supported on web', options);
    return;
  }

  async setUrl(options: { url: string }): Promise<any> {
    console.log('setUrl', options.url);
    return;
  }

  async reload(options?: { id?: string }): Promise<any> {
    console.log('reload', options);
    return;
  }
  async postMessage(options: Record<string, any>): Promise<any> {
    console.log('postMessage', options);
    return options;
  }

  async takeScreenshot(options?: { id?: string }): Promise<ScreenshotResult> {
    console.log('takeScreenshot not supported on web', options);
    throw this.unimplemented('Screenshots are not supported on web.');
  }

  async goBack(): Promise<any> {
    console.log('goBack');
    return;
  }

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: 'web' };
  }

  async updateDimensions(options: DimensionOptions): Promise<void> {
    console.log('updateDimensions', options);
    // Web platform doesn't support dimension control
    return;
  }

  async handleProxyRequest(options: Parameters<InAppBrowserPlugin['handleProxyRequest']>[0]): Promise<void> {
    console.log('handleProxyRequest not supported on web', options);
    return;
  }

  async setEnabledSafeTopMargin(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setEnabledSafeTopMargin not supported on web', options);
    return;
  }

  async setFullscreen(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setFullscreen not supported on web', options);
    throw this.unimplemented('Fullscreen is only supported by native openWebView presentations.');
  }

  async getFullscreen(options?: { id?: string }): Promise<{ enabled: boolean }> {
    console.log('getFullscreen not supported on web', options);
    throw this.unimplemented('Fullscreen is only supported by native openWebView presentations.');
  }

  async setEnabledSafeBottomMargin(options: { enabled: boolean; id?: string }): Promise<void> {
    console.log('setEnabledSafeBottomMargin not supported on web', options);
    return;
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
