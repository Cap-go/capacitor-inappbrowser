/* eslint-disable */
/**
 * Proxy bridge script — injected into in-app browser webview on Android.
 * Patches fetch() and XMLHttpRequest to route requests through native interceptor.
 *
 * Flow:
 * 1. Patched fetch/XHR stores request body via __capgoProxy JavascriptInterface
 * 2. Rewrites URL to /_capgo_proxy_?u=<encoded-url>&rid=<requestId>
 * 3. shouldInterceptRequest catches the rewritten URL
 * 4. Native fires proxyRequest event to app JS
 * 5. App JS responds via handleProxyRequest → semaphore release → response returned
 */
(function () {
  // @ts-ignore - __capgoProxy is the JavascriptInterface injected by Android native
  const proxyBridge = (window as any).__capgoProxy;
  if (!proxyBridge) return;

  // Access token set by native before this script runs — prevents page JS from
  // calling storeRequest directly without knowing the per-webview secret.
  const accessToken: string = (window as any).__capgoProxyToken || '';
  // Clean up so page JS can't read it
  delete (window as any).__capgoProxyToken;

  let requestCounter = 0;

  function generateRequestId(): string {
    return 'pr_' + Date.now() + '_' + requestCounter++;
  }

  function arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  function stringToBase64(str: string): string {
    return btoa(unescape(encodeURIComponent(str)));
  }

  function resolveUrl(url: string): string {
    // Resolve relative URLs to absolute using the current page origin
    if (url && !url.match(/^[a-zA-Z][a-zA-Z0-9+\-.]*:\/\//)) {
      try {
        return new URL(url, window.location.href).href;
      } catch (_e) {
        return url;
      }
    }
    return url;
  }

  async function bodyToBase64(body: BodyInit | null | undefined): Promise<string | null> {
    if (body === null || body === undefined) return null;
    if (typeof body === 'string') return stringToBase64(body);
    if (body instanceof ArrayBuffer) return arrayBufferToBase64(body);
    if (body instanceof Uint8Array)
      return arrayBufferToBase64(body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength));
    if (body instanceof Blob) {
      const ab = await body.arrayBuffer();
      return arrayBufferToBase64(ab);
    }
    if (body instanceof FormData) {
      // Use Response to get proper multipart encoding with boundaries and file contents
      const ab = await new Response(body).arrayBuffer();
      return arrayBufferToBase64(ab);
    }
    if (body instanceof URLSearchParams) {
      return stringToBase64(body.toString());
    }
    return null;
  }

  // Patch fetch
  const originalFetch = window.fetch;
  window.fetch = async function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const requestId = generateRequestId();
    let url: string;
    let method = 'GET';
    const headers: Record<string, string> = {};
    let body: BodyInit | null | undefined = null;

    if (input instanceof Request) {
      url = input.url;
      method = input.method;
      input.headers.forEach((v, k) => {
        headers[k] = v;
      });
      try {
        const cloned = input.clone();
        const ab = await cloned.arrayBuffer();
        if (ab.byteLength > 0) {
          body = ab;
        }
      } catch (_e) {
        // body may not be readable
      }
    } else {
      url = input instanceof URL ? input.toString() : input;
    }

    // Resolve relative URLs to absolute
    url = resolveUrl(url);

    if (init) {
      if (init.method) method = init.method;
      if (init.headers) {
        const h = new Headers(init.headers);
        h.forEach((v, k) => {
          headers[k] = v;
        });
      }
      if (init.body !== undefined) body = init.body;
    }

    // FormData needs special handling: serialize via Response to get proper multipart
    // encoding with boundaries, and extract the generated Content-Type header
    if (body instanceof FormData) {
      const encoded = new Response(body);
      const ct = encoded.headers.get('content-type');
      if (ct) {
        // Remove any existing Content-Type — the boundary must match the encoded body
        Object.keys(headers).forEach((k) => {
          if (k.toLowerCase() === 'content-type') delete headers[k];
        });
        headers['content-type'] = ct;
      }
      body = await encoded.arrayBuffer();
    }

    const base64Body = await bodyToBase64(body);

    // Store request details via JavascriptInterface
    proxyBridge.storeRequest(accessToken, requestId, method, JSON.stringify(headers), base64Body || '');

    // Rewrite URL to proxy interceptor
    const proxyUrl = '/_capgo_proxy_?u=' + encodeURIComponent(url) + '&rid=' + requestId;

    // Call original fetch with rewritten URL (GET, no body — body is stored natively)
    return originalFetch.call(window, proxyUrl, { method: 'GET' });
  };

  // Patch XMLHttpRequest
  const XHROpen = XMLHttpRequest.prototype.open;
  const XHRSend = XMLHttpRequest.prototype.send;
  const XHRSetHeader = XMLHttpRequest.prototype.setRequestHeader;

  XMLHttpRequest.prototype.open = function (method: string, url: string | URL, ...rest: any[]) {
    (this as any).__proxyMethod = method;
    (this as any).__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
    (this as any).__proxyHeaders = {};
    return XHROpen.apply(this, [method, url, ...rest] as any);
  };

  XMLHttpRequest.prototype.setRequestHeader = function (name: string, value: string) {
    if ((this as any).__proxyHeaders) {
      (this as any).__proxyHeaders[name] = value;
    }
    return XHRSetHeader.call(this, name, value);
  };

  XMLHttpRequest.prototype.send = function (body?: Document | XMLHttpRequestBodyInit | null) {
    const xhr = this;
    const requestId = generateRequestId();
    const method = (xhr as any).__proxyMethod || 'GET';
    const url = (xhr as any).__proxyUrl || '';
    const headers = (xhr as any).__proxyHeaders || {};

    function completeSend(base64Body: string) {
      proxyBridge.storeRequest(accessToken, requestId, method, JSON.stringify(headers), base64Body);
      const proxyUrl = '/_capgo_proxy_?u=' + encodeURIComponent(url) + '&rid=' + requestId;
      XHROpen.call(xhr, 'GET', proxyUrl, true);
      XHRSend.call(xhr, null);
    }

    // Synchronous fast path for simple types
    if (body === null || body === undefined) {
      completeSend('');
      return;
    }
    if (typeof body === 'string') {
      completeSend(stringToBase64(body));
      return;
    }
    if (body instanceof ArrayBuffer) {
      completeSend(arrayBufferToBase64(body));
      return;
    }
    if (body instanceof Uint8Array) {
      completeSend(arrayBufferToBase64(body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength)));
      return;
    }
    if (body instanceof URLSearchParams) {
      completeSend(stringToBase64(body.toString()));
      return;
    }

    // Async path for FormData and Blob — deferred to microtask
    if (body instanceof Blob || body instanceof FormData) {
      const encoded = new Response(body);
      if (body instanceof FormData) {
        const ct = encoded.headers.get('content-type');
        if (ct) {
          Object.keys(headers).forEach((k) => {
            if (k.toLowerCase() === 'content-type') delete headers[k];
          });
          headers['content-type'] = ct;
        }
      }
      encoded.arrayBuffer().then((ab) => {
        completeSend(arrayBufferToBase64(ab));
      });
      return;
    }

    // Unknown type (e.g. Document) — send without body
    completeSend('');
  };
})();
