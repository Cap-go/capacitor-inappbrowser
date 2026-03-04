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
    if (!proxyBridge) return;
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
    function stringToBase64(str) {
      return btoa(unescape(encodeURIComponent(str)));
    }
    function resolveUrl(url) {
      if (url && !url.match(/^[a-zA-Z][a-zA-Z0-9+\-.]*:\/\//)) {
        try {
          return new URL(url, window.location.href).href;
        } catch (_e) {
          return url;
        }
      }
      return url;
    }
    function bodyToBase64(body) {
      return __async(this, null, function* () {
        if (body === null || body === void 0) return null;
        if (typeof body === "string") return stringToBase64(body);
        if (body instanceof ArrayBuffer) return arrayBufferToBase64(body);
        if (ArrayBuffer.isView(body))
          return arrayBufferToBase64(body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength));
        if (body instanceof Blob) {
          const ab = yield body.arrayBuffer();
          return arrayBufferToBase64(ab);
        }
        if (body instanceof FormData) {
          const ab = yield new Response(body).arrayBuffer();
          return arrayBufferToBase64(ab);
        }
        if (body instanceof URLSearchParams) {
          return stringToBase64(body.toString());
        }
        return null;
      });
    }
    const originalFetch = window.fetch;
    window.fetch = function(input, init) {
      return __async(this, null, function* () {
        const requestId = generateRequestId();
        let url;
        let method = "GET";
        const headers = {};
        let body = null;
        if (input instanceof Request) {
          url = input.url;
          method = input.method;
          input.headers.forEach((v, k) => {
            headers[k] = v;
          });
          try {
            const cloned = input.clone();
            const ab = yield cloned.arrayBuffer();
            if (ab.byteLength > 0) {
              body = ab;
            }
          } catch (_e) {
          }
        } else {
          url = input instanceof URL ? input.toString() : input;
        }
        url = resolveUrl(url);
        if (init) {
          if (init.method) method = init.method;
          if (init.headers) {
            const h = new Headers(init.headers);
            h.forEach((v, k) => {
              headers[k] = v;
            });
          }
          if (init.body !== void 0) body = init.body;
        }
        if (body instanceof FormData) {
          const encoded = new Response(body);
          const ct = encoded.headers.get("content-type");
          if (ct) {
            Object.keys(headers).forEach((k) => {
              if (k.toLowerCase() === "content-type") delete headers[k];
            });
            headers["content-type"] = ct;
          }
          body = yield encoded.arrayBuffer();
        }
        const base64Body = yield bodyToBase64(body);
        proxyBridge.storeRequest(accessToken, requestId, method, JSON.stringify(headers), base64Body || "");
        const proxyUrl = "/_capgo_proxy_?u=" + encodeURIComponent(url) + "&rid=" + requestId;
        return originalFetch.call(window, proxyUrl, { method: "GET" });
      });
    };
    const XHROpen = XMLHttpRequest.prototype.open;
    const XHRSend = XMLHttpRequest.prototype.send;
    const XHRSetHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
      this.__proxyMethod = method;
      this.__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
      this.__proxyHeaders = {};
      return XHROpen.apply(this, [method, url, ...rest]);
    };
    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
      if (this.__proxyHeaders) {
        this.__proxyHeaders[name] = value;
      }
      return XHRSetHeader.call(this, name, value);
    };
    XMLHttpRequest.prototype.send = function(body) {
      const xhr = this;
      const requestId = generateRequestId();
      const method = xhr.__proxyMethod || "GET";
      const url = xhr.__proxyUrl || "";
      const headers = xhr.__proxyHeaders || {};
      function completeSend(base64Body) {
        proxyBridge.storeRequest(accessToken, requestId, method, JSON.stringify(headers), base64Body);
        const proxyUrl = "/_capgo_proxy_?u=" + encodeURIComponent(url) + "&rid=" + requestId;
        XHROpen.call(xhr, "GET", proxyUrl, true);
        XHRSend.call(xhr, null);
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
          const ct = encoded.headers.get("content-type");
          if (ct) {
            Object.keys(headers).forEach((k) => {
              if (k.toLowerCase() === "content-type") delete headers[k];
            });
            headers["content-type"] = ct;
          }
        }
        encoded.arrayBuffer().then((ab) => {
          completeSend(arrayBufferToBase64(ab));
        }).catch((_e) => {
          console.error("[proxy-bridge] Failed to encode Blob/FormData body:", _e);
          completeSend("");
        });
        return;
      }
      completeSend("");
    };
  })();
})();
