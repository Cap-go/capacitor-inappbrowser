import { afterEach, beforeEach, describe, expect, it, mock } from 'bun:test';

import { InAppBrowserWeb, mayAdoptPostMessageOrigin } from './web';
import { CAPGO_CONSOLE_POST_TYPE } from './web-console-capture';

describe('openWebView on web', () => {
  let browser: InAppBrowserWeb;
  let mockWindow: {
    closed: boolean;
    location: { href: string; origin?: string };
    document: {
      open: () => void;
      write: (html: string) => void;
      close: () => void;
      head: { appendChild: ReturnType<typeof mock> };
      createElement: ReturnType<typeof mock>;
    };
    close: () => void;
    postMessage: ReturnType<typeof mock>;
  };
  let consoleEvents: unknown[];
  let openedWindows: (typeof mockWindow)[];
  const realSetInterval = globalThis.setInterval;
  const realClearInterval = globalThis.clearInterval;
  const realOpen = globalThis.open;
  const realFetch = globalThis.fetch;

  beforeEach(() => {
    // No real 500 ms polling timers leak out of these tests.
    globalThis.setInterval = mock(() => 1) as unknown as typeof setInterval;
    globalThis.clearInterval = mock(() => undefined) as unknown as typeof clearInterval;
    const openMockHolder: { fn?: typeof window.open } = {};
    openedWindows = [];
    consoleEvents = [];
    const createScriptElement = () => ({
      text: '',
      remove: mock(() => undefined),
    });
    const markConsoleCaptureInstalled = (win: Record<string, unknown>, element: { text?: string }) => {
      if (element.text?.includes('__capgoInAppBrowserConsoleCaptureInstalled')) {
        win.__capgoInAppBrowserConsoleCaptureInstalled = true;
      }
    };
    mockWindow = {
      closed: false,
      location: { href: 'https://example.com/page', origin: 'https://example.com' },
      document: {
        open: () => undefined,
        write: () => undefined,
        close: () => undefined,
        head: {
          appendChild: mock((element: { text?: string }) => {
            markConsoleCaptureInstalled(mockWindow, element);
          }),
        },
        createElement: mock(() => createScriptElement()),
      },
      close: () => {
        mockWindow.closed = true;
      },
      postMessage: mock(() => undefined),
    };
    openMockHolder.fn = mock(() => {
      const win = {
        closed: false,
        location: { href: 'https://example.com/page', origin: 'https://example.com' },
        document: {
          open: () => undefined,
          write: () => undefined,
          close: () => undefined,
          head: {
            appendChild: mock((element: { text?: string }) => {
              markConsoleCaptureInstalled(win, element);
            }),
          },
          createElement: mock(() => createScriptElement()),
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
    browser.addListener('consoleMessage', (event) => {
      consoleEvents.push(event);
    });
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

  it('forwards postMessage to the tracked window', async () => {
    await browser.openWebView({ url: 'https://example.com' });
    await browser.postMessage({ detail: { hello: 'world' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'world' }, 'https://example.com');
  });

  it('passes popup window features when web.popup is set', async () => {
    await browser.openWebView({ url: 'https://example.com', web: { popup: true, width: 390, height: 844 } });
    expect(globalThis.open).toHaveBeenCalledWith('', '_blank', 'popup=yes,width=390,height=844');
  });

  it('warns and navigates to the real URL when headers are provided', async () => {
    const fetchMock = mock(async () => ({
      ok: true,
      status: 200,
      url: 'https://example.com/',
      text: async () => '',
    }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    const warnSpy = mock(() => undefined);
    const realWarn = console.warn;
    console.warn = warnSpy as typeof console.warn;
    try {
      await browser.openWebView({ url: 'https://example.com/start', headers: { Authorization: 'Bearer t' } });
    } finally {
      console.warn = realWarn;
    }
    expect(warnSpy).toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(mockWindow.location.href).toBe('https://example.com/start');
    await browser.postMessage({ detail: { ping: true } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ ping: true }, 'https://example.com');
  });

  it('does not warn when headers is empty', async () => {
    const warnSpy = mock(() => undefined);
    const realWarn = console.warn;
    console.warn = warnSpy as typeof console.warn;
    try {
      await browser.openWebView({ url: 'https://example.com/', headers: {} });
    } finally {
      console.warn = realWarn;
    }
    expect(warnSpy).not.toHaveBeenCalled();
    expect(mockWindow.location.href).toBe('https://example.com/');
  });

  it('targets the origin of the last message received after a same-site redirect', async () => {
    await browser.openWebView({ url: 'https://app.example.com' });
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://login.app.example.com',
      data: { ready: true },
    } as unknown as MessageEvent);
    await browser.postMessage({ detail: { hello: 'again' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'again' }, 'https://login.app.example.com');
  });

  it('rejects parent hostnames for postMessage target adoption', () => {
    expect(mayAdoptPostMessageOrigin('https://login.app.example.com', 'https://app.example.com')).toBe(false);
  });

  it('rejects scheme downgrades for postMessage target adoption', () => {
    expect(mayAdoptPostMessageOrigin('https://app.example.com', 'http://login.app.example.com')).toBe(false);
  });

  it('rejects a different port for postMessage target adoption', () => {
    expect(mayAdoptPostMessageOrigin('https://app.example.com', 'https://login.app.example.com:8443')).toBe(false);
    expect(mayAdoptPostMessageOrigin('https://app.example.com:8443', 'https://login.app.example.com:8443')).toBe(true);
  });

  it('installs console capture when captureConsoleLogs is true', async () => {
    await browser.openWebView({ url: 'https://example.com/page', captureConsoleLogs: true });
    expect(mockWindow.document.createElement).toHaveBeenCalledWith('script');
  });

  it('does not install console capture by default', async () => {
    await browser.openWebView({ url: 'https://example.com/page' });
    expect(mockWindow.document.createElement).not.toHaveBeenCalled();
  });

  it('emits consoleMessage from child postMessage payloads', async () => {
    await browser.openWebView({ url: 'https://example.com/page', captureConsoleLogs: true });
    (
      mockWindow as { __capgoInAppBrowserConsoleCaptureInstalled?: boolean }
    ).__capgoInAppBrowserConsoleCaptureInstalled = true;
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://example.com',
      data: {
        type: CAPGO_CONSOLE_POST_TYPE,
        level: 'log',
        message: 'feature-smoke-console-ok',
      },
    } as unknown as MessageEvent);
    expect(consoleEvents).toEqual([{ id: 'web-1', level: 'log', message: 'feature-smoke-console-ok' }]);
  });

  it('ignores console postMessage payloads when captureConsoleLogs is false', async () => {
    await browser.openWebView({ url: 'https://example.com/page' });
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://example.com',
      data: {
        type: CAPGO_CONSOLE_POST_TYPE,
        level: 'log',
        message: 'should-not-emit',
      },
    } as unknown as MessageEvent);
    expect(consoleEvents).toEqual([]);
  });

  it('ignores console postMessage payloads from disallowed origins', async () => {
    await browser.openWebView({ url: 'https://example.com/page', captureConsoleLogs: true });
    (
      mockWindow as { __capgoInAppBrowserConsoleCaptureInstalled?: boolean }
    ).__capgoInAppBrowserConsoleCaptureInstalled = true;
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://evil.example.org',
      data: {
        type: CAPGO_CONSOLE_POST_TYPE,
        level: 'log',
        message: 'forged',
      },
    } as unknown as MessageEvent);
    expect(consoleEvents).toEqual([]);
  });

  it('ignores forged console payloads when capture hooks are not active', async () => {
    await browser.openWebView({ url: 'https://example.com/page', captureConsoleLogs: true });
    delete (mockWindow as { __capgoInAppBrowserConsoleCaptureInstalled?: boolean })
      .__capgoInAppBrowserConsoleCaptureInstalled;
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://example.com',
      data: {
        type: CAPGO_CONSOLE_POST_TYPE,
        level: 'log',
        message: 'forged-without-hooks',
      },
    } as unknown as MessageEvent);
    expect(consoleEvents).toEqual([]);
  });

  it('ignores message origins outside the opened site', async () => {
    await browser.openWebView({ url: 'https://app.example.com' });
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://evil.example.org',
      data: { forged: true },
    } as unknown as MessageEvent);
    await browser.postMessage({ detail: { hello: 'safe' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'safe' }, 'https://app.example.com');
  });
});
