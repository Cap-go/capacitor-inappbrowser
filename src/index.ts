import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import type { InAppBrowserPlugin, ProxyHandler, ProxyResponse } from './definitions';

const InAppBrowser = registerPlugin<InAppBrowserPlugin>('InAppBrowser', {
  web: () => import('./web').then((m) => new m.InAppBrowserWeb()),
});

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function headersToRecord(headers: Headers): Record<string, string> {
  const result: Record<string, string> = {};
  headers.forEach((value, key) => {
    result[key] = value;
  });
  return result;
}

function isProxyResponse(obj: unknown): obj is ProxyResponse {
  return (
    obj !== null &&
    typeof obj === 'object' &&
    'status' in obj &&
    'headers' in obj &&
    !(obj instanceof Response)
  );
}

const addProxyHandler = (callback: ProxyHandler): Promise<PluginListenerHandle> => {
  return InAppBrowser.addListener('proxyRequest', async (event) => {
    try {
      const result = await callback(event);
      if (result === null) {
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          response: null,
        });
      } else if (isProxyResponse(result)) {
        // ProxyResponse returned directly (e.g. from CapacitorHttp)
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          response: result,
        });
      } else {
        // fetch Response object
        const cloned = result.clone();
        const buffer = await cloned.arrayBuffer();
        const base64Body = arrayBufferToBase64(buffer);
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          response: {
            body: base64Body,
            status: result.status,
            headers: headersToRecord(result.headers),
          },
        });
      }
    } catch (_e) {
      await InAppBrowser.handleProxyRequest({
        requestId: event.requestId,
        webviewId: event.webviewId,
        response: null,
      });
    }
  });
};

export * from './definitions';
export { InAppBrowser, addProxyHandler };
