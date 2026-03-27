import { registerPlugin } from '@capacitor/core';
import type {
  InAppBrowserPlugin,
  OpenWebViewOptions,
  ProxyRule,
  NativeProxyRule,
  ProxyRequest,
  ProxyResponse,
  ModifiedRequest,
  ModifiedResponse,
} from './definitions';
import type { PluginListenerHandle } from '@capacitor/core';

const InAppBrowserNative = registerPlugin<InAppBrowserPlugin>('InAppBrowser', {
  web: () => import('./web').then((m) => new m.InAppBrowserWeb()),
});

type ProxyCallbacks = {
  onRequest?: ProxyRule['onRequest'];
  onResponse?: ProxyRule['onResponse'];
};

let activeProxyCallbacks: Map<number, ProxyCallbacks> | null = null;
let activeListeners: PluginListenerHandle[] = [];
let closeListener: PluginListenerHandle | null = null;

const JS_CLEANUP_TIMEOUT_MS = 11_000;

async function cleanupProxy(): Promise<void> {
  for (const listener of activeListeners) {
    await listener.remove();
  }
  activeListeners = [];
  activeProxyCallbacks = null;
  if (closeListener) {
    await closeListener.remove();
    closeListener = null;
  }
}

async function handleInterceptEvent(
  event: (ProxyRequest | ProxyResponse) & { ruleIndex: number },
  type: 'request' | 'response',
): Promise<void> {
  const callbacks = activeProxyCallbacks?.get(event.ruleIndex);
  const callback = type === 'request' ? callbacks?.onRequest : callbacks?.onResponse;

  let result: ModifiedRequest | ModifiedResponse | null = null;

  if (callback) {
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    const timeoutPromise = new Promise<null>((resolve) => {
      timeoutId = setTimeout(() => resolve(null), JS_CLEANUP_TIMEOUT_MS);
    });

    try {
      result = await Promise.race([
        callback(event as any),
        timeoutPromise,
      ]);
    } catch {
      result = null;
    } finally {
      if (timeoutId !== undefined) clearTimeout(timeoutId);
    }
  }

  if (type === 'request') {
    await InAppBrowserNative.handleProxyRequest({
      requestId: event.requestId,
      modifiedRequest: result as ModifiedRequest | null,
    });
  } else {
    await InAppBrowserNative.handleProxyResponse({
      requestId: event.requestId,
      modifiedResponse: result as ModifiedResponse | null,
    });
  }
}

function setupProxyListeners(rules: ProxyRule[]): void {
  activeProxyCallbacks = new Map();

  for (let i = 0; i < rules.length; i++) {
    activeProxyCallbacks.set(i, {
      onRequest: rules[i].onRequest,
      onResponse: rules[i].onResponse,
    });
  }

  const reqListener = InAppBrowserNative.addListener(
    'proxyRequestIntercept',
    (event) => handleInterceptEvent(event, 'request'),
  );

  const resListener = InAppBrowserNative.addListener(
    'proxyResponseIntercept',
    (event) => handleInterceptEvent(event, 'response'),
  );

  reqListener.then((h) => activeListeners.push(h));
  resListener.then((h) => activeListeners.push(h));

  InAppBrowserNative.addListener('closeEvent', () => cleanupProxy()).then(
    (h) => {
      closeListener = h;
    },
  );
}

function serializeRules(rules: ProxyRule[]): NativeProxyRule[] {
  return rules.map((rule, i) => ({
    ruleIndex: i,
    urlPattern: rule.urlPattern,
    methods: rule.methods,
    includeBody: rule.includeBody,
    intercept: rule.intercept,
  }));
}

// Proxy-aware wrapper. Uses a Proxy to forward all methods to InAppBrowserNative
// while intercepting openWebView to handle callback-based proxy rules.
export const InAppBrowser: InAppBrowserPlugin = new Proxy(InAppBrowserNative, {
  get(target, prop, receiver) {
    if (prop === 'openWebView') {
      return async (options: OpenWebViewOptions): Promise<void> => {
        const { proxyRules, ...rest } = options;

        if (proxyRules && proxyRules.length > 0) {
          setupProxyListeners(proxyRules);
          const nativeOptions = {
            ...rest,
            proxyRules: serializeRules(proxyRules),
          };
          try {
            await target.openWebView(nativeOptions as any);
          } catch (e) {
            await cleanupProxy();
            throw e;
          }
        } else {
          await target.openWebView(options);
        }
      };
    }
    return Reflect.get(target, prop, receiver);
  },
}) as InAppBrowserPlugin;

export * from './definitions';
