"use strict";
(() => {
  var __async = (__this, __arguments, generator) => {
    return new Promise((resolve, reject) => {
      var fulfilled = (value) => {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      };
      var rejected = (value) => {
        try {
          step(generator.throw(value));
        } catch (e) {
          reject(e);
        }
      };
      var step = (x) => x.done ? resolve(x.value) : Promise.resolve(x.value).then(fulfilled, rejected);
      step((generator = generator.apply(__this, __arguments)).next());
    });
  };

  // src/proxy-bridge.ts
  (function() {
    const proxyBridge = window.__capgoProxy;
    if (!proxyBridge) {
      return;
    }
    const accessToken = "___CAPGO_PROXY_TOKEN___";
    let requestCounter = 0;
    function generateRequestId() {
      return "pr_" + Date.now() + "_" + requestCounter++;
    }
    function arrayBufferToBase64(buffer) {
      const bytes = new Uint8Array(buffer);
      let binary = "";
      for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
      }
      return btoa(binary);
    }
    function stringToBase64(value) {
      return btoa(unescape(encodeURIComponent(value)));
    }
    function normalizeMethod(method) {
      return (method || "GET").toUpperCase();
    }
    function resolveUrl(url) {
      if (url && !url.match(/^[a-zA-Z][a-zA-Z0-9+\-.]*:\/\//)) {
        try {
          return new URL(url, window.location.href).href;
        } catch (_error) {
          return url;
        }
      }
      return url;
    }
    function shouldProxyUrl(url) {
      try {
        const protocol = new URL(url, window.location.href).protocol.toLowerCase();
        return protocol === "http:" || protocol === "https:";
      } catch (_error) {
        return false;
      }
    }
    function bodyToBase64(body) {
      return __async(this, null, function* () {
        if (body === null || body === void 0) {
          return null;
        }
        if (typeof body === "string") {
          return stringToBase64(body);
        }
        if (body instanceof ArrayBuffer) {
          return arrayBufferToBase64(body);
        }
        if (ArrayBuffer.isView(body)) {
          return arrayBufferToBase64(body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength));
        }
        if (body instanceof Blob) {
          const buffer = yield body.arrayBuffer();
          return arrayBufferToBase64(buffer);
        }
        if (body instanceof FormData) {
          const buffer = yield new Response(body).arrayBuffer();
          return arrayBufferToBase64(buffer);
        }
        if (body instanceof URLSearchParams) {
          return stringToBase64(body.toString());
        }
        return null;
      });
    }
    function storeInterceptedRequest(url, method, headers, body) {
      return __async(this, null, function* () {
        const requestId = generateRequestId();
        let normalizedBody = body;
        if (normalizedBody instanceof FormData) {
          const encoded = new Response(normalizedBody);
          const contentType = encoded.headers.get("content-type");
          if (contentType) {
            Object.keys(headers).forEach((key) => {
              if (key.toLowerCase() === "content-type") {
                delete headers[key];
              }
            });
            headers["content-type"] = contentType;
          }
          normalizedBody = yield encoded.arrayBuffer();
        }
        const base64Body = yield bodyToBase64(normalizedBody);
        proxyBridge.storeRequest(
          accessToken,
          requestId,
          normalizeMethod(method),
          JSON.stringify(headers),
          base64Body || ""
        );
        return "/_capgo_proxy_?u=" + encodeURIComponent(url) + "&rid=" + requestId;
      });
    }
    function getSubmitterAttribute(submitter, attributeName) {
      if (!(submitter instanceof HTMLElement)) {
        return null;
      }
      return submitter.getAttribute(attributeName);
    }
    function createFormData(form, submitter) {
      if (submitter instanceof HTMLElement) {
        try {
          return new FormData(form, submitter);
        } catch (_error) {
        }
      }
      return new FormData(form);
    }
    function appendFormDataToUrl(url, formData) {
      const resolvedUrl = new URL(url, window.location.href);
      const searchParams = new URLSearchParams(resolvedUrl.search);
      formData.forEach((value, key) => {
        searchParams.append(key, typeof value === "string" ? value : value.name);
      });
      resolvedUrl.search = searchParams.toString();
      return resolvedUrl.toString();
    }
    function formDataToUrlSearchParams(formData) {
      const searchParams = new URLSearchParams();
      formData.forEach((value, key) => {
        searchParams.append(key, typeof value === "string" ? value : value.name);
      });
      return searchParams;
    }
    function formDataToPlainText(formData) {
      const lines = [];
      formData.forEach((value, key) => {
        lines.push(key + "=" + (typeof value === "string" ? value : value.name));
      });
      return lines.join("\r\n");
    }
    function resolveFormMethod(form, submitter) {
      return normalizeMethod(getSubmitterAttribute(submitter, "formmethod") || form.getAttribute("method"));
    }
    function resolveFormAction(form, submitter) {
      return resolveUrl(
        getSubmitterAttribute(submitter, "formaction") || form.getAttribute("action") || window.location.href
      );
    }
    function resolveFormTarget(form, submitter) {
      return (getSubmitterAttribute(submitter, "formtarget") || form.getAttribute("target") || "").trim();
    }
    function resolveFormEnctype(form, submitter) {
      return (getSubmitterAttribute(submitter, "formenctype") || form.getAttribute("enctype") || "application/x-www-form-urlencoded").toLowerCase();
    }
    function canProxyFormTarget(target) {
      const normalizedTarget = target.toLowerCase();
      return normalizedTarget === "" || normalizedTarget === "_self" || normalizedTarget === "_top" || normalizedTarget === "_parent";
    }
    function navigateFormProxy(proxyUrl, target) {
      const normalizedTarget = target.toLowerCase();
      if (!normalizedTarget || normalizedTarget === "_self") {
        window.location.assign(proxyUrl);
        return;
      }
      if (normalizedTarget === "_top" && window.top) {
        window.top.location.assign(proxyUrl);
        return;
      }
      if (normalizedTarget === "_parent" && window.parent) {
        window.parent.location.assign(proxyUrl);
        return;
      }
      window.location.assign(proxyUrl);
    }
    function proxyFormSubmission(form, submitter) {
      return __async(this, null, function* () {
        const target = resolveFormTarget(form, submitter);
        if (!canProxyFormTarget(target)) {
          return false;
        }
        const method = resolveFormMethod(form, submitter);
        const actionUrl = resolveFormAction(form, submitter);
        const formData = createFormData(form, submitter);
        const headers = {};
        let requestUrl = actionUrl;
        let body = null;
        if (method === "GET" || method === "HEAD") {
          requestUrl = appendFormDataToUrl(actionUrl, formData);
        } else {
          const enctype = resolveFormEnctype(form, submitter);
          if (enctype === "text/plain") {
            headers["content-type"] = "text/plain;charset=UTF-8";
            body = formDataToPlainText(formData);
          } else if (enctype === "multipart/form-data") {
            body = formData;
          } else {
            headers["content-type"] = "application/x-www-form-urlencoded;charset=UTF-8";
            body = formDataToUrlSearchParams(formData);
          }
        }
        if (!shouldProxyUrl(requestUrl)) {
          return false;
        }
        const proxyUrl = yield storeInterceptedRequest(requestUrl, method, headers, body);
        navigateFormProxy(proxyUrl, target);
        return true;
      });
    }
    const originalFetch = window.fetch;
    window.fetch = function(input, init) {
      return __async(this, null, function* () {
        var _a;
        let url;
        let method = "GET";
        const headers = {};
        let body = null;
        if (input instanceof Request) {
          url = input.url;
          method = input.method;
          input.headers.forEach((value, key) => {
            headers[key] = value;
          });
          try {
            const cloned = input.clone();
            const buffer = yield cloned.arrayBuffer();
            if (buffer.byteLength > 0) {
              body = buffer;
            }
          } catch (_error) {
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
          if (init.body !== void 0) {
            body = init.body;
          }
        }
        if (!shouldProxyUrl(url)) {
          return originalFetch.call(window, input, init);
        }
        const signal = (_a = init == null ? void 0 : init.signal) != null ? _a : input instanceof Request ? input.signal : void 0;
        const proxyUrl = yield storeInterceptedRequest(url, method, headers, body);
        return originalFetch.call(window, proxyUrl, {
          method: "GET",
          signal
        });
      });
    };
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;
    const originalXhrSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
      this.__proxyMethod = method;
      this.__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
      this.__proxyHeaders = {};
      this.__proxyAsync = rest[0] !== false;
      return originalXhrOpen.apply(this, [method, url, ...rest]);
    };
    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
      if (this.__proxyHeaders) {
        this.__proxyHeaders[name] = value;
      }
      return originalXhrSetRequestHeader.call(this, name, value);
    };
    XMLHttpRequest.prototype.send = function(body) {
      const xhr = this;
      const method = xhr.__proxyMethod || "GET";
      const url = xhr.__proxyUrl || "";
      const headers = xhr.__proxyHeaders || {};
      const isAsync = xhr.__proxyAsync !== false;
      function completeSend(proxyUrl) {
        originalXhrOpen.call(xhr, "GET", proxyUrl, true);
        originalXhrSend.call(xhr, null);
      }
      if (!shouldProxyUrl(url)) {
        originalXhrSend.call(xhr, body != null ? body : null);
        return;
      }
      if (!isAsync) {
        console.warn("[proxy-bridge] Synchronous XMLHttpRequest cannot be proxied; falling back to the original request");
        originalXhrSend.call(xhr, body != null ? body : null);
        return;
      }
      storeInterceptedRequest(url, method, headers, body).then((proxyUrl) => {
        completeSend(proxyUrl);
      }).catch((_error) => {
        console.error("[proxy-bridge] Failed to encode XMLHttpRequest body");
        originalXhrSend.call(xhr, body != null ? body : null);
      });
    };
    const originalFormSubmit = HTMLFormElement.prototype.submit;
    document.addEventListener(
      "submit",
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
          console.error("[proxy-bridge] Failed to proxy form submission");
          originalFormSubmit.call(form);
        });
      },
      true
    );
    HTMLFormElement.prototype.submit = function() {
      const form = this;
      proxyFormSubmission(form).then((handled) => {
        if (!handled) {
          originalFormSubmit.call(form);
        }
      }).catch((_error) => {
        console.error("[proxy-bridge] Failed to proxy programmatic form submission");
        originalFormSubmit.call(form);
      });
    };
  })();
})();
