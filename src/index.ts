import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import type {
  InAppBrowserPlugin,
  ProxyDecision,
  ProxyHandler,
  ProxyRequestOverride,
  ProxyResponse,
} from './definitions';

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

function isProxyResponse(value: unknown): value is ProxyResponse {
  return (
    value !== null &&
    typeof value === 'object' &&
    'status' in value &&
    'headers' in value &&
    !(value instanceof Response)
  );
}

function isProxyRequestOverride(value: unknown): value is ProxyRequestOverride {
  return value !== null && typeof value === 'object' && 'url' in value && !(value instanceof Response);
}

function isProxyDecision(value: unknown): value is ProxyDecision {
  return (
    value !== null && typeof value === 'object' && ('request' in value || 'response' in value || 'cancel' in value)
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
          decision: null,
        });
      } else if (isProxyDecision(result)) {
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          decision: result,
        });
      } else if (isProxyRequestOverride(result)) {
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          decision: { request: result },
        });
      } else if (isProxyResponse(result)) {
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          decision: { response: result },
        });
      } else {
        const cloned = result.clone();
        const buffer = await cloned.arrayBuffer();
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          decision: {
            response: {
              body: arrayBufferToBase64(buffer),
              status: result.status,
              headers: headersToRecord(result.headers),
            },
          },
        });
      }
    } catch (_error) {
      await InAppBrowser.handleProxyRequest({
        requestId: event.requestId,
        webviewId: event.webviewId,
        decision: null,
      });
    }
  });
};

export * from './definitions';
export { InAppBrowser, addProxyHandler };
