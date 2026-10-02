import { afterEach, beforeEach, describe, expect, it, mock } from 'bun:test';

import { InAppBrowserWeb } from './web';

describe('fullscreen on Web', () => {
  let browser: InAppBrowserWeb;
  let mockWindow: { closed: boolean; location: { href: string }; close: () => void };

  beforeEach(() => {
    mockWindow = {
      closed: false,
      location: { href: '' },
      close: () => {
        mockWindow.closed = true;
      },
    };
    const openMock = mock(() => mockWindow as unknown as Window) as typeof window.open;
    (globalThis as { window?: Window }).window = {
      addEventListener: () => undefined,
      location: { href: 'https://app.test/' },
      open: openMock,
    } as Window;
    globalThis.open = openMock;
    browser = new InAppBrowserWeb();
  });

  afterEach(() => {
    delete (globalThis as { window?: Window }).window;
  });

  it('rejects fullscreen opening instead of reporting a successful native presentation', async () => {
    await expect(browser.openWebView({ url: 'https://example.com', fullscreen: true })).rejects.toMatchObject({
      code: 'UNIMPLEMENTED',
    });
  });

  it('opens a tracked web view when fullscreen is omitted or disabled', async () => {
    await expect(browser.openWebView({ url: 'https://example.com' })).resolves.toEqual({ id: 'web-1' });
    await expect(browser.openWebView({ url: 'https://example.com', fullscreen: false })).resolves.toEqual({
      id: 'web-2',
    });
  });

  it('rejects runtime entry and exit rather than silently changing no state', async () => {
    for (const enabled of [true, false]) {
      await expect(browser.setFullscreen({ enabled, id: 'webview' })).rejects.toMatchObject({
        code: 'UNIMPLEMENTED',
      });
    }
  });

  it('rejects state queries instead of implying native fullscreen is supported', async () => {
    await expect(browser.getFullscreen()).rejects.toMatchObject({ code: 'UNIMPLEMENTED' });
  });
});
