import { afterEach, beforeEach, describe, expect, it, mock } from 'bun:test';

import { InAppBrowserWeb } from './web';

describe('openWebView on web', () => {
  let browser: InAppBrowserWeb;
  let mockWindow: {
    closed: boolean;
    location: { href: string };
    document: { open: () => void; write: (html: string) => void; close: () => void };
    close: () => void;
    postMessage: ReturnType<typeof mock>;
  };
  let openedWindows: typeof mockWindow[];

  beforeEach(() => {
    const openMockHolder: { fn?: typeof window.open } = {};
    openedWindows = [];
    mockWindow = {
      closed: false,
      location: { href: '' },
      document: {
        open: () => undefined,
        write: () => undefined,
        close: () => undefined,
      },
      close: () => {
        mockWindow.closed = true;
      },
      postMessage: mock(() => undefined),
    };
    openMockHolder.fn = mock(() => {
      const win = {
        closed: false,
        location: { href: '' },
        document: {
          open: () => undefined,
          write: () => undefined,
          close: () => undefined,
        },
        close: () => {
          win.closed = true;
        },
        postMessage: mock(() => undefined),
      };
      openedWindows.push(win);
      mockWindow = win;
      return win as unknown as Window;
    }) as typeof window.open;
    (globalThis as { window?: Window }).window = {
      addEventListener: () => undefined,
      location: { href: 'https://app.test/' },
      open: openMockHolder.fn,
    } as Window;
    globalThis.open = openMockHolder.fn;
    browser = new InAppBrowserWeb();
  });

  afterEach(() => {
    delete (globalThis as { window?: Window }).window;
  });

  it('opens a blank window synchronously then navigates', async () => {
    await expect(browser.openWebView({ url: 'https://example.com/page' })).resolves.toEqual({ id: 'web-1' });
    expect(globalThis.open).toHaveBeenCalledWith('', '_blank', '');
    expect(mockWindow.location.href).toBe('https://example.com/page');
  });

  it('closes the newest open tracked window when close() has no id', async () => {
    await browser.openWebView({ url: 'https://example.com/a' });
    await browser.openWebView({ url: 'https://example.com/b' });
    await browser.close();
    expect(mockWindow.closed).toBe(true);
  });

  it('rejects cookie reads on web', async () => {
    await expect(browser.getCookies({ url: 'https://example.com' })).rejects.toMatchObject({
      code: 'UNIMPLEMENTED',
    });
  });

  it('rejects urlChangeEvent listeners on web', async () => {
    await expect(browser.addListener('urlChangeEvent', () => undefined)).rejects.toMatchObject({
      code: 'UNIMPLEMENTED',
    });
  });

  it('forwards postMessage to the tracked window', async () => {
    await browser.openWebView({ url: 'https://example.com' });
    await browser.postMessage({ detail: { hello: 'world' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'world' }, '*');
  });
});
