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
      return method || "GET";
    }
    function normalizeCredentialsMode(mode) {
      if (mode === "omit" || mode === "include") {
        return mode;
      }
      return "same-origin";
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
    function storeRequest(url, method, headers, body, credentialsMode) {
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
          base64Body || "",
          normalizeCredentialsMode(credentialsMode)
        );
        return "/_capgo_proxy_?u=" + encodeURIComponent(url) + "&rid=" + requestId;
      });
    }
    const originalFetch = window.fetch;
    window.fetch = function(input, init) {
      return __async(this, null, function* () {
        let url;
        let method = "GET";
        const headers = {};
        let body = null;
        let credentialsMode = "same-origin";
        if (input instanceof Request) {
          url = input.url;
          method = input.method;
          credentialsMode = normalizeCredentialsMode(input.credentials);
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
          if (init.credentials) {
            credentialsMode = normalizeCredentialsMode(init.credentials);
          }
        }
        const proxyUrl = yield storeRequest(url, method, headers, body, credentialsMode);
        return originalFetch.call(window, proxyUrl, { method: "GET" });
      });
    };
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;
    const originalXhrSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
      this.__proxyMethod = method;
      this.__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
      this.__proxyHeaders = {};
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
      const requestId = generateRequestId();
      const method = normalizeMethod(xhr.__proxyMethod);
      const url = xhr.__proxyUrl || "";
      const headers = xhr.__proxyHeaders || {};
      const credentialsMode = xhr.withCredentials ? "include" : "same-origin";
      function completeSend(base64Body) {
        proxyBridge.storeRequest(
          accessToken,
          requestId,
          method,
          JSON.stringify(headers),
          base64Body,
          credentialsMode
        );
        const proxyUrl = "/_capgo_proxy_?u=" + encodeURIComponent(url) + "&rid=" + requestId;
        originalXhrOpen.call(xhr, "GET", proxyUrl, true);
        originalXhrSend.call(xhr, null);
      }
      if (body === null || body === void 0) {
        completeSend("");
        return;
      }
      if (typeof body === "string") {
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
          const contentType = encoded.headers.get("content-type");
          if (contentType) {
            Object.keys(headers).forEach((key) => {
              if (key.toLowerCase() === "content-type") {
                delete headers[key];
              }
            });
            headers["content-type"] = contentType;
          }
        }
        encoded.arrayBuffer().then((buffer) => {
          completeSend(arrayBufferToBase64(buffer));
        }).catch((_error) => {
          console.error("[proxy-bridge] Failed to encode Blob/FormData body");
          completeSend("");
        });
        return;
      }
      completeSend("");
    };
  })();
})();
