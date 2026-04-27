/* eslint-disable */
/**
 * Proxy bridge script injected into the Android webview.
 *
 * This keeps the beta catch-all fetch/XHR rewrite model and leaves HTML form
 * navigations on the normal WebView path.
 */
(function () {
  // @ts-ignore - injected by Android native
  const proxyBridge = (window as any).__capgoProxy;
  if (!proxyBridge) {
    return;
  }

  const accessToken = '___CAPGO_PROXY_TOKEN___';
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

  function stringToBase64(value: string): string {
    return btoa(unescape(encodeURIComponent(value)));
  }

  function normalizeMethod(method: string | null | undefined): string {
    return method || 'GET';
  }

  function normalizeCredentialsMode(mode: string | null | undefined): RequestCredentials {
    if (mode === 'omit' || mode === 'include') {
      return mode;
    }
    return 'same-origin';
  }

  function resolveUrl(url: string): string {
    if (url && !url.match(/^[a-zA-Z][a-zA-Z0-9+\-.]*:\/\//)) {
      try {
        return new URL(url, window.location.href).href;
      } catch (_error) {
        return url;
      }
    }
    return url;
  }

  async function bodyToBase64(body: BodyInit | null | undefined): Promise<string | null> {
    if (body === null || body === undefined) {
      return null;
    }
    if (typeof body === 'string') {
      return stringToBase64(body);
    }
    if (body instanceof ArrayBuffer) {
      return arrayBufferToBase64(body);
    }
    if (ArrayBuffer.isView(body)) {
      return arrayBufferToBase64(body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength));
    }
    if (body instanceof Blob) {
      const buffer = await body.arrayBuffer();
      return arrayBufferToBase64(buffer);
    }
    if (body instanceof FormData) {
      const buffer = await new Response(body).arrayBuffer();
      return arrayBufferToBase64(buffer);
    }
    if (body instanceof URLSearchParams) {
      return stringToBase64(body.toString());
    }
    return null;
  }

  async function storeRequest(
    url: string,
    method: string,
    headers: Record<string, string>,
    body: BodyInit | null | undefined,
    credentialsMode: RequestCredentials,
  ): Promise<string> {
    const requestId = generateRequestId();
    let normalizedBody = body;

    if (normalizedBody instanceof FormData) {
      const encoded = new Response(normalizedBody);
      const contentType = encoded.headers.get('content-type');
      if (contentType) {
        Object.keys(headers).forEach((key) => {
          if (key.toLowerCase() === 'content-type') {
            delete headers[key];
          }
        });
        headers['content-type'] = contentType;
      }
      normalizedBody = await encoded.arrayBuffer();
    }

    const base64Body = await bodyToBase64(normalizedBody);
    proxyBridge.storeRequest(
      accessToken,
      requestId,
      normalizeMethod(method),
      JSON.stringify(headers),
      base64Body || '',
      normalizeCredentialsMode(credentialsMode),
    );

    return '/_capgo_proxy_?u=' + encodeURIComponent(url) + '&rid=' + requestId;
  }

  const originalFetch = window.fetch;
  window.fetch = async function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    let url: string;
    let method = 'GET';
    const headers: Record<string, string> = {};
    let body: BodyInit | null | undefined = null;
    let credentialsMode: RequestCredentials = 'same-origin';

    if (input instanceof Request) {
      url = input.url;
      method = input.method;
      credentialsMode = normalizeCredentialsMode(input.credentials);
      input.headers.forEach((value, key) => {
        headers[key] = value;
      });
      try {
        const cloned = input.clone();
        const buffer = await cloned.arrayBuffer();
        if (buffer.byteLength > 0) {
          body = buffer;
        }
      } catch (_error) {
        // Keep beta fallback behavior when the request body cannot be cloned.
      }
    } else {
      url = input instanceof URL ? input.toString() : input;
    }

    url = resolveUrl(url);

    if (init) {
      if (init.method) {
        method = init.method;
      }
      if (init.headers) {
        const normalized = new Headers(init.headers);
        normalized.forEach((value, key) => {
          headers[key] = value;
        });
      }
      if (init.body !== undefined) {
        body = init.body;
      }
      if (init.credentials) {
        credentialsMode = normalizeCredentialsMode(init.credentials);
      }
    }

    const proxyUrl = await storeRequest(url, method, headers, body, credentialsMode);
    return originalFetch.call(window, proxyUrl, { method: 'GET' });
  };

  const originalXhrOpen = XMLHttpRequest.prototype.open;
  const originalXhrSend = XMLHttpRequest.prototype.send;
  const originalXhrSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;

  XMLHttpRequest.prototype.open = function (method: string, url: string | URL, ...rest: any[]) {
    (this as any).__proxyMethod = method;
    (this as any).__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
    (this as any).__proxyHeaders = {};
    return originalXhrOpen.apply(this, [method, url, ...rest] as any);
  };

  XMLHttpRequest.prototype.setRequestHeader = function (name: string, value: string) {
    if ((this as any).__proxyHeaders) {
      (this as any).__proxyHeaders[name] = value;
    }
    return originalXhrSetRequestHeader.call(this, name, value);
  };

  XMLHttpRequest.prototype.send = function (body?: Document | XMLHttpRequestBodyInit | null) {
    const xhr = this;
    const requestId = generateRequestId();
    const method = normalizeMethod((xhr as any).__proxyMethod);
    const url = (xhr as any).__proxyUrl || '';
    const headers = (xhr as any).__proxyHeaders || {};
    const credentialsMode: RequestCredentials = xhr.withCredentials ? 'include' : 'same-origin';

    function completeSend(base64Body: string) {
      proxyBridge.storeRequest(accessToken, requestId, method, JSON.stringify(headers), base64Body, credentialsMode);
      const proxyUrl = '/_capgo_proxy_?u=' + encodeURIComponent(url) + '&rid=' + requestId;
      originalXhrOpen.call(xhr, 'GET', proxyUrl, true);
      originalXhrSend.call(xhr, null);
    }

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
    if (ArrayBuffer.isView(body)) {
      completeSend(arrayBufferToBase64(body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength)));
      return;
    }
    if (body instanceof URLSearchParams) {
      completeSend(stringToBase64(body.toString()));
      return;
    }
    if (body instanceof Blob || body instanceof FormData) {
      const encoded = new Response(body);
      if (body instanceof FormData) {
        const contentType = encoded.headers.get('content-type');
        if (contentType) {
          Object.keys(headers).forEach((key) => {
            if (key.toLowerCase() === 'content-type') {
              delete headers[key];
            }
          });
          headers['content-type'] = contentType;
        }
      }
      encoded
        .arrayBuffer()
        .then((buffer) => {
          completeSend(arrayBufferToBase64(buffer));
        })
        .catch((_error) => {
          console.error('[proxy-bridge] Failed to encode Blob/FormData body');
          completeSend('');
        });
      return;
    }
    completeSend('');
  };
})();
