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
