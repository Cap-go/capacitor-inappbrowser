export function findCapturedHeaderKey(headers: Record<string, string>, headerName: string): string | null {
  const normalizedHeaderName = headerName.toLowerCase();
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === normalizedHeaderName) {
      return key;
    }
  }
  return null;
}

export const NATIVE_PROXY_ERROR_STATUS = 599;
export const NATIVE_PROXY_ERROR_HEADER = 'x-capgo-proxy-error';

export function isNativeProxyErrorResponse(response: Pick<Response, 'status' | 'headers'>): boolean {
  return response.status === 599 && response.headers.get('x-capgo-proxy-error') !== null;
}

export function nativeProxyErrorMessage(response: Pick<Response, 'headers'>): string {
  const nativeErrorHeader = response.headers.get('x-capgo-proxy-error');
  if (!nativeErrorHeader) {
    return 'Network request failed';
  }
  const nativeMessage = nativeErrorHeader.trim();
  return nativeMessage || 'Network request failed';
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
  } catch {
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
    } catch {
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

export type JavascriptBridgeOriginMode = 'unrestricted' | 'disabled' | 'allowlist';

function defaultPortForScheme(scheme: string): number {
  if (scheme === 'http') {
    return 80;
  }
  if (scheme === 'https') {
    return 443;
  }
  return -1;
}

export function originFromUrl(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  try {
    const parsed = new URL(url);
    const scheme = parsed.protocol.replace(/:$/, '').toLowerCase();
    const host = parsed.hostname.toLowerCase();
    if (!scheme || !host) {
      return null;
    }
    const explicitPort = parsed.port ? Number(parsed.port) : -1;
    const defaultPort = defaultPortForScheme(scheme);
    const normalizedPort = explicitPort > 0 ? explicitPort : defaultPort;
    if (normalizedPort === defaultPort) {
      return `${scheme}://${host}`;
    }
    return `${scheme}://${host}:${normalizedPort}`;
  } catch (error: unknown) {
    console.debug('[proxy-bridge] Invalid origin URL', error);
    return null;
  }
}

function normalizeOriginCandidate(origin: string): string | null {
  const trimmed = origin.trim();
  if (!trimmed) {
    return null;
  }
  const candidate = trimmed.includes('://') ? trimmed : `https://${trimmed}`;
  return originFromUrl(candidate);
}

function matchesHostWildcard(host: string, patternHost: string): boolean {
  if (!patternHost.startsWith('*.')) {
    return false;
  }
  const baseDomain = patternHost.slice(2);
  if (!baseDomain || host.toLowerCase() === baseDomain.toLowerCase()) {
    return false;
  }
  return host.toLowerCase().endsWith(`.${baseDomain.toLowerCase()}`);
}

export function matchesJavascriptBridgeAllowRule(pageOrigin: string, rule: string): boolean {
  const normalizedRule = normalizeOriginCandidate(rule);
  if (!normalizedRule) {
    return false;
  }
  if (pageOrigin.toLowerCase() === normalizedRule.toLowerCase()) {
    return true;
  }

  const originParts = new URL(pageOrigin);
  const ruleParts = new URL(normalizedRule);
  const originScheme = originParts.protocol.replace(/:$/, '').toLowerCase();
  const ruleScheme = ruleParts.protocol.replace(/:$/, '').toLowerCase();
  const originHost = originParts.hostname.toLowerCase();
  const ruleHost = ruleParts.hostname.toLowerCase();
  if (originScheme !== ruleScheme) {
    return false;
  }

  const originPort = originParts.port ? Number(originParts.port) : defaultPortForScheme(originScheme);
  const rulePort = ruleParts.port ? Number(ruleParts.port) : defaultPortForScheme(ruleScheme);
  if (originPort !== rulePort) {
    return false;
  }

  if (originHost === ruleHost) {
    return true;
  }

  return matchesHostWildcard(originHost, ruleHost);
}

export function parseJavascriptBridgeOriginRules(rulesJson: string): string[] {
  if (!rulesJson) {
    return [];
  }
  try {
    const parsed = JSON.parse(rulesJson) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0);
  } catch (error: unknown) {
    console.debug('[proxy-bridge] Invalid bridge origin rules JSON', error);
    return [];
  }
}

export function isJavascriptBridgeDocumentOriginAllowed(
  documentUrl: string,
  mode: JavascriptBridgeOriginMode,
  allowlistRules: string[],
): boolean {
  if (mode === 'unrestricted') {
    return true;
  }
  if (mode === 'disabled') {
    return false;
  }
  const origin = originFromUrl(documentUrl);
  if (!origin) {
    return false;
  }
  return allowlistRules.some((rule) => matchesJavascriptBridgeAllowRule(origin, rule));
}
