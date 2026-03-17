import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import type { InAppBrowserPlugin, ProxyDecision, ProxyHandler, ProxyRequestOverride, ProxyResponse } from './definitions';

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
  return obj !== null && typeof obj === 'object' && 'status' in obj && 'headers' in obj && !(obj instanceof Response);
}

function isProxyRequestOverride(obj: unknown): obj is ProxyRequestOverride {
  return obj !== null && typeof obj === 'object' && 'url' in obj && !(obj instanceof Response);
}

function isProxyDecision(obj: unknown): obj is ProxyDecision {
  return obj !== null && typeof obj === 'object' && ('request' in obj || 'response' in obj || 'cancel' in obj);
}

/**
 * Register a handler for proxy events delegated by native rules from the in-app browser webview.
 *
 * The callback receives a {@link ProxyRequest} for every request and must return one of:
 * - A {@link ProxyDecision} or {@link ProxyRequestOverride} to modify matched native requests/responses
 * - A {@link ProxyResponse} object (recommended — use with CapacitorHttp for CORS-free fetching)
 * - A fetch `Response` object
 * - `null` to let native continue unchanged
 *
 * **Platform note (Android):** Requests initiated directly by HTML elements (`<img>`,
 * `<script>`, `<link>`, `<iframe>`, etc.) are intercepted via `shouldInterceptRequest`,
 * which does not expose the request body. These requests will have an empty `body` field.
 * In practice this only affects direct resource loads, which are always GET. Requests made
 * via `fetch()` or `XMLHttpRequest` go through the JS bridge and include the full body.
 *
 * @since 9.0.0
 */
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
        // ProxyResponse returned directly (e.g. from CapacitorHttp)
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          decision: { response: result },
        });
      } else {
        // fetch Response object
        const cloned = result.clone();
        const buffer = await cloned.arrayBuffer();
        const base64Body = arrayBufferToBase64(buffer);
        await InAppBrowser.handleProxyRequest({
          requestId: event.requestId,
          webviewId: event.webviewId,
          decision: {
            response: {
              body: base64Body,
              status: result.status,
              headers: headersToRecord(result.headers),
            },
          },
        });
      }
    } catch (_e) {
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
