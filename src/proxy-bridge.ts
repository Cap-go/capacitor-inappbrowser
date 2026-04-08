/* eslint-disable */
/**
 * Proxy bridge script injected into the Android webview.
 *
 * It patches fetch() and XMLHttpRequest so native can see request bodies before
 * the requests are rewritten to a proxy marker URL that shouldInterceptRequest
 * can intercept reliably.
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
    return (method || 'GET').toUpperCase();
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

  function shouldProxyUrl(url: string): boolean {
    try {
      const protocol = new URL(url, window.location.href).protocol.toLowerCase();
      return protocol === 'http:' || protocol === 'https:';
    } catch (_error) {
      return false;
    }
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

  async function storeInterceptedRequest(
    url: string,
    method: string,
    headers: Record<string, string>,
    body: BodyInit | null | undefined,
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
    );

    return '/_capgo_proxy_?u=' + encodeURIComponent(url) + '&rid=' + requestId;
  }

  function getSubmitterAttribute(submitter: Element | null | undefined, attributeName: string): string | null {
    if (!(submitter instanceof HTMLElement)) {
      return null;
    }
    return submitter.getAttribute(attributeName);
  }

  function createFormData(form: HTMLFormElement, submitter?: Element | null): FormData {
    if (submitter instanceof HTMLElement) {
      try {
        return new FormData(form, submitter as HTMLButtonElement | HTMLInputElement);
      } catch (_error) {
        // Fall back to the form-only constructor on older WebViews.
      }
    }
    return new FormData(form);
  }

  function appendFormDataToUrl(url: string, formData: FormData): string {
    const resolvedUrl = new URL(url, window.location.href);
    const searchParams = new URLSearchParams(resolvedUrl.search);

    formData.forEach((value, key) => {
      searchParams.append(key, typeof value === 'string' ? value : value.name);
    });

    resolvedUrl.search = searchParams.toString();
    return resolvedUrl.toString();
  }

  function formDataToUrlSearchParams(formData: FormData): URLSearchParams {
    const searchParams = new URLSearchParams();
    formData.forEach((value, key) => {
      searchParams.append(key, typeof value === 'string' ? value : value.name);
    });
    return searchParams;
  }

  function formDataToPlainText(formData: FormData): string {
    const lines: string[] = [];
    formData.forEach((value, key) => {
      lines.push(key + '=' + (typeof value === 'string' ? value : value.name));
    });
    return lines.join('\r\n');
  }

  function resolveFormMethod(form: HTMLFormElement, submitter?: Element | null): string {
    return normalizeMethod(getSubmitterAttribute(submitter, 'formmethod') || form.getAttribute('method'));
  }

  function resolveFormAction(form: HTMLFormElement, submitter?: Element | null): string {
    return resolveUrl(
      getSubmitterAttribute(submitter, 'formaction') || form.getAttribute('action') || window.location.href,
    );
  }

  function resolveFormTarget(form: HTMLFormElement, submitter?: Element | null): string {
    return (getSubmitterAttribute(submitter, 'formtarget') || form.getAttribute('target') || '').trim();
  }

  function resolveFormEnctype(form: HTMLFormElement, submitter?: Element | null): string {
    return (
      getSubmitterAttribute(submitter, 'formenctype') ||
      form.getAttribute('enctype') ||
      'application/x-www-form-urlencoded'
    ).toLowerCase();
  }

  function canProxyFormTarget(target: string): boolean {
    const normalizedTarget = target.toLowerCase();
    return (
      normalizedTarget === '' ||
      normalizedTarget === '_self' ||
      normalizedTarget === '_top' ||
      normalizedTarget === '_parent'
    );
  }

  function navigateFormProxy(proxyUrl: string, target: string): void {
    const normalizedTarget = target.toLowerCase();
    if (!normalizedTarget || normalizedTarget === '_self') {
      window.location.assign(proxyUrl);
      return;
    }
    if (normalizedTarget === '_top' && window.top) {
      window.top.location.assign(proxyUrl);
      return;
    }
    if (normalizedTarget === '_parent' && window.parent) {
      window.parent.location.assign(proxyUrl);
      return;
    }
    window.location.assign(proxyUrl);
  }

  async function proxyFormSubmission(form: HTMLFormElement, submitter?: Element | null): Promise<boolean> {
    const target = resolveFormTarget(form, submitter);
    if (!canProxyFormTarget(target)) {
      return false;
    }

    const method = resolveFormMethod(form, submitter);
    const actionUrl = resolveFormAction(form, submitter);
    const formData = createFormData(form, submitter);
    const headers: Record<string, string> = {};
    let requestUrl = actionUrl;
    let body: BodyInit | null = null;

    if (method === 'GET' || method === 'HEAD') {
      requestUrl = appendFormDataToUrl(actionUrl, formData);
    } else {
      const enctype = resolveFormEnctype(form, submitter);
      if (enctype === 'text/plain') {
        headers['content-type'] = 'text/plain;charset=UTF-8';
        body = formDataToPlainText(formData);
      } else if (enctype === 'multipart/form-data') {
        body = formData;
      } else {
        headers['content-type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        body = formDataToUrlSearchParams(formData);
      }
    }

    if (!shouldProxyUrl(requestUrl)) {
      return false;
    }

    const proxyUrl = await storeInterceptedRequest(requestUrl, method, headers, body);
    navigateFormProxy(proxyUrl, target);
    return true;
  }

  const originalFetch = window.fetch;
  window.fetch = async function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    let url: string;
    let method = 'GET';
    const headers: Record<string, string> = {};
    let body: BodyInit | null | undefined = null;

    if (input instanceof Request) {
      url = input.url;
      method = input.method;
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
        // Ignore unreadable bodies.
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
    }

    if (!shouldProxyUrl(url)) {
      return originalFetch.call(window, input, init);
    }

    const signal = init?.signal ?? (input instanceof Request ? input.signal : undefined);
    const proxyUrl = await storeInterceptedRequest(url, method, headers, body);
    return originalFetch.call(window, proxyUrl, {
      method: 'GET',
      signal,
    });
  };

  const originalXhrOpen = XMLHttpRequest.prototype.open;
  const originalXhrSend = XMLHttpRequest.prototype.send;
  const originalXhrSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;

  XMLHttpRequest.prototype.open = function (method: string, url: string | URL, ...rest: any[]) {
    (this as any).__proxyMethod = method;
    (this as any).__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
    (this as any).__proxyHeaders = {};
    (this as any).__proxyAsync = rest[0] !== false;
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
    const method = (xhr as any).__proxyMethod || 'GET';
    const url = (xhr as any).__proxyUrl || '';
    const headers = (xhr as any).__proxyHeaders || {};
    const isAsync = (xhr as any).__proxyAsync !== false;

    function completeSend(proxyUrl: string) {
      originalXhrOpen.call(xhr, 'GET', proxyUrl, true);
      originalXhrSend.call(xhr, null);
    }

    if (!shouldProxyUrl(url)) {
      originalXhrSend.call(xhr, body ?? null);
      return;
    }

    if (!isAsync) {
      console.warn('[proxy-bridge] Synchronous XMLHttpRequest cannot be proxied; falling back to the original request');
      originalXhrSend.call(xhr, body ?? null);
      return;
    }

    storeInterceptedRequest(url, method, headers, body as BodyInit | null | undefined)
      .then((proxyUrl) => {
        completeSend(proxyUrl);
      })
      .catch((_error) => {
        console.error('[proxy-bridge] Failed to encode XMLHttpRequest body');
        originalXhrSend.call(xhr, body ?? null);
      });
  };

  const originalFormSubmit = HTMLFormElement.prototype.submit;

  document.addEventListener(
    'submit',
    (event) => {
      const form = event.target instanceof HTMLFormElement ? event.target : null;
      if (!form) {
        return;
      }

      const submitter = event instanceof SubmitEvent ? event.submitter : null;
      if (!canProxyFormTarget(resolveFormTarget(form, submitter))) {
        return;
      }

      event.preventDefault();
      proxyFormSubmission(form, submitter).catch((_error) => {
        console.error('[proxy-bridge] Failed to proxy form submission');
        originalFormSubmit.call(form);
      });
    },
    true,
  );

  HTMLFormElement.prototype.submit = function () {
    const form = this;
    proxyFormSubmission(form)
      .then((handled) => {
        if (!handled) {
          originalFormSubmit.call(form);
        }
      })
      .catch((_error) => {
        console.error('[proxy-bridge] Failed to proxy programmatic form submission');
        originalFormSubmit.call(form);
      });
  };
})();
