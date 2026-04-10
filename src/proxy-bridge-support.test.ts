import { describe, expect, it } from 'bun:test';

import {
  appendCapturedHeader,
  ensureInferredContentType,
  inferContentTypeFromBody,
  replaceCapturedHeader,
} from './proxy-bridge-support';

describe('proxy bridge header helpers', () => {
  it('appends repeated xhr headers case-insensitively', () => {
    const headers = { Accept: 'application/json' };

    appendCapturedHeader(headers, 'accept', 'text/plain');

    expect(headers).toEqual({ Accept: 'application/json, text/plain' });
  });

  it('replaces captured headers case-insensitively', () => {
    const headers = { 'Content-Type': 'text/plain' };

    replaceCapturedHeader(headers, 'content-type', 'multipart/form-data; boundary=test');

    expect(headers).toEqual({ 'content-type': 'multipart/form-data; boundary=test' });
  });

  it('adds inferred content type only when one is missing', () => {
    const inferredHeaders: Record<string, string> = {};
    const explicitHeaders = { 'Content-Type': 'application/json' };

    ensureInferredContentType(inferredHeaders, new URLSearchParams({ q: 'grailed' }));
    ensureInferredContentType(explicitHeaders, 'kept as-is');

    expect(inferredHeaders).toEqual({ 'content-type': 'application/x-www-form-urlencoded;charset=UTF-8' });
    expect(explicitHeaders).toEqual({ 'Content-Type': 'application/json' });
  });
});

describe('proxy bridge content type inference', () => {
  it('infers browser-managed content types for common body types', () => {
    expect(inferContentTypeFromBody('hello')).toBe('text/plain;charset=UTF-8');
    expect(inferContentTypeFromBody(new URLSearchParams({ q: 'grailed' }))).toBe(
      'application/x-www-form-urlencoded;charset=UTF-8',
    );
    expect(inferContentTypeFromBody(new Blob(['hello'], { type: 'text/custom' }))).toBe('text/custom');
  });

  it('returns null for bodies without implicit content types', () => {
    expect(inferContentTypeFromBody(new Uint8Array([1, 2, 3]))).toBeNull();
  });
});
