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

  // src/proxy-bridge-support.ts
  function findCapturedHeaderKey(headers, headerName) {
    const normalizedHeaderName = headerName.toLowerCase();
    for (const key of Object.keys(headers)) {
      if (key.toLowerCase() === normalizedHeaderName) {
        return key;
      }
    }
    return null;
  }
  function replaceCapturedHeader(headers, name, value) {
    const existingKey = findCapturedHeaderKey(headers, name);
    if (existingKey && existingKey !== name) {
      delete headers[existingKey];
    }
    headers[name] = value;
  }
  function appendCapturedHeader(headers, name, value) {
    const existingKey = findCapturedHeaderKey(headers, name);
    if (!existingKey) {
      headers[name] = value;
      return;
    }
    headers[existingKey] = headers[existingKey] ? `${headers[existingKey]}, ${value}` : value;
  }
  function inferContentTypeFromBody(body) {
    if (typeof body === "string") {
      return "text/plain;charset=UTF-8";
    }
    if (body instanceof URLSearchParams) {
      return "application/x-www-form-urlencoded;charset=UTF-8";
    }
    if (typeof Blob !== "undefined" && body instanceof Blob) {
      return body.type || null;
    }
    return null;
  }
  function ensureInferredContentType(headers, body) {
    if (findCapturedHeaderKey(headers, "content-type")) {
      return;
    }
    const inferredContentType = inferContentTypeFromBody(body);
    if (inferredContentType) {
      headers["content-type"] = inferredContentType;
    }
  }
  function resolveProxyBridgeUrl(rawUrl, baseUrl) {
    try {
      return new URL(rawUrl, baseUrl).href;
    } catch (_error) {
      return null;
    }
  }
  function shouldProxyBridgeUrl(rawUrl, baseUrl, urlRegex) {
    const resolvedUrl = resolveProxyBridgeUrl(rawUrl, baseUrl);
    if (!resolvedUrl) {
      return false;
    }
    const protocol = new URL(resolvedUrl).protocol.toLowerCase();
    if (protocol !== "http:" && protocol !== "https:") {
      return false;
    }
    return !urlRegex || urlRegex.test(resolvedUrl);
  }
  function getSubmitEventSubmitter(event, submitEventCtor) {
    var _a, _b;
    const resolvedSubmitEventCtor = submitEventCtor != null ? submitEventCtor : typeof SubmitEvent !== "undefined" ? SubmitEvent : void 0;
    if (resolvedSubmitEventCtor && event instanceof resolvedSubmitEventCtor) {
      return (_a = event.submitter) != null ? _a : null;
    }
    if ("submitter" in event) {
      return (_b = event.submitter) != null ? _b : null;
    }
    return null;
  }
  function shouldProxySubmitEvent(defaultPrevented, canProxyTarget) {
    return !defaultPrevented && canProxyTarget;
  }
  function shouldProxySubmitRequest(defaultPrevented, canProxyTarget, rawUrl, baseUrl, urlRegex) {
    return shouldProxySubmitEvent(defaultPrevented, canProxyTarget) && shouldProxyBridgeUrl(rawUrl, baseUrl, urlRegex);
  }
  function consumeProxySubmitReplayBypass(form) {
    if (!form.__capgoSkipNextProxySubmit) {
      return false;
    }
    delete form.__capgoSkipNextProxySubmit;
    return true;
  }
  function replaySubmitAfterProxyFailure(form, originalSubmit, submitter) {
    if (typeof form.requestSubmit === "function") {
      form.__capgoSkipNextProxySubmit = true;
      try {
        if (submitter !== null && submitter !== void 0) {
          form.requestSubmit(submitter);
        } else {
          form.requestSubmit();
        }
        return;
      } catch (_error) {
        delete form.__capgoSkipNextProxySubmit;
      }
    }
    originalSubmit.call(form);
  }
  function captureXhrReplayState(xhr) {
    var _a, _b, _c, _d;
    return {
      responseType: (_a = xhr.responseType) != null ? _a : "",
      timeout: (_b = xhr.timeout) != null ? _b : 0,
      withCredentials: (_c = xhr.withCredentials) != null ? _c : false,
      overrideMimeType: (_d = xhr.__proxyOverrideMimeType) != null ? _d : null
    };
  }
  function restoreXhrReplayState(xhr, state) {
    xhr.responseType = state.responseType;
    xhr.timeout = state.timeout;
    xhr.withCredentials = state.withCredentials;
    xhr.__proxyOverrideMimeType = state.overrideMimeType;
    if (state.overrideMimeType && typeof xhr.overrideMimeType === "function") {
      xhr.overrideMimeType(state.overrideMimeType);
    }
  }

  // src/proxy-bridge.ts
  (function() {
    const proxyBridge = window.__capgoProxy;
    if (!proxyBridge) {
      return;
    }
    const accessToken = "___CAPGO_PROXY_TOKEN___";
    const proxyRegexSource = "___CAPGO_PROXY_REGEX___";
    let requestCounter = 0;
    let proxyRequestPattern = null;
    if (proxyRegexSource) {
      try {
        proxyRequestPattern = new RegExp(proxyRegexSource);
      } catch (_error) {
        proxyRequestPattern = null;
      }
    }
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
    function hasHeader(headers, headerName) {
      const normalizedHeaderName = headerName.toLowerCase();
      return Object.keys(headers).some((key) => key.toLowerCase() === normalizedHeaderName);
    }
    function normalizeMethod(method) {
      return (method || "GET").toUpperCase();
    }
    function normalizeCredentialsMode(mode) {
      if (mode === "omit" || mode === "include") {
        return mode;
      }
      return "same-origin";
    }
    function getDocumentBaseUrl() {
      if (typeof document !== "undefined" && typeof document.baseURI === "string" && document.baseURI) {
        return document.baseURI;
      }
      return window.location.href;
    }
    function resolveUrl(url) {
      if (url && !url.match(/^[a-zA-Z][a-zA-Z0-9+\-.]*:\/\//)) {
        try {
          return new URL(url, getDocumentBaseUrl()).href;
        } catch (_error) {
          return url;
        }
      }
      return url;
    }
    function methodSupportsRequestBody(method) {
      const normalizedMethod = normalizeMethod(method);
      return normalizedMethod !== "GET" && normalizedMethod !== "HEAD";
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
    function storeInterceptedRequest(url, method, headers, body, credentialsMode) {
      return __async(this, null, function* () {
        const requestId = generateRequestId();
        const normalizedMethod = normalizeMethod(method);
        let normalizedBody = body;
        if (normalizedBody instanceof FormData) {
          const encoded = new Response(normalizedBody);
          const contentType = encoded.headers.get("content-type");
          if (contentType) {
            replaceCapturedHeader(headers, "content-type", contentType);
          }
          normalizedBody = yield encoded.arrayBuffer();
        }
        if (!methodSupportsRequestBody(normalizedMethod)) {
          normalizedBody = null;
        }
        ensureInferredContentType(headers, normalizedBody);
        const base64Body = yield bodyToBase64(normalizedBody);
        if (normalizedBody !== null && normalizedBody !== void 0 && base64Body === null && methodSupportsRequestBody(normalizedMethod)) {
          throw new Error(`[proxy-bridge] Unsupported request body for ${normalizedMethod} proxy replay`);
        }
        proxyBridge.storeRequest(
          accessToken,
          requestId,
          normalizedMethod,
          JSON.stringify(headers),
          base64Body || "",
          normalizeCredentialsMode(credentialsMode)
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
      const resolvedUrl = new URL(url, getDocumentBaseUrl());
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
        getSubmitterAttribute(submitter, "formaction") || form.getAttribute("action") || getDocumentBaseUrl()
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
        if (!shouldProxyBridgeUrl(requestUrl, getDocumentBaseUrl(), proxyRequestPattern)) {
          return false;
        }
        const proxyUrl = yield storeInterceptedRequest(requestUrl, method, headers, body, "include");
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
        let inheritedHeaders = false;
        let body = null;
        let credentialsMode = "same-origin";
        let requestCloneError = null;
        if (input instanceof Request) {
          url = input.url;
          method = input.method;
          credentialsMode = normalizeCredentialsMode(input.credentials);
          inheritedHeaders = true;
          input.headers.forEach((value, key) => {
            headers[key] = value;
          });
          try {
            const cloned = input.clone();
            const buffer = yield cloned.arrayBuffer();
            if (buffer.byteLength > 0) {
              body = buffer;
            }
          } catch (error) {
            requestCloneError = error;
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
            if (inheritedHeaders) {
              Object.keys(headers).forEach((key) => {
                delete headers[key];
              });
              inheritedHeaders = false;
            }
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
        if (requestCloneError != null) {
          return originalFetch.call(window, input, init);
        }
        if (!shouldProxyBridgeUrl(url, getDocumentBaseUrl(), proxyRequestPattern)) {
          return originalFetch.call(window, input, init);
        }
        const signal = (_a = init == null ? void 0 : init.signal) != null ? _a : input instanceof Request ? input.signal : void 0;
        let proxyUrl;
        try {
          proxyUrl = yield storeInterceptedRequest(url, method, headers, body, credentialsMode);
        } catch (_error) {
          return originalFetch.call(window, input, init);
        }
        return originalFetch.call(window, proxyUrl, {
          method: "GET",
          signal
        });
      });
    };
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalXhrSend = XMLHttpRequest.prototype.send;
    const originalXhrAbort = XMLHttpRequest.prototype.abort;
    const originalXhrSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    const originalXhrOverrideMimeType = XMLHttpRequest.prototype.overrideMimeType;
    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
      this.__proxyMethod = method;
      this.__proxyUrl = resolveUrl(url instanceof URL ? url.toString() : url);
      this.__proxyHeaders = {};
      this.__proxyAsync = rest[0] !== false;
      this.__proxyCredentials = this.withCredentials ? "include" : "same-origin";
      this.__proxyUsername = typeof rest[1] === "string" ? rest[1] : null;
      this.__proxyPassword = typeof rest[2] === "string" ? rest[2] : "";
      this.__proxyAborted = false;
      this.__proxyOverrideMimeType = null;
      return originalXhrOpen.apply(this, [method, url, ...rest]);
    };
    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
      if (this.__proxyHeaders) {
        appendCapturedHeader(this.__proxyHeaders, name, value);
      }
      return originalXhrSetRequestHeader.call(this, name, value);
    };
    XMLHttpRequest.prototype.abort = function() {
      this.__proxyAborted = true;
      return originalXhrAbort.call(this);
    };
    if (typeof originalXhrOverrideMimeType === "function") {
      XMLHttpRequest.prototype.overrideMimeType = function(mimeType) {
        this.__proxyOverrideMimeType = mimeType;
        return originalXhrOverrideMimeType.call(this, mimeType);
      };
    }
    XMLHttpRequest.prototype.send = function(body) {
      const xhr = this;
      const method = xhr.__proxyMethod || "GET";
      const url = xhr.__proxyUrl || "";
      const headers = xhr.__proxyHeaders || {};
      const isAsync = xhr.__proxyAsync !== false;
      const credentialsMode = xhr.withCredentials ? "include" : "same-origin";
      const username = xhr.__proxyUsername;
      const password = xhr.__proxyPassword;
      if (typeof username === "string" && username.length > 0 && !hasHeader(headers, "Authorization")) {
        headers["Authorization"] = "Basic " + stringToBase64(username + ":" + (typeof password === "string" ? password : ""));
      }
      function completeSend(proxyUrl) {
        if (xhr.__proxyAborted) {
          return;
        }
        const replayState = captureXhrReplayState(xhr);
        originalXhrOpen.call(xhr, "GET", proxyUrl, true);
        restoreXhrReplayState(xhr, replayState);
        originalXhrSend.call(xhr, null);
      }
      if (!shouldProxyBridgeUrl(url, getDocumentBaseUrl(), proxyRequestPattern)) {
        originalXhrSend.call(xhr, body != null ? body : null);
        return;
      }
      if (!isAsync) {
        console.warn("[proxy-bridge] Synchronous XMLHttpRequest cannot be proxied; falling back to the original request");
        originalXhrSend.call(xhr, body != null ? body : null);
        return;
      }
      storeInterceptedRequest(url, method, headers, body, credentialsMode).then((proxyUrl) => {
        completeSend(proxyUrl);
      }).catch((_error) => {
        if (xhr.__proxyAborted) {
          return;
        }
        console.error("[proxy-bridge] Failed to encode XMLHttpRequest body");
        originalXhrSend.call(xhr, body != null ? body : null);
      });
    };
    const originalFormSubmit = HTMLFormElement.prototype.submit;
    document.addEventListener("submit", (event) => {
      const form = event.target instanceof HTMLFormElement ? event.target : null;
      if (!form) {
        return;
      }
      if (consumeProxySubmitReplayBypass(form)) {
        return;
      }
      const submitter = getSubmitEventSubmitter(event);
      const target = resolveFormTarget(form, submitter);
      const method = resolveFormMethod(form, submitter);
      let requestUrl = resolveFormAction(form, submitter);
      if (method === "GET" || method === "HEAD") {
        requestUrl = appendFormDataToUrl(requestUrl, createFormData(form, submitter));
      }
      if (!shouldProxySubmitRequest(
        event.defaultPrevented,
        canProxyFormTarget(target),
        requestUrl,
        getDocumentBaseUrl(),
        proxyRequestPattern
      )) {
        return;
      }
      event.preventDefault();
      proxyFormSubmission(form, submitter).then((handled) => {
        if (!handled) {
          replaySubmitAfterProxyFailure(form, originalFormSubmit, submitter);
        }
      }).catch((error) => {
        console.error("[proxy-bridge] Failed to proxy form submission", error);
        replaySubmitAfterProxyFailure(form, originalFormSubmit, submitter);
      });
    });
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
