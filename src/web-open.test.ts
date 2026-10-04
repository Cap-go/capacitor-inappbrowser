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
  let openedWindows: (typeof mockWindow)[];
  const realSetInterval = globalThis.setInterval;
  const realClearInterval = globalThis.clearInterval;
  const realOpen = globalThis.open;
  const realFetch = globalThis.fetch;
  const realCreateObjectURL = URL.createObjectURL;
  const realRevokeObjectURL = URL.revokeObjectURL;

  beforeEach(() => {
    // No real 500 ms polling timers leak out of these tests.
    globalThis.setInterval = mock(() => 1) as unknown as typeof setInterval;
    globalThis.clearInterval = mock(() => undefined) as unknown as typeof clearInterval;
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
    for (const win of openedWindows) {
      win.close();
    }
    delete (globalThis as { window?: Window }).window;
    globalThis.setInterval = realSetInterval;
    globalThis.clearInterval = realClearInterval;
    globalThis.open = realOpen;
    globalThis.fetch = realFetch;
    URL.createObjectURL = realCreateObjectURL;
    URL.revokeObjectURL = realRevokeObjectURL;
  });

  const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

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
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'world' }, 'https://example.com');
  });

  it('passes popup window features when web.popup is set', async () => {
    await browser.openWebView({ url: 'https://example.com', web: { popup: true, width: 390, height: 844 } });
    expect(globalThis.open).toHaveBeenCalledWith('', '_blank', 'popup=yes,width=390,height=844');
  });

  it('loads header requests into an isolated sandboxed document and revokes the object URL on close', async () => {
    const fetchMock = mock(async () => ({
      ok: true,
      status: 200,
      url: 'https://example.com/final/page?x=1',
      text: async () => '<html><HEAD lang="en"><title>t</title></HEAD><body>hi</body></html>',
    }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    let blobText = '';
    URL.createObjectURL = mock((blob: Blob) => {
      void blob.text().then((t) => (blobText = t));
      return 'blob:https://app.test/1';
    }) as typeof URL.createObjectURL;
    URL.revokeObjectURL = mock(() => undefined) as typeof URL.revokeObjectURL;

    await browser.openWebView({ url: 'https://example.com/start', headers: { Authorization: 'Bearer t' } });
    await flush();
    await flush();

    expect(fetchMock).toHaveBeenCalledWith('https://example.com/start', { headers: { Authorization: 'Bearer t' } });
    expect(mockWindow.location.href).toBe('blob:https://app.test/1');
    expect(blobText).toContain('sandbox="allow-scripts');
    expect(blobText).not.toContain('allow-same-origin');
    expect(blobText).toContain(
      '&lt;HEAD lang=&quot;en&quot;&gt;&lt;base href=&quot;https://example.com/final/page?x=1&quot;&gt;',
    );

    await browser.close();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:https://app.test/1');
  });

  it('closes the window when the header request fails', async () => {
    globalThis.fetch = mock(async () => ({
      ok: false,
      status: 401,
      url: '',
      text: async () => '',
    })) as unknown as typeof fetch;
    const errorSpy = console.error;
    console.error = () => undefined;
    try {
      await browser.openWebView({ url: 'https://example.com', headers: { 'X-Test': '1' } });
      await flush();
      await flush();
    } finally {
      console.error = errorSpy;
    }
    expect(mockWindow.closed).toBe(true);
  });

  it('targets the origin of the last message received after a redirect', async () => {
    await browser.openWebView({ url: 'https://example.com' });
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://login.example.org',
      data: { ready: true },
    } as unknown as MessageEvent);
    await browser.postMessage({ detail: { hello: 'again' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'again' }, 'https://login.example.org');
  });
});
