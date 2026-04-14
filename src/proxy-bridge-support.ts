export function findCapturedHeaderKey(headers: Record<string, string>, headerName: string): string | null {
  const normalizedHeaderName = headerName.toLowerCase();
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === normalizedHeaderName) {
      return key;
    }
  }
  return null;
}

export function replaceCapturedHeader(headers: Record<string, string>, name: string, value: string): void {
  const existingKey = findCapturedHeaderKey(headers, name);
  if (existingKey && existingKey !== name) {
    delete headers[existingKey];
  }
  headers[name] = value;
}

export function appendCapturedHeader(headers: Record<string, string>, name: string, value: string): void {
  const existingKey = findCapturedHeaderKey(headers, name);
  if (!existingKey) {
    headers[name] = value;
    return;
  }
  headers[existingKey] = headers[existingKey] ? `${headers[existingKey]}, ${value}` : value;
}

export function inferContentTypeFromBody(body: unknown): string | null {
  if (typeof body === 'string') {
    return 'text/plain;charset=UTF-8';
  }
  if (body instanceof URLSearchParams) {
    return 'application/x-www-form-urlencoded;charset=UTF-8';
  }
  if (typeof Blob !== 'undefined' && body instanceof Blob) {
    return body.type || null;
  }
  return null;
}

export function ensureInferredContentType(headers: Record<string, string>, body: unknown): void {
  if (findCapturedHeaderKey(headers, 'content-type')) {
    return;
  }
  const inferredContentType = inferContentTypeFromBody(body);
  if (inferredContentType) {
    headers['content-type'] = inferredContentType;
  }
}

export function resolveProxyBridgeUrl(rawUrl: string, baseUrl: string): string | null {
  try {
    return new URL(rawUrl, baseUrl).href;
  } catch (_error) {
    return null;
  }
}

export function shouldProxyBridgeUrl(rawUrl: string, baseUrl: string, urlRegex?: RegExp | null): boolean {
  const resolvedUrl = resolveProxyBridgeUrl(rawUrl, baseUrl);
  if (!resolvedUrl) {
    return false;
  }

  const protocol = new URL(resolvedUrl).protocol.toLowerCase();
  if (protocol !== 'http:' && protocol !== 'https:') {
    return false;
  }

  return !urlRegex || urlRegex.test(resolvedUrl);
}

type SubmitEventLike = Event & { submitter?: unknown };

export function getSubmitEventSubmitter(
  event: SubmitEventLike,
  submitEventCtor?: { prototype: Event; new (...args: any[]): Event },
): unknown | null {
  const resolvedSubmitEventCtor =
    submitEventCtor ??
    (typeof SubmitEvent !== 'undefined'
      ? (SubmitEvent as unknown as { prototype: Event; new (...args: any[]): Event })
      : undefined);

  if (resolvedSubmitEventCtor && event instanceof resolvedSubmitEventCtor) {
    return event.submitter ?? null;
  }

  if ('submitter' in event) {
    return event.submitter ?? null;
  }

  return null;
}

export function shouldProxySubmitEvent(defaultPrevented: boolean, canProxyTarget: boolean): boolean {
  return !defaultPrevented && canProxyTarget;
}

export function shouldProxySubmitRequest(
  defaultPrevented: boolean,
  canProxyTarget: boolean,
  rawUrl: string,
  baseUrl: string,
  urlRegex?: RegExp | null,
): boolean {
  return shouldProxySubmitEvent(defaultPrevented, canProxyTarget) && shouldProxyBridgeUrl(rawUrl, baseUrl, urlRegex);
}

type SubmitReplayForm = {
  requestSubmit?: (submitter?: unknown) => void;
  __capgoSkipNextProxySubmit?: boolean;
};

export function consumeProxySubmitReplayBypass(form: SubmitReplayForm): boolean {
  if (!form.__capgoSkipNextProxySubmit) {
    return false;
  }

  delete form.__capgoSkipNextProxySubmit;
  return true;
}

export function replaySubmitAfterProxyFailure(
  form: SubmitReplayForm,
  originalSubmit: (this: HTMLFormElement) => void,
  submitter?: unknown | null,
): void {
  if (typeof form.requestSubmit === 'function') {
    form.__capgoSkipNextProxySubmit = true;
    try {
      if (submitter !== null && submitter !== undefined) {
        form.requestSubmit(submitter);
      } else {
        form.requestSubmit();
      }
      return;
    } catch (_error) {
      delete form.__capgoSkipNextProxySubmit;
    }
  }

  originalSubmit.call(form as HTMLFormElement);
}

type XhrReplayStateSource = {
  responseType?: string;
  timeout?: number;
  withCredentials?: boolean;
  __proxyOverrideMimeType?: string | null;
  overrideMimeType?: (mime: string) => void;
};

export type XhrReplayState = {
  responseType: string;
  timeout: number;
  withCredentials: boolean;
  overrideMimeType: string | null;
};

export function captureXhrReplayState(xhr: XhrReplayStateSource): XhrReplayState {
  return {
    responseType: xhr.responseType ?? '',
    timeout: xhr.timeout ?? 0,
    withCredentials: xhr.withCredentials ?? false,
    overrideMimeType: xhr.__proxyOverrideMimeType ?? null,
  };
}

export function restoreXhrReplayState(xhr: XhrReplayStateSource, state: XhrReplayState): void {
  xhr.responseType = state.responseType;
  xhr.timeout = state.timeout;
  xhr.withCredentials = state.withCredentials;
  xhr.__proxyOverrideMimeType = state.overrideMimeType;
  if (state.overrideMimeType && typeof xhr.overrideMimeType === 'function') {
    xhr.overrideMimeType(state.overrideMimeType);
  }
}
