import { describe, expect, it } from 'bun:test';

import { shouldEmitDeepLinkPreventedEvent } from './deep-link-prevented-support';

describe('shouldEmitDeepLinkPreventedEvent', () => {
  it('emits for blocked custom schemes and OS handlers', () => {
    expect(shouldEmitDeepLinkPreventedEvent('myapp://oauth')).toBe(true);
    expect(shouldEmitDeepLinkPreventedEvent('tel:+15555550123')).toBe(true);
    expect(shouldEmitDeepLinkPreventedEvent('mailto:test@example.com')).toBe(true);
  });

  it('emits for authorized HTTP(S) links', () => {
    expect(shouldEmitDeepLinkPreventedEvent('https://partner.example/path', ['https://partner.example'])).toBe(true);
  });

  it('skips normal in-webview HTTP navigation', () => {
    expect(shouldEmitDeepLinkPreventedEvent('https://example.com/page')).toBe(false);
    expect(shouldEmitDeepLinkPreventedEvent('about:blank')).toBe(false);
  });
});
