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

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message;
  }
  return String(error);
}

function isStaleProxyResponseError(error: unknown): boolean {
  const message = errorMessage(error);
  return message.includes('No proxy handler found') || message.includes('Target WebView not found for proxy request');
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

async function sendProxyDecision(
  requestId: string,
  webviewId: string | undefined,
  decision: ProxyDecision | null,
  phase: 'outbound' | 'inbound',
): Promise<void> {
  try {
    await InAppBrowser.handleProxyRequest({
      requestId,
      webviewId,
      decision,
      phase,
    } as Parameters<typeof InAppBrowser.handleProxyRequest>[0]);
  } catch (error) {
    if (isStaleProxyResponseError(error)) {
      return;
    }
    throw error;
  }
}

const addProxyHandler = (callback: ProxyHandler): Promise<PluginListenerHandle> => {
  return InAppBrowser.addListener('proxyRequest', async (event) => {
    let decision: ProxyDecision | null = null;

    try {
      const result = await callback(event);
      if (result === null) {
        decision = null;
      } else if (isProxyDecision(result)) {
        decision = result;
      } else if (isProxyRequestOverride(result)) {
        decision = { request: result };
      } else if (isProxyResponse(result)) {
        decision = { response: result };
      } else {
        const cloned = result.clone();
        const buffer = await cloned.arrayBuffer();
        decision = {
          response: {
            body: arrayBufferToBase64(buffer),
            status: result.status,
            headers: headersToRecord(result.headers),
          },
        };
      }
    } catch (_error) {
      decision = null;
    }

    await sendProxyDecision(event.requestId, event.webviewId, decision, event.phase);
  });
};

export * from './definitions';
export { InAppBrowser, addProxyHandler };
