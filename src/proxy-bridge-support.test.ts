import { describe, expect, it } from 'bun:test';

import {
  appendCapturedHeader,
  captureXhrReplayState,
  ensureInferredContentType,
  getSubmitEventSubmitter,
  inferContentTypeFromBody,
  replaceCapturedHeader,
  restoreXhrReplayState,
  resolveProxyBridgeUrl,
  shouldProxyBridgeUrl,
  shouldProxySubmitEvent,
  shouldProxySubmitRequest,
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

describe('proxy bridge xhr replay helpers', () => {
  it('captures xhr request settings before the proxy replay reopens the request', () => {
    expect(
      captureXhrReplayState({
        responseType: 'arraybuffer',
        timeout: 4500,
        withCredentials: true,
      }),
    ).toEqual({
      responseType: 'arraybuffer',
      timeout: 4500,
      withCredentials: true,
    });
  });

  it('restores xhr request settings after the proxy replay reopens the request', () => {
    const xhr = {
      responseType: '',
      timeout: 0,
      withCredentials: false,
    };

    restoreXhrReplayState(xhr, {
      responseType: 'blob',
      timeout: 9000,
      withCredentials: true,
    });

    expect(xhr).toEqual({
      responseType: 'blob',
      timeout: 9000,
      withCredentials: true,
    });
  });
});

describe('proxy bridge submit helpers', () => {
  it('reads the submitter without requiring SubmitEvent support', () => {
    const submitter = { id: 'submit-button' };
    const event = { submitter } as Event & { submitter: { id: string } };

    expect(getSubmitEventSubmitter(event)).toBe(submitter);
  });

  it('reads the submitter from a submit event instance when a constructor is available', () => {
    const submitter = { id: 'submit-button' };

    class FakeSubmitEvent extends Event {
      submitter: unknown;

      constructor(currentSubmitter: unknown) {
        super('submit');
        this.submitter = currentSubmitter;
      }
    }

    expect(getSubmitEventSubmitter(new FakeSubmitEvent(submitter), FakeSubmitEvent)).toBe(submitter);
  });

  it('skips proxy interception when the page already canceled submission', () => {
    expect(shouldProxySubmitEvent(false, true)).toBe(true);
    expect(shouldProxySubmitEvent(true, true)).toBe(false);
    expect(shouldProxySubmitEvent(false, false)).toBe(false);
  });

  it('skips proxy interception before preventDefault when the target url misses the regex', () => {
    expect(
      shouldProxySubmitRequest(
        false,
        true,
        'https://cdn.example.com/login',
        'https://www.grailed.com/users/sign_up',
        /accounts\.example\.com/,
      ),
    ).toBe(false);
    expect(
      shouldProxySubmitRequest(
        false,
        true,
        'https://accounts.example.com/login',
        'https://www.grailed.com/users/sign_up',
        /accounts\.example\.com/,
      ),
    ).toBe(true);
  });
});

describe('proxy bridge url helpers', () => {
  it('resolves relative urls against the page location', () => {
    expect(resolveProxyBridgeUrl('/api/private', 'https://www.grailed.com/users/sign_up')).toBe(
      'https://www.grailed.com/api/private',
    );
  });

  it('skips non-http urls and regex misses', () => {
    expect(shouldProxyBridgeUrl('mailto:test@example.com', 'https://www.grailed.com', null)).toBe(false);
    expect(
      shouldProxyBridgeUrl('https://cdn.example.com/script.js', 'https://www.grailed.com', /api\.example\.com/),
    ).toBe(false);
    expect(shouldProxyBridgeUrl('https://api.example.com/login', 'https://www.grailed.com', /api\.example\.com/)).toBe(
      true,
    );
  });
});
